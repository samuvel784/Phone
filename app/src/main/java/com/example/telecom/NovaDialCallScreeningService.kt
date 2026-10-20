/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ContactCache
import com.example.data.AppDatabase
import com.example.data.DialerDao
import com.example.util.BlockedNumberContractManager
import com.example.util.CallScreenPermissionHelper
import com.example.util.PhoneNumberCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screens every incoming call BEFORE it rings:
 *  1. Emergency numbers are never touched.
 *  2. Numbers the user blocked, and numbers in the offline spam database, are rejected.
 *  3. Focus mode, protection level, VIP / favorites / repeat callers decide whether the call is silenced.
 * A silenced call still appears in the call log as a missed call.
 *
 * Latency contract: Telecom expects one response promptly. All work is on IO, settings are read
 * once, the number is normalised once, contact/VIP/repeat facts are only fetched when the active
 * policy can actually use them, and the final response is submitted at most once. If the deadline
 * expires the call is allowed (fail-open) instead of being blocked by a slow provider.
 */
class NovaDialCallScreeningService : CallScreeningService() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val dao: DialerDao by lazy { AppDatabase.getDatabase(applicationContext).dialerDao() }

    override fun onScreenCall(callDetails: Call.Details) {
        val startedAt = SystemClock.elapsedRealtime()
        val trace = ScreeningTrace(startedAt)
        val responded = AtomicBoolean(false)
        fun respondOnce(response: CallResponse) {
            if (!responded.compareAndSet(false, true)) return
            try {
                respondToCall(callDetails, response)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to submit screening response", e)
            }
        }

        val isIncoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        if (!isIncoming) {
            respondOnce(CallResponse.Builder().build())
            return
        }

        val number = NormalizedNumber.from(callDetails.handle?.schemeSpecificPart.orEmpty())
        if (number.raw.isBlank()) {
            respondOnce(CallResponse.Builder().build())
            return
        }

        scope.launch {
            val decision = try {
                withTimeoutOrNull(SCREENING_DEADLINE_MS) { decide(number, trace) }
            } catch (e: Exception) {
                Log.e(TAG, "Error screening call (${number.redacted})", e)
                null
            } ?: ScreeningDecision(CallResponse.Builder().build(), "timeoutOrError")

            Log.d(
                TAG,
                "screen ${number.redacted} decision=${decision.outcome} " +
                    "totalMs=${SystemClock.elapsedRealtime() - startedAt} stages=${trace.summary()}"
            )
            respondOnce(decision.response)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun decide(number: NormalizedNumber, trace: ScreeningTrace): ScreeningDecision {
        val allow = CallResponse.Builder().build()
        val context = applicationContext

        // 1. Emergency numbers: never blocked, never silenced. Check the local short-list first
        //    and only ask TelephonyManager when it is not an obvious match.
        if (isEmergency(number)) {
            trace.mark("emergency")
            return ScreeningDecision(allow, "emergency")
        }
        trace.mark("emergency")

        // 2. Your blocklist and offline spam database. Both checks run concurrently because they
        //    touch different stores (system blocklist / Room) and either one can reject the call.
        val (blocked, spam) = coroutineScope {
            val blockedCheck = async {
                try {
                    BlockedNumberContractManager.isBlocked(context, number.raw, dao)
                } catch (_: Exception) {
                    false
                }
            }
            val spamCheck = async {
                try {
                    dao.isSpamNumberAny(number.variants)
                } catch (_: Exception) {
                    false
                }
            }
            blockedCheck.await() to spamCheck.await()
        }
        trace.mark("blockAndSpam")

        if (blocked || spam) {
            Log.d(TAG, "Rejecting blocked/spam call from ${number.redacted}")
            return ScreeningDecision(
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .build(),
                if (blocked) "blocked" else "spam"
            )
        }

        // Read all policy inputs once. The old path read SharedPreferences and performed a
        // ContactsProvider lookup several times per call, including lookups the active policy
        // could never use.
        val focusMode = CallProtectionPrefs.getFocusMode(context)
        val protectionLevel = CallProtectionPrefs.getProtectionLevel(context)
        val quietHours = DoNotDisturbPrefs.isQuietHoursNow(context)
        val needContact = quietHours ||
            focusMode == FocusMode.SAVED_CONTACTS_ONLY ||
            focusMode == FocusMode.SILENCE_ALL ||
            (focusMode == FocusMode.OFF && protectionLevel != ProtectionLevel.STANDARD)
        val needVip = quietHours ||
            focusMode != FocusMode.OFF ||
            protectionLevel != ProtectionLevel.STANDARD
        val repeatAllowed = if (focusMode == FocusMode.OFF && protectionLevel != ProtectionLevel.STANDARD) {
            CallProtectionPrefs.isRepeatCallerAllowed(context)
        } else {
            false
        }

        val contactFacts = if (needContact) lookupContact(context, number.raw) else ContactFacts.EMPTY
        trace.mark("contact")

        val isVip = needVip && vipNumbers(context).any {
            PhoneNumberCompat.numbersMatch(it, number.raw)
        }
        trace.mark("vip")

        // 2.5 Do Not Disturb schedule: silence everyone except saved contacts and VIPs.
        if (quietHours && !contactFacts.isSaved && !isVip) {
            Log.d(TAG, "DND quiet hours: silencing ${number.redacted}")
            trace.mark("dnd")
            return ScreeningDecision(silenced(number.raw), "dndSilence")
        }

        val isRepeatCaller = repeatAllowed && isRepeatCaller(context, number)
        trace.mark("repeat")

        // 3. Focus mode and protection level
        val result = CallProtectionPolicy.evaluateCall(
            phoneNumber = number.raw,
            callerName = null,
            isSavedContact = contactFacts.isSaved,
            isVip = isVip,
            isFavorite = contactFacts.isFavorite,
            isRepeatCaller = isRepeatCaller,
            focusMode = focusMode,
            protectionLevel = protectionLevel,
            isEmergencyNumber = false
        )
        Log.d(TAG, "Policy for ${number.redacted}: silence=${result.shouldSilence} (${result.reason})")
        trace.mark("policy")

        return ScreeningDecision(
            if (result.shouldSilence) silenced(number.raw) else allow,
            if (result.shouldSilence) "silence" else "allow"
        )
    }

    private fun silenced(number: String): CallResponse {
        CallDecisionStore.markSilenced(number)
        val builder = CallResponse.Builder()
        // setSilenceCall exists from Android 10; on older versions the call simply rings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setSilenceCall(true)
        return builder.build()
    }

    private fun isEmergency(number: NormalizedNumber): Boolean {
        if (number.digits in EMERGENCY_NUMBERS) return true
        return PhoneNumberCompat.isEmergencyNumber(applicationContext, number.raw)
    }

    /** Returns contact facts from the in-memory cache first, then the phone's real address book. */
    private fun lookupContact(context: Context, number: String): ContactFacts {
        ContactCache.getContact(number)?.let { cached ->
            return ContactFacts(isSaved = true, isFavorite = cached.favorite)
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return ContactFacts.EMPTY

        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.STARRED), null, null, null)?.use { c ->
                if (c.moveToFirst()) ContactFacts(isSaved = true, isFavorite = c.getInt(0) == 1)
                else ContactFacts.EMPTY
            } ?: ContactFacts.EMPTY
        } catch (_: Exception) {
            ContactFacts.EMPTY
        }
    }

    private fun vipNumbers(context: Context): Set<String> = try {
        CallProtectionPrefs.getVipNumbers(context)
    } catch (_: Exception) {
        emptySet()
    }

    /** True if the same number called in the last 3 minutes (the current call is not in the log yet). */
    private fun isRepeatCaller(context: Context, number: NormalizedNumber): Boolean {
        if (!CallScreenPermissionHelper.isDefaultDialer(context)) return false
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return false

        return try {
            val since = System.currentTimeMillis() - 3 * 60 * 1000L
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.DATE} > ? AND ${CallLog.Calls.TYPE} IN (?, ?, ?)",
                arrayOf(
                    since.toString(),
                    CallLog.Calls.INCOMING_TYPE.toString(),
                    CallLog.Calls.MISSED_TYPE.toString(),
                    CallLog.Calls.REJECTED_TYPE.toString()
                ),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                var found = false
                while (!found && c.moveToNext()) {
                    val logged = c.getString(0).orEmpty()
                    found = logged.isNotBlank() &&
                        PhoneNumberCompat.numbersMatch(logged, number.raw)
                }
                found
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private data class ScreeningDecision(val response: CallResponse, val outcome: String)

    private data class ContactFacts(val isSaved: Boolean, val isFavorite: Boolean) {
        companion object {
            val EMPTY = ContactFacts(isSaved = false, isFavorite = false)
        }
    }

    /** Immutable number view: raw for provider lookups, digits for exact local matches. */
    private data class NormalizedNumber(val raw: String, val digits: String) {
        val variants: List<String>
            get() = buildList {
                add(raw)
                add(digits)
                if (digits.isNotEmpty()) add("+$digits")
            }.distinct()

        val redacted: String
            get() {
                if (digits.isEmpty()) return "<private>"
                if (digits.length <= 4) return "***${digits.takeLast(2)}"
                return "***${digits.takeLast(4)}"
            }

        companion object {
            fun from(value: String): NormalizedNumber {
                val raw = value.trim()
                return NormalizedNumber(raw = raw, digits = raw.filter { it.isDigit() })
            }
        }
    }

    /**
     * Lightweight, monotonic stage recorder. Request-local, so no caller data survives screening.
     * Each mark is the elapsed time since `onScreenCall` and is only emitted in the debug log.
     */
    private class ScreeningTrace(private val startedAt: Long) {
        private val marks = ArrayList<Pair<String, Long>>(8)

        fun mark(stage: String) {
            if (marks.size < 16) marks += stage to (SystemClock.elapsedRealtime() - startedAt)
        }

        fun summary(): String = marks.joinToString(",") { "${it.first}=${it.second}ms" }
    }

    private companion object {
        const val TAG = "Nova Dialer CallScreening"

        /**
         * Telecom allows a few seconds, but waiting almost all of it makes a slow provider feel
         * like a delayed ring. 1.5 s is long enough for one ContactsProvider query on a cold
         * device and short enough to keep the fallback imperceptible. Expired work is cancelled
         * and the call is allowed.
         */
        val SCREENING_DEADLINE_MS = 1_500.milliseconds

        val EMERGENCY_NUMBERS = setOf(
            "911", "112", "999", "108", "000", "110", "119"
        )
    }
}

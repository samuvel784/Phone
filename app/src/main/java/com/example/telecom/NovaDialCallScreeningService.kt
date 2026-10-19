/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.DialerDao
import com.example.util.BlockedNumberContractManager
import com.example.util.CallScreenPermissionHelper
import com.example.util.PhoneNumberCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screens every incoming call BEFORE it rings:
 *  1. Emergency numbers are never touched.
 *  2. Numbers the user blocked, and numbers in the offline spam database, are rejected.
 *  3. Focus mode, protection level, VIP / favorites / repeat callers decide whether the call is silenced.
 * A silenced call still appears in the call log as a missed call.
 */
class NovaDialCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dao: DialerDao by lazy { AppDatabase.getDatabase(applicationContext).dialerDao() }

    override fun onScreenCall(callDetails: Call.Details) {
        val isIncoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        if (!isIncoming) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart.orEmpty()

        scope.launch {
            val response = try {
                // Android allows only a few seconds; if we are too slow the call simply rings normally.
                withTimeoutOrNull(3500.milliseconds) { decide(number) }
            } catch (e: Exception) {
                Log.e(TAG, "Error screening call", e)
                null
            } ?: CallResponse.Builder().build()

            respondToCall(callDetails, response)
        }
    }

    private suspend fun decide(number: String): CallResponse = withContext(Dispatchers.IO) {
        val allow = CallResponse.Builder().build()

        // 1. Emergency numbers: never blocked, never silenced
        if (number.isNotBlank() && isEmergency(number)) return@withContext allow

        // 2. Your blocklist and offline spam database
        if (number.isNotBlank()) {
            val blocked = try {
                BlockedNumberContractManager.isBlocked(applicationContext, number, dao)
            } catch (e: Exception) {
                false
            }
            val spam = try {
                dao.isSpamNumber(number) || dao.isSpamNumber(number.filter { it.isDigit() })
            } catch (e: Exception) {
                false
            }
            if (blocked || spam) {
                Log.d(TAG, "Rejecting blocked/spam call from $number")
                return@withContext CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .build()
            }
        }

        // 2.5 Do Not Disturb schedule: silence everyone except saved contacts and VIPs
        val dndContext = applicationContext
        if (DoNotDisturbPrefs.isQuietHoursNow(dndContext)) {
            val (savedNow, _) = lookupContact(dndContext, number)
            val vipNow = CallProtectionPrefs.isVip(dndContext, number)
            if (!savedNow && !vipNow) {
                Log.d(TAG, "DND quiet hours: silencing $number")
                return@withContext silenced(number)
            }
        }

        // 3. Focus mode and protection level
        val context = applicationContext
        val (isSaved, isFavorite) = lookupContact(context, number)
        val repeatAllowed = CallProtectionPrefs.isRepeatCallerAllowed(context)

        val result = CallProtectionPolicy.evaluateCall(
            phoneNumber = number,
            callerName = null,
            isSavedContact = isSaved,
            isVip = CallProtectionPrefs.isVip(context, number),
            isFavorite = isFavorite,
            isRepeatCaller = repeatAllowed && isRepeatCaller(context, number),
            focusMode = CallProtectionPrefs.getFocusMode(context),
            protectionLevel = CallProtectionPrefs.getProtectionLevel(context),
            isEmergencyNumber = false
        )
        Log.d(TAG, "Policy for $number: silence=${result.shouldSilence} (${result.reason})")

        if (result.shouldSilence) silenced(number) else allow
    }

    private fun silenced(number: String): CallResponse {
        CallDecisionStore.markSilenced(number)
        val builder = CallResponse.Builder()
        // setSilenceCall exists from Android 10; on older versions the call simply rings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setSilenceCall(true)
        return builder.build()
    }

    private fun isEmergency(number: String): Boolean {
        val clean = number.replace(Regex("[^0-9]"), "")
        if (clean == "911" || clean == "112" || clean == "999" || clean == "108" ||
            clean == "000" || clean == "110" || clean == "119") return true
        return PhoneNumberCompat.isEmergencyNumber(applicationContext, number)
    }

    /** Returns (isSavedContact, isFavorite) from the phone's real address book. */
    private fun lookupContact(context: Context, number: String): Pair<Boolean, Boolean> {
        if (number.isBlank()) return Pair(false, false)
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return Pair(false, false)

        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.STARRED), null, null, null)?.use { c ->
                if (c.moveToFirst()) Pair(true, c.getInt(0) == 1) else Pair(false, false)
            } ?: Pair(false, false)
        } catch (e: Exception) {
            Pair(false, false)
        }
    }

    /** True if the same number called in the last 3 minutes (the current call is not in the log yet). */
    private fun isRepeatCaller(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
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
                null
            )?.use { c ->
                var found = false
                while (!found && c.moveToNext()) {
                    val logged = c.getString(0).orEmpty()
                    found = logged.isNotBlank() &&
                        PhoneNumberCompat.numbersMatch(logged, number)
                }
                found
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val TAG = "NovaDialCallScreening"
    }
}

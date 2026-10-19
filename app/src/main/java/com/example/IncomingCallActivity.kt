/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.security.IntentGuard
import com.example.ui.components.ActiveCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The lock-screen incoming-call surface.
 *
 * Why this Activity exists instead of reusing [MainActivity]:
 *  - It is started by the incoming-call notification's **full-screen intent**, which is the only
 *    platform-sanctioned way to present UI while the device is locked (Android 10+ blocks ordinary
 *    background activity starts).
 *  - It deliberately bypasses MainActivity's app-lock gate, so the ringing dashboard appears even
 *    when NovaDial is behind biometric auth — answering a call must never require an unlock first.
 *  - It performs **no** contact/provider/database lookups. The caller name and number were already
 *    resolved by [MyInCallService] and handed over as intent extras, so the first frame is immediate
 *    and nothing on the incoming-call critical path touches disk.
 *
 * Lifecycle contract: this screen owns the *ringing* UI only. As soon as the call is answered it
 * hands the user over to [MainActivity] (which owns the in-call dashboard) and finishes itself.
 */
class IncomingCallActivity : ComponentActivity() {

    /**
     * Guards against a stale launch finishing the screen before the call has been published to
     * [CallManager]. Only once a live call has actually been observed do we treat its disappearance
     * as "the call is over".
     */
    private var sawLiveCall = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyLockScreenFlags()

        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        // Match MainActivity: keep call details out of the OS task switcher / screenshots in release.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        // Read the extras once; they are only a fallback for the very first frame, before the
        // authoritative values arrive from CallManager.
        val extraName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty()
        val extraNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER).orEmpty()

        // Theme settings are plain SharedPreferences reads (no database, no disk I/O beyond a tiny
        // cached XML), so the lock-screen dashboard matches the user's palette without slowing the
        // incoming-call path down.
        val themePrefs = getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        val themeMode = ThemeMode.fromKey(themePrefs.getString("theme_mode", "default"))
        val themeColor = themePrefs.getString("theme_color", "system") ?: "system"
        val customColorHex = themePrefs.getString("custom_color_hex", "#5A4BE0") ?: "#5A4BE0"
        val amoledMode = themePrefs.getBoolean("is_amoled_mode", false)
        val m3Expressive = themePrefs.getBoolean("is_m3_expressive", true)
        val useDynamicColor = themePrefs.getBoolean(
            "use_dynamic_color",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )

        setContent {
            MyApplicationTheme(
                themeMode = themeMode,
                dynamicColor = useDynamicColor,
                themeColor = themeColor,
                customColorHex = customColorHex,
                isAmoledMode = amoledMode,
                isM3Expressive = m3Expressive
            ) {
                val currentCall by CallManager.currentCall.collectAsStateWithLifecycle()
                val resolvedName by CallManager.callerName.collectAsStateWithLifecycle()
                val resolvedNumber by CallManager.callerNumber.collectAsStateWithLifecycle()

                val number = resolvedNumber.ifBlank { extraNumber }
                val name = resolvedName.ifBlank { extraName }

                ActiveCallScreen(
                    contactName = name.ifBlank { number },
                    contactNumber = number,
                    preferredSim = "",
                    // The lock path stays allocation-free: quick responses come from the database only
                    // once the user is inside the unlocked app.
                    quickResponses = emptyList(),
                    isIncoming = true,
                    callState = currentCall?.safeState ?: Call.STATE_RINGING,
                    onAnswer = { answerCall() },
                    onHangUp = { declineCall() },
                    onQuickDecline = { declineCall() },
                    // Nothing to minimise from the lock screen: the ring UI stays until the call is
                    // answered, declined or ends by itself.
                    onMinimize = null
                )
            }
        }

        observeCallLifecycle()
    }

    /**
     * Draws over the keyguard and wakes the panel. The `Activity.setShowWhenLocked` /
     * `setTurnScreenOn` methods are API 27+, so this app (minSdk 24) needs the legacy window flags
     * below that.
     */
    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Follows the real Telecom state:
     *  - answered (here, from the notification, a headset or a car kit) -> hand off to MainActivity;
     *  - ended / declined -> just get out of the way.
     */
    private fun observeCallLifecycle() {
        // Liveness guard: if this screen was opened by a stale full-screen intent and no live call
        // ever materialises, do not sit invisibly on top of the keyguard.
        lifecycleScope.launch {
            delay(STALE_LAUNCH_GRACE_MS)
            if (!sawLiveCall) finish()
        }

        lifecycleScope.launch {
            CallManager.currentCall.collect { call ->
                val state = call?.safeState ?: Call.STATE_DISCONNECTED
                val isLive = call != null && state != Call.STATE_DISCONNECTED
                if (isLive) sawLiveCall = true

                when {
                    isLive && (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING) -> {
                        openMainCallUi()
                        finish()
                    }
                    !isLive && sawLiveCall -> finish()
                }
            }
        }
    }

    /** Hands the answered call over to the in-call dashboard, which owns everything post-answer. */
    private fun openMainCallUi() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    // NOT ACTION_ANSWER: the call is already connected, and an ANSWER_* action would
                    // let MainActivity try to answer again (possibly a different, ringing call).
                    action = MyInCallService.ACTION_INCOMING_CALL
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MyInCallService.EXTRA_SHOW_CALL, true)
                    putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@IncomingCallActivity))
                }
            )
        } catch (_: Exception) {
            // If the hand-off is refused the service still launches MainActivity on the ACTIVE
            // transition, so the call is never left without a UI.
        }
    }

    /** Answers exactly the call this screen was opened for, never an unrelated one. */
    private fun answerCall() {
        val callId = intent.getLongExtra(MyInCallService.EXTRA_CALL_ID, MyInCallService.NO_CALL_ID)
        val answered = if (callId != MyInCallService.NO_CALL_ID) {
            CallManager.answerCallById(callId)
        } else {
            false
        }
        if (!answered) CallManager.answer()
    }

    /** Declines exactly the call this screen was opened for. Never touches a connected call. */
    private fun declineCall() {
        val callId = intent.getLongExtra(MyInCallService.EXTRA_CALL_ID, MyInCallService.NO_CALL_ID)
        val rejected = if (callId != MyInCallService.NO_CALL_ID) {
            CallManager.rejectCallById(callId)
        } else {
            false
        }
        if (!rejected) CallManager.rejectCall()
    }

    companion object {
        /** Display-only fallbacks; the authoritative values come from [CallManager]. */
        const val EXTRA_CALLER_NAME = "com.example.EXTRA_CALLER_NAME"
        const val EXTRA_CALLER_NUMBER = "com.example.EXTRA_CALLER_NUMBER"

        /**
         * How long to wait for a live call after being opened by a full-screen intent before
         * assuming the launch was stale. This is a liveness guard only; it never fakes call state.
         */
        private const val STALE_LAUNCH_GRACE_MS = 1_500L
    }
}

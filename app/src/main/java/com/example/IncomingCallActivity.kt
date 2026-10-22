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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.security.IntentGuard
import com.example.telecom.CallAction
import com.example.telecom.CallActionRouter
import com.example.ui.components.CallOnlyCallSurface
import com.example.ui.components.IncomingCallAccessNotice
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.util.CallScreenPermissionHelper
import com.example.util.IncomingCallAccess
import com.example.util.IncomingCallAccessReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single call-only surface for incoming and active calls.
 *
 * It is started by the incoming notification's full-screen intent when Android permits it, and by
 * notification taps/Answer actions otherwise. It deliberately does not hand off to [MainActivity]:
 * the same activity follows the exact Telecom call through ringing, connected, held and
 * disconnected states. It never dismisses the device keyguard and never marks Nova Dialer itself
 * as authenticated.
 *
 * The activity is private (`android:exported="false"`). Call actions additionally require the
 * private [IntentGuard] token and are always resolved by the exact Telecom call id in the intent.
 */
class IncomingCallActivity : ComponentActivity() {

    private val callIdState = mutableStateOf(MyInCallService.NO_CALL_ID)
    private val fallbackNameState = mutableStateOf("")
    private val fallbackNumberState = mutableStateOf("")
    private val accessState = mutableStateOf<IncomingCallAccess?>(null)
    private val noticeDismissedState = mutableStateOf(false)

    /** Guards against a stale launch finishing the screen before the call has been published. */
    private var sawLiveCall = false
    private var pendingAnswer = false
    private var staleLaunchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyLockScreenFlags()

        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        // Match MainActivity: keep call details out of the OS task switcher / screenshots in release.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        readIntent(intent)

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
                Box(modifier = Modifier.fillMaxSize()) {
                    CallOnlyCallSurface(
                        callId = callIdState.value,
                        fallbackName = fallbackNameState.value,
                        fallbackNumber = fallbackNumberState.value,
                        onBack = { moveTaskToBack(true) }
                    )

                    val access = accessState.value
                    if (!noticeDismissedState.value && access?.hasActionableIssue == true) {
                        IncomingCallAccessNotice(
                            access = access,
                            onOpenFullScreenSettings = {
                                CallScreenPermissionHelper.requestFullScreenIntentPermission(this@IncomingCallActivity)
                            },
                            onOpenNotificationSettings = {
                                CallScreenPermissionHelper.openChannelNotificationSettings(this@IncomingCallActivity)
                            },
                            onDismiss = { noticeDismissedState.value = true },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(start = 16.dp, top = 44.dp, end = 16.dp)
                        )
                    }
                }
            }
        }

        observeCallLifecycle()
    }

    override fun onResume() {
        super.onResume()
        // Re-read the real OS state after the user returns from Settings. The app never assumes
        // that opening the settings screen granted anything.
        refreshAccess()
        refreshCallState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        refreshAccess()
        refreshCallState()
    }

    /**
     * Reads only routing/display extras here. Sensitive answer handling waits until the exact
     * Telecom call is visible, and requires the private intent token.
     */
    private fun readIntent(intent: Intent?) {
        if (intent == null) return

        val incomingId = intent.getLongExtra(MyInCallService.EXTRA_CALL_ID, MyInCallService.NO_CALL_ID)
        if (incomingId != callIdState.value) {
            callIdState.value = incomingId
            sawLiveCall = false
            // A pending answer belongs to the old call id. Never let it fall through to a new
            // incoming call that arrives while the activity is reused.
            pendingAnswer = false
            noticeDismissedState.value = false
            startStaleLaunchGuard()
        } else if (incomingId == MyInCallService.NO_CALL_ID) {
            // A missing id can never become authoritative; leave a short grace for a process
            // race, then close instead of showing a false call screen.
            startStaleLaunchGuard()
        }

        fallbackNameState.value = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty()
        fallbackNumberState.value = intent.getStringExtra(EXTRA_CALLER_NUMBER).orEmpty()

        val trusted = IntentGuard.isValid(
            this,
            intent.getStringExtra(IntentGuard.EXTRA_GUARD_TOKEN)
        )
        val wantsAnswer = intent.getBooleanExtra(MyInCallService.EXTRA_ANSWER, false) ||
                intent.action == MyInCallService.ACTION_ANSWER
        if (trusted && wantsAnswer) {
            pendingAnswer = true
        }
        // Do not leave a privileged token in the Activity intent, even when it was invalid.
        intent.removeExtra(IntentGuard.EXTRA_GUARD_TOKEN)
    }

    private fun startStaleLaunchGuard() {
        staleLaunchJob?.cancel()
        staleLaunchJob = lifecycleScope.launch {
            delay(STALE_LAUNCH_GRACE_MS)
            if (!sawLiveCall) finish()
        }
    }

    private fun observeCallLifecycle() {
        lifecycleScope.launch {
            CallManager.calls.collect { calls ->
                val targetId = callIdState.value
                if (targetId == MyInCallService.NO_CALL_ID) return@collect

                val target = calls.firstOrNull { CallManager.callId(it) == targetId }
                if (target != null && target.safeState != Call.STATE_DISCONNECTED) {
                    sawLiveCall = true
                    maybeAnswerPending(target)
                } else if (sawLiveCall) {
                    // The call this screen owns has ended. Never finish because an unrelated
                    // second call disappeared.
                    finish()
                }
            }
        }
    }

    private fun refreshCallState() {
        val targetId = callIdState.value
        if (targetId == MyInCallService.NO_CALL_ID) return
        val target = CallManager.calls.value.firstOrNull { CallManager.callId(it) == targetId }
        if (target != null && target.safeState != Call.STATE_DISCONNECTED) {
            sawLiveCall = true
            maybeAnswerPending(target)
        } else if (sawLiveCall) {
            finish()
        }
    }

    private fun maybeAnswerPending(target: Call) {
        if (!pendingAnswer) return
        val targetId = callIdState.value
        if (targetId == MyInCallService.NO_CALL_ID) {
            pendingAnswer = false
            return
        }
        if (target.safeState != Call.STATE_RINGING) {
            // Another entry point already answered or ended this call. Never answer a later call
            // because this stale action happened to remain pending.
            pendingAnswer = false
            return
        }

        val resolvedId = CallActionRouter.resolve(
            action = CallAction.ANSWER,
            requestedId = targetId,
            calls = CallManager.liveCallSnapshots()
        ) ?: return
        pendingAnswer = false
        CallManager.answerCallById(resolvedId)
    }

    private fun refreshAccess() {
        accessState.value = IncomingCallAccessReader.read(this, MyInCallService.CHANNEL_INCOMING)
    }

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

    companion object {
        /** Display-only fallbacks; authoritative values come from the exact Telecom call. */
        const val EXTRA_CALLER_NAME = "com.example.EXTRA_CALLER_NAME"
        const val EXTRA_CALLER_NUMBER = "com.example.EXTRA_CALLER_NUMBER"

        /** How long to wait for the exact call before treating the launch as stale. */
        private const val STALE_LAUNCH_GRACE_MS = 1_500L
    }
}

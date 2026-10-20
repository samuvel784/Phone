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

import com.example.ui.theme.novaCanvas
import com.example.ui.theme.NovaTheme
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.safeState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.security.IntentGuard
import com.example.ui.MainScreen
import com.example.ui.theme.AppShapes
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DialerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()
    private val isAppAuthenticated = mutableStateOf(false)
    private var isAuthenticating = false
    private var isAppStopped = false
    private var isLaunchedForCall = false

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isAuthenticating = false
        isAppAuthenticated.value = (result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.telecom.CallTimingLogger.markActive(com.example.telecom.CallTimingLogger.Event.ACTIVITY_CREATED)
        CallManager.appContext = applicationContext
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        // Protect PII from OS switcher task snapshots
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        enableEdgeToEdge()

        if (savedInstanceState != null && !viewModel.isBiometricLockEnabled.value) {
            isAppAuthenticated.value = savedInstanceState.getBoolean("is_authenticated", false)
        } else {
            isAppAuthenticated.value = !viewModel.isBiometricLockEnabled.value
        }

        enableEdgeToEdge()

        // Only authoritative Telecom state may open the call surface. MainActivity is exported
        // for DIAL/VIEW, so intent extras/actions alone must never be treated as proof of a call.
        // Token-gated answer handling happens in handleIntent().
        val hasRealCall = CallManager.currentCall.value != null ||
                CallManager.calls.value.isNotEmpty() ||
                CallManager.calls.value.any { it.safeState == Call.STATE_RINGING }

        if (hasRealCall) {
            viewModel.isCallActive.value = true
            setLockScreenVisibility(true)
        }

        try {
            handleIntent(intent)
        } catch (_: Exception) {}

        setContent {
            val context = LocalContext.current
            var showRestrictedSettingsDialog by remember { mutableStateOf(false) }
            val themeMode by viewModel.themeMode
            val isCallActive by viewModel.isCallActive

            val allCalls by CallManager.calls.collectAsStateWithLifecycle()
            val currentCall by CallManager.currentCall.collectAsStateWithLifecycle()
            LaunchedEffect(allCalls, currentCall) {
                val hasCall = currentCall != null ||
                        allCalls.isNotEmpty() ||
                        allCalls.any { it.safeState == Call.STATE_RINGING }
                if (hasCall) {
                    setLockScreenVisibility(true)
                }
            }

            // FIXED: Prevent lock screen back-gesture from revealing private contacts or recents
            val keyguardManager = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
            BackHandler(enabled = keyguardManager?.isKeyguardLocked == true) {
                if (isCallActive) {
                    moveTaskToBack(true)
                } else {
                    dismissCallUiAndExit()
                }
            }

            LaunchedEffect(isCallActive) {
                try {
                    setLockScreenVisibility(isCallActive)
                    if (!isCallActive) {
                        isLaunchedForCall = false
                        viewModel.isLaunchedForCall.value = false
                        viewModel.isCallMinimized.value = false
                    }
                } catch (_: Exception) {}
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        updateDefaultDialerStatus(context)
                        val hasActiveCall = CallManager.currentCall.value != null ||
                                CallManager.calls.value.isNotEmpty()
                        if (hasTrustedToken() || (hasActiveCall && isAppAuthenticated.value)) {
                            isAppAuthenticated.value = true
                            setLockScreenVisibility(true)
                        } else if (hasActiveCall) {
                            // A real call is in progress but this intent is not ours: stay locked.
                        } else if (viewModel.isBiometricLockEnabled.value) {
                            if (!isAuthenticating && !isAppAuthenticated.value) {
                                triggerDeviceAuthentication()
                            }
                        } else {
                            isAppAuthenticated.value = true
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(Unit) {
                updateDefaultDialerStatus(context)
            }

            if (showRestrictedSettingsDialog) {
                RestrictedSettingsDialog(
                    onDismiss = { showRestrictedSettingsDialog = false },
                    onOpenSettings = {
                        showRestrictedSettingsDialog = false
                        try {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            })
                        } catch (_: Exception) {}
                    }
                )
            }

            val currentThemeColor by viewModel.themeColor
            val customColorHex by viewModel.customColorHex
            val isAmoledMode by viewModel.isAmoledMode
            val isM3Expressive by viewModel.isM3Expressive
            val useDynamicColor by viewModel.useDynamicColor

            MyApplicationTheme(
                themeMode = themeMode,
                dynamicColor = useDynamicColor,
                themeColor = currentThemeColor,
                customColorHex = customColorHex,
                isAmoledMode = isAmoledMode,
                isM3Expressive = isM3Expressive
            ) {
                if (isAppAuthenticated.value || isCallActive) {
                    MainScreen(
                        viewModel = viewModel,
                        onShowRestrictedSettings = { showRestrictedSettingsDialog = true },
                        isDefaultDialer = viewModel.isDefaultDialer.value
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize().novaCanvas(),
                        color = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                // gradient badge with a soft glow
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(104.dp)
                                        .shadow(
                                            elevation = 22.dp,
                                            shape = AppShapes.Avatar,
                                            ambientColor = NovaTheme.colors.glow,
                                            spotColor = NovaTheme.colors.glow
                                        )
                                        .clip(AppShapes.Avatar)
                                        .background(NovaTheme.colors.brand)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = stringResource(R.string.app_locked_title),
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.size(24.dp))

                                Text(
                                    text = stringResource(R.string.app_locked_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.size(12.dp))

                                Text(
                                    text = stringResource(R.string.app_locked_message),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.size(36.dp))

                                Button(
                                    onClick = { triggerDeviceAuthentication() },
                                    modifier = Modifier.fillMaxWidth(0.7f).height(52.dp),
                                    shape = AppShapes.Keypad
                                ) {
                                    Text(text = stringResource(R.string.btn_unlock_app), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Only preserve authentication in state if biometric lock is turned off
        if (!viewModel.isBiometricLockEnabled.value) {
            outState.putBoolean("is_authenticated", isAppAuthenticated.value)
        }
    }

    override fun onPause() {
        super.onPause()
        CallManager.setAppInForeground(false)
    }

    override fun onStop() {
        super.onStop()
        CallManager.setAppInForeground(false)
        isAppStopped = true
    }

    override fun onResume() {
        super.onResume()
        com.example.telecom.CallTimingLogger.markActive(com.example.telecom.CallTimingLogger.Event.ACTIVITY_RESUMED)
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        CallManager.setAppInForeground(true)
        updateDefaultDialerStatus(this)
        viewModel.hasRecordAudioPermission.value = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun onStart() {
        super.onStart()
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        CallManager.setAppInForeground(true)
        // Re-arm the opt-in shake-to-SOS watch whenever the app is opened.
        if (com.example.telecom.EmergencySosPrefs.isShakeToTriggerEnabled(this)) {
            com.example.telecom.EmergencySosService.start(this)
        }
        val hasRealCall = CallManager.currentCall.value != null ||
                CallManager.calls.value.isNotEmpty()
        if (hasTrustedToken() || (hasRealCall && isAppAuthenticated.value)) {
            isAppAuthenticated.value = true
            setLockScreenVisibility(true)
        } else if (isAppStopped) {
            isAppStopped = false
            if (viewModel.isBiometricLockEnabled.value) {
                isAppAuthenticated.value = false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val hasRealCall = CallManager.currentCall.value != null ||
                CallManager.calls.value.isNotEmpty()
        if (hasTrustedToken() || (hasRealCall && isAppAuthenticated.value)) {
            setLockScreenVisibility(true)
        }
    }

    @Suppress("DEPRECATION") // KeyguardManager.createConfirmDeviceCredentialIntent; BiometricPrompt needs androidx.biometric
    private fun triggerDeviceAuthentication() {
        if (isAuthenticating) return
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return
        if (keyguardManager.isDeviceSecure) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Nova Dialer",
                "Authenticate to open Nova Dialer"
            )
            if (intent != null) {
                isAuthenticating = true
                try {
                    authLauncher.launch(intent)
                } catch (_: Exception) {
                    // FIXED: Prevent deadlock if OS fails to launch credential chooser
                    isAuthenticating = false
                    isAppAuthenticated.value = false
                }
            } else {
                isAppAuthenticated.value = true
            }
        } else {
            isAppAuthenticated.value = true
        }
    }

    private fun dismissCallUiAndExit() {
        setLockScreenVisibility(false)
        isLaunchedForCall = false
        viewModel.isLaunchedForCall.value = false
        viewModel.isCallMinimized.value = false
    }

    private fun setLockScreenVisibility(show: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(show)
                setTurnScreenOn(show)
            }
            @Suppress("DEPRECATION")
            if (show) {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
        } catch (_: Exception) {}
    }

    private fun updateDefaultDialerStatus(context: Context) {
        viewModel.isDefaultDialer.value = com.example.util.CallScreenPermissionHelper.isDefaultDialer(context)
    }

    /** True when the current intent carries Nova Dialer's private per-install token. */
    private fun hasTrustedToken(intent: Intent? = this.intent): Boolean =
        IntentGuard.isValid(this, intent?.getStringExtra(IntentGuard.EXTRA_GUARD_TOKEN))

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // Only intents carrying our private per-install token may perform sensitive
        // actions. MainActivity is exported, so any app can send it an explicit intent.
        val trusted = IntentGuard.isValid(this, intent.getStringExtra(IntentGuard.EXTRA_GUARD_TOKEN))
        try {
            // Answering is a sensitive Telecom action. An exported activity may be started
            // by any app, so ANSWER_ON_LAUNCH/ACTION_ANSWER are honoured only when they
            // carry Nova Dialer's private token (notification/FSI/IncomingCallActivity paths).
            val isAnswerRequest = trusted && (
                    intent.getBooleanExtra("ANSWER_ON_LAUNCH", false) ||
                            intent.action == MyInCallService.ACTION_ANSWER
                    )
            if (isAnswerRequest) {
                // The notification's Answer action carries the id of the exact call it was built for.
                // Only that call is answered; if it is no longer ringing nothing else is answered.
                val answerCallId = intent.getLongExtra(MyInCallService.EXTRA_CALL_ID, MyInCallService.NO_CALL_ID)
                val answered = if (answerCallId != MyInCallService.NO_CALL_ID) {
                    CallManager.answerCallById(answerCallId)
                } else {
                    val before = CallManager.currentCall.value
                    CallManager.answer()
                    before != null && before.safeState == Call.STATE_RINGING
                }
                if (answered || CallManager.currentCall.value != null) {
                    viewModel.isCallActive.value = true
                    isLaunchedForCall = true
                    viewModel.isLaunchedForCall.value = true
                    viewModel.isCallMinimized.value = false
                    setLockScreenVisibility(true)
                }
            }

            // Never derive "a call is active" from exported intent extras. Only the
            // authoritative Telecom state may open the in-call surface.
            val hasGenuineActiveCall = CallManager.currentCall.value != null ||
                    CallManager.calls.value.isNotEmpty() ||
                    CallManager.calls.value.any { it.safeState == Call.STATE_RINGING }
            if (hasGenuineActiveCall) {
                viewModel.isCallActive.value = true
                isLaunchedForCall = true
                viewModel.isLaunchedForCall.value = true
                viewModel.isCallMinimized.value = false
                setLockScreenVisibility(true)
            }

            // Recents contains private call history, so only a Nova Dialer-owned notification
            // may preselect it. The normal app lock still applies before it is displayed.
            if (trusted && (
                            intent.getBooleanExtra("SHOW_CALL_LOG", false) ||
                                    intent.getBooleanExtra("MISSED_CALL", false) ||
                                    intent.action == "com.example.dialer.ACTION_MISSED_CALLS"
                            )
            ) {
                viewModel.selectTabBySlotKey("RECENTS")
            }

            val action = intent.action
            val data = intent.data
            if (action == Intent.ACTION_CALL || action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
                if (data?.scheme == "tel") {
                    val number = data.schemeSpecificPart ?: ""
                    if (number.isNotEmpty()) {
                        // SEC-02: Populate dialpad preview instead of placing call directly to avoid confused deputy attacks
                        viewModel.onDialpadInputChange(number)
                        viewModel.selectTabBySlotKey("DIALPAD")
                    }
                }
            }
        } finally {
            // The token is single-use. Keeping it in Activity.intent would make every later
            // onStart()/onResume() look like a fresh trusted launch and bypass the app lock.
            if (trusted) intent.removeExtra(IntentGuard.EXTRA_GUARD_TOKEN)
        }
    }

    @Composable
    private fun RestrictedSettingsDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.restricted_settings_title)) },
            text = { Text(stringResource(R.string.restricted_settings_desc)) },
            confirmButton = {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.btn_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

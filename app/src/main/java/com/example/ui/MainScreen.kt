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

package com.example.ui

import kotlin.math.absoluteValue
import com.example.ui.components.NovaFab
import com.example.ui.components.LockScreenAndPopupPermissionsBanner
import com.example.ui.theme.NovaMotion
import com.example.ui.theme.novaCanvas
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CallManager
import com.example.ContactCache
import com.example.R
import com.example.getContactNameFromNumber
import com.example.getSavedCnapName
import com.example.ui.components.*
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.CallScreenPermissionHelper
import com.example.util.ContactRingtonePrefs
import com.example.util.MultiSimManager
import com.example.util.RichHapticEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val PHONE_STATE_PROMPT_PREF = "phone_state_prompted"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: DialerViewModel,
    onShowRestrictedSettings: () -> Unit,
    isDefaultDialer: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val preferredSim by viewModel.preferredSim
    val permissionPromptPrefs = remember(context) {
        context.getSharedPreferences("nova_permission_prompts", android.content.Context.MODE_PRIVATE)
    }
    // READ_PHONE_STATE is requested only when the device reports more than one modem. Keep the
    // permission state as a remember key so the callable-SIM list refreshes after the grant.
    var hasPhoneStatePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    // Real, callable SIMs only — never placeholder slots. Size drives the "ask which SIM?" logic.
    val callableSims = remember(context, hasPhoneStatePermission) {
        MultiSimManager.getCallableSims(context)
    }
    val blockedNumbersEntities by viewModel.blockedNumbersFlow.collectAsStateWithLifecycle()
    val blockedNumbers = remember(blockedNumbersEntities) {
        blockedNumbersEntities.mapTo(HashSet(blockedNumbersEntities.size)) { it.number }
    }

    var selectedTab by viewModel.selectedTab
    val searchQuery by viewModel.searchQuery
    val isCallHistoryDetailsOpen by viewModel.isCallHistoryDetailsOpen
    var isDialpadVisible by viewModel.isDialpadVisible
    var dialpadInput by viewModel.dialpadInput
    var isSettingsVisible by viewModel.isSettingsVisible
    var isCallActive by viewModel.isCallActive
    var isCallMinimized by viewModel.isCallMinimized
    // Which settings page the panel should open on (13 = Emergency SOS).
    var settingsInitialTab by remember { mutableIntStateOf(0) }

    val favoriteContacts by viewModel.favoriteContacts.collectAsStateWithLifecycle()

    LaunchedEffect(selectedTab) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(isSettingsVisible) {
        if (isSettingsVisible) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            isSettingsVisible = false
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    LaunchedEffect(isCallActive) {
        if (!isCallActive) {
            isCallMinimized = false
        }
    }

    var callingContactName by viewModel.callingContactName
    var callingContactNumber by viewModel.callingContactNumber
    var isAddContactDialogVisible by viewModel.isAddContactDialogVisible
    var oldContactToEdit by viewModel.oldContactToEdit
    var editContactName by viewModel.editContactName
    var editContactNumber by viewModel.editContactNumber
    var editContactLabel by viewModel.editContactLabel
    var isEditContactDialogVisible by viewModel.isEditContactDialogVisible

    var hasContactsPermission by viewModel.hasContactsPermission
    var hasCallLogPermission by viewModel.hasCallLogPermission
    var hasNotificationPermission by viewModel.hasNotificationPermission
    var isLoadingPermissions by viewModel.isLoadingPermissions
    val contactsLoading by viewModel.contactsLoading.collectAsStateWithLifecycle()
    val contactsRefreshing by viewModel.contactsRefreshing.collectAsStateWithLifecycle()
    val contactsLoadFailed by viewModel.contactsLoadFailed.collectAsStateWithLifecycle()
    val callLogLoading by viewModel.callLogLoading.collectAsStateWithLifecycle()
    val callLogRefreshing by viewModel.callLogRefreshing.collectAsStateWithLifecycle()
    val callLogLoadFailed by viewModel.callLogLoadFailed.collectAsStateWithLifecycle()
    var showProminentDisclosure by remember { mutableStateOf(false) }
    var showCallLogDisclosure by remember { mutableStateOf(false) }
    var pendingPermissionsAfterRole by remember { mutableStateOf(false) }

    val systemActiveCall by CallManager.currentCall.collectAsStateWithLifecycle()
    val systemCallState by CallManager.callState.collectAsStateWithLifecycle()
    val systemAudioState by CallManager.audioState.collectAsStateWithLifecycle()
    val systemCallerNumber by CallManager.callerNumber.collectAsStateWithLifecycle()
    val systemCallerCnapName by CallManager.callerCnapName.collectAsStateWithLifecycle()

    var toneGenerator by remember { mutableStateOf<ToneGenerator?>(null) }
    DisposableEffect(Unit) {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (_: Exception) {}
        onDispose {
            try { toneGenerator?.release() } catch (_: Exception) {}
            toneGenerator = null
        }
    }

    fun playDtmf(key: String) {
        toneGenerator?.let { tg ->
            try {
                val tone = when (key) {
                    "1" -> ToneGenerator.TONE_DTMF_1
                    "2" -> ToneGenerator.TONE_DTMF_2
                    "3" -> ToneGenerator.TONE_DTMF_3
                    "4" -> ToneGenerator.TONE_DTMF_4
                    "5" -> ToneGenerator.TONE_DTMF_5
                    "6" -> ToneGenerator.TONE_DTMF_6
                    "7" -> ToneGenerator.TONE_DTMF_7
                    "8" -> ToneGenerator.TONE_DTMF_8
                    "9" -> ToneGenerator.TONE_DTMF_9
                    "0" -> ToneGenerator.TONE_DTMF_0
                    "*" -> ToneGenerator.TONE_DTMF_S
                    "#" -> ToneGenerator.TONE_DTMF_P
                    else -> -1
                }
                if (tone != -1) tg.startTone(tone, 120)
            } catch (_: Throwable) {}
        }
    }

    fun buildPermissionRequest(includeCallLog: Boolean): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        // Call Log is requested only after Nova Dialer actually holds the default Phone role.
        if (includeCallLog) {
            perms.add(Manifest.permission.READ_CALL_LOG)
            perms.add(Manifest.permission.WRITE_CALL_LOG)
        }
        // Phone state is needed to enumerate call-capable SIM accounts. Ask only on multi-SIM
        // hardware; single-SIM devices can let the platform select their only SIM.
        if (!hasPhoneStatePermission && MultiSimManager.getPhysicalSimCount(context) >= 2) {
            perms.add(Manifest.permission.READ_PHONE_STATE)
            permissionPromptPrefs.edit { putBoolean(PHONE_STATE_PROMPT_PREF, true) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] ?: false
        val callLogGranted = permissions[Manifest.permission.READ_CALL_LOG] ?: false
        hasContactsPermission = contactsGranted
        hasCallLogPermission = callLogGranted
        // Only update when the permission was actually part of this request; otherwise a
        // single-SIM request would overwrite an existing grant with false.
        permissions[Manifest.permission.READ_PHONE_STATE]?.let { hasPhoneStatePermission = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        }
        isLoadingPermissions = false
        if (contactsGranted || (callLogGranted && isDefaultDialer)) {
            viewModel.startDataSyncAndObservation()
        }
    }

    val defaultDialerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val nowDefault = CallScreenPermissionHelper.isDefaultDialer(context)
        viewModel.isDefaultDialer.value = nowDefault
        if (pendingPermissionsAfterRole) {
            pendingPermissionsAfterRole = false
            permissionLauncher.launch(buildPermissionRequest(includeCallLog = nowDefault))
        }
    }

    LaunchedEffect(Unit) {
        val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val callLogGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

        hasContactsPermission = contactsGranted
        hasCallLogPermission = callLogGranted
        val phoneStateGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
        hasPhoneStatePermission = phoneStateGranted
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        isLoadingPermissions = false

        if (contactsGranted || (callLogGranted && isDefaultDialer)) {
            viewModel.startDataSyncAndObservation()
        }

        val needsPhoneStatePrompt = !phoneStateGranted &&
            MultiSimManager.getPhysicalSimCount(context) >= 2 &&
            !permissionPromptPrefs.getBoolean(PHONE_STATE_PROMPT_PREF, false)
        if (!contactsGranted) {
            showProminentDisclosure = true
        } else if (isDefaultDialer && !callLogGranted) {
            showCallLogDisclosure = true
        } else if (needsPhoneStatePrompt) {
            // Upgrade path: contacts/call log may already be granted, but this build now needs
            // phone state for the dual-SIM chooser. Offer the disclosure once.
            permissionPromptPrefs.edit { putBoolean(PHONE_STATE_PROMPT_PREF, true) }
            showProminentDisclosure = true
        }
    }

    LaunchedEffect(isDefaultDialer) {
        if (isDefaultDialer) {
            // Register the CallLog observer now that the role is held. The repository is
            // idempotent, so this also covers a role granted after first launch.
            viewModel.startDataSyncAndObservation()
            if (!hasCallLogPermission) {
                showCallLogDisclosure = true
            }
        }
    }

    LaunchedEffect(systemActiveCall, systemCallState, systemCallerNumber, systemCallerCnapName) {
        if (systemActiveCall != null) {
            isCallActive = true
            if (systemCallState == android.telecom.Call.STATE_RINGING) {
                isCallMinimized = false
            }
            if (systemCallerNumber.isNotEmpty()) {
                callingContactNumber = systemCallerNumber
                val contactName = getContactNameFromNumber(context, systemCallerNumber)
                if (contactName != null) {
                    callingContactName = contactName
                } else if (systemCallerCnapName.isNotEmpty()) {
                    callingContactName = systemCallerCnapName
                } else {
                    val savedCnap = getSavedCnapName(context, systemCallerNumber)
                    callingContactName = savedCnap ?: systemCallerNumber
                }
                CallManager.setCallerName(callingContactName)
            }
        } else {
            if (isCallActive) {
                delay(650.milliseconds)
            }
            isCallActive = false
        }
    }

    var showSimSelectDialog by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf("") }
    var pendingCallName by remember { mutableStateOf("") }

    fun initiateCall(name: String, number: String, label: String = "Mobile", simChoice: String? = null) {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (blockedNumbers.contains(number)) {
            Toast.makeText(context, context.getString(R.string.call_blocked_toast), Toast.LENGTH_LONG).show()
            return
        }

        var resolvedName = name
        val memoryContact = getContactNameFromNumber(context, number)
        val memoryCnap = ContactCache.getCnapName(number)

        if (resolvedName == "Unknown" || resolvedName.isEmpty() || resolvedName == number) {
            if (memoryContact != null) {
                resolvedName = memoryContact
            } else if (!memoryCnap.isNullOrBlank()) {
                resolvedName = memoryCnap
            }
        }

        val targetSim = simChoice ?: preferredSim
        val isEmergency = CallManager.isEmergencyNumber(number)
        // Only prompt when the device really has two callable SIMs and the user asked to be prompted.
        // A single-SIM device must never show a "SIM 1 / SIM 2" chooser.
        val needsSimChoice = !isEmergency && targetSim == "Ask" && callableSims.size >= 2
        if (needsSimChoice) {
            pendingCallName = resolvedName
            pendingCallNumber = number
            showSimSelectDialog = true

            if (resolvedName == "Unknown" || resolvedName.isEmpty() || resolvedName == number) {
                coroutineScope.launch {
                    val savedCnap = getSavedCnapName(context, number)
                    if (!savedCnap.isNullOrBlank()) {
                        pendingCallName = savedCnap
                    }
                }
            }
        } else {
            val effectiveSim = when {
                isEmergency -> "Ask"
                // Single SIM: dial it directly instead of asking the user to choose.
                targetSim == "Ask" && callableSims.size == 1 -> "SIM ${callableSims.first().slotIndex + 1}"
                else -> targetSim
            }
            callingContactName = resolvedName
            callingContactNumber = number
            isCallActive = true
            CallManager.setCallerName(resolvedName)
            CallManager.placeCall(context, number, effectiveSim)

            if (resolvedName == "Unknown" || resolvedName.isEmpty() || resolvedName == number) {
                coroutineScope.launch {
                    val savedCnap = getSavedCnapName(context, number)
                    if (!savedCnap.isNullOrBlank()) {
                        callingContactName = savedCnap
                        CallManager.setCallerName(savedCnap)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Keep the top/cutout/IME insets on the content, but let the bottom navigation bar handle
        // its own gesture-bar inset so the tabs sit flush with the bottom of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        )
    ) { paddingValues ->
        val tabSlots = listOf("HOME", "RECENTS", "FAVORITES")

        val defaultTabKey = viewModel.defaultStartupTabKey.value
        val defaultTabIndex = listOf("HOME", "RECENTS", "FAVORITES").indexOf(defaultTabKey).coerceAtLeast(0)

        BackHandler(enabled = isDialpadVisible || isSettingsVisible || (isCallActive && !isCallMinimized) || selectedTab != defaultTabIndex) {
            if (isDialpadVisible) {
                isDialpadVisible = false
            } else if (isSettingsVisible) {
                isSettingsVisible = false
            } else if (isCallActive && !isCallMinimized) {
                isCallMinimized = true
            } else if (selectedTab != defaultTabIndex) {
                selectedTab = defaultTabIndex
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .novaCanvas()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            if (showProminentDisclosure) {
                AlertDialog(
                    onDismissRequest = { showProminentDisclosure = false },
                    title = {
                        Text(
                            stringResource(R.string.permission_disclosure_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.permission_disclosure_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showProminentDisclosure = false
                                if (isDefaultDialer) {
                                    permissionLauncher.launch(buildPermissionRequest(includeCallLog = true))
                                } else {
                                    // Ask for the Phone role first. Call Log is requested only
                                    // after the role is actually held.
                                    pendingPermissionsAfterRole = true
                                    try {
                                        defaultDialerLauncher.launch(
                                            CallScreenPermissionHelper.getDefaultDialerIntent(context)
                                        )
                                    } catch (_: Exception) {
                                        pendingPermissionsAfterRole = false
                                        permissionLauncher.launch(buildPermissionRequest(includeCallLog = false))
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.permission_disclosure_continue))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showProminentDisclosure = false }) {
                            Text(stringResource(R.string.permission_disclosure_not_now))
                        }
                    }
                )
            }

            if (showCallLogDisclosure && isDefaultDialer && !hasCallLogPermission) {
                AlertDialog(
                    onDismissRequest = { showCallLogDisclosure = false },
                    title = {
                        Text(
                            stringResource(R.string.call_log_disclosure_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.call_log_disclosure_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCallLogDisclosure = false
                                permissionLauncher.launch(buildPermissionRequest(includeCallLog = true))
                            }
                        ) {
                            Text(stringResource(R.string.permission_disclosure_continue))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCallLogDisclosure = false }) {
                            Text(stringResource(R.string.permission_disclosure_not_now))
                        }
                    }
                )
            }

            if (showSimSelectDialog && callableSims.size >= 2) {
                SimSelectDialog(
                    context = context,
                    pendingCallNumber = pendingCallNumber,
                    sims = callableSims,
                    onDismiss = { showSimSelectDialog = false },
                    onSimSelected = { simLabel ->
                        showSimSelectDialog = false
                        callingContactName = pendingCallName
                        callingContactNumber = pendingCallNumber
                        isCallActive = true
                        CallManager.setCallerName(pendingCallName)
                        CallManager.placeCall(context, pendingCallNumber, simLabel)
                    }
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                LockScreenAndPopupPermissionsBanner()
                val isRowSwipeEnabled by viewModel.isRowSwipeEnabled

                AnimatedVisibility(
                    visible = !isCallHistoryDetailsOpen,
                    enter = expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                    ) + fadeIn(animationSpec = tween(120)),
                    exit = shrinkVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                    ) + fadeOut(animationSpec = tween(120))
                ) {
                    HeaderSearchBar(
                        searchQuery = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        menuVisible = true,
                        onMenuClick = {
                            settingsInitialTab = 0
                            isSettingsVisible = true
                        }
                    )
                }

                // FIXED: Standard, accessible 3-page pager eliminates infinite carousel jumps and TalkBack bugs
                val pagerState = rememberPagerState(initialPage = selectedTab.coerceIn(0, tabSlots.size - 1)) { tabSlots.size }

                LaunchedEffect(pagerState.currentPage) {
                    if (selectedTab != pagerState.currentPage) {
                        selectedTab = pagerState.currentPage
                    }
                }

                LaunchedEffect(selectedTab) {
                    if (pagerState.currentPage != selectedTab) {
                        pagerState.animateScrollToPage(selectedTab)
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // Floats stay aligned with the centred content column on tablets instead of
                    // drifting to the far window edge.
                    val contentInset = contentSideInset(this.maxWidth)
                    val currentSlot = tabSlots.getOrElse(pagerState.currentPage) { "RECENTS" }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        // Keep only the visible page composed. The pager has three heavy tabs and
                        // pre-composing a neighbour doubled contact/call-log collection work.
                        beyondViewportPageCount = 0,
                        userScrollEnabled = !isRowSwipeEnabled || currentSlot != "RECENTS"
                    ) { page ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                        .absoluteValue
                                        .coerceIn(0f, 1f)
                                    alpha = 1f - 0.30f * distance
                                    val sc = 1f - 0.035f * distance
                                    scaleX = sc
                                    scaleY = sc
                                }
                        ) {
                            NovaContentFrame(modifier = Modifier.fillMaxSize()) {
                            when (tabSlots.getOrElse(page) { "HOME" }) {
                            "HOME" -> {
                                ContactsTabContent(
                                    viewModel = viewModel,
                                    favoriteContacts = favoriteContacts,
                                    cardStyle = ContactCardStyle.HOME,
                                    onCallClick = { initiateCall(it.name, it.number, it.label) },
                                    onAddContactClick = { isAddContactDialogVisible = true },
                                    onToggleFavorite = { contact -> viewModel.toggleFavorite(contact.number, !contact.favorite) },
                                    hasPermission = hasContactsPermission,
                                    isLoading = isLoadingPermissions || (hasContactsPermission && contactsLoading),
                                    hasError = hasContactsPermission && contactsLoadFailed,
                                    isRefreshing = hasContactsPermission && contactsRefreshing,
                                    onRetry = viewModel::retryDataSync,
                                    onRequestPermission = { showProminentDisclosure = true },
                                    onEditContact = {
                                        oldContactToEdit = it
                                        editContactName = it.name
                                        editContactNumber = it.number
                                        editContactLabel = it.label
                                        isEditContactDialogVisible = true
                                    },
                                    onDeleteContact = { viewModel.deleteContact(it) }
                                )
                            }
                            "RECENTS" -> {
                                val allCallHistory by viewModel.allCallHistoryFlow.collectAsStateWithLifecycle()
                                RecentsTabContent(
                                    viewModel = viewModel,
                                    callRecords = allCallHistory,
                                    onCallClick = { initiateCall(it.name, it.number, it.label) },
                                    onDeleteRecord = { id -> viewModel.deleteCallLog(id) },
                                    hasPermission = hasCallLogPermission,
                                    isLoading = isLoadingPermissions || (hasCallLogPermission && callLogLoading),
                                    hasError = hasCallLogPermission && callLogLoadFailed,
                                    isRefreshing = hasCallLogPermission && callLogRefreshing,
                                    onRetry = viewModel::retryDataSync,
                                    onRequestPermission = { showProminentDisclosure = true }
                                )
                            }
                            "CONTACTS" -> {
                                ContactsTabContent(
                                    viewModel = viewModel,
                                    favoriteContacts = favoriteContacts,
                                    cardStyle = ContactCardStyle.HOME,
                                    onCallClick = { initiateCall(it.name, it.number, it.label) },
                                    onAddContactClick = { isAddContactDialogVisible = true },
                                    onToggleFavorite = { contact -> viewModel.toggleFavorite(contact.number, !contact.favorite) },
                                    hasPermission = hasContactsPermission,
                                    isLoading = isLoadingPermissions || (hasContactsPermission && contactsLoading),
                                    hasError = hasContactsPermission && contactsLoadFailed,
                                    isRefreshing = hasContactsPermission && contactsRefreshing,
                                    onRetry = viewModel::retryDataSync,
                                    onRequestPermission = { showProminentDisclosure = true },
                                    onEditContact = {
                                        oldContactToEdit = it
                                        editContactName = it.name
                                        editContactNumber = it.number
                                        editContactLabel = it.label
                                        isEditContactDialogVisible = true
                                    },
                                    onDeleteContact = { viewModel.deleteContact(it) }
                                )
                            }
                            "FAVORITES" -> {
                                ContactsTabContent(
                                    viewModel = viewModel,
                                    favoriteContacts = favoriteContacts,
                                    cardStyle = ContactCardStyle.FAVORITES,
                                    onCallClick = { initiateCall(it.name, it.number, it.label) },
                                    onAddContactClick = { isAddContactDialogVisible = true },
                                    onToggleFavorite = { contact -> viewModel.toggleFavorite(contact.number, !contact.favorite) },
                                    hasPermission = hasContactsPermission,
                                    isLoading = isLoadingPermissions || (hasContactsPermission && contactsLoading),
                                    hasError = hasContactsPermission && contactsLoadFailed,
                                    isRefreshing = hasContactsPermission && contactsRefreshing,
                                    onRetry = viewModel::retryDataSync,
                                    onRequestPermission = { showProminentDisclosure = true },
                                    onEditContact = {
                                        oldContactToEdit = it
                                        editContactName = it.name
                                        editContactNumber = it.number
                                        editContactLabel = it.label
                                        isEditContactDialogVisible = true
                                    },
                                    onDeleteContact = { viewModel.deleteContact(it) },
                                    startWithFavoritesOnly = true
                                )
                            }
                        }
                        }
                        }
                    }
                    // Floating keypad button.
                    // It lives in the content area (the space between the search bar and the bottom navigation), so it
                    // always floats a fixed distance ABOVE the navigation bar and above the minimised-call banner, and
                    // it can no longer change the height or position of the navigation bar.
                    if (!WindowInsets.isImeVisible) {
                        // pops in with a little spring every time it appears
                        var fabShown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { fabShown = true }
                        val fabScale = animateFloatAsState(
                            targetValue = if (fabShown) 1f else 0f,
                            animationSpec = NovaMotion.bouncy(),
                            label = "fabScale"
                        )
                        NovaFab(
                            onClick = {
                                RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                isDialpadVisible = true
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = contentInset + 20.dp, bottom = 28.dp)
                                .graphicsLayer {
                                    val sc = fabScale.value
                                    scaleX = sc
                                    scaleY = sc
                                    alpha = sc.coerceIn(0f, 1f)
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = stringResource(R.string.tab_dialpad),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                if (isCallActive && isCallMinimized) {
                    MinimizedCallBanner(
                        contactName = callingContactName,
                        contactNumber = callingContactNumber,
                        callState = systemCallState,
                        onExpand = { isCallMinimized = false },
                        onHangUp = {
                            CallManager.disconnect()
                            isCallActive = false
                        }
                    )
                }

                // Bottom navigation (Home / Recent / Favourites).
                if (!WindowInsets.isImeVisible) {
                    BottomNavBar(
                        selectedTab = selectedTab,
                        onTabSelected = { targetTab ->
                            selectedTab = targetTab
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetTab)
                            }
                        },
                        tabSlots = tabSlots
                    )
                }
            }

            AnimatedVisibility(
                visible = isSettingsVisible && (!isCallActive || isCallMinimized),
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(150)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(160, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(120))
            ) {
                SettingsPanel(
                    viewModel = viewModel,
                    onClose = { isSettingsVisible = false },
                    initialTab = settingsInitialTab
                )
            }

            AnimatedVisibility(
                visible = isCallActive && !isCallMinimized,
                enter = fadeIn(animationSpec = tween(120)) + scaleIn(initialScale = 0.95f, animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(100)) + scaleOut(targetScale = 0.95f, animationSpec = tween(100))
            ) {
                val allContacts by viewModel.allContactsFlow.collectAsStateWithLifecycle()
                val quickResponsesEntities by viewModel.quickResponsesFlow.collectAsStateWithLifecycle()
                val quickResponses = remember(quickResponsesEntities) { quickResponsesEntities.map { it.message } }

                ActiveCallScreen(
                    contactName = callingContactName,
                    contactNumber = callingContactNumber,
                    preferredSim = preferredSim,
                    quickResponses = quickResponses,
                    isSpam = false,
                    onHangUp = { CallManager.disconnect() },
                    onAnswer = { CallManager.answer() },
                    onQuickDecline = { CallManager.disconnect() },
                    isIncoming = systemCallState == android.telecom.Call.STATE_RINGING,
                    contacts = allContacts,
                    callState = systemCallState,
                    callNotesEnabled = viewModel.isCallNotesEnabled.value,
                    onSaveNote = { content -> viewModel.saveCallNote(callingContactNumber, content) },
                    onMinimize = { isCallMinimized = true },
                    avatarShapeType = viewModel.avatarShapeType.value,
                    isPocketProtectionEnabled = viewModel.isPocketProtectionEnabled.value
                )
            }

            MainScreenContactDialogs(viewModel = viewModel)

            // The scrim fades in/out with the dialpad sheet. The alpha has to depend on
            // isDialpadVisible for the animation to run at all (a constant targetValue never
            // animates), so it is declared outside the conditional; the box only exists while
            // it is even slightly visible.
            val dialpadBackdropAlpha by animateFloatAsState(
                targetValue = if (isDialpadVisible) 0.5f else 0f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "backdrop_alpha"
            )
            if (dialpadBackdropAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dialpadBackdropAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isDialpadVisible = false
                        }
                )
            }

            AnimatedVisibility(
                visible = isDialpadVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(animationSpec = tween(150)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                ) + fadeOut(animationSpec = tween(100)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val dialpadTonesEnabled by viewModel.dialpadTonesEnabled
                val voicemailNumber by viewModel.voicemailNumber
                val speedDialEntities by viewModel.speedDialFlow.collectAsStateWithLifecycle()
                val speedDialMap = remember(speedDialEntities) { speedDialEntities.associate { it.key to it.number } }
                // Subscribed only while the sheet is composed. Keeping this at screen scope made
                // the contacts + call-history streams live for the entire session.
                val dialpadMatches by viewModel.dialpadMatches.collectAsStateWithLifecycle()

                DialpadOverlay(
                    inputValue = dialpadInput,
                    onValueChange = {
                        if (it.length > dialpadInput.length) {
                            if (dialpadTonesEnabled) playDtmf(it.last().toString())
                        }
                        viewModel.onDialpadInputChange(it)
                    },
                    onClose = { isDialpadVisible = false },
                    onCallClick = { num ->
                        if (num.isNotEmpty()) {
                            initiateCall("Unknown", num)
                            viewModel.onDialpadInputChange("")
                            isDialpadVisible = false
                        }
                    },
                    onCallWithSim = { num, sim ->
                        if (num.isNotEmpty()) {
                            initiateCall("Unknown", num, simChoice = sim)
                            viewModel.onDialpadInputChange("")
                            isDialpadVisible = false
                        }
                    },
                    onSpeedDialCall = { num ->
                        initiateCall("Speed Dial", num)
                        isDialpadVisible = false
                    },
                    speedDialMap = speedDialMap,
                    voicemailNumber = voicemailNumber,
                    dialpadMatches = dialpadMatches,
                    viewModel = viewModel
                )
            }
        }
    }
}

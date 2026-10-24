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

package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.*
import com.example.*
import com.example.model.*
import com.example.util.CallAudioRecorder
import com.example.util.RecordingCompressionProfile
import com.example.util.RecordingCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import com.example.ui.theme.ThemeMode

class DialerViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        // Compiled once instead of once per contact/record inside the dialpad match loop.
        val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")
    }

    private val repository = DialerRepository(application)
    private val prefs = repository.context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)

    // Search Query
    var searchQuery = mutableStateOf("")
    private val _searchQueryFlow = MutableStateFlow("")

    // Account Source Filter & Default Contact Account
    private val initialAccountFilter = prefs.getString("selected_account_filter", "") ?: ""
    var selectedAccountFilter = mutableStateOf(initialAccountFilter)
    private val _selectedAccountFilterFlow = MutableStateFlow(initialAccountFilter)
    var availableAccounts = mutableStateListOf<ContactAccount>()
    var defaultContactAccountName = mutableStateOf(prefs.getString("default_contact_account_name", "") ?: "")
    var defaultContactAccountType = mutableStateOf(prefs.getString("default_contact_account_type", "") ?: "")

    // Dialpad Input Flow
    private val _dialpadInputFlow = MutableStateFlow("")
    var dialpadTextFieldValue = mutableStateOf(TextFieldValue(""))

    // Details Screen state
    var isCallHistoryDetailsOpen = mutableStateOf(false)

    fun onSearchQueryChange(newQuery: String) {
        searchQuery.value = newQuery
        _searchQueryFlow.value = newQuery
    }

    fun onAccountFilterChange(accountName: String) {
        selectedAccountFilter.value = accountName
        _selectedAccountFilterFlow.value = accountName
        prefs.edit { putString("selected_account_filter", accountName) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("selected_account_filter", accountName))
            } catch (_: Exception) {}
        }
    }

    fun updateDefaultContactAccount(accountName: String, accountType: String) {
        defaultContactAccountName.value = accountName
        defaultContactAccountType.value = accountType
        prefs.edit {
            putString("default_contact_account_name", accountName)
            putString("default_contact_account_type", accountType)
        }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("default_contact_account_name", accountName))
                repository.dao.insertSetting(AppSetting("default_contact_account_type", accountType))
            } catch (_: Exception) {}
        }
    }

    fun onDialpadTextFieldValueChange(newValue: TextFieldValue) {
        applyDialpadText(newValue.text)
    }

    fun onDialpadInputChange(newInput: String) {
        applyDialpadText(newInput)
    }

    fun insertDialpadDigit(digit: String) {
        applyDialpadText(dialpadInput.value + digit)
    }

    fun backspaceDialpad() {
        val raw = dialpadInput.value
        if (raw.isEmpty()) return
        applyDialpadText(raw.dropLast(1))
    }

    /**
     * Stores [text] as the canonical (raw) dialpad value and renders the spaced
     * display form into [dialpadTextFieldValue]. The keypad caps the number at
     * [com.example.util.DialpadFormatter.MAX_DIGITS] digits.
     */
    private fun applyDialpadText(text: String) {
        val raw = com.example.util.DialpadFormatter.raw(text)
        dialpadInput.value = raw
        _dialpadInputFlow.value = raw
        val display = com.example.util.DialpadFormatter.format(raw)
        dialpadTextFieldValue.value = TextFieldValue(text = display, selection = TextRange(display.length))
    }

    // PERF FIX: Replaces 100,000 nested iterations with instantaneous O(1) in-memory cache lookup
    fun isNumberUnsaved(number: String): Boolean {
        if (number.isBlank()) return false
        return ContactCache.getContact(number) == null
    }

    fun openAddContactWithNumber(number: String) {
        newContactName.value = ""
        newContactNumber.value = number
        newContactLabel.value = "Mobile"
        newContactEmail.value = ""
        isAddContactDialogVisible.value = true
    }

    fun openAddToExistingContactWithNumber(number: String, contact: Contact) {
        val currentNumbers = contact.getAllNumbers().toMutableList()
        val cleanNumber = number.filter { it.isDigit() || it == '+' }
        if (!currentNumbers.any { it.number.filter { c -> c.isDigit() || c == '+' } == cleanNumber }) {
            currentNumbers.add(LabeledNumber(number = number, label = "Mobile"))
        }
        val updatedContact = contact.copy(numbers = currentNumbers)
        oldContactToEdit.value = updatedContact
        editContactName.value = updatedContact.name
        editContactNumber.value = updatedContact.number
        editContactLabel.value = updatedContact.label
        editContactEmail.value = updatedContact.email
        isEditContactDialogVisible.value = true
    }

    private val _contactsLoading = MutableStateFlow(true)
    val contactsLoading: StateFlow<Boolean> = _contactsLoading.asStateFlow()
    private val _contactsRefreshing = MutableStateFlow(false)
    val contactsRefreshing: StateFlow<Boolean> = _contactsRefreshing.asStateFlow()
    private val _contactsLoadFailed = MutableStateFlow(false)
    val contactsLoadFailed: StateFlow<Boolean> = _contactsLoadFailed.asStateFlow()

    private val _callLogLoading = MutableStateFlow(true)
    val callLogLoading: StateFlow<Boolean> = _callLogLoading.asStateFlow()
    private val _callLogRefreshing = MutableStateFlow(false)
    val callLogRefreshing: StateFlow<Boolean> = _callLogRefreshing.asStateFlow()
    private val _callLogLoadFailed = MutableStateFlow(false)
    val callLogLoadFailed: StateFlow<Boolean> = _callLogLoadFailed.asStateFlow()

    val favoriteContacts: StateFlow<List<Contact>> = repository.getFavoriteContacts()
        .onEach { if (it.isNotEmpty()) _contactsLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000.milliseconds), emptyList())

    val allContactsFlow: StateFlow<List<Contact>> = repository.getAllContactsFlow()
        .onEach { if (it.isNotEmpty()) _contactsLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000.milliseconds), emptyList())

    val allCallHistoryFlow: StateFlow<List<CallRecord>> = repository.getAllCallHistoryFlow()
        .onEach { if (it.isNotEmpty()) _callLogLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000.milliseconds), emptyList())

    @OptIn(FlowPreview::class)
    val dialpadMatches: StateFlow<List<DialpadMatch>> = combine(
        allContactsFlow,
        allCallHistoryFlow,
        _dialpadInputFlow.debounce(100.milliseconds)
    ) { contacts, recents, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val cleanQuery = query.trim()
            val cleanQueryDigits = cleanQuery.filter { it.isDigit() || it == '+' }
            val normalizedQuery = java.text.Normalizer.normalize(cleanQuery, java.text.Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase(java.util.Locale.ROOT)

            val matchedContacts = com.example.util.DialpadSearch.findContactMatches(
                contacts = contacts,
                rawQuery = cleanQuery,
                limit = 15
            )

            if (matchedContacts.size >= 15) {
                matchedContacts
            } else {
                val contactNumbers = matchedContacts.map { it.number }.toSet()
                val matchedRecents = recents.asSequence().filter { record ->
                    if (record.number.isBlank() || record.number in contactNumbers) return@filter false

                    val normalizedName = java.text.Normalizer.normalize(record.name, java.text.Normalizer.Form.NFD)
                        .replace(COMBINING_MARKS, "")
                        .lowercase(java.util.Locale.ROOT)
                    val cleanRecNumber = record.number.filter { it.isDigit() || it == '+' }
                    val t9 = com.example.util.T9HighlightHelper.nameToT9(record.name)

                    normalizedName.contains(normalizedQuery) ||
                    (cleanQueryDigits.isNotEmpty() && cleanRecNumber.contains(cleanQueryDigits)) ||
                    record.number.contains(cleanQuery, ignoreCase = true) ||
                    t9.contains(cleanQuery, ignoreCase = true)
                }.distinctBy { it.number }
                .map { record ->
                    DialpadMatch(
                        number = record.number,
                        name = record.name,
                        label = "Recent • ${record.label}",
                        avatarText = record.avatarText,
                        avatarBgValue = record.avatarBgValue,
                        avatarTextColorValue = record.avatarTextColorValue,
                        isFromContacts = false,
                        isFromRecents = true,
                        photoUri = record.photoUri
                    )
                }.take(15 - matchedContacts.size).toList()

                matchedContacts + matchedRecents
            }
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000.milliseconds), emptyList())

    // UI State
    var isDialpadVisible = mutableStateOf(false)
    var dialpadInput = mutableStateOf("")
    var isSettingsVisible = mutableStateOf(false)
    var isDarkTheme = mutableStateOf(prefs.getBoolean("is_dark_theme", true))
    var themeMode = mutableStateOf(
        ThemeMode.fromKey(prefs.getString("theme_mode", "default"))
    )
    var isAmoledMode = mutableStateOf(prefs.getBoolean("is_amoled_mode", false))
    var isQuickReplyDirectSms = mutableStateOf(prefs.getBoolean("quick_reply_direct_sms", true))
    var customColorHex = mutableStateOf(prefs.getString("custom_color_hex", "#5A4BE0") ?: "#5A4BE0")
    var isM3Expressive = mutableStateOf(prefs.getBoolean("is_m3_expressive", true))
    var avatarShapeType = mutableStateOf(prefs.getString("avatar_shape_type", "squircle") ?: "squircle")
    var themeColor = mutableStateOf(
        run {
            val stored = prefs.getString("theme_color", "system") ?: "system"
            val migrated = prefs.getBoolean("accent_theme_migrated", false)
            val resolved = if (!migrated && stored == "nova_aurora") "system" else stored
            if (!migrated) prefs.edit { putBoolean("accent_theme_migrated", true) }
            resolved
        }
    )
    var useDynamicColor = mutableStateOf(prefs.getBoolean("use_dynamic_color", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S))
    var callWaitingEnabled = mutableStateOf(prefs.getBoolean("call_waiting_enabled", true))
    var isBiometricLockEnabled = mutableStateOf(prefs.getBoolean("is_biometric_lock_enabled", false))
    var isPocketProtectionEnabled = mutableStateOf(prefs.getBoolean("is_pocket_protection_enabled", false))
    var defaultStartupTabKey = mutableStateOf(prefs.getString("default_startup_tab_key", "RECENTS") ?: "RECENTS")
    var selectedTab = mutableIntStateOf(
        listOf("HOME", "RECENTS", "FAVORITES")
            .indexOf(prefs.getString("default_startup_tab_key", "RECENTS") ?: "RECENTS").coerceAtLeast(0)
    )
    var isCallNotesEnabled = mutableStateOf(prefs.getBoolean("is_call_notes_enabled", true))

    // Recording Preferences
    var recordingEnabled = mutableStateOf(prefs.getBoolean("recording_enabled", false))
    var autoTuneRecordingVolume = mutableStateOf(prefs.getBoolean("auto_tune_recording_volume", true))
    var recordingChimeEnabled = mutableStateOf(prefs.getBoolean("recording_chime_enabled", false))
    var isRecordingsBiometricLockEnabled = mutableStateOf(prefs.getBoolean("is_recordings_biometric_lock_enabled", false))
    var isAutoRecordCallsEnabled = mutableStateOf(prefs.getBoolean("is_auto_record_calls_enabled", false))
    var isAutoExportRecordingsEnabled = mutableStateOf(prefs.getBoolean("is_auto_export_recordings_enabled", false))
    var recordingCompressionProfile = mutableStateOf(
        RecordingCompressionProfile.fromKey(prefs.getString("recording_compression_profile", RecordingCompressionProfile.BALANCED.key))
    )
    var pendingPostCallRecordingNote = mutableStateOf<CallRecording?>(null)

    // Dashboard & Tab Layout & Swipe Preferences
    var isCallLogFiltersEnabled = mutableStateOf(prefs.getBoolean("is_call_log_filters_enabled", true))
    var isRowSwipeEnabled = mutableStateOf(prefs.getBoolean("is_row_swipe_enabled", false))

    var dialpadTonesEnabled = mutableStateOf(prefs.getBoolean("dialpad_tones_enabled", true))
    var vibrateOnClickEnabled = mutableStateOf(prefs.getBoolean("vibrate_on_click_enabled", true))
    var flipToSilenceEnabled = mutableStateOf(prefs.getBoolean("flip_to_silence_enabled", false))
    var isHideCallerIdEnabled = mutableStateOf(prefs.getBoolean("is_hide_caller_id_enabled", false))
    var clirPrefix = mutableStateOf(prefs.getString("clir_prefix", "#31#") ?: "#31#")

    // DUAL SIM FIX: Default to "Ask" on first launch so user is never locked out of SIM 2
    var preferredSim = mutableStateOf(prefs.getString("preferred_sim", "Ask") ?: "Ask")
    var voicemailNumber = mutableStateOf("")

    var hasContactsPermission = mutableStateOf(false)
    var hasCallLogPermission = mutableStateOf(false)
    var hasNotificationPermission = mutableStateOf(false)
    var isLoadingPermissions = mutableStateOf(true)

    var isCallActive = mutableStateOf(false)
    var isCallMinimized = mutableStateOf(false)
    var isLaunchedForCall = mutableStateOf(false)
    var callingContactName = mutableStateOf("")
    var callingContactNumber = mutableStateOf("")
    var isDefaultDialer = mutableStateOf(false)

    // Settings Flow observation
    val blockedNumbersFlow: StateFlow<List<BlockedNumber>> = repository.getBlockedNumbers()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speedDialFlow: StateFlow<List<SpeedDial>> = repository.getSpeedDial()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val quickResponsesFlow: StateFlow<List<QuickResponse>> = repository.getQuickResponses()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recordingsFlow: StateFlow<List<CallRecording>> = repository.getAllCallRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000.milliseconds), emptyList())

    val notesFlow: StateFlow<List<CallNote>> = repository.getAllCallNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000.milliseconds), emptyList())

    init {
        viewModelScope.launch {
            val savedSim = repository.getPreferredSim()
            preferredSim.value = savedSim.ifBlank { "Ask" }
            voicemailNumber.value = repository.getVoicemailNumber()

            try {
                val settings = repository.dao.getAllSettingsList().associate { it.key to it.value }
                prefs.edit {

                settings["dialpad_tones_enabled"]?.toBooleanStrictOrNull()?.let {
                    dialpadTonesEnabled.value = it
                    putBoolean("dialpad_tones_enabled", it)
                }
                settings["vibrate_on_click_enabled"]?.toBooleanStrictOrNull()?.let {
                    vibrateOnClickEnabled.value = it
                    putBoolean("vibrate_on_click_enabled", it)
                }
                settings["flip_to_silence_enabled"]?.toBooleanStrictOrNull()?.let {
                    flipToSilenceEnabled.value = it
                    putBoolean("flip_to_silence_enabled", it)
                }
                settings["call_waiting_enabled"]?.toBooleanStrictOrNull()?.let {
                    callWaitingEnabled.value = it
                    putBoolean("call_waiting_enabled", it)
                }
                settings["is_biometric_lock_enabled"]?.toBooleanStrictOrNull()?.let {
                    isBiometricLockEnabled.value = it
                    putBoolean("is_biometric_lock_enabled", it)
                }
                settings["is_pocket_protection_enabled"]?.toBooleanStrictOrNull()?.let {
                    isPocketProtectionEnabled.value = it
                    putBoolean("is_pocket_protection_enabled", it)
                }
                settings["is_call_notes_enabled"]?.toBooleanStrictOrNull()?.let {
                    isCallNotesEnabled.value = it
                    putBoolean("is_call_notes_enabled", it)
                }
                settings["is_hide_caller_id_enabled"]?.toBooleanStrictOrNull()?.let {
                    isHideCallerIdEnabled.value = it
                    putBoolean("is_hide_caller_id_enabled", it)
                }
                settings["clir_prefix"]?.let {
                    clirPrefix.value = it
                    putString("clir_prefix", it)
                }
                settings["is_call_log_filters_enabled"]?.toBooleanStrictOrNull()?.let {
                    isCallLogFiltersEnabled.value = it
                    putBoolean("is_call_log_filters_enabled", it)
                }
                settings["is_row_swipe_enabled"]?.toBooleanStrictOrNull()?.let {
                    isRowSwipeEnabled.value = it
                    putBoolean("is_row_swipe_enabled", it)
                }
                settings["selected_account_filter"]?.let {
                    selectedAccountFilter.value = it
                    _selectedAccountFilterFlow.value = it
                    putString("selected_account_filter", it)
                }
                settings["default_contact_account_name"]?.let {
                    defaultContactAccountName.value = it
                    putString("default_contact_account_name", it)
                }
                settings["recording_enabled"]?.toBooleanStrictOrNull()?.let {
                    recordingEnabled.value = it
                    putBoolean("recording_enabled", it)
                }
                settings["auto_tune_recording_volume"]?.toBooleanStrictOrNull()?.let {
                    autoTuneRecordingVolume.value = it
                    putBoolean("auto_tune_recording_volume", it)
                }
                settings["recording_chime_enabled"]?.toBooleanStrictOrNull()?.let {
                    recordingChimeEnabled.value = it
                    putBoolean("recording_chime_enabled", it)
                }
                settings["is_recordings_biometric_lock_enabled"]?.toBooleanStrictOrNull()?.let {
                    isRecordingsBiometricLockEnabled.value = it
                    putBoolean("is_recordings_biometric_lock_enabled", it)
                }
                settings["is_auto_record_calls_enabled"]?.toBooleanStrictOrNull()?.let {
                    isAutoRecordCallsEnabled.value = it
                    putBoolean("is_auto_record_calls_enabled", it)
                }
                settings["is_auto_export_recordings_enabled"]?.toBooleanStrictOrNull()?.let {
                    isAutoExportRecordingsEnabled.value = it
                    putBoolean("is_auto_export_recordings_enabled", it)
                }

                }
            } catch (_: Exception) {}

            val currentResponses = repository.getQuickResponses().first()
            if (currentResponses.isEmpty()) {
                listOf(
                    "Can't talk right now. I'll call you later.",
                    "I'm in a meeting. What's up?",
                    "I'm driving. I'll get back to you shortly.",
                    "Sorry, I'm busy. Can I call you back?"
                ).forEach { repository.addQuickResponse(it) }
            }

            try {
                syncRecordingsFromDisk(repository.context)
            } catch (_: Exception) {}
        }
    }

    fun updatePreferredSim(sim: String) {
        preferredSim.value = sim
        prefs.edit { putString("preferred_sim", sim) }
        viewModelScope.launch { repository.savePreferredSim(sim) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        val dark = mode != ThemeMode.DAY
        prefs.edit {
            putString("theme_mode", mode.key)
            putBoolean("is_dark_theme", dark)
            putBoolean("is_amoled_mode", mode == ThemeMode.NIGHT)
        }
        // keep isDarkTheme state coherent for any legacy readers
        isDarkTheme.value = dark
        isAmoledMode.value = mode == ThemeMode.NIGHT
    }

    fun updateThemeColor(color: String) {
        themeColor.value = color
        prefs.edit { putString("theme_color", color) }
    }

    fun updateCustomColorHex(hex: String) {
        customColorHex.value = hex
        prefs.edit { putString("custom_color_hex", hex) }
    }

    fun updateDarkTheme(dark: Boolean) {
        isDarkTheme.value = dark
        prefs.edit { putBoolean("is_dark_theme", dark) }
    }

    fun updateAmoledMode(amoled: Boolean) {
        isAmoledMode.value = amoled
        prefs.edit {
            putBoolean("is_amoled_mode", amoled)
            if (amoled && !isDarkTheme.value) {
                isDarkTheme.value = true
                putBoolean("is_dark_theme", true)
            }
        }
    }

    fun updateM3Expressive(expressive: Boolean) {
        isM3Expressive.value = expressive
        prefs.edit { putBoolean("is_m3_expressive", expressive) }
    }

    fun updateDefaultStartupTabKey(key: String) {
        defaultStartupTabKey.value = key
        prefs.edit { putString("default_startup_tab_key", key) }
        val slots = listOf("HOME", "RECENTS", "FAVORITES")
        selectedTab.intValue = slots.indexOf(key).coerceAtLeast(0)
    }

    fun selectTabBySlotKey(key: String) {
        val slots = listOf("HOME", "RECENTS", "FAVORITES")
        val index = slots.indexOf(key)
        if (index != -1) {
            selectedTab.intValue = index
        } else if (key == "DIALPAD") {
            isDialpadVisible.value = true
        }
    }

    fun updateDialpadTonesEnabled(enabled: Boolean) {
        dialpadTonesEnabled.value = enabled
        prefs.edit { putBoolean("dialpad_tones_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("dialpad_tones_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateVibrateOnClickEnabled(enabled: Boolean) {
        vibrateOnClickEnabled.value = enabled
        prefs.edit { putBoolean("vibrate_on_click_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("vibrate_on_click_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateFlipToSilenceEnabled(enabled: Boolean) {
        flipToSilenceEnabled.value = enabled
        prefs.edit { putBoolean("flip_to_silence_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("flip_to_silence_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateCallWaitingEnabled(enabled: Boolean) {
        callWaitingEnabled.value = enabled
        prefs.edit { putBoolean("call_waiting_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("call_waiting_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateHideCallerIdEnabled(enabled: Boolean) {
        isHideCallerIdEnabled.value = enabled
        prefs.edit { putBoolean("is_hide_caller_id_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("is_hide_caller_id_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateClirPrefix(prefix: String) {
        val sanitized = prefix.trim().ifBlank { "#31#" }
        clirPrefix.value = sanitized
        prefs.edit { putString("clir_prefix", sanitized) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("clir_prefix", sanitized))
            } catch (_: Exception) {}
        }
    }

    fun updateRecordingEnabled(enabled: Boolean) {
        recordingEnabled.value = enabled
        prefs.edit { putBoolean("recording_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("recording_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateAutoTuneRecordingVolume(enabled: Boolean) {
        autoTuneRecordingVolume.value = enabled
        prefs.edit { putBoolean("auto_tune_recording_volume", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("auto_tune_recording_volume", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateRecordingChimeEnabled(enabled: Boolean) {
        recordingChimeEnabled.value = enabled
        prefs.edit { putBoolean("recording_chime_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("recording_chime_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateBiometricLockEnabled(enabled: Boolean) {
        isBiometricLockEnabled.value = enabled
        prefs.edit { putBoolean("is_biometric_lock_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("is_biometric_lock_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateRecordingsBiometricLockEnabled(enabled: Boolean) {
        isRecordingsBiometricLockEnabled.value = enabled
        prefs.edit { putBoolean("is_recordings_biometric_lock_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("is_recordings_biometric_lock_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateAutoRecordCallsEnabled(enabled: Boolean) {
        isAutoRecordCallsEnabled.value = enabled
        prefs.edit { putBoolean("is_auto_record_calls_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("is_auto_record_calls_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateAutoExportRecordingsEnabled(enabled: Boolean) {
        isAutoExportRecordingsEnabled.value = enabled
        prefs.edit { putBoolean("is_auto_export_recordings_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("is_auto_export_recordings_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateRecordingCompressionProfile(profile: RecordingCompressionProfile) {
        recordingCompressionProfile.value = profile
        CallAudioRecorder.setCompressionProfile(repository.context, profile)
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("recording_compression_profile", profile.key))
            } catch (_: Exception) {}
        }
    }

    fun updatePocketProtectionEnabled(enabled: Boolean) {
        isPocketProtectionEnabled.value = enabled
        prefs.edit { putBoolean("is_pocket_protection_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("is_pocket_protection_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateCallNotesEnabled(enabled: Boolean) {
        isCallNotesEnabled.value = enabled
        prefs.edit { putBoolean("is_call_notes_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("is_call_notes_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateCallLogFiltersEnabled(enabled: Boolean) {
        isCallLogFiltersEnabled.value = enabled
        prefs.edit { putBoolean("is_call_log_filters_enabled", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("is_call_log_filters_enabled", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    fun updateRowSwipeEnabled(enabled: Boolean) {
        isRowSwipeEnabled.value = enabled
        prefs.edit { putBoolean("is_row_swipe_enabled", enabled) }
    }

    fun updateVoicemailNumber(num: String) {
        voicemailNumber.value = num
        viewModelScope.launch { repository.saveVoicemailNumber(num) }
    }

    fun addBlockedNumber(num: String) {
        viewModelScope.launch { repository.addBlockedNumber(num) }
    }

    fun removeBlockedNumber(num: String) {
        viewModelScope.launch { repository.removeBlockedNumber(num) }
    }

    fun writeTextToUri(uri: Uri, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = com.example.data.BackupRestoreManager.writeTextToUri(getApplication(), uri, content)
            onResult(success)
        }
    }

    fun writeBytesToUri(uri: Uri, bytes: ByteArray, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = try {
                val os = getApplication<Application>().contentResolver.openOutputStream(uri)
                if (os != null) {
                    os.use { it.write(bytes) }
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
            onResult(success)
        }
    }

    fun readTextFromUri(
        uri: Uri,
        kind: com.example.data.BackupRestoreManager.TextImportKind,
        onResult: (com.example.data.BackupRestoreManager.TextReadResult) -> Unit
    ) {
        viewModelScope.launch {
            onResult(com.example.data.BackupRestoreManager.readTextFromUri(getApplication(), uri, kind))
        }
    }

    fun exportBlockedNumbers(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val data = com.example.data.BackupRestoreManager.exportBlockedNumbers(getApplication())
            onResult(data)
        }
    }

    fun importBlockedNumbers(rawData: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = com.example.data.BackupRestoreManager.importBlockedNumbers(getApplication(), rawData)
            if (count > 0) syncData()
            onResult(count)
        }
    }

    fun exportBackup(password: String = "", onResult: (String) -> Unit) {
        viewModelScope.launch {
            val data = runCatching { repository.exportBackup(password) }.getOrDefault("")
            onResult(data)
        }
    }

    fun importBackup(rawData: String, password: String = "", onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importBackup(rawData, password)
            if (success) syncData()
            onResult(success)
        }
    }

    /**
     * Same as [importBackup] but returns the specific failure reason so the UI can explain what
     * went wrong (wrong password, damaged file, unsupported version, ...) instead of a generic error.
     */
    fun importBackupResult(
        rawData: String,
        password: String = "",
        onResult: (com.example.data.BackupRestoreManager.ImportResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.importBackupResult(rawData, password)
            if (result is com.example.data.BackupRestoreManager.ImportResult.Success) syncData()
            onResult(result)
        }
    }

    fun exportContactsVcf(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val data = com.example.data.BackupRestoreManager.exportContactsToVcf(getApplication())
            onResult(data)
        }
    }

    fun importContactsVcf(rawData: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = com.example.data.BackupRestoreManager.importContactsFromVcf(getApplication(), rawData)
            if (success) syncData()
            onResult(success)
        }
    }

    fun saveSpeedDial(key: Int, num: String, name: String) {
        viewModelScope.launch { repository.saveSpeedDial(key, num, name) }
    }

    fun deleteSpeedDial(key: Int) {
        viewModelScope.launch { repository.deleteSpeedDial(key) }
    }

    fun addQuickResponse(msg: String) {
        viewModelScope.launch { repository.addQuickResponse(msg) }
    }

    fun deleteQuickResponse(resp: QuickResponse) {
        viewModelScope.launch { repository.deleteQuickResponse(resp) }
    }

    fun updateQuickReplyDirectSms(enabled: Boolean) {
        isQuickReplyDirectSms.value = enabled
        prefs.edit { putBoolean("quick_reply_direct_sms", enabled) }
        viewModelScope.launch {
            try {
                repository.dao.insertSetting(AppSetting("quick_reply_direct_sms", enabled.toString()))
            } catch (_: Exception) {}
        }
    }

    // Helper Dialog State
    var isAddContactDialogVisible = mutableStateOf(false)
    var isEditContactDialogVisible = mutableStateOf(false)
    var isAddToExistingSheetVisible = mutableStateOf(false)
    var addToExistingPendingNumber = mutableStateOf("")
    var newContactName = mutableStateOf("")
    var newContactNumber = mutableStateOf("")
    var newContactLabel = mutableStateOf("Mobile")
    var newContactEmail = mutableStateOf("")
    var oldContactToEdit = mutableStateOf<Contact?>(null)
    var editContactName = mutableStateOf("")
    var editContactNumber = mutableStateOf("")
    var editContactLabel = mutableStateOf("Mobile")
    var editContactEmail = mutableStateOf("")

    private var isObserving = false

    fun startDataSyncAndObservation() {
        if (!isObserving) {
            isObserving = true
            syncData()
            refreshAvailableAccounts()
        }

        // Idempotent: re-evaluates the default-Phone role so a CallLog observer is added
        // after the user grants the role, or removed if it is revoked.
        repository.startObservingChanges {
            // A provider change is already the signal that something moved. Do not force a full
            // address-book rebuild for every call-log tick; the incremental check is enough.
            syncData(force = false)
            refreshAvailableAccounts()
        }
    }

    fun refreshAvailableAccounts() {
        viewModelScope.launch {
            try {
                val accounts = repository.fetchAvailableAccounts()
                availableAccounts.clear()
                availableAccounts.addAll(accounts)
            } catch (_: Exception) {}
        }
    }

    fun syncData(force: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_contactsLoading.value) _contactsRefreshing.value = true
            if (force) _contactsLoadFailed.value = false
            try {
                repository.syncContacts(force)
                _contactsLoadFailed.value = false
            } catch (_: Exception) {
                // A denial is rendered as the permission prompt, not as an error card. Keep the
                // failure flag so a genuine provider failure can still offer a retry.
                _contactsLoadFailed.value = true
            } finally {
                _contactsLoading.value = false
                _contactsRefreshing.value = false
            }

            if (!_callLogLoading.value) _callLogRefreshing.value = true
            if (force) _callLogLoadFailed.value = false
            try {
                repository.syncCallLogs()
                _callLogLoadFailed.value = false
            } catch (_: Exception) {
                _callLogLoadFailed.value = true
            } finally {
                _callLogLoading.value = false
                _callLogRefreshing.value = false
            }
        }
    }

    fun retryDataSync() {
        syncData(force = true)
    }

    fun addContactWithDetails(
        name: String,
        numbers: List<LabeledNumber>,
        emails: List<LabeledEmail> = emptyList(),
        addresses: List<LabeledAddress> = emptyList(),
        accountName: String = "",
        accountType: String = ""
    ) {
        viewModelScope.launch {
            repository.addContactWithDetails(name, numbers, emails, addresses, accountName, accountType)
            refreshAvailableAccounts()
        }
    }

    fun updateContactWithDetails(
        contact: Contact,
        name: String,
        numbers: List<LabeledNumber>,
        emails: List<LabeledEmail> = emptyList(),
        addresses: List<LabeledAddress> = emptyList()
    ) {
        viewModelScope.launch {
            repository.updateContactWithDetails(contact, name, numbers, emails, addresses)
            refreshAvailableAccounts()
        }
    }

    fun saveCallNote(number: String, note: String) {
        if (note.isBlank()) return
        viewModelScope.launch {
            repository.saveCallNote(CallNote(number = number, note = note, lastUpdated = System.currentTimeMillis()))
        }
    }

    fun deleteCallNoteById(id: Long) {
        viewModelScope.launch { repository.deleteCallNoteById(id) }
    }

    fun getCallNotesForNumberFlow(number: String): Flow<List<CallNote>> = repository.getCallNotesForNumberFlow(number)

    fun saveCallRecording(recording: CallRecording) {
        viewModelScope.launch {
            val insertedId = repository.saveCallRecording(recording)
            if (isAutoExportRecordingsEnabled.value) {
                withContext(Dispatchers.IO) {
                    val f = File(recording.filePath)
                    if (f.exists() && f.length() > 128L) {
                        CallAudioRecorder.exportRecordingToPublicDownloads(repository.context, f)
                    }
                }
            }
            if (isCallNotesEnabled.value) {
                pendingPostCallRecordingNote.value = recording.copy(id = insertedId.toInt())
            }
        }
    }

    fun dismissPostCallRecordingNote() {
        pendingPostCallRecordingNote.value = null
    }

    fun savePostCallRecordingNote(id: Int, number: String, note: String) {
        viewModelScope.launch {
            if (id > 0) repository.updateCallRecordingNote(id, note)
            if (note.isNotBlank() && number.isNotBlank()) repository.saveCallNote(number, note)
            pendingPostCallRecordingNote.value = null
        }
    }

    fun deleteCallRecording(id: Int) {
        viewModelScope.launch { repository.deleteCallRecording(id) }
    }

    fun updateCallRecordingNote(id: Int, note: String) {
        viewModelScope.launch { repository.updateCallRecordingNote(id, note) }
    }

    fun syncRecordingsFromDisk(context: Context, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = repository.getAllCallRecordings().first()
                // Upgrade legacy plaintext recordings in place. Encryption happens before the
                // new path is written, so a failure leaves the original file usable.
                val secured = existing.map { recording ->
                    val file = File(recording.filePath)
                    if (file.exists() && !RecordingCrypto.isEncrypted(file)) {
                        val encrypted = RecordingCrypto.encryptInPlace(file)
                        if (encrypted != null) {
                            val updated = recording.copy(filePath = encrypted.absolutePath)
                            repository.saveCallRecording(updated)
                            updated
                        } else {
                            recording
                        }
                    } else {
                        recording
                    }
                }
                val existingPaths = secured.map { it.filePath }.toSet()
                val existingNames = secured.map { File(it.filePath).name }.toSet()
                val recovered = CallAudioRecorder.recoverRecordingsFromDisk(context)
                var newCount = 0
                for (rec in recovered) {
                    val f = File(rec.filePath)
                    if (f.exists() && f.length() > 128L && !existingPaths.contains(rec.filePath) && !existingNames.contains(f.name)) {
                        val contactName = getContactNameFromNumber(context, rec.number) ?: rec.name
                        repository.saveCallRecording(rec.copy(name = contactName))
                        newCount++
                    }
                }
                withContext(Dispatchers.Main) { onComplete?.invoke(newCount) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onComplete?.invoke(0) }
            }
        }
    }

    fun recoverRecordingsFromDisk(context: Context, onComplete: ((Int) -> Unit)? = null) {
        syncRecordingsFromDisk(context, onComplete)
    }

    fun exportRecordingToDownloads(context: Context, filePath: String): Boolean {
        val f = File(filePath)
        return CallAudioRecorder.exportRecordingToPublicDownloads(context, f)
    }

    fun exportAllRecordingsToDownloads(context: Context): Int = CallAudioRecorder.exportAllRecordingsToDownloads(context)

    fun cleanupCorruptOrEmptyRecordings(context: Context): Int {
        val count = CallAudioRecorder.cleanupCorruptOrEmptyFiles(context)
        syncRecordingsFromDisk(context)
        return count
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
            refreshAvailableAccounts()
        }
    }

    fun deleteCallLog(id: Int) {
        viewModelScope.launch { repository.deleteCallLog(id) }
    }

    fun deleteCallLogsByNumber(number: String) {
        viewModelScope.launch { repository.deleteCallLogsByNumber(number) }
    }

    fun clearAllCallLogs() {
        viewModelScope.launch { repository.clearAllCallLogs() }
    }

    fun toggleFavorite(number: String, isFavorite: Boolean) {
        viewModelScope.launch { repository.toggleFavorite(number, isFavorite) }
    }

    suspend fun getCallHistoryByNumber(number: String): List<CallRecord> = repository.getCallHistoryByNumber(number)

    fun logCall(name: String, number: String, type: CallType, durationSeconds: Long, simSlot: Int = 1) {
        viewModelScope.launch {
            repository.insertLocalCallRecord(name, number, type, durationSeconds, simSlot)
            syncData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopObservingChanges()
    }
}

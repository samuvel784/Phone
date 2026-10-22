/*
 * Copyright (C) 2026 Nova Dialer contributors
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

package com.example.ui.components

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.getAvatarShape
import com.example.model.getInitials
import com.example.telecom.AnnouncerScript
import com.example.telecom.CallProtectionPrefs
import com.example.telecom.CallerAnnouncerManager
import com.example.telecom.DoNotDisturbPrefs
import com.example.telecom.FocusMode
import com.example.telecom.ProtectionLevel
import com.example.ui.theme.AppShapes
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.CallScreenPermissionHelper
import com.example.util.RichHapticEngine
import java.util.Locale

/**
 * Self-contained "Focus & Protection" settings screen.
 *
 * Combines the two features ported from Nova Dialer on top of the Secure Dialer core:
 *  - Focus mode + call protection level (VIP / saved / repeat caller rules)
 *  - Caller announcer (text-to-speech that reads the caller's name while ringing)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusAndProtectionSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color,
    highlightedTitle: String? = null
) {
    val context = LocalContext.current

    var focusMode by remember { mutableStateOf(CallProtectionPrefs.getFocusMode(context)) }
    var protectionLevel by remember { mutableStateOf(CallProtectionPrefs.getProtectionLevel(context)) }
    var allowRepeat by remember { mutableStateOf(CallProtectionPrefs.isRepeatCallerAllowed(context)) }
    var vipNumbers by remember { mutableStateOf(CallProtectionPrefs.getVipNumbers(context).sorted()) }

    var announcerEnabled by remember { mutableStateOf(CallProtectionPrefs.isAnnouncerEnabled(context)) }
    var announcerTemplate by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerTemplate(context)) }
    var announcerUserName by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerUserName(context)) }
    var announcerRepeat by remember { mutableIntStateOf(CallProtectionPrefs.getAnnouncerRepeat(context)) }
    var announcerSpeed by remember { mutableIntStateOf(CallProtectionPrefs.getAnnouncerSpeedPercent(context)) }
    var announcerPitch by remember { mutableIntStateOf(CallProtectionPrefs.getAnnouncerPitchPercent(context)) }
    var announcerVolume by remember { mutableIntStateOf(CallProtectionPrefs.getAnnouncerVolumePercent(context)) }
    var headphonesOnly by remember { mutableStateOf(CallProtectionPrefs.isAnnouncerHeadphonesOnly(context)) }
    var announcerVoice by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerVoice(context)) }
    var announceUnknown by remember { mutableStateOf(CallProtectionPrefs.isAnnounceUnknown(context)) }

    var showAddVipDialog by remember { mutableStateOf(false) }
    var vipSearchInput by remember { mutableStateOf("") }
    val allContacts by viewModel.allContactsFlow.collectAsStateWithLifecycle()

    var dndEnabled by remember { mutableStateOf(DoNotDisturbPrefs.isEnabled(context)) }
    var dndStartMin by remember { mutableIntStateOf(DoNotDisturbPrefs.getStartMin(context)) }
    var dndEndMin by remember { mutableIntStateOf(DoNotDisturbPrefs.getEndMin(context)) }
    var dndDays by remember { mutableIntStateOf(DoNotDisturbPrefs.getDays(context)) }
    var pickerTarget by remember { mutableStateOf<Int?>(null) }

    var hasScreeningRole by remember { mutableStateOf(CallScreenPermissionHelper.hasCallScreeningRole(context)) }
    val screeningLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasScreeningRole = CallScreenPermissionHelper.hasCallScreeningRole(context)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasScreeningRole = CallScreenPermissionHelper.hasCallScreeningRole(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // TTS engine used only for the preview button on this screen.
    val announcer = remember { CallerAnnouncerManager(context) }
    DisposableEffect(Unit) {
        onDispose { announcer.shutdown() }
    }

    val scrollState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ------------------------------------------------- CALL SCREENING ROLE STATUS
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasScreeningRole) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasScreeningRole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (hasScreeningRole) R.string.screening_role_granted else R.string.screening_role_needed
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.screening_role_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!hasScreeningRole) {
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        CallScreenPermissionHelper.requestCallScreeningRole(context, screeningLauncher)
                    }) {
                        Text(stringResource(R.string.btn_grant))
                    }
                }
            }
        }

        // ---------------------------------------------------------------- FOCUS MODE
        PreferenceHeader(stringResource(R.string.header_focus_mode))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("Focus Mode", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DoNotDisturbOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.focus_mode_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.focus_mode_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            FocusMode.entries.forEach { mode ->
                ProtectionChoiceRow(
                    title = focusModeLabel(mode),
                    subtitle = focusModeSubtitle(mode),
                    selected = mode == focusMode,
                    onSelect = {
                        focusMode = mode
                        CallProtectionPrefs.setFocusMode(context, mode)
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        // ------------------------------------------------- DO NOT DISTURB SCHEDULE
        PreferenceHeader(stringResource(R.string.dnd_title))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("Do Not Disturb", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dnd_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dnd_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dnd_enabled_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dnd_enabled_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dndEnabled,
                        onCheckedChange = {
                            dndEnabled = it
                            DoNotDisturbPrefs.setAll(context, it, dndStartMin, dndEndMin, dndDays)
                        }
                    )
                }
                if (dndEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DndTimeChip(
                            label = stringResource(R.string.dnd_start_label),
                            value = formatTimeOfDay(dndStartMin),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                pickerTarget = 0
                            }
                        )
                        DndTimeChip(
                            label = stringResource(R.string.dnd_end_label),
                            value = formatTimeOfDay(dndEndMin),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                pickerTarget = 1
                            }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.dnd_days_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dayEntries = listOf(
                            R.string.dnd_day_mon to DoNotDisturbPrefs.MON,
                            R.string.dnd_day_tue to DoNotDisturbPrefs.TUE,
                            R.string.dnd_day_wed to DoNotDisturbPrefs.WED,
                            R.string.dnd_day_thu to DoNotDisturbPrefs.THU,
                            R.string.dnd_day_fri to DoNotDisturbPrefs.FRI,
                            R.string.dnd_day_sat to DoNotDisturbPrefs.SAT,
                            R.string.dnd_day_sun to DoNotDisturbPrefs.SUN
                        )
                        dayEntries.forEach { (label, bit) ->
                            val active = (dndDays and (1 shl bit)) != 0
                            FilterChip(
                                selected = active,
                                onClick = {
                                    dndDays = if (active) dndDays and (1 shl bit).inv() else dndDays or (1 shl bit)
                                    DoNotDisturbPrefs.setAll(context, dndEnabled, dndStartMin, dndEndMin, dndDays)
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                    if (DoNotDisturbPrefs.isQuietHoursNow(context)) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = AppShapes.Chip,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NightsStay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.dnd_active_now),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        // -------------------------------------------------------- PROTECTION LEVEL
        PreferenceHeader(stringResource(R.string.header_call_protection))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("Call Protection Level", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.protection_level_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.protection_level_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            ProtectionLevel.entries.forEach { level ->
                ProtectionChoiceRow(
                    title = protectionLevelLabel(level),
                    subtitle = protectionLevelSubtitle(level),
                    selected = level == protectionLevel,
                    onSelect = {
                        protectionLevel = level
                        CallProtectionPrefs.setProtectionLevel(context, level)
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                    }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsRowToggle(
                title = stringResource(R.string.protection_allow_repeat_title),
                subtitle = stringResource(R.string.protection_allow_repeat_sub),
                checked = allowRepeat,
                onCheckedChange = {
                    allowRepeat = it
                    CallProtectionPrefs.setRepeatCallerAllowed(context, it)
                }
            )
            Text(
                text = stringResource(R.string.protection_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ---------------------------------------------------------------- VIP LIST
        PreferenceHeader(stringResource(R.string.header_vip_numbers))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("VIP Numbers", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vip_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.vip_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (vipNumbers.isEmpty()) {
                Text(
                    text = stringResource(R.string.vip_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            } else {
                vipNumbers.forEach { number ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = number,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                CallProtectionPrefs.removeVipNumber(context, number)
                                vipNumbers = CallProtectionPrefs.getVipNumbers(context).sorted()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.vip_remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsRowNav(
                title = stringResource(R.string.vip_add),
                subtitle = stringResource(R.string.vip_add_sub),
                onClick = {
                    vipSearchInput = ""
                    showAddVipDialog = true
                },
                icon = Icons.Default.Add,
                iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
        }

        // ------------------------------------------------------- CALLER ANNOUNCER
        PreferenceHeader(stringResource(R.string.header_caller_announcer))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("Caller Announcer", highlightedTitle) ||
                isMatchTitle("Announcer", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            SettingsRowToggle(
                title = stringResource(R.string.announcer_enable_title),
                subtitle = stringResource(R.string.announcer_enable_sub),
                checked = announcerEnabled,
                onCheckedChange = {
                    announcerEnabled = it
                    CallProtectionPrefs.setAnnouncerEnabled(context, it)
                },
                icon = Icons.Default.Campaign,
                iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary
            )

            AnimatedVisibility(
                visible = announcerEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnnouncerVoiceSelector(
                        selected = announcerVoice,
                        onSelect = {
                            announcerVoice = it
                            CallProtectionPrefs.setAnnouncerVoice(context, it)
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(
                        text = stringResource(R.string.announcer_message_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            AnnouncerScript.PRESETS.forEach { (label, template) ->
                ProtectionChoiceRow(
                    title = label,
                    subtitle = AnnouncerScript.render(template, "Alex Morgan", announcerUserName, "saved contact"),
                    selected = announcerTemplate == template,
                    onSelect = {
                        announcerTemplate = template
                        CallProtectionPrefs.setAnnouncerTemplate(context, template)
                    }
                )
            }
            OutlinedTextField(
                value = announcerTemplate,
                onValueChange = {
                    val capped = it.take(AnnouncerScript.MAX_TEMPLATE_LENGTH)
                    announcerTemplate = capped
                    CallProtectionPrefs.setAnnouncerTemplate(context, capped)
                },
                label = { Text(stringResource(R.string.announcer_template_label)) },
                supportingText = { Text(stringResource(R.string.announcer_template_help)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = announcerUserName,
                onValueChange = {
                    val capped = it.take(AnnouncerScript.MAX_USER_NAME_LENGTH)
                    announcerUserName = capped
                    CallProtectionPrefs.setAnnouncerUserName(context, capped)
                },
                label = { Text(stringResource(R.string.announcer_your_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            AnnouncerSlider(
                title = stringResource(R.string.announcer_repeat_title),
                value = announcerRepeat,
                valueLabel = "$announcerRepeat×",
                range = 1f..3f,
                steps = 1,
                onChange = {
                    announcerRepeat = it
                    CallProtectionPrefs.setAnnouncerRepeat(context, it)
                }
            )
            AnnouncerSlider(
                title = stringResource(R.string.announcer_speed_title),
                value = announcerSpeed,
                valueLabel = "$announcerSpeed%",
                range = 50f..150f,
                steps = 0,
                onChange = {
                    announcerSpeed = it
                    CallProtectionPrefs.setAnnouncerSpeedPercent(context, it)
                }
            )
            AnnouncerSlider(
                title = stringResource(R.string.announcer_pitch_title),
                value = announcerPitch,
                valueLabel = "$announcerPitch%",
                range = 50f..150f,
                steps = 0,
                onChange = {
                    announcerPitch = it
                    CallProtectionPrefs.setAnnouncerPitchPercent(context, it)
                }
            )
            AnnouncerSlider(
                title = stringResource(R.string.announcer_volume_title),
                value = announcerVolume,
                valueLabel = "$announcerVolume%",
                range = 10f..100f,
                steps = 0,
                onChange = {
                    announcerVolume = it
                    CallProtectionPrefs.setAnnouncerVolumePercent(context, it)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsRowToggle(
                title = stringResource(R.string.announcer_unknown_title),
                subtitle = stringResource(R.string.announcer_unknown_sub),
                checked = announceUnknown,
                onCheckedChange = {
                    announceUnknown = it
                    CallProtectionPrefs.setAnnounceUnknown(context, it)
                }
            )
            SettingsRowToggle(
                title = stringResource(R.string.announcer_headphones_title),
                subtitle = stringResource(R.string.announcer_headphones_sub),
                checked = headphonesOnly,
                onCheckedChange = {
                    headphonesOnly = it
                    CallProtectionPrefs.setAnnouncerHeadphonesOnly(context, it)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsRowNav(
                title = stringResource(R.string.announcer_test_title),
                subtitle = stringResource(R.string.announcer_test_sub),
                onClick = { announcer.testAnnouncement() },
                icon = Icons.Default.Campaign,
                iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(4.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showAddVipDialog) {
        AlertDialog(
            onDismissRequest = { showAddVipDialog = false },
            title = { Text(stringResource(R.string.vip_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = vipSearchInput,
                        onValueChange = { vipSearchInput = it },
                        label = { Text(stringResource(R.string.add_call_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val filteredContacts = remember(vipSearchInput, allContacts) {
                        val query = vipSearchInput.trim()
                        if (query.isBlank()) {
                            allContacts
                        } else {
                            allContacts.filter { contact ->
                                contact.name.contains(query, ignoreCase = true) ||
                                contact.number.contains(query) ||
                                contact.getAllNumbers().any { it.number.contains(query) }
                            }
                        }
                    }

                    if (filteredContacts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.vip_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = filteredContacts,
                                key = { "${it.id}_${it.number}" }
                            ) { contact ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                        if (contact.number.isNotBlank()) {
                                            CallProtectionPrefs.addVipNumber(context, contact.number)
                                            vipNumbers = CallProtectionPrefs.getVipNumbers(context).sorted()
                                            showAddVipDialog = false
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = getAvatarShape("circular"),
                                            color = contact.avatarBg
                                        ) {
                                            var imageFailed by remember(contact.photoUri) { mutableStateOf(false) }
                                            Box(contentAlignment = Alignment.Center) {
                                                if (contact.photoUri.isNotEmpty() && !imageFailed) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context)
                                                            .data(contact.photoUri)
                                                            .size(128, 128)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = contact.name,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop,
                                                        onError = { imageFailed = true }
                                                    )
                                                } else {
                                                    Text(
                                                        text = contact.avatarText.ifEmpty { getInitials(contact.name) },
                                                        color = contact.avatarTextColor,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = contact.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = contact.number,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddVipDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (pickerTarget != null) {
            val current = if (pickerTarget == 0) dndStartMin else dndEndMin
            val timeState = rememberTimePickerState(
                initialHour = (current / 60) % 24,
                initialMinute = current % 60,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { pickerTarget = null },
                title = { Text(stringResource(R.string.dnd_pick_time_title)) },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(onClick = {
                        val mins = timeState.hour * 60 + timeState.minute
                        if (pickerTarget == 0) {
                            dndStartMin = mins
                        } else {
                            dndEndMin = mins
                        }
                        DoNotDisturbPrefs.setAll(context, dndEnabled, dndStartMin, dndEndMin, dndDays)
                        pickerTarget = null
                    }) { Text(stringResource(R.string.btn_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { pickerTarget = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
    }

@Composable
private fun ProtectionChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnnouncerVoiceSelector(
    selected: CallProtectionPrefs.AnnouncerVoice,
    onSelect: (CallProtectionPrefs.AnnouncerVoice) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.announcer_voice_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(
                CallProtectionPrefs.AnnouncerVoice.DEFAULT to R.string.announcer_voice_default,
                CallProtectionPrefs.AnnouncerVoice.MALE to R.string.announcer_voice_male,
                CallProtectionPrefs.AnnouncerVoice.FEMALE to R.string.announcer_voice_female
            )
            options.forEach { (voice, labelRes) ->
                FilterChip(
                    selected = selected == voice,
                    onClick = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        onSelect(voice)
                    },
                    label = { Text(stringResource(labelRes)) },
                    leadingIcon = if (selected == voice) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.announcer_voice_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnnouncerSlider(
    title: String,
    value: Int,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun focusModeLabel(mode: FocusMode): String = when (mode) {
    FocusMode.OFF -> stringResource(R.string.focus_mode_off)
    FocusMode.SAVED_CONTACTS_ONLY -> stringResource(R.string.focus_mode_saved)
    FocusMode.VIP_ONLY -> stringResource(R.string.focus_mode_vip)
    FocusMode.SILENCE_ALL -> stringResource(R.string.focus_mode_silence_all)
}

@Composable
private fun focusModeSubtitle(mode: FocusMode): String = when (mode) {
    FocusMode.OFF -> stringResource(R.string.focus_mode_off_sub)
    FocusMode.SAVED_CONTACTS_ONLY -> stringResource(R.string.focus_mode_saved_sub)
    FocusMode.VIP_ONLY -> stringResource(R.string.focus_mode_vip_sub)
    FocusMode.SILENCE_ALL -> stringResource(R.string.focus_mode_silence_all_sub)
}

@Composable
private fun protectionLevelLabel(level: ProtectionLevel): String = when (level) {
    ProtectionLevel.STANDARD -> stringResource(R.string.protection_standard)
    ProtectionLevel.STRICT -> stringResource(R.string.protection_strict)
    ProtectionLevel.MAXIMUM -> stringResource(R.string.protection_maximum)
}

@Composable
private fun protectionLevelSubtitle(level: ProtectionLevel): String = when (level) {
    ProtectionLevel.STANDARD -> stringResource(R.string.protection_standard_sub)
    ProtectionLevel.STRICT -> stringResource(R.string.protection_strict_sub)
    ProtectionLevel.MAXIMUM -> stringResource(R.string.protection_maximum_sub)
}

private fun formatTimeOfDay(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return String.format(Locale.US, "%02d:%02d", h, m)
}

@Composable
private fun DndTimeChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

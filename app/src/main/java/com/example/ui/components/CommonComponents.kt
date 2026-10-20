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

package com.example.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Dialpad as DialpadOutlined
import androidx.compose.material.icons.outlined.History as HistoryOutlined
import androidx.compose.material.icons.outlined.Home as HomeOutlined
import androidx.compose.material.icons.outlined.Person as PersonOutlined
import androidx.compose.material.icons.outlined.StarBorder as StarOutlined
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.compose.ui.res.stringResource

import com.example.ui.theme.AppShapes
import com.example.ui.theme.NovaMotion
import com.example.ui.theme.NovaTheme

@Composable
fun HeaderSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    menuVisible: Boolean = false,
    onMenuClick: () -> Unit = {}
) {
    val nova = NovaTheme.colors
    var focused by remember { mutableStateOf(false) }

    // The bar is white on the porcelain Day page and one tone lighter than the page in the dark themes.
    val containerColor = if (nova.isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = NovaMotion.fade(NovaMotion.QUICK),
        label = "searchBorder"
    )
    val iconColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = NovaMotion.fade(NovaMotion.QUICK),
        label = "searchIcon"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp),
        shape = AppShapes.Chip,
        color = containerColor,
        border = BorderStroke(if (focused) 1.5.dp else 1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (menuVisible) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search_placeholder),
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1.0f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/**
 * Bottom navigation (Home / Recent / Favourites ...).
 * A tonal pill slides between the tabs on a spring, the selected icon switches from outline to filled, and the
 * bar paints its own background under the system gesture area, so it sits flush at the bottom of the screen.
 */
@Composable
fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabSlots: List<String> = listOf("HOME", "RECENTS", "FAVORITES")
) {
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val unselected = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
                    .height(70.dp)
            ) {
                val slotCount = tabSlots.size.coerceAtLeast(1)
                // Explicit receiver makes the BoxWithConstraints scope use visible to
                // both the compiler and Android Studio's inspection.
                val availableWidth = this@BoxWithConstraints.maxWidth
                val itemWidth = availableWidth / slotCount
                val pillWidth = 64.dp
                val indicatorX = animateDpAsState(
                    targetValue = itemWidth * selectedTab.coerceIn(0, slotCount - 1),
                    animationSpec = NovaMotion.snappy(),
                    label = "navIndicator"
                )

                // sliding pill behind the selected icon (offset is read while drawing, so no recomposition per frame)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (indicatorX.value + (itemWidth - pillWidth) / 2).roundToPx(),
                                y = 9.dp.roundToPx()
                            )
                        }
                        .size(width = pillWidth, height = 32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    tabSlots.forEachIndexed { index, slotKey ->
                        val selected = selectedTab == index
                        val label = stringResource(
                            when (slotKey) {
                                "HOME" -> R.string.tab_home
                                "FAVORITES" -> R.string.tab_favorites
                                "CONTACTS" -> R.string.tab_contacts
                                "DIALPAD" -> R.string.tab_dialpad
                                else -> R.string.tab_recents
                            }
                        )
                        val icon: ImageVector = when (slotKey) {
                            "HOME" -> if (selected) Icons.Default.Home else Icons.Outlined.HomeOutlined
                            "FAVORITES" -> if (selected) Icons.Default.Star else Icons.Outlined.StarOutlined
                            "CONTACTS" -> if (selected) Icons.Default.Person else Icons.Outlined.PersonOutlined
                            "DIALPAD" -> if (selected) Icons.Default.Dialpad else Icons.Outlined.DialpadOutlined
                            else -> if (selected) Icons.Default.History else Icons.Outlined.HistoryOutlined
                        }
                        val tint by animateColorAsState(
                            targetValue = if (selected) primary else unselected,
                            animationSpec = NovaMotion.fade(NovaMotion.QUICK),
                            label = "navTint"
                        )
                        val iconScale = animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = NovaMotion.snappy(),
                            label = "navIconScale"
                        )
                        val source = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(
                                    selected = selected,
                                    interactionSource = source,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (!selected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onTabSelected(index)
                                        }
                                    }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(9.dp))
                            Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            val s = iconScale.value
                                            scaleX = s
                                            scaleY = s
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                color = tint,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateIllustration(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Default.Person
) {
    val nova = NovaTheme.colors
    // gentle fade-and-rise when the empty state appears
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress = animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = NovaMotion.smooth(),
        label = "emptyProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, bottom = 32.dp, start = 32.dp, end = 32.dp)
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * 24.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(nova.canvasTint),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(nova.brand),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun localizeContactLabel(label: String): String {
    val cleanLabel = label.trim()
    return when {
        cleanLabel.startsWith("Recent • ", ignoreCase = true) -> {
            val subLabel = cleanLabel.substringAfter("Recent • ").trim()
            val localizedSub = when (subLabel.lowercase(java.util.Locale.ROOT)) {
                "mobile" -> stringResource(R.string.label_mobile)
                "work" -> stringResource(R.string.label_work)
                "home" -> stringResource(R.string.label_home)
                "other" -> stringResource(R.string.label_other)
                else -> subLabel
            }
            stringResource(R.string.recent_label_format, localizedSub)
        }
        else -> {
            when (cleanLabel.lowercase(java.util.Locale.ROOT)) {
                "mobile" -> stringResource(R.string.label_mobile)
                "work" -> stringResource(R.string.label_work)
                "home" -> stringResource(R.string.label_home)
                "other" -> stringResource(R.string.label_other)
                else -> label
            }
        }
    }
}

fun getCurrentLocale(context: Context): java.util.Locale {
    // minSdk 24 > N (24); the modern LocaleList API is always available.
    return context.resources.configuration.locales[0]
}

@Composable
fun ExpandableSettingsCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconBgColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    cardBgColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    initiallyExpanded: Boolean = false,
    badgeText: String? = null,
    hasSwitch: Boolean = false,
    isSwitchChecked: Boolean = true,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "expand_icon_rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = AppShapes.Card
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = AppShapes.Chip,
                    color = if (hasSwitch && !isSwitchChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else iconBgColor,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (hasSwitch && !isSwitchChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (hasSwitch && !isSwitchChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (hasSwitch && !isSwitchChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (badgeText != null && (!hasSwitch || isSwitchChecked)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                if (hasSwitch && onSwitchChange != null) {
                    Switch(
                        checked = isSwitchChecked,
                        onCheckedChange = { checked ->
                            onSwitchChange(checked)
                            if (checked && !expanded) {
                                expanded = true
                            }
                        }
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(if (expanded) R.string.btn_collapse else R.string.btn_expand),
                        modifier = Modifier.rotate(rotationState),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (hasSwitch && !isSwitchChecked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.feature_disabled_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.feature_enable_prompt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = { onSwitchChange?.invoke(true) }
                        ) {
                            Text(
                                text = stringResource(R.string.btn_enable_feature),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    content()
                }
            }
        }
    }
}

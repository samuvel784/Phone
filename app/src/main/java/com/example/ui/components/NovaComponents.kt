/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NovaTheme
import com.example.ui.theme.pressScale

/** Floating action button in the brand gradient, with a soft coloured glow and a springy press. */
@Composable
fun NovaFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val nova = NovaTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .size(62.dp)
            .pressScale(source, 0.92f)
            .shadow(elevation = 16.dp, shape = shape, ambientColor = nova.glow, spotColor = nova.glow)
            .clip(shape)
            .background(nova.brand)
            .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

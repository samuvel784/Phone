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

import com.example.ui.theme.NovaMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppShapes
import com.example.util.RichHapticEngine

@Composable
fun InCallButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.Keypad,
    /**
     * When false the control is visibly dimmed and does nothing. Used for actions the current
     * Telecom state / platform does not support (e.g. Bluetooth with no call-audio endpoint).
     */
    enabled: Boolean = true
) {
    val context = LocalContext.current

    // Off: soft tonal tile. On: solid accent colour with its contrasting icon, so the state is obvious at a glance.
    val targetColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val targetContent = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val btnColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = NovaMotion.fade(NovaMotion.QUICK),
        label = "incall_button_color"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = NovaMotion.fade(NovaMotion.QUICK),
        label = "incall_button_content"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessHigh,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "incall_button_scale"
    )

    Surface(
        onClick = {
            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        // Disabled controls are reported as disabled to accessibility services and cannot be
        // activated, instead of silently swallowing the tap.
        enabled = enabled,
        modifier = modifier.graphicsLayer {
            val sc = scale.value
            scaleX = sc
            scaleY = sc
            alpha = if (enabled) 1f else 0.55f
        },
        shape = shape,
        color = btnColor,
        contentColor = contentColor,
        border = if (isActive || !enabled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    // The always-visible label below already carries the name; leaving the icon
                    // undescribed avoids TalkBack announcing the button twice.
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                    textAlign = TextAlign.Center,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

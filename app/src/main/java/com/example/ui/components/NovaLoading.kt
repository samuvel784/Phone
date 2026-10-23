/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.AppShapes
import com.example.ui.theme.NovaMotion
import com.example.ui.theme.NovaTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * System animation preference. The app intentionally has no separate "reduce motion" switch:
 * Android's animator duration scale is the single source of truth, and it is re-read whenever
 * the app returns to the foreground so a change in Developer options takes effect immediately.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reduced by remember(context) { mutableStateOf(reducedMotionFromSystem(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reduced = reducedMotionFromSystem(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return reduced
}

private fun reducedMotionFromSystem(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
        return true
    }
    val scale = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Exception) {
        1f
    }
    return scale == 0f
}

/**
 * The Nova Orbit mark: two restrained orbital arcs around a soft accent core.
 *
 * It is a single Canvas (no per-frame recomposition) and falls back to a static mark when the
 * system animator scale is disabled. No layout inside the canvas means it is cheap enough for
 * repeated use in a list header.
 */
@Composable
fun NovaOrbitIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    contentDescription: String? = null
) {
    val description = contentDescription ?: stringResource(R.string.loading)
    val reduced = rememberReducedMotion()
    val accent = MaterialTheme.colorScheme.primary
    val accentSecondary = NovaTheme.colors.gradientEnd
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    val angle = if (reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "nova_orbit")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "nova_orbit_angle"
        )
        animated
    }
    val pulse = if (reduced) {
        0.6f
    } else {
        val transition = rememberInfiniteTransition(label = "nova_orbit_pulse")
        val animated by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1300, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "nova_orbit_pulse_value"
        )
        animated
    }

    val semantics = Modifier.semantics { this.contentDescription = description }

    Canvas(modifier = modifier.size(size).then(semantics).testTag("nova_orbit_indicator")) {
        val stroke = size.toPx() * 0.045f
        val r = this.size.minDimension / 2f
        val inset = stroke
        drawCircle(
            color = track,
            radius = r - inset,
            style = Stroke(width = stroke)
        )
        rotate(angle) {
            drawArc(
                color = accent,
                startAngle = 18f,
                sweepAngle = 108f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        val inner = r * 0.62f
        rotate(-angle * 0.72f) {
            drawArc(
                color = accentSecondary.copy(alpha = 0.85f),
                startAngle = 200f,
                sweepAngle = 74f,
                useCenter = false,
                topLeft = Offset(r - inner, r - inner),
                size = Size(inner * 2, inner * 2),
                style = Stroke(width = stroke * 0.72f, cap = StrokeCap.Round)
            )
        }
        // A small orbit dot makes the motion readable without adding another composable.
        val dotAngle = Math.toRadians((angle + 18f).toDouble())
        val orbitRadius = r - inset
        drawCircle(
            color = accent.copy(alpha = pulse),
            radius = stroke * 1.35f,
            center = Offset(
                x = r + cos(dotAngle).toFloat() * orbitRadius,
                y = r + sin(dotAngle).toFloat() * orbitRadius
            )
        )
    }
}

/** One lightweight shimmer block. Reduced motion turns it into a stable tonal placeholder. */
@Composable
fun NovaSkeletonBlock(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    val reduced = rememberReducedMotion()
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    val highlight = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val progress = if (reduced) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "nova_shimmer")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1350, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "nova_shimmer_progress"
        )
        animated
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(base)
            .drawBehind {
                val travel = size.width * 1.8f
                val start = -travel + progress * travel * 2f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(start + travel, 0f)
                    )
                )
            }
    )
}

/** Compact placeholder that mirrors a contact row (avatar + name + secondary line). */
@Composable
fun NovaContactRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NovaSkeletonBlock(
            modifier = Modifier.size(46.dp),
            shape = CircleShape
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            NovaSkeletonBlock(
                modifier = Modifier.fillMaxWidth(0.48f).height(15.dp),
                shape = RoundedCornerShape(6.dp)
            )
            Spacer(modifier = Modifier.height(7.dp))
            NovaSkeletonBlock(
                modifier = Modifier.fillMaxWidth(0.30f).height(11.dp),
                shape = RoundedCornerShape(5.dp)
            )
        }
    }
}

/** Contacts/favorites skeleton: header + six rows (favorites get two columns). */
@Composable
fun NovaContactsSkeleton(
    modifier: Modifier = Modifier,
    favoritesOnly: Boolean = false
) {
    val description = stringResource(R.string.loading_contacts)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            }
            .testTag("nova_contacts_skeleton")
    ) {
        NovaSkeletonBlock(
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp, bottom = 12.dp)
                .width(26.dp)
                .height(14.dp),
            shape = RoundedCornerShape(5.dp)
        )
        if (favoritesOnly) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NovaSkeletonBlock(
                        modifier = Modifier.weight(1f).height(84.dp),
                        shape = AppShapes.Card
                    )
                    NovaSkeletonBlock(
                        modifier = Modifier.weight(1f).height(84.dp),
                        shape = AppShapes.Card
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            repeat(6) { NovaContactRowSkeleton() }
        }
    }
}

/** Recents skeleton that matches the card rhythm of [RecentCallRow]. */
@Composable
fun NovaRecentsSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading_recents)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("nova_recents_skeleton"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(AppShapes.Card)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovaSkeletonBlock(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    NovaSkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.42f).height(15.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NovaSkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.62f).height(11.dp),
                        shape = RoundedCornerShape(5.dp)
                    )
                }
            }
        }
    }
}

/** A page-level error with one actionable retry. Never used for permission denial. */
@Composable
fun NovaErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.load_error_title),
    message: String = stringResource(R.string.load_error_message)
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .testTag("nova_error_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onRetry, shape = AppShapes.Card) {
            Text(stringResource(R.string.btn_retry))
        }
    }
}

/** Thin, non-blocking refresh indicator for data that is already on screen. */
@Composable
fun NovaRefreshingBar(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.refreshing)
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .semantics { contentDescription = description }
            .testTag("nova_refreshing_bar"),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent
    )
}

/** Full-surface blocking progress for backup/restore/import work that must not be double-tapped. */
@Composable
fun NovaLoadingOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            // Swallow taps while the export/import is running so the work cannot be started twice.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .testTag("nova_loading_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(AppShapes.Dialog)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NovaOrbitIndicator(size = 64.dp, contentDescription = message)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

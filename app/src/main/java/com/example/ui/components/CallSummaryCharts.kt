/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CallRecord
import com.example.ui.theme.getDialedCallColor
import com.example.ui.theme.getMissedCallColor
import com.example.ui.theme.getReceivedCallColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Ring chart showing how the selected period splits between received, dialled and
 * missed calls. Drawn with Compose Canvas so no charting library is required.
 */
@Composable
fun CallTypeDonutChart(
    missed: Int,
    outgoing: Int,
    received: Int,
    modifier: Modifier = Modifier
) {
    val total = (missed + outgoing + received).coerceAtLeast(1)
    val missedColor = getMissedCallColor()
    val outgoingColor = getDialedCallColor()
    val receivedColor = getReceivedCallColor()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val centerColor = MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                var startAngle = -90f
                fun slice(value: Int, color: Color) {
                    if (value <= 0) return
                    val sweep = 360f * value / total
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
                slice(received, receivedColor)
                slice(outgoing, outgoingColor)
                slice(missed, missedColor)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = (missed + outgoing + received).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = centerColor
                )
                Text(
                    text = stringResource(R.string.filter_all),
                    style = MaterialTheme.typography.labelSmall,
                    color = subColor
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartLegendRow(received, stringResource(R.string.filter_received), receivedColor)
            ChartLegendRow(outgoing, stringResource(R.string.filter_dialed), outgoingColor)
            ChartLegendRow(missed, stringResource(R.string.filter_missed), missedColor)
        }
    }
}

@Composable
private fun ChartLegendRow(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Simple 7-day bar chart of call volume (oldest on the left, today on the right).
 */
@Composable
fun WeeklyActivityChart(
    records: List<CallRecord>,
    modifier: Modifier = Modifier
) {
    val dayLabels = remember(records) {
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val labels = ArrayList<String>(7)
        val counts = IntArray(7)
        for (i in 6 downTo 0) {
            val start = todayStart - i * DAY_MS
            val end = start + DAY_MS
            counts[6 - i] = records.count { it.timestampMs in start until end }
            labels.add(fmt.format(Date(start)))
        }
        labels to counts
    }

    val labels = dayLabels.first
    val counts = dayLabels.second
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 4.dp)) {
            val gaps = 8.dp.toPx()
            val barWidth = (size.width - gaps * (counts.size - 1)) / counts.size
            val radius = CornerRadius(6.dp.toPx())
            counts.forEachIndexed { index, value ->
                val left = index * (barWidth + gaps)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius
                )
                if (value > 0) {
                    val barHeight = (size.height - 8.dp.toPx()) * value / maxCount
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = if (index == labels.lastIndex) MaterialTheme.colorScheme.primary else labelColor,
                    fontWeight = if (index == labels.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Compact two-line stat tile used for average duration / busiest day. */
@Composable
fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

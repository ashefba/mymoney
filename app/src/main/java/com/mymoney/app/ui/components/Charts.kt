package com.mymoney.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Chart geometry uses floats purely for drawing; all amounts shown as text come from exact cents.

data class ChartSlice(val label: String, val value: Long, val color: Color)

@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 26.dp,
    center: @Composable () -> Unit = {},
) {
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        val cx = this.size.width / 2f
                        val cy = this.size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        val outer = minOf(cx, cy)
                        val inner = outer - thickness.toPx() * 1.6f
                        if (dist < inner || dist > outer + 8f) {
                            onSelect(null); return@detectTapGestures
                        }
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (angle < 0f) angle += 360f
                        var acc = 0f
                        var hit: Int? = null
                        slices.forEachIndexed { i, s ->
                            val sweep = 360f * s.value / total
                            if (hit == null && angle >= acc && angle < acc + sweep) hit = i
                            acc += sweep
                        }
                        onSelect(if (hit == selectedIndex) null else hit)
                    }
                }
        ) {
            val strokePx = thickness.toPx()
            val diameter = minOf(this.size.width, this.size.height) - strokePx * 1.4f
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = 360f * s.value / total * progress.value
                val selected = i == selectedIndex
                val gap = if (slices.size > 1) 1.5f else 0f
                if (sweep > gap) {
                    drawArc(
                        color = if (selectedIndex == null || selected) s.color else s.color.copy(alpha = 0.35f),
                        startAngle = start + gap / 2f,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(if (selected) strokePx * 1.35f else strokePx, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

data class BarGroup(val label: String, val values: List<Long>)

@Composable
fun BarChart(
    groups: List<BarGroup>,
    colors: List<Color>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
) {
    val progress = remember(groups) { Animatable(0f) }
    LaunchedEffect(groups) { progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val maxValue = groups.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val grid = MaterialTheme.colorScheme.outlineVariant

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(groups) {
                    detectTapGestures { offset ->
                        if (groups.isEmpty()) return@detectTapGestures
                        val w = this.size.width / groups.size
                        val i = (offset.x / w).toInt().coerceIn(0, groups.size - 1)
                        onSelect(if (i == selectedIndex) null else i)
                    }
                }
        ) {
            if (groups.isEmpty()) return@Canvas
            val groupW = this.size.width / groups.size
            val h = this.size.height
            for (k in 1..3) {
                val y = h - h * k / 4f
                drawLine(grid, Offset(0f, y), Offset(this.size.width, y), strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            groups.forEachIndexed { gi, g ->
                val x0 = gi * groupW
                if (gi == selectedIndex) {
                    drawRoundRect(highlight, Offset(x0 + 2f, 0f), Size(groupW - 4f, h), CornerRadius(14f, 14f))
                }
                val n = g.values.size.coerceAtLeast(1)
                val inner = groupW * 0.7f
                val barW = (inner / n) * 0.82f
                val startX = x0 + (groupW - inner) / 2f
                g.values.forEachIndexed { vi, v ->
                    val bh = (h * 0.92f) * (v.toFloat() / maxValue.toFloat()) * progress.value
                    val bx = startX + vi * (inner / n) + ((inner / n) - barW) / 2f
                    if (bh > 0f) {
                        val color = colors[vi % colors.size]
                        drawRoundRect(
                            color = if (selectedIndex == null || selectedIndex == gi) color else color.copy(alpha = 0.45f),
                            topLeft = Offset(bx, h - bh),
                            size = Size(barW, bh),
                            cornerRadius = CornerRadius(barW / 3f, barW / 3f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            groups.forEachIndexed { i, g ->
                Text(
                    g.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selectedIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

data class LineSeries(val label: String, val values: List<Long>, val color: Color, val dashed: Boolean = false)

@Composable
fun LineChart(
    series: List<LineSeries>,
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    val count = series.maxOfOrNull { it.values.size } ?: 0
    val progress = remember(series) { Animatable(0f) }
    LaunchedEffect(series) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val all = series.flatMap { it.values }
    val maxV = (all.maxOrNull() ?: 1L).coerceAtLeast(0L)
    val minV = (all.minOrNull() ?: 0L).coerceAtMost(0L)
    val range = (maxV - minV).coerceAtLeast(1L).toFloat()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.onSurfaceVariant

    fun indexAt(x: Float, width: Float): Int {
        if (count <= 1) return 0
        val step = width / (count - 1)
        return (x / step).roundToInt().coerceIn(0, count - 1)
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(series) {
                    detectTapGestures { offset -> if (count > 0) onSelect(indexAt(offset.x, this.size.width.toFloat())) }
                }
                .pointerInput(series) {
                    detectHorizontalDragGestures { change, _ ->
                        if (count > 0) onSelect(indexAt(change.position.x, this.size.width.toFloat()))
                    }
                }
        ) {
            if (count == 0) return@Canvas
            val w = this.size.width
            val h = this.size.height
            val pad = 8f
            fun yOf(v: Long): Float = pad + (h - 2 * pad) * (1f - (v - minV) / range)
            val step = if (count > 1) w / (count - 1) else w
            for (k in 0..3) {
                val y = pad + (h - 2 * pad) * k / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            if (minV < 0) {
                drawLine(marker.copy(alpha = 0.6f), Offset(0f, yOf(0)), Offset(w, yOf(0)), strokeWidth = 2f)
            }
            series.forEach { s ->
                if (s.values.isEmpty()) return@forEach
                val path = Path()
                val visible = ((s.values.size - 1) * progress.value).coerceAtLeast(0f)
                s.values.forEachIndexed { i, v ->
                    if (i > visible.toInt() + 1) return@forEachIndexed
                    val x = i * step
                    val y = yOf(v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path, s.color,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
                    ),
                )
                if (s.values.size == 1) drawCircle(s.color, 5.dp.toPx(), Offset(0f, yOf(s.values[0])))
            }
            selectedIndex?.let { i ->
                if (i in 0 until count) {
                    val x = i * step
                    drawLine(marker, Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                    series.forEach { s ->
                        s.values.getOrNull(i)?.let { v ->
                            drawCircle(Color.White, 6.dp.toPx(), Offset(x, yOf(v)))
                            drawCircle(s.color, 4.dp.toPx(), Offset(x, yOf(v)))
                        }
                    }
                }
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val picks = listOf(0, labels.size / 2, labels.size - 1).distinct()
                picks.forEach { i -> Text(labels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

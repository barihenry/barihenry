package com.barihenry.decide.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barihenry.decide.model.OptionStats
import kotlin.math.max
import kotlin.math.min

/** Score range shown on the x axis: at least -10..10, widened to fit all outcomes. */
fun xRangeFor(stats: List<OptionStats?>): ClosedFloatingPointRange<Double> {
    val s = stats.filterNotNull()
    val lo = min(-10.0, s.minOfOrNull { it.worst } ?: -10.0)
    val hi = max(10.0, s.maxOfOrNull { it.best } ?: 10.0)
    return lo..hi
}

/**
 * Probability histogram of an option's outcomes: one bar per distinct score, height =
 * probability. Good outcomes are drawn in the primary colour, bad ones in the error colour,
 * and a dashed line marks the expected value.
 */
@Composable
fun Histogram(
    stats: OptionStats,
    modifier: Modifier = Modifier,
    xRange: ClosedFloatingPointRange<Double> = xRangeFor(listOf(stats)),
    height: Dp = 200.dp,
    detailed: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val evStyle = TextStyle(fontSize = 10.sp, color = colors.tertiary)

    Canvas(modifier.fillMaxWidth().height(height)) {
        val topPad = if (detailed) 30.dp.toPx() else 2.dp.toPx() // EV label row + bar label row
        val bottomPad = if (detailed) 16.dp.toPx() else 2.dp.toPx()
        val sidePad = 8.dp.toPx()
        val plotW = size.width - 2 * sidePad
        val plotH = size.height - topPad - bottomPad
        val baseY = topPad + plotH
        val span = (xRange.endInclusive - xRange.start).coerceAtLeast(1e-9)
        fun xOf(score: Double) = sidePad + ((score - xRange.start) / span * plotW).toFloat()
        val maxP = stats.bars.maxOf { it.probability }.coerceAtLeast(1e-9)
        val barW = (plotW / span * 0.8).toFloat().coerceIn(3.dp.toPx(), 28.dp.toPx())

        // Axis and zero line.
        drawLine(colors.outline, Offset(sidePad, baseY), Offset(size.width - sidePad, baseY), 1.dp.toPx())
        drawLine(colors.outlineVariant, Offset(xOf(0.0), topPad), Offset(xOf(0.0), baseY), 1.dp.toPx())

        // Bars.
        for (bar in stats.bars) {
            val h = (bar.probability / maxP * plotH).toFloat()
            val color = when {
                bar.score > 0 -> colors.primary
                bar.score < 0 -> colors.error
                else -> colors.outline
            }
            val left = xOf(bar.score) - barW / 2
            drawRect(color, Offset(left, baseY - h), Size(barW, h))
            if (detailed && stats.bars.size <= 10) {
                val t = measurer.measure("${(bar.probability * 100).toInt()}%", labelStyle)
                drawText(t, topLeft = Offset(xOf(bar.score) - t.size.width / 2f, baseY - h - t.size.height))
            }
        }

        // Expected value marker.
        val evX = xOf(stats.expectedValue)
        drawLine(
            colors.tertiary, Offset(evX, topPad), Offset(evX, baseY), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )

        if (detailed) {
            val ev = measurer.measure("EV ${fmt(stats.expectedValue)}", evStyle)
            val evLabelX = (evX - ev.size.width / 2f).coerceIn(0f, size.width - ev.size.width)
            drawText(ev, topLeft = Offset(evLabelX, 0f))
            for (v in listOf(xRange.start, 0.0, xRange.endInclusive)) {
                val t = measurer.measure(fmt(v), labelStyle)
                val x = (xOf(v) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
                drawText(t, topLeft = Offset(x, baseY + 2.dp.toPx()))
            }
        }
    }
}

/** Compact number formatting: rounds to one decimal and drops a trailing ".0". */
fun fmt(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return when {
        r == Math.rint(r) -> r.toLong().toString()
        else -> "%.1f".format(r)
    }
}

fun pct(fraction: Double): String = "${fmt(fraction * 100)}%"

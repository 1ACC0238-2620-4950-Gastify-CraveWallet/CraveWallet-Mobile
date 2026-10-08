package com.cravewallet.app.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cravewallet.app.data.Bar
import com.cravewallet.app.data.CategoryShare
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.OutlineVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Escala "bonita" del eje Y: tope y paso. */
private fun niceScale(max: Double, ticks: Int = 5): Pair<Double, Double> {
    if (max <= 0) return 100.0 to 20.0
    val raw = max / ticks
    val candidates = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 250.0, 500.0, 1000.0, 2000.0, 5000.0)
    val step = candidates.firstOrNull { it >= raw } ?: candidates.last()
    return step * ceil(max / step) to step
}

/**
 * Gráfico de barras con eje Y en soles. La barra seleccionada se pinta en primario y muestra un tooltip.
 * [showTooltip] = false oculta el tooltip (p. ej. en la barra del mes actual).
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 140.dp,
    showTooltip: Boolean = true,
    tooltipDetail: (Bar) -> String? = { "${it.count} ${if (it.count == 1) "suscripción" else "suscripciones"}" },
) {
    val (top, step) = niceScale(bars.maxOfOrNull { it.total } ?: 0.0)
    val tickCount = (top / step).roundToInt()
    val density = LocalDensity.current
    Row(modifier.fillMaxWidth()) {
        // Eje Y
        Column(
            Modifier
                .height(chartHeight)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            for (i in tickCount downTo 0) {
                Text("S/ ${(step * i).roundToInt()}", style = CwType.Label, color = OnSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight)) {
                val slot = maxWidth / bars.size.coerceAtLeast(1)
                Canvas(Modifier.fillMaxSize()) {
                    val labelPad = 6.dp.toPx() // centra las líneas con el texto del eje
                    for (i in 0..tickCount) {
                        val y = labelPad + (size.height - 2 * labelPad) * i / tickCount
                        drawLine(OutlineVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(vertical = 6.dp),
                ) {
                    bars.forEachIndexed { i, bar ->
                        val isSelected = i == selected
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(role = Role.Button) { onSelect(i) }
                                .semantics {
                                    this.selected = isSelected
                                    contentDescription = "${bar.label}: ${Fmt.pen(bar.total)}"
                                },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val fraction = if (top > 0) (bar.total / top).toFloat() else 0f
                            Box(
                                Modifier
                                    .fillMaxWidth(0.62f)
                                    .fillMaxHeight(fraction.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (isSelected) Primary else PrimaryContainer),
                            )
                        }
                    }
                }
                // Tooltip
                if (showTooltip && selected in bars.indices) {
                    val bar = bars[selected]
                    val fraction = if (top > 0) (bar.total / top).toFloat() else 0f
                    val barTop = 6.dp + (maxHeight - 12.dp) * (1f - fraction)
                    var tipWidth by remember { mutableIntStateOf(0) }
                    var tipHeight by remember { mutableIntStateOf(0) }
                    val centerX = with(density) { (slot * selected + slot / 2).toPx() }
                    val maxX = with(density) { maxWidth.toPx() } - tipWidth
                    val x = (centerX - tipWidth / 2f).coerceIn(0f, maxX.coerceAtLeast(0f))
                    val y = with(density) { barTop.toPx() } - tipHeight - with(density) { 6.dp.toPx() }
                    Column(
                        Modifier
                            .offset { IntOffset(x.roundToInt(), y.roundToInt().coerceAtLeast(-tipHeight / 2)) }
                            .onSizeChanged { tipWidth = it.width; tipHeight = it.height }
                            .wrapContentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(OnSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(bar.tooltip, style = CwType.BodyStrong, color = Color.White)
                        tooltipDetail(bar)?.let { Text(it, style = CwType.Caption, color = Color.White.copy(alpha = 0.85f)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                bars.forEachIndexed { i, bar ->
                    Text(
                        bar.label,
                        style = CwType.Caption,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == selected) OnSurface else OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Gráfico de dona por categoría con el total al centro. */
@Composable
fun DonutChart(shares: List<CategoryShare>, centerLabel: String, centerValue: String, modifier: Modifier = Modifier) {
    Box(modifier.size(148.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = 22.dp.toPx()
            val gap = if (shares.size > 1) 2f else 0f
            var start = -90f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            if (shares.isEmpty()) {
                drawArc(OutlineVariant, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            }
            shares.forEach { s ->
                val sweep = (s.share * 360f).toFloat()
                drawArc(s.category.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerValue, style = CwType.BodyStrong.copy(fontWeight = FontWeight.Bold), color = OnSurface)
            Text(centerLabel, style = CwType.Label, color = OnSurfaceVariant)
        }
    }
}

/** Barras punteadas del estado vacío (N4). */
@Composable
fun EmptyBarsPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val heights = listOf(0.45f, 0.64f, 0.55f, 0.80f, 0.70f, 0.90f)
        val slot = size.width / heights.size
        val w = slot * 0.55f
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        heights.forEachIndexed { i, h ->
            val left = slot * i + (slot - w) / 2
            val barH = size.height * h
            drawRect(
                OnSurfaceVariant,
                topLeft = Offset(left, size.height - barH),
                size = Size(w, barH),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash),
            )
        }
        drawLine(OnSurfaceVariant, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.5.dp.toPx())
    }
}

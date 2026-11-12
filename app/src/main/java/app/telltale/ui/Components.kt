package app.telltale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.telltale.model.FieldStatus

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) { content() }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)

@Composable
fun BigValue(value: String, unit: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.displaySmall.merge(Tabular), fontWeight = FontWeight.Medium)
        if (unit != null) Text(" $unit", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall.merge(Tabular), color = color)

@Composable
fun StatusDot(status: FieldStatus, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(statusColor(status)))
}

@Composable
fun StatusPill(status: FieldStatus) {
    val c = statusColor(status)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        StatusDot(status, 7)
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun TwoUp(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)

/** Small multi-series line graph (e.g. x/y/z), auto-scaled, drawn on a Canvas (no flicker, no allocations per value). */
@Composable
fun Sparkline(series: List<FloatArray>, modifier: Modifier = Modifier, colors: List<Color> = sparkColors()) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        if (series.size < 2) return@Canvas
        val dims = series.first().size
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        series.forEach { a -> a.forEach { if (it < lo) lo = it; if (it > hi) hi = it } }
        if (hi - lo < 1e-3f) { hi += 0.5f; lo -= 0.5f }
        val dx = size.width / (series.size - 1)
        for (d in 0 until dims) {
            val p = Path()
            series.forEachIndexed { i, a ->
                val v = a.getOrElse(d) { 0f }
                val y = size.height - (v - lo) / (hi - lo) * size.height
                if (i == 0) p.moveTo(0f, y) else p.lineTo(i * dx, y)
            }
            drawPath(p, colors[d % colors.size], style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun sparkColors(): List<Color> {
    val on = MaterialTheme.colorScheme.onSurfaceVariant
    return listOf(MaterialTheme.colorScheme.primary, on, on.copy(alpha = 0.45f))
}

@Composable
fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

@Composable
fun Stack(content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }

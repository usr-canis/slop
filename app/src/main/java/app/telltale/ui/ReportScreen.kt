package app.telltale.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import app.telltale.BuildConfig
import app.telltale.model.CapabilityReport
import app.telltale.model.FieldReading
import app.telltale.model.FieldStatus
import app.telltale.model.Fields
import java.io.File

@Composable
fun ReportScreen() {
    val f = rememberFields()
    val ctx = LocalContext.current
    val (avail, total) = CapabilityReport.summary(f)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(CapabilityReport.carTitle(f), style = MaterialTheme.typography.headlineSmall)
        Text("$avail of $total car fields available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { shareText(ctx, CapabilityReport.text(f, BuildConfig.VERSION_NAME)) }) { Text("Share text") }
            OutlinedButton(onClick = { shareImage(ctx, f) }) { Text("Share image") }
        }
        Fields.all.groupBy { it.group }.forEach { (group, defs) ->
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(group)
                    defs.forEach { d ->
                        val r = f[d.key]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(d.label, style = MaterialTheme.typography.bodyMedium)
                                if (r?.status == FieldStatus.AVAILABLE && r.text != null) Mono(r.text, Modifier.padding(end = 8.dp))
                            }
                            StatusPill(r?.status ?: FieldStatus.WAITING)
                        }
                    }
                }
            }
        }
    }
}

private fun shareText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
}

/** Renders the checklist to a PNG with plain Android Canvas and shares it. */
private fun shareImage(ctx: Context, f: Map<String, FieldReading>) {
    val dark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val bg = if (dark) 0xFF111413.toInt() else 0xFFFAFAF9.toInt()
    val fg = if (dark) 0xFFE1E3E2.toInt() else 0xFF191C1B.toInt()
    val sub = if (dark) 0xFFA6ADAA.toInt() else 0xFF5B615F.toInt()
    fun color(s: FieldStatus) = when (s) {
        FieldStatus.AVAILABLE -> 0xFF0E7C6B.toInt()
        FieldStatus.UNAVAILABLE -> 0xFFB07A12.toInt()
        FieldStatus.NO_PERMISSION -> 0xFFC2402F.toInt()
        else -> sub
    }
    val groups = Fields.all.groupBy { it.group }
    val w = 1080; val line = 52; val pad = 64
    val h = pad * 2 + 200 + groups.size * 90 + Fields.all.size * line
    val bmp = createBitmap(w, h)
    val c = Canvas(bmp); c.drawColor(bg)
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; textSize = 56f; typeface = Typeface.DEFAULT_BOLD }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; textSize = 36f }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sub; textSize = 30f; letterSpacing = 0.08f }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    var y = pad + 56f
    c.drawText(CapabilityReport.carTitle(f), pad.toFloat(), y, title); y += 60
    val (a, t) = CapabilityReport.summary(f)
    c.drawText("$a of $t car fields available · Telltale ${BuildConfig.VERSION_NAME}", pad.toFloat(), y, small); y += 70
    groups.forEach { (g, defs) ->
        y += 30; c.drawText(g.uppercase(), pad.toFloat(), y, small); y += 50
        defs.forEach { d ->
            val st = f[d.key]?.status ?: FieldStatus.WAITING
            dot.color = color(st); c.drawCircle(pad + 12f, y - 12, 12f, dot)
            c.drawText(d.label, pad + 44f, y, text)
            val p = Paint(text).apply { color = color(st); textAlign = Paint.Align.RIGHT }
            c.drawText(st.label, (w - pad).toFloat(), y, p)
            y += line
        }
    }
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "telltale-report.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share report"))
}

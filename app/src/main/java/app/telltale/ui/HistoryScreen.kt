package app.telltale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telltale.TelltaleApp
import app.telltale.model.Fields
import app.telltale.model.Units
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun HistoryScreen() {
    val dao = TelltaleApp.graph.db.dao()
    val settings = rememberSettings()
    val imperial = Units.imperial(settings.units, null)
    val since = remember { System.currentTimeMillis() - 90L * 86_400_000 }
    val sessions by dao.sessions().collectAsStateWithLifecycle(emptyList())
    val odo by remember { dao.series(Fields.ODOMETER, since) }.collectAsStateWithLifecycle(emptyList())
    val fuel by remember { dao.series(Fields.FUEL, since) }.collectAsStateWithLifecycle(emptyList())
    val battery by remember { dao.series(Fields.BATTERY, since) }.collectAsStateWithLifecycle(emptyList())
    val range by remember { dao.series(Fields.RANGE, since) }.collectAsStateWithLifecycle(emptyList())
    val locale = LocalConfiguration.current.locales[0]
    val fmt = remember(locale) { SimpleDateFormat("EEE d MMM, HH:mm", locale) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!settings.recording) Text("Recording is off. Turn it on in Settings to build odometer, fuel and range history automatically.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HistoryChart("Odometer", odo.map { it.value.toFloat() }, odo.lastOrNull()?.let { Units.distanceKm(it.value, imperial, 0) + " " + Units.distanceUnit(imperial) })
        HistoryChart("Fuel level", fuel.map { it.value.toFloat() }, fuel.lastOrNull()?.let { "%.0f%%".format(it.value) })
        if (battery.isNotEmpty()) HistoryChart("Battery level", battery.map { it.value.toFloat() }, "%.0f%%".format(battery.last().value))
        HistoryChart("Range", range.map { it.value.toFloat() }, range.lastOrNull()?.let { Units.distanceKm(it.value, imperial, 0) + " " + Units.distanceUnit(imperial) })

        SectionTitle("Session log")
        if (sessions.isEmpty()) Text("No sessions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        sessions.forEach { s ->
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(listOfNotNull(s.year?.toString(), s.make, s.model).joinToString(" ").ifBlank { "Car session" }, style = MaterialTheme.typography.titleSmall)
                    Mono("Connected    ${fmt.format(Date(s.startedAt))}")
                    Mono("Disconnected ${s.endedAt?.let { fmt.format(Date(it)) } ?: "still connected"}")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val mins = ((s.endedAt ?: System.currentTimeMillis()) - s.startedAt) / 60000
                        Mono("$mins min")
                        Mono("${s.sampleCount} samples")
                        s.connectionType?.let { Mono(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChart(title: String, values: List<Float>, latest: String?) {
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(title)
                Mono(latest ?: "no data")
            }
            // Downsample to at most 300 points for drawing.
            val step = (values.size / 300).coerceAtLeast(1)
            Sparkline(values.filterIndexed { i, _ -> i % step == 0 }.map { floatArrayOf(it) })
        }
    }
}

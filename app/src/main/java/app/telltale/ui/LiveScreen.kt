package app.telltale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telltale.TelltaleApp
import app.telltale.data.AppSettings
import app.telltale.model.CapabilityReport
import app.telltale.model.CarDataMapper
import app.telltale.model.FieldReading
import app.telltale.model.FieldStatus
import app.telltale.model.Fields
import app.telltale.model.Units
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample

/** Fields throttled to 10 updates/second for smooth, flicker-free UI. */
@Composable
fun rememberFields(): Map<String, FieldReading> {
    val repo = TelltaleApp.graph.repository
    val flow = remember { repo.fields.sample(100).onStart { emit(repo.fields.value) } }
    val v by flow.collectAsStateWithLifecycle(emptyMap())
    return v
}

@Composable
fun rememberSettings(): AppSettings {
    val s by TelltaleApp.graph.settings.settings.collectAsStateWithLifecycle(AppSettings())
    return s
}

@Composable
private fun rememberSeries(key: String): List<FloatArray> {
    val repo = TelltaleApp.graph.repository
    val v by produceState(emptyList<FloatArray>(), key) { while (true) { value = repo.series(key); delay(100) } }
    return v
}

private fun Map<String, FieldReading>.num(k: String) = this[k]?.takeIf { it.status == FieldStatus.AVAILABLE }?.numeric
private fun Map<String, FieldReading>.st(k: String) = this[k]?.status ?: FieldStatus.WAITING

@Composable
private fun StatusLine(f: Map<String, FieldReading>, key: String) {
    val st = f.st(key)
    if (st != FieldStatus.AVAILABLE) Row(Modifier.padding(top = 6.dp)) { StatusPill(st) }
}

@Composable
fun LiveScreen() {
    val f = rememberFields()
    val settings = rememberSettings()
    val imperial = Units.imperial(settings.units, f[Fields.SPEED_UNIT]?.numeric?.toInt() ?: f[Fields.ODOMETER_UNIT]?.numeric?.toInt())
    val su = Units.speedUnit(imperial)
    val du = Units.distanceUnit(imperial)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(CapabilityReport.carTitle(f), style = MaterialTheme.typography.headlineSmall)

        Panel(Modifier.fillMaxWidth()) {
            Column {
                Label("Speed")
                val carMs = f.num(Fields.SPEED_DISPLAY) ?: f.num(Fields.SPEED_RAW)
                BigValue(Units.speed(carMs, imperial), su)
                StatusLine(f, Fields.SPEED_DISPLAY)
                Spacer(Modifier.height(8.dp))
                val phone = f.num(Fields.PHONE_SPEED)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Mono("Phone GPS ${Units.speed(phone, imperial)} $su")
                    if (carMs != null && phone != null) {
                        val diff = if (imperial) CarDataMapper.msToMph(carMs - phone) else CarDataMapper.msToKmh(carMs - phone)
                        Mono("Δ %+.1f %s".format(diff, su))
                    }
                }
                Mono("Raw ${Units.speed(f.num(Fields.SPEED_RAW), imperial)} $su")
            }
        }

        TwoUp {
            Panel(Modifier.weight(1f)) {
                Column {
                    Label("Odometer")
                    BigValue(Units.distanceKm(f.num(Fields.ODOMETER), imperial, 0), du)
                    StatusLine(f, Fields.ODOMETER)
                }
            }
            Panel(Modifier.weight(1f)) {
                Column {
                    Label("Range")
                    BigValue(Units.distanceKm(f.num(Fields.RANGE), imperial, 0), du)
                    StatusLine(f, Fields.RANGE)
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Energy")
                EnergyBar("Fuel", f.num(Fields.FUEL), f.st(Fields.FUEL))
                EnergyBar("Battery", f.num(Fields.BATTERY), f.st(Fields.BATTERY))
                if (f.num(Fields.ENERGY_LOW) == 1.0) Text("Low fuel / energy", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        TwoUp {
            Panel(Modifier.weight(1f)) {
                Column {
                    Label("Heading")
                    val h = f.num(Fields.COMPASS)
                    BigValue(h?.let { "%.0f°".format(it) } ?: "—", h?.let { CarDataMapper.cardinal(it) })
                    StatusLine(f, Fields.COMPASS)
                }
            }
            Panel(Modifier.weight(1f)) {
                Column {
                    Label("EV port")
                    Text(f[Fields.EV_PORT_CONNECTED]?.text ?: "—", style = MaterialTheme.typography.titleMedium)
                    Mono(f[Fields.EV_PORT_OPEN]?.text ?: "")
                    StatusLine(f, Fields.EV_PORT_CONNECTED)
                }
            }
        }

        SensorPanel("Accelerometer", Fields.ACCEL, f, "m/s²")
        SensorPanel("Gyroscope", Fields.GYRO, f, "rad/s")

        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Location")
                Mono("Car    ${f[Fields.CAR_LOCATION]?.text ?: "—"}", color = MaterialTheme.colorScheme.onSurface)
                StatusLine(f, Fields.CAR_LOCATION)
                Mono("Phone  ${f[Fields.PHONE_LOCATION]?.text ?: "—"}", color = MaterialTheme.colorScheme.onSurface)
                f.num(Fields.PHONE_LOCATION)?.let { Mono("Phone accuracy ±%.0f m".format(it)) }
            }
        }
    }
}

@Composable
private fun EnergyBar(label: String, pct: Double?, st: FieldStatus) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (pct != null) Text("%.0f%%".format(pct), style = MaterialTheme.typography.bodyMedium.merge(Tabular)) else StatusPill(st)
        }
        if (pct != null) LinearProgressIndicator(progress = { (pct / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

@Composable
private fun SensorPanel(title: String, key: String, f: Map<String, FieldReading>, unit: String) {
    val series = rememberSeries(key)
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(title)
                f[key]?.let { if (it.ratePerSecond > 0) Mono("%.1f Hz".format(it.ratePerSecond)) }
            }
            if (f.st(key) == FieldStatus.AVAILABLE) {
                Sparkline(series)
                val last = series.lastOrNull()
                Mono(if (last != null) "x %+.2f   y %+.2f   z %+.2f  %s".format(last.getOrElse(0) { 0f }, last.getOrElse(1) { 0f }, last.getOrElse(2) { 0f }, unit) else "")
            } else StatusPill(f.st(key))
        }
    }
}

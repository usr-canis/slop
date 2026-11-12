package app.telltale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.telltale.model.FieldStatus
import app.telltale.model.Fields
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun RawScreen() {
    val f = rememberFields()
    val locale = LocalConfiguration.current.locales[0]
    val fmt = remember(locale) { SimpleDateFormat("HH:mm:ss.SSS", locale) }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(Fields.all, key = { it.key }) { d ->
            val r = f[d.key]
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Mono(d.key, color = MaterialTheme.colorScheme.onSurface)
                    StatusPill(r?.status ?: FieldStatus.WAITING)
                }
                Text(r?.text ?: "—", style = MaterialTheme.typography.bodyMedium.merge(Tabular))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Mono(r?.let { fmt.format(Date(it.timestampMillis)) } ?: "no update yet")
                    if (r != null && r.ratePerSecond > 0) Mono("%.1f /s".format(r.ratePerSecond))
                    r?.numeric?.let { Mono("num %.4f".format(it)) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

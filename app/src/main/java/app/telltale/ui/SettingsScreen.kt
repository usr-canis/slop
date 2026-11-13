package app.telltale.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.telltale.BuildConfig
import app.telltale.TelltaleApp
import app.telltale.data.ExportFormat
import app.telltale.model.UnitPref
import app.telltale.update.ReleaseInfo
import app.telltale.update.UpdateChecker
import app.telltale.update.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = TelltaleApp.graph.settings
    val s = rememberSettings()
    var confirmDelete by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateUi?>(null) }

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { toast(ctx, runIo { write(ctx, uri, ExportFormat.toCsv(buildExport())); "Exported CSV" }) }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { toast(ctx, runIo { write(ctx, uri, ExportFormat.toJson(buildExport())); "Exported JSON" }) }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            toast(ctx, runIo {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val file = ExportFormat.fromJson(text)
                val dao = TelltaleApp.graph.db.dao()
                file.sessions.forEach { val (se, sa) = ExportFormat.toEntities(it); dao.importSession(se, sa) }
                "Imported ${file.sessions.size} sessions"
            })
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Your car (automatic logging)")
        CarSection()

        SectionTitle("Android Auto")
        AndroidAutoHelp()

        SectionTitle("Recording")
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Record data while connected")
                        Mono("Stored only on this phone")
                    }
                    Switch(checked = s.recording, onCheckedChange = { v -> scope.launch { store.setRecording(v) } })
                }
                Label("Sample rate (per field)")
                Chips(listOf(200L to "5/s", 1000L to "1/s", 5000L to "every 5 s", 10000L to "every 10 s"), s.sampleIntervalMs) { v -> scope.launch { store.setInterval(v) } }
                Label("Keep data for")
                Chips(listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "Forever"), s.retentionDays) { v -> scope.launch { store.setRetention(v) } }
            }
        }

        SectionTitle("Units")
        Panel(Modifier.fillMaxWidth()) {
            Chips(UnitPref.entries.map { it to it.label }, s.units) { v -> scope.launch { store.setUnits(v) } }
        }

        SectionTitle("Data")
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportCsv.launch("telltale-export.csv") }) { Text("Export CSV") }
                    OutlinedButton(onClick = { exportJson.launch("telltale-export.json") }) { Text("Export JSON") }
                }
                OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "*/*")) }) { Text("Import JSON") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete all data", color = MaterialTheme.colorScheme.error) }
            }
        }

        SectionTitle("Updates")
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Installed version ${BuildConfig.VERSION_NAME}")
                Button(onClick = {
                    update = UpdateUi(checking = true)
                    scope.launch {
                        update = try {
                            val r = UpdateChecker(ctx, BuildConfig.GITHUB_REPO).latest()
                            UpdateUi(release = r, newer = Versions.isNewer(r.tag, BuildConfig.VERSION_NAME))
                        } catch (e: Exception) { UpdateUi(error = e.message ?: "Update check failed") }
                    }
                }) { Text("Check for updates") }
            }
        }

        SectionTitle("About")
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Telltale ${BuildConfig.VERSION_NAME}")
                Mono("Reads car data via Android Auto (Car App Library 1.7.0) and shows it on your phone.")
                Mono("Source: github.com/${BuildConfig.GITHUB_REPO}")
                if (!BuildConfig.RELEASE_KEY) Mono("Preview build: signed with a temporary key.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { (ctx as? app.telltale.MainActivity)?.requestPermissions() }) { Text("Re-request permissions") }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete all data?") },
        text = { Text("All recorded sessions and samples on this phone will be removed. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { runIo { TelltaleApp.graph.db.dao().deleteAll(); "" }; toast(ctx, "Deleted") } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )

    update?.let { u -> UpdateDialog(u, onChange = { update = it }, onDismiss = { update = null }) }
}

data class UpdateUi(
    val checking: Boolean = false, val release: ReleaseInfo? = null, val newer: Boolean = false,
    val error: String? = null, val progress: Float? = null,
)

@Composable
private fun UpdateDialog(u: UpdateUi, onChange: (UpdateUi) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val r = u.release
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { u.checking -> "Checking…"; u.error != null -> "Update check"; u.newer -> "Update available: ${r?.tag}"; else -> "You're up to date" }) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    u.checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    u.error != null -> Text(u.error)
                    r != null -> {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Text(r.notes.ifBlank { "No release notes." }, style = MaterialTheme.typography.bodySmall)
                        if (u.newer && r.apkUrl == null) Text("This release has no APK attached.")
                        u.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
        confirmButton = {
            if (u.newer && r?.apkUrl != null && u.progress == null) TextButton(onClick = {
                val checker = UpdateChecker(ctx, BuildConfig.GITHUB_REPO)
                if (!checker.canInstall()) { checker.openUnknownSourcesSettings(); toast(ctx, "Allow Telltale to install apps, then tap again"); return@TextButton }
                onChange(u.copy(progress = 0f))
                scope.launch {
                    try {
                        val apk = checker.download(r.apkUrl) { p -> scope.launch { onChange(u.copy(progress = p)) } }
                        onDismiss(); checker.install(apk)
                    } catch (e: Exception) { onChange(u.copy(progress = null, error = e.message ?: "Download failed")) }
                }
            }) { Text("Download & install") }
            else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = { if (u.newer && u.progress == null) TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp).horizontalScroll(rememberScrollState())) {
        options.forEach { (v, label) -> FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) }) }
    }
}

private suspend fun buildExport() = run {
    val dao = TelltaleApp.graph.db.dao()
    ExportFormat.build(dao.allSessions().map { it to dao.samples(it.id) })
}

private fun write(ctx: Context, uri: Uri, text: String) {
    ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) }
}

private suspend fun runIo(block: suspend () -> String): String = withContext(Dispatchers.IO) {
    try { block() } catch (e: Exception) { "Failed: ${e.message}" }
}

private fun toast(ctx: Context, msg: String) { if (msg.isNotEmpty()) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

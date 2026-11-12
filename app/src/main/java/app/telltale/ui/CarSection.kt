package app.telltale.ui

import android.Manifest
import android.app.Activity
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.telltale.TelltaleApp
import app.telltale.phone.CarLink
import kotlinx.coroutines.launch

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

/** Ticks every time the screen resumes, so permission/link state refreshes after visiting system settings. */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    return tick
}

/** Companion-device link flow (from Hejje): shows Android's own "allow Telltale to manage this car" dialog. */
@Composable
private fun rememberCarLinker(onResult: (Boolean) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val address = pending
        pending = null
        val ok = result.resultCode == Activity.RESULT_OK
        if (ok) CarLink.observe(context, address)
        onResult(ok && CarLink.isLinked(context, address))
    }
    return link@{ address ->
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null || !CarLink.supportsLink(context)) { onResult(false); return@link }
        pending = address
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = launch(intentSender)

            @Deprecated("Used before Android 13")
            override fun onDeviceFound(intentSender: IntentSender) { if (Build.VERSION.SDK_INT < 33) launch(intentSender) }

            private fun launch(intentSender: IntentSender) {
                runCatching { launcher.launch(IntentSenderRequest.Builder(intentSender).build()) }.onFailure { onResult(false) }
            }

            override fun onFailure(error: CharSequence?) { pending = null; onResult(false) }
        }
        @Suppress("DEPRECATION")
        runCatching { cdm.associate(CarLink.associationRequest(address), callback, null) }.onFailure { onResult(false) }
    }
}

/** "Your car": pick the car's Bluetooth, link it, and grant background location, so logging starts by itself. */
@Composable
fun CarSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = TelltaleApp.graph
    val s = rememberSettings()
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    var linkFailed by remember { mutableStateOf(false) }
    val btOk = remember(tick, refresh) { CarLink.hasPermission(ctx) }
    val devices = remember(tick, refresh, btOk) { CarLink.paired(ctx) }
    val linked = remember(tick, refresh, s.carAddress) { CarLink.isLinked(ctx, s.carAddress) }
    val bgOk = remember(tick, refresh) { Build.VERSION.SDK_INT < 29 || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    val fgOk = remember(tick, refresh) { granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val link = rememberCarLinker { ok -> linkFailed = !ok; refresh++ }

    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pick your car's Bluetooth. Telltale then starts logging by itself whenever the car connects, even without the car screen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!btOk && Build.VERSION.SDK_INT >= 31) {
                Button(onClick = { perms.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) }) { Text("Allow Bluetooth access") }
                return@Column
            }
            if (devices.isEmpty()) Text("No paired Bluetooth devices. Pair your car in the phone's Bluetooth settings first.", style = MaterialTheme.typography.bodySmall)
            devices.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch { g.settings.setCar(d.address, d.name) }
                        link(d.address)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = d.address.equals(s.carAddress, true), onClick = null)
                    Text(d.name, Modifier.padding(start = 8.dp))
                }
            }

            if (s.carAddress != null) {
                when {
                    linked -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusDot(app.telltale.model.FieldStatus.AVAILABLE)
                        Text("Linked: Android wakes Telltale when ${s.carName ?: "the car"} connects", style = MaterialTheme.typography.bodySmall)
                    }
                    CarLink.supportsLink(ctx) -> Column {
                        Text(if (linkFailed) "Linking was cancelled or failed." else "Not linked yet. Linking makes background start reliable.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { link(s.carAddress) }) { Text("Link ${s.carName ?: "car"}") }
                    }
                    else -> Mono("This phone can't link devices; using Bluetooth broadcasts instead.")
                }
                if (!fgOk) Button(onClick = { perms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Allow location") }
                else if (!bgOk) Column {
                    Text("For logging while the phone is locked, set location to \"Allow all the time\".", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { perms.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) }) { Text("Allow all the time") }
                }
                TextButton(onClick = {
                    CarLink.unlink(ctx, s.carAddress)
                    scope.launch { g.settings.setCar(null, null) }
                    g.carDisconnected(); refresh++
                }) { Text("Forget car") }
            }
        }
    }
}

/** Checklist for when Telltale doesn't appear in the Android Auto launcher. */
@Composable
fun AndroidAutoHelp() {
    val ctx = LocalContext.current
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Telltale not on the car screen?", style = MaterialTheme.typography.titleSmall)
            listOf(
                "1. Android Auto settings → tap Version 10× → OK.",
                "2. ⋮ menu → Developer settings → turn ON \"Unknown sources\".",
                "3. Force-stop Android Auto (button below → Force stop), then reconnect the car.",
                "4. Android Auto settings → Customize launcher → make sure Telltale is ticked.",
                "5. Still missing? Uninstall Telltale, enable Unknown sources first, then reinstall.",
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openAndroidAuto(ctx) }) { Text("Open Android Auto") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", AA_PACKAGE, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("App info") }
            }
        }
    }
}

private const val AA_PACKAGE = "com.google.android.projection.gearhead"

private fun openAndroidAuto(ctx: Context) {
    val pm = ctx.packageManager
    val intent = pm.getLaunchIntentForPackage(AA_PACKAGE)
        ?: Intent("com.google.android.projection.gearhead.SETTINGS").setPackage(AA_PACKAGE)
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", AA_PACKAGE, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

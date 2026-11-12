package app.telltale.car

import android.content.Intent
import android.content.pm.PackageManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.telltale.TelltaleApp
import app.telltale.data.SessionState
import app.telltale.model.FieldReading
import app.telltale.model.FieldStatus
import app.telltale.model.Fields
import app.telltale.service.CollectorService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class TelltaleCarAppService : CarAppService() {
    // Personal, sideloaded app: accept any host (Android Auto, DHU). Do not ship this to Play as-is.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = TelltaleSession()
}

class TelltaleSession : Session() {
    private var collector: CarDataCollector? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                val graph = TelltaleApp.graph
                val repo = graph.repository
                repo.setSession(SessionState.ACTIVE)
                publishHostInfo(carContext)
                collector = CarDataCollector(carContext, repo).also { it.start() }
                graph.phoneGps.acquire("car")
                CollectorService.tryStart(carContext)
                requestMissingPermissions()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                collector?.stop(); collector = null
                val graph = TelltaleApp.graph
                graph.phoneGps.release("car")
                graph.repository.setSession(SessionState.NONE)
                graph.repository.clearLiveValues()
            }
        })
    }

    private fun requestMissingPermissions() {
        val missing = CarDataCollector.ALL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(carContext, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return
        // On Android Auto the dialog appears on the PHONE screen.
        runCatching {
            carContext.requestPermissions(missing) { _, _ -> collector?.start() }
        }
    }

    private fun publishHostInfo(ctx: CarContext) {
        val repo = TelltaleApp.graph.repository
        repo.put(FieldReading(Fields.HOST_API, FieldStatus.AVAILABLE, ctx.carAppApiLevel.toString(), ctx.carAppApiLevel.toDouble()))
        val host = ctx.hostInfo
        if (host == null) {
            repo.putStatus(listOf(Fields.HOST_PACKAGE, Fields.HOST_VERSION, Fields.HOST_UID), FieldStatus.UNKNOWN)
            return
        }
        repo.put(FieldReading(Fields.HOST_PACKAGE, FieldStatus.AVAILABLE, host.packageName))
        repo.put(FieldReading(Fields.HOST_UID, FieldStatus.AVAILABLE, host.uid.toString(), host.uid.toDouble()))
        val version = runCatching { ctx.packageManager.getPackageInfo(host.packageName, 0).versionName }.getOrNull()
        repo.put(if (version != null) FieldReading(Fields.HOST_VERSION, FieldStatus.AVAILABLE, version) else FieldReading(Fields.HOST_VERSION, FieldStatus.UNKNOWN))
    }

    override fun onCreateScreen(intent: Intent): Screen = StatusScreen(carContext)
}

/** One static, glanceable screen. No interaction needed while driving. */
class StatusScreen(ctx: CarContext) : Screen(ctx) {
    private var available = 0

    init {
        val repo = TelltaleApp.graph.repository
        lifecycleScope.launch {
            // Refresh at most every 5 s and only when the count changes (keeps within template refresh limits).
            repo.fields
                .sample(5000)
                .map { m -> Fields.carFields.count { m[it.key]?.status == FieldStatus.AVAILABLE } }
                .distinctUntilChanged()
                .collect { available = it; invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val total = Fields.carFields.size
        val msg = "Connected · Collecting car data\nView on your phone\n\n$available of $total car fields available"
        @Suppress("DEPRECATION")
        return MessageTemplate.Builder(msg)
            .setTitle("Telltale")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}

package app.telltale

import android.app.Application
import androidx.car.app.connection.CarConnection
import app.telltale.data.CarDataRepository
import app.telltale.data.ConnectionState
import app.telltale.data.Recorder
import app.telltale.data.SettingsStore
import app.telltale.data.db.TelltaleDb
import app.telltale.phone.PhoneGps
import app.telltale.service.CollectorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency container. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository = CarDataRepository()
    val db = TelltaleDb.create(app)
    val settings = SettingsStore(app)
    val phoneGps = PhoneGps(app, repository)
    val recorder = Recorder(scope, repository, db.dao(), settings)
}

class TelltaleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        CollectorService.createChannel(this)
        graph.recorder.start()
        CarConnection(this).type.observeForever { type ->
            graph.repository.setConnection(
                when (type) {
                    CarConnection.CONNECTION_TYPE_PROJECTION -> ConnectionState.PROJECTION
                    CarConnection.CONNECTION_TYPE_NATIVE -> ConnectionState.NATIVE
                    CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> ConnectionState.NOT_CONNECTED
                    else -> ConnectionState.UNKNOWN
                },
            )
        }
    }

    companion object {
        lateinit var graph: AppGraph
            private set
    }
}

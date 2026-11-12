package app.telltale

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.telltale.car.CarDataCollector
import app.telltale.data.ConnectionState
import app.telltale.service.CollectorService
import app.telltale.ui.AppRoot
import app.telltale.ui.TelltaleTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        TelltaleApp.graph.phoneGps.update()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TelltaleTheme { AppRoot() } }
        if (savedInstanceState == null) requestPermissions()
        // While the app is visible we may start the foreground service; it then survives screen lock.
        lifecycleScope.launch {
            TelltaleApp.graph.repository.connection.collect {
                if (it == ConnectionState.PROJECTION) CollectorService.tryStart(this@MainActivity)
            }
        }
    }

    fun requestPermissions() {
        val list = buildList {
            addAll(CarDataCollector.ALL_PERMISSIONS)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(list.toTypedArray())
    }

    override fun onStart() { super.onStart(); TelltaleApp.graph.phoneGps.acquire("ui") }
    override fun onStop() { TelltaleApp.graph.phoneGps.release("ui"); super.onStop() }
}

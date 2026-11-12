package app.telltale.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telltale.TelltaleApp
import app.telltale.data.ConnectionState
import app.telltale.data.SessionState
import app.telltale.model.FieldStatus

private data class Tab(val title: String, val icon: ImageVector)

@Suppress("DEPRECATION")
private val tabs = listOf(
    Tab("Live", Icons.Filled.Home),
    Tab("Report", Icons.Filled.CheckCircle),
    Tab("Raw", Icons.Filled.List),
    Tab("History", Icons.Filled.DateRange),
    Tab("Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = { ConnectionBanner() },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.title) })
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> LiveScreen()
                1 -> ReportScreen()
                2 -> RawScreen()
                3 -> HistoryScreen()
                else -> SettingsScreen()
            }
        }
    }
}

@Composable
fun ConnectionBanner() {
    val repo = TelltaleApp.graph.repository
    val conn by repo.connection.collectAsStateWithLifecycle()
    val session by repo.session.collectAsStateWithLifecycle()
    val nearby by repo.carNearby.collectAsStateWithLifecycle()
    val (status, title, hint) = when {
        session == SessionState.ACTIVE -> Triple(FieldStatus.AVAILABLE, "Collecting car data", conn.label)
        nearby && conn != ConnectionState.PROJECTION ->
            Triple(FieldStatus.AVAILABLE, "Car connected · logging phone data", "Bluetooth link. Car-only data (fuel, odometer) needs Telltale on the car screen")
        conn == ConnectionState.PROJECTION || conn == ConnectionState.NATIVE ->
            Triple(FieldStatus.UNAVAILABLE, "Connected to Android Auto", "Open Telltale on your car screen to start reading data")
        conn == ConnectionState.UNKNOWN -> Triple(FieldStatus.WAITING, "Checking connection…", "")
        else -> Triple(FieldStatus.UNKNOWN, "Not connected", "Pick your car in Settings to log automatically, or connect Android Auto")
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(status, 12)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

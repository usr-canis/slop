package app.telltale.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.telltale.MainActivity
import app.telltale.R
import app.telltale.data.CarDataRepository
import app.telltale.data.ConnectionState
import app.telltale.data.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Pure rule (unit-tested): remind when Android Auto is connected but the car-screen session isn't running. */
fun needsCarScreenReminder(c: ConnectionState, s: SessionState) = c == ConnectionState.PROJECTION && s == SessionState.NONE

/**
 * Shows "Open Telltale on your car screen" if Android Auto stays connected for a while
 * without the car session starting. Removed as soon as the session starts or AA disconnects.
 * Only works while the app process is alive (Android doesn't wake sideloaded apps on connect).
 */
class ConnectReminder(private val context: Context, private val repo: CarDataRepository, private val scope: CoroutineScope) {
    fun start() {
        scope.launch {
            combine(repo.connection, repo.session, ::needsCarScreenReminder)
                .distinctUntilChanged()
                .collectLatest { needed ->
                    if (needed) { delay(DELAY_MS); show() } else cancel()
                }
        }
    }

    private fun show() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Open Telltale on your car screen")
            .setContentText("Android Auto is connected. Open Telltale in the car's app launcher to log car data.")
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }

    private fun cancel() = NotificationManagerCompat.from(context).cancel(ID)

    companion object {
        const val CHANNEL = "reminder"
        private const val ID = 2
        private const val DELAY_MS = 20_000L
    }
}

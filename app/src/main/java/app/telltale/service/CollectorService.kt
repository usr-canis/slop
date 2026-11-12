package app.telltale.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.telltale.MainActivity
import app.telltale.R
import app.telltale.TelltaleApp
import app.telltale.data.ConnectionState
import app.telltale.data.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process (and phone GPS) alive while the phone is locked.
 * Stops itself ~30 s after Android Auto disconnects and the car session ends.
 */
class CollectorService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Telltale is collecting car data")
            .setContentText("Open the app to see live values")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        val ok = runCatching { ServiceCompat.startForeground(this, 1, notification, type) }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        TelltaleApp.graph.phoneGps.acquire("service")
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val repo = TelltaleApp.graph.repository
        lifecycleScope.launch {
            combine(repo.connection, repo.session) { c, s -> c != ConnectionState.PROJECTION && s == SessionState.NONE }
                .collectLatest { idle ->
                    if (idle) { delay(30_000); stopSelf() }
                }
        }
    }

    override fun onDestroy() {
        TelltaleApp.graph.phoneGps.release("service")
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "collector"

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Car data collection", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(ConnectReminder.CHANNEL, "Open-on-car-screen reminder", NotificationManager.IMPORTANCE_DEFAULT))
        }

        /** Background starts may be refused by Android; the car session keeps working without it. */
        fun tryStart(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, CollectorService::class.java)) }
        }
    }
}

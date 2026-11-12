package app.telltale.phone

import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.IntentCompat
import app.telltale.TelltaleApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Runs [block] with the saved car address, if the user picked a car. */
private fun withCar(block: (String) -> Unit) {
    val g = TelltaleApp.graph
    g.scope.launch { g.settings.settings.first().carAddress?.let(block) }
}

/**
 * Bluetooth connect/disconnect broadcasts. They reach the app even when it isn't running
 * (they're on Android's list of exempted broadcasts). Fallback trigger when no companion link exists.
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val address = CarLink.deviceAddress(device) ?: return
        val pending = goAsync()
        val g = TelltaleApp.graph
        g.scope.launch {
            try {
                val car = g.settings.settings.first().carAddress
                if (car.equals(address, ignoreCase = true)) when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> g.carConnected()
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> g.carDisconnected()
                }
            } finally { pending.finish() }
        }
    }
}

/**
 * Companion-device presence: Android binds this service when the linked car connects. That wakes the
 * app and permits starting the foreground (logging) service from the background.
 */
@RequiresApi(31)
class CarPresenceService : CompanionDeviceService() {
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (Build.VERSION.SDK_INT < 36) return
        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED -> TelltaleApp.graph.carConnected()
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> TelltaleApp.graph.carDisconnected()
        }
    }

    @Deprecated("Replaced by onDevicePresenceEvent on Android 16")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) = appeared()

    @Deprecated("Replaced by onDevicePresenceEvent on Android 16")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) = disappeared()

    @Deprecated("Replaced on Android 13")
    override fun onDeviceAppeared(address: String) { if (Build.VERSION.SDK_INT < 33) appeared() }

    @Deprecated("Replaced on Android 13")
    override fun onDeviceDisappeared(address: String) { if (Build.VERSION.SDK_INT < 33) disappeared() }

    // Android 12-15: "appeared" can also mean "in Bluetooth LE range", so confirm the real connection.
    private fun appeared() {
        if (Build.VERSION.SDK_INT >= 36) return
        withCar { a -> CarLink.isConnected(this, a) { if (it) TelltaleApp.graph.carConnected() } }
    }

    private fun disappeared() {
        if (Build.VERSION.SDK_INT >= 36) return
        withCar { a -> CarLink.isConnected(this, a) { if (!it) TelltaleApp.graph.carDisconnected() } }
    }
}

/** After a reboot or app update: re-arm the companion presence observation. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        withCar { CarLink.observe(context, it) }
    }
}

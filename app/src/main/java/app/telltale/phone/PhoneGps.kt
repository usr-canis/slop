package app.telltale.phone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import app.telltale.data.CarDataRepository
import app.telltale.model.FieldReading
import app.telltale.model.FieldStatus
import app.telltale.model.Fields

/** Phone GPS speed/location, for comparing against the car. Reference-counted by users ("car", "ui"). */
class PhoneGps(private val context: Context, private val repo: CarDataRepository) {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val users = mutableSetOf<String>()
    private var running = false

    private val listener = LocationListener { loc: Location ->
        val t = System.currentTimeMillis()
        if (loc.hasSpeed()) repo.put(FieldReading(Fields.PHONE_SPEED, FieldStatus.AVAILABLE, "%.2f m/s".format(loc.speed), loc.speed.toDouble(), timestampMillis = t))
        else repo.put(FieldReading(Fields.PHONE_SPEED, FieldStatus.UNAVAILABLE, timestampMillis = t))
        repo.put(FieldReading(Fields.PHONE_LOCATION, FieldStatus.AVAILABLE, "%.5f, %.5f".format(loc.latitude, loc.longitude),
            loc.accuracy.toDouble(), listOf(loc.latitude.toFloat(), loc.longitude.toFloat()), t))
        if (loc.hasBearing() && loc.hasSpeed() && loc.speed > 1f) // bearing is noise when standing still
            repo.put(FieldReading(Fields.PHONE_HEADING, FieldStatus.AVAILABLE, "%.0f°".format(loc.bearing), loc.bearing.toDouble(), timestampMillis = t))
    }

    @Synchronized fun acquire(user: String) { users += user; update() }
    @Synchronized fun release(user: String) { users -= user; update() }

    @SuppressLint("MissingPermission")
    @Synchronized fun update() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val want = users.isNotEmpty()
        if (want && !granted) {
            repo.putStatus(listOf(Fields.PHONE_SPEED, Fields.PHONE_LOCATION), FieldStatus.NO_PERMISSION); return
        }
        if (want && !running) {
            val ok = runCatching {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper())
            }.isSuccess
            running = ok
            if (!ok) repo.putStatus(listOf(Fields.PHONE_SPEED, Fields.PHONE_LOCATION), FieldStatus.UNAVAILABLE)
            else if (repo.fields.value[Fields.PHONE_SPEED] == null) repo.putStatus(listOf(Fields.PHONE_SPEED, Fields.PHONE_LOCATION), FieldStatus.WAITING)
        } else if (!want && running) {
            lm.removeUpdates(listener); running = false
        }
    }
}

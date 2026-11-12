package app.telltale.car

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Accelerometer
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Gyroscope
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Speed
import androidx.car.app.hardware.info.TollCard
import androidx.core.content.ContextCompat
import app.telltale.data.CarDataRepository
import app.telltale.model.CarDataMapper as M
import app.telltale.model.FieldReading
import app.telltale.model.FieldStatus
import app.telltale.model.Fields as F
import kotlin.math.sqrt

/**
 * Registers every CarInfo / CarSensors listener exposed by the Car App Library
 * and pushes readings into the shared repository.
 * Permissions (Android Auto): CAR_FUEL for energy profile/level, CAR_SPEED for speed,
 * CAR_MILEAGE for odometer, ACCESS_FINE_LOCATION for car location; model & toll need none.
 */
class CarDataCollector(private val ctx: CarContext, private val repo: CarDataRepository) {
    private val exec = ContextCompat.getMainExecutor(ctx)
    private var info: CarInfo? = null
    private var sensors: CarSensors? = null
    private val removers = mutableListOf<() -> Unit>()

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun now() = System.currentTimeMillis()
    private fun ts(v: CarValue<*>) = if (v.timestampMillis > 1_000_000_000_000L) v.timestampMillis else if (v.timestampMillis > 0) {
        // CarValue timestamps are elapsedRealtime-based; convert to wall clock for display.
        now() - (android.os.SystemClock.elapsedRealtime() - v.timestampMillis).coerceAtLeast(0)
    } else now()

    private fun <T> put(key: String, v: CarValue<T>, text: (T) -> String, numeric: (T) -> Double? = { null }, values: (T) -> List<Float>? = { null }) {
        val value = v.value
        val st = M.statusOf(v.status, value)
        repo.put(
            if (st == FieldStatus.AVAILABLE && value != null)
                FieldReading(key, st, text(value), numeric(value), values(value), ts(v))
            else FieldReading(key, st, timestampMillis = ts(v)),
        )
    }

    /** Runs a registration; maps SecurityException to NO_PERMISSION and other failures to UNSUPPORTED. */
    private fun guard(keys: List<String>, permission: String?, block: () -> Unit) {
        if (permission != null && !granted(permission)) { repo.putStatus(keys, FieldStatus.NO_PERMISSION); return }
        repo.putStatus(keys, FieldStatus.WAITING)
        try { block() } catch (_: SecurityException) {
            repo.putStatus(keys, FieldStatus.NO_PERMISSION)
        } catch (_: Exception) {
            repo.putStatus(keys, FieldStatus.UNSUPPORTED)
        }
    }

    fun start() {
        stop()
        val api = ctx.carAppApiLevel
        if (api < 3) { repo.putStatus(F.carFields.map { it.key }, FieldStatus.UNSUPPORTED); return }
        val hw = try { ctx.getCarService(CarHardwareManager::class.java) } catch (_: Exception) {
            repo.putStatus(F.carFields.map { it.key }, FieldStatus.UNSUPPORTED); return
        }
        val i = hw.carInfo.also { info = it }
        val s = hw.carSensors.also { sensors = it }
        startInfo(i, api)
        startSensors(s)
    }

    @OptIn(markerClass = [ExperimentalCarApi::class]) // fuel volume display unit is experimental
    private fun startInfo(i: CarInfo, api: Int) {
        guard(listOf(F.MAKE, F.MODEL_NAME, F.YEAR), null) {
            i.fetchModel(exec) { m ->
                put(F.MAKE, m.manufacturer, { it })
                put(F.MODEL_NAME, m.name, { it })
                put(F.YEAR, m.year, { it.toString() }, { it.toDouble() })
            }
        }
        guard(listOf(F.FUEL_TYPES, F.EV_CONNECTORS), PERM_FUEL) {
            i.fetchEnergyProfile(exec) { p ->
                put(F.FUEL_TYPES, p.fuelTypes, { l -> l.joinToString { M.fuelType(it) }.ifEmpty { "None" } })
                put(F.EV_CONNECTORS, p.evConnectorTypes, { l -> l.joinToString { M.evConnector(it) }.ifEmpty { "None" } })
            }
        }
        if (api >= 7) guard(listOf(F.EXTERIOR), null) {
            i.fetchExteriorDimensions(exec) { d ->
                put(F.EXTERIOR, d.exteriorDimensions, { arr -> M.exteriorDimensions(arr.toList()) })
            }
        } else repo.putStatus(listOf(F.EXTERIOR), FieldStatus.UNSUPPORTED)

        guard(listOf(F.TOLL_STATE), null) {
            val l = OnCarDataAvailableListener<TollCard> { t -> put(F.TOLL_STATE, t.cardState, { M.tollState(it) }, { it.toDouble() }) }
            i.addTollListener(exec, l); removers += { i.removeTollListener(l) }
        }
        guard(listOf(F.BATTERY, F.FUEL, F.ENERGY_LOW, F.RANGE, F.DISTANCE_UNIT, F.VOLUME_UNIT), PERM_FUEL) {
            val l = OnCarDataAvailableListener<EnergyLevel> { e ->
                put(F.BATTERY, e.batteryPercent, { M.percent(it) }, { it.toDouble() })
                put(F.FUEL, e.fuelPercent, { M.percent(it) }, { it.toDouble() })
                put(F.ENERGY_LOW, e.energyIsLow, { if (it) "Yes" else "No" }, { if (it) 1.0 else 0.0 })
                put(F.RANGE, e.rangeRemainingMeters, { "%.1f km".format(it / 1000f) }, { it / 1000.0 })
                put(F.DISTANCE_UNIT, e.distanceDisplayUnit, { M.unit(it) }, { it.toDouble() })
                put(F.VOLUME_UNIT, e.fuelVolumeDisplayUnit, { M.unit(it) }, { it.toDouble() })
            }
            i.addEnergyLevelListener(exec, l); removers += { i.removeEnergyLevelListener(l) }
        }
        guard(listOf(F.SPEED_RAW, F.SPEED_DISPLAY, F.SPEED_UNIT), PERM_SPEED) {
            val l = OnCarDataAvailableListener<Speed> { sp ->
                put(F.SPEED_RAW, sp.rawSpeedMetersPerSecond, { "%.2f m/s".format(it) }, { it.toDouble() })
                put(F.SPEED_DISPLAY, sp.displaySpeedMetersPerSecond, { "%.2f m/s".format(it) }, { it.toDouble() })
                put(F.SPEED_UNIT, sp.speedDisplayUnit, { M.unit(it) }, { it.toDouble() })
            }
            i.addSpeedListener(exec, l); removers += { i.removeSpeedListener(l) }
        }
        guard(listOf(F.ODOMETER, F.ODOMETER_UNIT), PERM_MILEAGE) {
            val l = OnCarDataAvailableListener<Mileage> { m ->
                // Docs: getOdometerMeters() is misnamed and actually returns KILOMETERS.
                put(F.ODOMETER, m.odometerMeters, { "%.1f km".format(it) }, { it.toDouble() })
                put(F.ODOMETER_UNIT, m.distanceDisplayUnit, { M.unit(it) }, { it.toDouble() })
            }
            i.addMileageListener(exec, l); removers += { i.removeMileageListener(l) }
        }
        startEv(i)
    }

    @OptIn(markerClass = [ExperimentalCarApi::class])
    private fun startEv(i: CarInfo) {
        guard(listOf(F.EV_PORT_OPEN, F.EV_PORT_CONNECTED), PERM_FUEL) {
            val l = OnCarDataAvailableListener<EvStatus> { e ->
                put(F.EV_PORT_OPEN, e.evChargePortOpen, { if (it) "Open" else "Closed" }, { if (it) 1.0 else 0.0 })
                put(F.EV_PORT_CONNECTED, e.evChargePortConnected, { if (it) "Connected" else "Not connected" }, { if (it) 1.0 else 0.0 })
            }
            i.addEvStatusListener(exec, l); removers += { i.removeEvStatusListener(l) }
        }
    }

    private fun xyz(l: List<Float>) = l.joinToString(" · ") { "%.2f".format(it) }
    private fun mag(l: List<Float>) = sqrt(l.sumOf { (it * it).toDouble() })

    private fun startSensors(s: CarSensors) {
        val rate = CarSensors.UPDATE_RATE_FASTEST
        guard(listOf(F.COMPASS), null) {
            val l = OnCarDataAvailableListener<Compass> { c ->
                put(F.COMPASS, c.orientations, { o -> o.firstOrNull()?.let { "%.0f° %s".format(it, M.cardinal(it.toDouble())) } ?: "—" },
                    { it.firstOrNull()?.toDouble() }, { it })
            }
            s.addCompassListener(rate, exec, l); removers += { s.removeCompassListener(l) }
        }
        guard(listOf(F.ACCEL), null) {
            val l = OnCarDataAvailableListener<Accelerometer> { a ->
                put(F.ACCEL, a.forces, { xyz(it) + " m/s²" }, { mag(it) }, { it })
            }
            s.addAccelerometerListener(rate, exec, l); removers += { s.removeAccelerometerListener(l) }
        }
        guard(listOf(F.GYRO), null) {
            val l = OnCarDataAvailableListener<Gyroscope> { g ->
                put(F.GYRO, g.rotations, { xyz(it) + " rad/s" }, { mag(it) }, { it })
            }
            s.addGyroscopeListener(rate, exec, l); removers += { s.removeGyroscopeListener(l) }
        }
        guard(listOf(F.CAR_LOCATION), Manifest.permission.ACCESS_FINE_LOCATION) {
            val l = OnCarDataAvailableListener<CarHardwareLocation> { h ->
                put(F.CAR_LOCATION, h.location, { "%.5f, %.5f".format(it.latitude, it.longitude) },
                    { if (it.hasSpeed()) it.speed.toDouble() else null },
                    { listOf(it.latitude.toFloat(), it.longitude.toFloat()) })
            }
            s.addCarHardwareLocationListener(rate, exec, l); removers += { s.removeCarHardwareLocationListener(l) }
        }
    }

    fun stop() {
        removers.forEach { runCatching(it) }
        removers.clear()
    }

    companion object {
        const val PERM_FUEL = "com.google.android.gms.permission.CAR_FUEL"
        const val PERM_SPEED = "com.google.android.gms.permission.CAR_SPEED"
        const val PERM_MILEAGE = "com.google.android.gms.permission.CAR_MILEAGE"
        val ALL_PERMISSIONS = listOf(PERM_FUEL, PERM_SPEED, PERM_MILEAGE, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

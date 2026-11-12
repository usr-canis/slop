package app.telltale.model

import kotlin.math.roundToInt

/**
 * Pure mapping helpers: Car App Library integer codes -> readable values.
 * Constants mirror androidx.car.app.hardware.* (checked against car-app 1.7.0 sources)
 * and are duplicated here so they can be unit-tested on the JVM without Android.
 */
object CarDataMapper {
    // CarValue.STATUS_*
    const val STATUS_UNKNOWN = 0
    const val STATUS_SUCCESS = 1
    const val STATUS_UNIMPLEMENTED = 2
    const val STATUS_UNAVAILABLE = 3

    fun status(carValueStatus: Int): FieldStatus = when (carValueStatus) {
        STATUS_SUCCESS -> FieldStatus.AVAILABLE
        STATUS_UNIMPLEMENTED -> FieldStatus.UNIMPLEMENTED
        STATUS_UNAVAILABLE -> FieldStatus.UNAVAILABLE
        else -> FieldStatus.UNKNOWN
    }

    /** A value only counts as AVAILABLE if the status is success AND there is a value. */
    fun statusOf(carValueStatus: Int, value: Any?): FieldStatus {
        val s = status(carValueStatus)
        return if (s == FieldStatus.AVAILABLE && value == null) FieldStatus.UNKNOWN else s
    }

    fun fuelType(code: Int): String = when (code) {
        0 -> "Unknown"; 1 -> "Unleaded"; 2 -> "Leaded"; 3 -> "Diesel 1"; 4 -> "Diesel 2"
        5 -> "Biodiesel"; 6 -> "E85"; 7 -> "LPG"; 8 -> "CNG"; 9 -> "LNG"
        10 -> "Electric"; 11 -> "Hydrogen"; 12 -> "Other"
        else -> "Code $code"
    }

    fun evConnector(code: Int): String = when (code) {
        0 -> "Unknown"; 1 -> "J1772"; 2 -> "Mennekes (Type 2)"; 3 -> "CHAdeMO"; 4 -> "CCS1"
        5 -> "CCS2"; 6 -> "Tesla Roadster"; 7 -> "Tesla HPWC"; 8 -> "Tesla Supercharger"
        9 -> "GB/T"; 10 -> "GB/T DC"; 11 -> "Scame"; 101 -> "Other"
        else -> "Code $code"
    }

    fun tollState(code: Int): String = when (code) {
        0 -> "Unknown"; 1 -> "Valid"; 2 -> "Invalid"; 3 -> "Not inserted"
        else -> "Code $code"
    }

    /** CarUnit constants. */
    fun unit(code: Int): String = when (code) {
        1 -> "mm"; 2 -> "m"; 3 -> "km"; 4 -> "mi"
        101 -> "m/s"; 102 -> "km/h"; 103 -> "mph"
        201 -> "mL"; 202 -> "L"; 203 -> "US gal"; 204 -> "imp gal"
        else -> "unit $code"
    }

    const val UNIT_MILE = 4
    const val UNIT_MPH = 103

    fun msToKmh(ms: Double) = ms * 3.6
    fun msToMph(ms: Double) = ms * 2.2369362920544
    fun kmToMi(km: Double) = km / 1.609344

    /** Compass bearing in degrees -> N, NE, E ... */
    fun cardinal(degrees: Double): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val d = ((degrees % 360) + 360) % 360
        return names[((d + 22.5) / 45).toInt() % 8]
    }

    fun exteriorDimensions(mm: List<Int?>): String {
        val labels = listOf("height", "length", "width", "width w/ mirrors", "wheelbase", "track front", "track rear", "turning radius")
        return mm.mapIndexedNotNull { i, v -> if (v == null) null else "${labels.getOrElse(i) { "#$i" }} ${v} mm" }
            .joinToString(", ").ifEmpty { "—" }
    }

    fun percent(v: Float) = "${(v * 10).roundToInt() / 10.0}%"
}

/** Which units the phone UI should use. */
enum class UnitPref(val label: String) { CAR("Follow car"), METRIC("Metric"), IMPERIAL("Imperial") }

object Units {
    /** Decide whether to show imperial, given the user preference and the car's own display unit. */
    fun imperial(pref: UnitPref, carUnitCode: Int?): Boolean = when (pref) {
        UnitPref.METRIC -> false
        UnitPref.IMPERIAL -> true
        UnitPref.CAR -> carUnitCode == CarDataMapper.UNIT_MILE || carUnitCode == CarDataMapper.UNIT_MPH
    }

    fun speed(ms: Double?, imperial: Boolean): String =
        if (ms == null) "—" else if (imperial) "%.0f".format(CarDataMapper.msToMph(ms)) else "%.0f".format(CarDataMapper.msToKmh(ms))

    fun speedUnit(imperial: Boolean) = if (imperial) "mph" else "km/h"

    fun distanceKm(km: Double?, imperial: Boolean, decimals: Int = 1): String =
        if (km == null) "—" else "%.${decimals}f".format(if (imperial) CarDataMapper.kmToMi(km) else km)

    fun distanceUnit(imperial: Boolean) = if (imperial) "mi" else "km"
}

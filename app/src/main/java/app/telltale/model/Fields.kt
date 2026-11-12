package app.telltale.model

/** What we know about a single data field. Shown colour-coded in the capability report. */
enum class FieldStatus(val label: String) {
    AVAILABLE("Available"),
    UNAVAILABLE("Unavailable"),
    UNIMPLEMENTED("Unimplemented"),
    UNKNOWN("Unknown"),
    NO_PERMISSION("No permission"),
    UNSUPPORTED("Not supported by host"),
    WAITING("Waiting for data"),
}

data class FieldDef(val key: String, val group: String, val label: String)

/** One reading of one field, as shown in the Raw view. */
data class FieldReading(
    val key: String,
    val status: FieldStatus,
    val text: String? = null,
    val numeric: Double? = null,
    /** Extra numbers, e.g. x/y/z for motion sensors or lat/lon for location. */
    val values: List<Float>? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val ratePerSecond: Double = 0.0,
)

object Groups {
    const val MODEL = "Model"
    const val PROFILE = "Energy profile"
    const val DIMENSIONS = "Exterior dimensions"
    const val TOLL = "Toll card"
    const val ENERGY = "Energy level"
    const val SPEED = "Speed"
    const val MILEAGE = "Mileage"
    const val EV = "EV status"
    const val SENSORS = "Car sensors"
    const val HOST = "Host & session"
    const val PHONE = "Phone"
}

/** Registry of every field the app tries to read. Keys are stable (used in exports). */
object Fields {
    const val MAKE = "model.make"
    const val MODEL_NAME = "model.name"
    const val YEAR = "model.year"
    const val FUEL_TYPES = "profile.fuelTypes"
    const val EV_CONNECTORS = "profile.evConnectors"
    const val EXTERIOR = "dims.exterior"
    const val TOLL_STATE = "toll.cardState"
    const val BATTERY = "energy.batteryPercent"
    const val FUEL = "energy.fuelPercent"
    const val ENERGY_LOW = "energy.isLow"
    const val RANGE = "energy.rangeRemaining"
    const val DISTANCE_UNIT = "energy.distanceDisplayUnit"
    const val VOLUME_UNIT = "energy.fuelVolumeDisplayUnit"
    const val SPEED_RAW = "speed.raw"
    const val SPEED_DISPLAY = "speed.display"
    const val SPEED_UNIT = "speed.displayUnit"
    const val ODOMETER = "mileage.odometer"
    const val ODOMETER_UNIT = "mileage.displayUnit"
    const val EV_PORT_OPEN = "ev.chargePortOpen"
    const val EV_PORT_CONNECTED = "ev.chargePortConnected"
    const val COMPASS = "sensor.compass"
    const val ACCEL = "sensor.accelerometer"
    const val GYRO = "sensor.gyroscope"
    const val CAR_LOCATION = "sensor.carLocation"
    const val HOST_API = "host.carApiLevel"
    const val HOST_PACKAGE = "host.package"
    const val HOST_VERSION = "host.version"
    const val HOST_UID = "host.uid"
    const val CONNECTION = "host.connectionType"
    const val SESSION = "host.sessionState"
    const val PHONE_SPEED = "phone.gpsSpeed"
    const val PHONE_LOCATION = "phone.location"

    val all: List<FieldDef> = listOf(
        FieldDef(MAKE, Groups.MODEL, "Make"),
        FieldDef(MODEL_NAME, Groups.MODEL, "Model name"),
        FieldDef(YEAR, Groups.MODEL, "Year"),
        FieldDef(FUEL_TYPES, Groups.PROFILE, "Fuel types"),
        FieldDef(EV_CONNECTORS, Groups.PROFILE, "EV connector types"),
        FieldDef(EXTERIOR, Groups.DIMENSIONS, "Exterior dimensions"),
        FieldDef(TOLL_STATE, Groups.TOLL, "Toll card state"),
        FieldDef(BATTERY, Groups.ENERGY, "Battery level"),
        FieldDef(FUEL, Groups.ENERGY, "Fuel level"),
        FieldDef(ENERGY_LOW, Groups.ENERGY, "Fuel / energy low"),
        FieldDef(RANGE, Groups.ENERGY, "Range remaining"),
        FieldDef(DISTANCE_UNIT, Groups.ENERGY, "Distance display unit"),
        FieldDef(VOLUME_UNIT, Groups.ENERGY, "Fuel volume display unit"),
        FieldDef(SPEED_RAW, Groups.SPEED, "Raw speed"),
        FieldDef(SPEED_DISPLAY, Groups.SPEED, "Display speed"),
        FieldDef(SPEED_UNIT, Groups.SPEED, "Speed display unit"),
        FieldDef(ODOMETER, Groups.MILEAGE, "Odometer"),
        FieldDef(ODOMETER_UNIT, Groups.MILEAGE, "Odometer display unit"),
        FieldDef(EV_PORT_OPEN, Groups.EV, "Charge port open"),
        FieldDef(EV_PORT_CONNECTED, Groups.EV, "Charge port connected"),
        FieldDef(COMPASS, Groups.SENSORS, "Compass"),
        FieldDef(ACCEL, Groups.SENSORS, "Accelerometer"),
        FieldDef(GYRO, Groups.SENSORS, "Gyroscope"),
        FieldDef(CAR_LOCATION, Groups.SENSORS, "Car hardware location"),
        FieldDef(HOST_API, Groups.HOST, "Car App API level"),
        FieldDef(HOST_PACKAGE, Groups.HOST, "Host package"),
        FieldDef(HOST_VERSION, Groups.HOST, "Host version"),
        FieldDef(HOST_UID, Groups.HOST, "Host UID"),
        FieldDef(CONNECTION, Groups.HOST, "Car connection type"),
        FieldDef(SESSION, Groups.HOST, "Car session"),
        FieldDef(PHONE_SPEED, Groups.PHONE, "Phone GPS speed"),
        FieldDef(PHONE_LOCATION, Groups.PHONE, "Phone GPS location"),
    )

    private val byKey = all.associateBy { it.key }
    fun def(key: String): FieldDef? = byKey[key]

    /** Fields that come from the car itself (used for the "x of y available" summary). */
    val carFields: List<FieldDef> = all.filter { it.group != Groups.HOST && it.group != Groups.PHONE }
}

package app.telltale.data

import app.telltale.model.FieldReading
import app.telltale.model.FieldStatus
import app.telltale.model.Fields
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Android Auto connection as reported by the CarConnection API on the phone. */
enum class ConnectionState(val label: String) {
    UNKNOWN("Checking…"),
    NOT_CONNECTED("Not connected"),
    PROJECTION("Android Auto (projection)"),
    NATIVE("Android Automotive (native)"),
}

enum class SessionState { NONE, ACTIVE }

/**
 * Single in-memory source of truth shared by the car session (writer)
 * and the phone UI / recorder (readers). Lives as long as the app process.
 */
class CarDataRepository {
    private val _fields = MutableStateFlow<Map<String, FieldReading>>(emptyMap())
    val fields: StateFlow<Map<String, FieldReading>> = _fields.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.UNKNOWN)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _session = MutableStateFlow(SessionState.NONE)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _updates = MutableSharedFlow<FieldReading>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Every single update, for the recorder. */
    val updates: SharedFlow<FieldReading> = _updates.asSharedFlow()

    private val series = HashMap<String, ArrayDeque<FloatArray>>()
    private val lastTs = HashMap<String, Long>()
    private val rates = HashMap<String, Double>()

    fun put(reading: FieldReading) {
        val now = reading.timestampMillis
        val rate = synchronized(this) {
            val prev = lastTs[reading.key]
            lastTs[reading.key] = now
            val r = if (prev != null && now > prev) {
                val inst = 1000.0 / (now - prev)
                val old = rates[reading.key]?.takeIf { it > 0 } ?: inst
                old * 0.8 + inst * 0.2 // smoothed updates per second
            } else rates[reading.key] ?: 0.0
            rates[reading.key] = r
            val v = reading.values
            if (v != null && reading.status == FieldStatus.AVAILABLE) {
                val q = series.getOrPut(reading.key) { ArrayDeque() }
                q.addLast(v.toFloatArray())
                while (q.size > SERIES_LENGTH) q.removeFirst()
            }
            r
        }
        val r = reading.copy(ratePerSecond = rate)
        _fields.update { it + (r.key to r) }
        _updates.tryEmit(r)
    }

    fun putStatus(keys: Collection<String>, status: FieldStatus) =
        keys.forEach { put(FieldReading(it, status)) }

    fun setConnection(state: ConnectionState) {
        _connection.value = state
        put(FieldReading(Fields.CONNECTION, FieldStatus.AVAILABLE, text = state.label))
    }

    fun setSession(state: SessionState) {
        _session.value = state
        put(FieldReading(Fields.SESSION, FieldStatus.AVAILABLE, text = if (state == SessionState.ACTIVE) "Active" else "Not running"))
    }

    /** Clears car-provided values when the session ends (keeps model info for display). */
    fun clearLiveValues() {
        val keep = setOf(Fields.MAKE, Fields.MODEL_NAME, Fields.YEAR, Fields.CONNECTION, Fields.SESSION)
        _fields.update { m -> m.filterKeys { it in keep } }
        synchronized(this) { series.clear(); lastTs.clear(); rates.clear() }
    }

    fun series(key: String): List<FloatArray> = synchronized(this) { series[key]?.toList() ?: emptyList() }

    companion object { const val SERIES_LENGTH = 150 }
}

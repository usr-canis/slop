package app.telltale.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.telltale.model.UnitPref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    /** On by default: records automatically whenever Telltale runs on the car screen. */
    val recording: Boolean = true,
    val sampleIntervalMs: Long = 1000,
    val units: UnitPref = UnitPref.CAR,
    /** 0 = keep forever */
    val retentionDays: Int = 30,
    /** Bluetooth address/name of the user's car, for automatic phone-side logging. */
    val carAddress: String? = null,
    val carName: String? = null,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val kRecording = booleanPreferencesKey("recording")
    private val kInterval = longPreferencesKey("sample_interval_ms")
    private val kUnits = stringPreferencesKey("units")
    private val kRetention = intPreferencesKey("retention_days")
    private val kCarAddress = stringPreferencesKey("car_address")
    private val kCarName = stringPreferencesKey("car_name")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            recording = p[kRecording] ?: true,
            sampleIntervalMs = p[kInterval] ?: 1000,
            units = p[kUnits]?.let { runCatching { UnitPref.valueOf(it) }.getOrNull() } ?: UnitPref.CAR,
            retentionDays = p[kRetention] ?: 30,
            carAddress = p[kCarAddress],
            carName = p[kCarName],
        )
    }

    suspend fun setRecording(v: Boolean) = context.dataStore.edit { it[kRecording] = v }
    suspend fun setInterval(v: Long) = context.dataStore.edit { it[kInterval] = v }
    suspend fun setUnits(v: UnitPref) = context.dataStore.edit { it[kUnits] = v.name }
    suspend fun setRetention(v: Int) = context.dataStore.edit { it[kRetention] = v }
    suspend fun setCar(address: String?, name: String?) = context.dataStore.edit {
        if (address == null) { it.remove(kCarAddress); it.remove(kCarName) } else { it[kCarAddress] = address; it[kCarName] = name ?: address }
    }
}

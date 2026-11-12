package app.telltale.data

import app.telltale.data.db.SampleEntity
import app.telltale.data.db.SessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class ExportSample(val t: Long, val key: String, val value: Double? = null, val text: String? = null, val status: String)

@Serializable
data class ExportSession(
    val startedAt: Long, val endedAt: Long? = null, val make: String? = null, val model: String? = null,
    val year: Int? = null, val connectionType: String? = null, val hostPackage: String? = null,
    val carApiLevel: Int? = null, val samples: List<ExportSample> = emptyList(),
)

@Serializable
data class ExportFile(val format: String = FORMAT, val version: Int = 1, val exportedAt: Long, val sessions: List<ExportSession>) {
    companion object { const val FORMAT = "telltale-export" }
}

/** Pure export/import logic (no Android), covered by unit tests. */
object ExportFormat {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun build(data: List<Pair<SessionEntity, List<SampleEntity>>>, now: Long = System.currentTimeMillis()) = ExportFile(
        exportedAt = now,
        sessions = data.map { (s, samples) ->
            ExportSession(s.startedAt, s.endedAt, s.make, s.model, s.year, s.connectionType, s.hostPackage, s.carApiLevel,
                samples.map { ExportSample(it.t, it.key, it.value, it.text, it.status) })
        },
    )

    fun toJson(file: ExportFile): String = json.encodeToString(ExportFile.serializer(), file)

    fun fromJson(text: String): ExportFile {
        val f = json.decodeFromString(ExportFile.serializer(), text)
        require(f.format == ExportFile.FORMAT) { "Not a Telltale export file" }
        return f
    }

    fun toEntities(s: ExportSession): Pair<SessionEntity, List<SampleEntity>> =
        SessionEntity(0, s.startedAt, s.endedAt, s.make, s.model, s.year, s.connectionType, s.hostPackage, s.carApiLevel) to
            s.samples.map { SampleEntity(0, 0, it.t, it.key, it.value, it.text, it.status) }

    const val CSV_HEADER = "session_start,timestamp_iso,timestamp_ms,key,value,text,status"

    fun toCsv(file: ExportFile): String = buildString {
        append(CSV_HEADER).append('\n')
        for (s in file.sessions) for (x in s.samples) {
            append(Instant.ofEpochMilli(s.startedAt)).append(',')
            append(Instant.ofEpochMilli(x.t)).append(',')
            append(x.t).append(',')
            append(csv(x.key)).append(',')
            append(x.value?.toString() ?: "").append(',')
            append(csv(x.text)).append(',')
            append(x.status).append('\n')
        }
    }

    fun csv(v: String?): String {
        if (v == null) return ""
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}

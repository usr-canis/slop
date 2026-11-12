package app.telltale.data

import app.telltale.data.db.SampleEntity
import app.telltale.data.db.SessionEntity
import app.telltale.data.db.TelltaleDao
import app.telltale.model.FieldReading
import app.telltale.model.Fields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Downsampling rule, kept pure for unit tests:
 * a reading is stored if enough time passed since the last stored one for that key,
 * or (for text-only fields) if the text changed.
 */
class Downsampler(var intervalMs: Long) {
    private val lastT = HashMap<String, Long>()
    private val lastText = HashMap<String, String?>()

    fun accept(r: FieldReading): Boolean {
        val prevT = lastT[r.key]
        val changed = lastText[r.key] != r.text || !lastText.containsKey(r.key)
        val due = prevT == null || r.timestampMillis - prevT >= intervalMs
        val ok = if (r.numeric == null) changed else due
        if (ok) { lastT[r.key] = r.timestampMillis; lastText[r.key] = r.text }
        return ok
    }

    fun reset() { lastT.clear(); lastText.clear() }
}

/** Keeps a session log (always) and records samples (only when recording is on). */
class Recorder(
    private val scope: CoroutineScope,
    private val repo: CarDataRepository,
    private val dao: TelltaleDao,
    private val settingsStore: SettingsStore,
) {
    private var settings = AppSettings()
    private var sessionId: Long? = null
    private val buffer = ArrayList<SampleEntity>()
    private val lock = Mutex()
    private val sampler = Downsampler(1000)

    fun start() {
        scope.launch { dao.closeDangling() }
        scope.launch {
            settingsStore.settings.collect {
                settings = it; sampler.intervalMs = it.sampleIntervalMs
                if (it.retentionDays > 0) dao.deleteOlderThan(System.currentTimeMillis() - it.retentionDays * 86_400_000L)
            }
        }
        scope.launch {
            repo.session.collect { st ->
                lock.withLock {
                    if (st == SessionState.ACTIVE && sessionId == null) {
                        sampler.reset()
                        sessionId = dao.insertSession(SessionEntity(startedAt = System.currentTimeMillis(), connectionType = repo.connection.value.label))
                    } else if (st == SessionState.NONE && sessionId != null) {
                        flushLocked()
                        dao.endSession(sessionId!!, System.currentTimeMillis())
                        sessionId = null
                    }
                }
            }
        }
        scope.launch { repo.updates.collect { onUpdate(it) } }
        scope.launch { while (true) { delay(2000); lock.withLock { flushLocked() } } }
    }

    private suspend fun onUpdate(r: FieldReading) {
        lock.withLock {
            val id = sessionId ?: return
            when (r.key) {
                Fields.MAKE, Fields.MODEL_NAME, Fields.YEAR -> {
                    val f = repo.fields.value
                    dao.setModel(id, f[Fields.MAKE]?.text, f[Fields.MODEL_NAME]?.text, f[Fields.YEAR]?.numeric?.toInt())
                }
                Fields.HOST_PACKAGE, Fields.HOST_API -> {
                    val f = repo.fields.value
                    dao.setHost(id, f[Fields.HOST_PACKAGE]?.text, f[Fields.HOST_API]?.numeric?.toInt(), repo.connection.value.label)
                }
            }
            if (!settings.recording || !sampler.accept(r)) return
            buffer += SampleEntity(sessionId = id, t = r.timestampMillis, key = r.key, value = r.numeric, text = r.text, status = r.status.name)
            if (buffer.size >= 500) flushLocked()
        }
    }

    private suspend fun flushLocked() {
        if (buffer.isEmpty()) return
        val copy = ArrayList(buffer); buffer.clear()
        dao.insertSamples(copy)
    }
}

package app.telltale.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val connectionType: String? = null,
    val hostPackage: String? = null,
    val carApiLevel: Int? = null,
)

@Entity(
    tableName = "samples",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("key", "t")],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val t: Long,
    val key: String,
    val value: Double? = null,
    val text: String? = null,
    val status: String,
)

data class SessionWithCount(
    val id: Long, val startedAt: Long, val endedAt: Long?, val make: String?, val model: String?,
    val year: Int?, val connectionType: String?, val sampleCount: Int,
)

data class TimePoint(val t: Long, val value: Double)

@Dao
interface TelltaleDao {
    @Insert suspend fun insertSession(s: SessionEntity): Long
    @Insert suspend fun insertSamples(s: List<SampleEntity>)

    @Query("UPDATE sessions SET endedAt = :end WHERE id = :id") suspend fun endSession(id: Long, end: Long)
    @Query("UPDATE sessions SET endedAt = startedAt WHERE endedAt IS NULL") suspend fun closeDangling()
    @Query("UPDATE sessions SET make = :make, model = :model, year = :year WHERE id = :id")
    suspend fun setModel(id: Long, make: String?, model: String?, year: Int?)
    @Query("UPDATE sessions SET hostPackage = :pkg, carApiLevel = :api, connectionType = :conn WHERE id = :id")
    suspend fun setHost(id: Long, pkg: String?, api: Int?, conn: String?)

    @Query("SELECT s.*, (SELECT COUNT(*) FROM samples WHERE sessionId = s.id) AS sampleCount FROM sessions s ORDER BY startedAt DESC")
    fun sessions(): Flow<List<SessionWithCount>>

    @Query("SELECT * FROM sessions ORDER BY startedAt") suspend fun allSessions(): List<SessionEntity>
    @Query("SELECT * FROM samples WHERE sessionId = :id ORDER BY t") suspend fun samples(id: Long): List<SampleEntity>

    @Query("SELECT t, value FROM samples WHERE `key` = :key AND value IS NOT NULL AND t >= :since ORDER BY t")
    fun series(key: String, since: Long): Flow<List<TimePoint>>

    @Query("DELETE FROM sessions WHERE startedAt < :cutoff") suspend fun deleteOlderThan(cutoff: Long)
    @Query("DELETE FROM sessions") suspend fun deleteAll()

    @Transaction
    suspend fun importSession(s: SessionEntity, samples: List<SampleEntity>) {
        val id = insertSession(s.copy(id = 0))
        samples.chunked(500).forEach { c -> insertSamples(c.map { it.copy(id = 0, sessionId = id) }) }
    }
}

@Database(entities = [SessionEntity::class, SampleEntity::class], version = 1, exportSchema = true)
abstract class TelltaleDb : RoomDatabase() {
    abstract fun dao(): TelltaleDao

    companion object {
        fun create(context: Context): TelltaleDb =
            Room.databaseBuilder(context, TelltaleDb::class.java, "telltale.db").build()
    }
}

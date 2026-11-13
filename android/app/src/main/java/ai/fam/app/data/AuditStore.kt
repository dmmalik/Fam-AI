package ai.fam.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Entity(tableName = "audit")
data class AuditEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val action: String,
    val label: String,
    val severity: String,
    val backend: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    val language: String,
    val region: String,
    @ColumnInfo(name = "latency_ms") val latencyMs: Long,
    @ColumnInfo(name = "image_sha256") val imageSha256: String,
    @ColumnInfo(name = "advice_source") val adviceSource: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String = "",
    @ColumnInfo(name = "display_name") val displayName: String = "",
    val crop: String = "",
    val kind: String = "",
    val confidence: Double = 0.0,
    @ColumnInfo(name = "lesion_ratio") val lesionRatio: Double = 0.0,
    @ColumnInfo(name = "advice_json") val adviceJson: String = "",
    val note: String = "",
)

data class HistoryRow(
    val id: Long,
    val time: String,
    val label: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val severity: String,
    val backend: String,
    @ColumnInfo(name = "latency_ms") val latencyMs: Long,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String,
)

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(event: AuditEvent): Long

    @Query(
        """
        SELECT id, time, label, display_name, severity, backend, latency_ms, thumbnail_path
        FROM audit
        WHERE action IN ('diagnose', 'local_diagnose', 'demo_diagnose')
        ORDER BY time DESC, id DESC
        """,
    )
    fun history(): Flow<List<HistoryRow>>

    @Query("SELECT * FROM audit WHERE id = :id")
    suspend fun find(id: Long): AuditEvent?
}

@Database(entities = [AuditEvent::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var instance: AuditDatabase? = null

        fun get(context: Context): AuditDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuditDatabase::class.java,
                    "fam_ai_audit.db",
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                lockDown(db)
                            }

                            override fun onOpen(db: SupportSQLiteDatabase) {
                                lockDown(db)
                            }
                        },
                    )
                    .build()
                    .also { instance = it }
            }
        }

        private fun lockDown(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS audit_no_update
                BEFORE UPDATE ON audit
                BEGIN
                    SELECT RAISE(ABORT, 'append-only');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS audit_no_delete
                BEFORE DELETE ON audit
                BEGIN
                    SELECT RAISE(ABORT, 'append-only');
                END
                """.trimIndent(),
            )
        }
    }
}

object AuditTime {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    fun now(): String = format.format(Instant.now())
}

fun auditEvent(
    deviceId: String,
    action: String,
    language: String,
    region: String,
    label: String = "",
    severity: String = "",
    backend: String = "",
    modelId: String = "",
    modelVersion: String = "",
    latencyMs: Long = 0,
    imageSha256: String = "",
    adviceSource: String = "",
    thumbnailPath: String = "",
    displayName: String = "",
    crop: String = "",
    kind: String = "",
    confidence: Double = 0.0,
    lesionRatio: Double = 0.0,
    adviceJson: String = "",
    note: String = "",
): AuditEvent {
    return AuditEvent(
        time = AuditTime.now(),
        deviceId = deviceId,
        action = action,
        label = label,
        severity = severity,
        backend = backend,
        modelId = modelId,
        modelVersion = modelVersion,
        language = language,
        region = region,
        latencyMs = latencyMs,
        imageSha256 = imageSha256,
        adviceSource = adviceSource,
        thumbnailPath = thumbnailPath,
        displayName = displayName,
        crop = crop,
        kind = kind,
        confidence = confidence,
        lesionRatio = lesionRatio,
        adviceJson = adviceJson,
        note = note,
    )
}

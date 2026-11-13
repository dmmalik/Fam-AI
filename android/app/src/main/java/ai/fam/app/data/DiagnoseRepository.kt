package ai.fam.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class DiagnoseOutcome {
    data class Ready(val id: Long) : DiagnoseOutcome()
    data object Unreadable : DiagnoseOutcome()
    data object ModelMissing : DiagnoseOutcome()
    data object ModelFailed : DiagnoseOutcome()
}

class DiagnoseRepository(context: Context) {
    private val app = context.applicationContext
    private val settings = SettingsStore(app)
    private val tokens = TokenStore(app)
    private val devices = DeviceIdStore(app)
    private val audit = AuditDatabase.get(app).auditDao()
    private val kb = KnowledgeBase.load(app.assets)
    private val pi = PiClient()
    private val vision = PhoneVision(app)

    suspend fun diagnoseFile(file: File, crop: String?): DiagnoseOutcome {
        return withContext(Dispatchers.IO) { run(file, crop) }
    }

    suspend fun diagnoseUri(uri: Uri, crop: String?): DiagnoseOutcome {
        return withContext(Dispatchers.IO) {
            val staged = try {
                ImagePrep.stage(app, uri)
            } catch (_: Exception) {
                return@withContext DiagnoseOutcome.Unreadable
            }
            try {
                run(staged, crop)
            } finally {
                staged.delete()
            }
        }
    }

    private suspend fun run(source: File, crop: String?): DiagnoseOutcome {
        val prepared = try {
            ImagePrep.prepare(app, source)
        } catch (_: Exception) {
            return DiagnoseOutcome.Unreadable
        }
        val current = settings.read()
        val started = System.nanoTime()
        val address = PiAddress.parse(current.address)
        val token = tokens.get()
        if (current.mode == "pi" && token.isNotBlank() && address != null) {
            val origin = address.origin()
            if (!pi.healthy(origin)) {
                insertNote(current, prepared, "health_failure", "")
                return local(prepared, current, crop, started, note = "health")
            }
            try {
                val remote = pi.diagnose(
                    origin = origin,
                    token = token,
                    photo = prepared.jpeg,
                    language = current.language,
                    regionId = current.regionId,
                    deviceId = devices.get(),
                    crop = crop.orEmpty(),
                )
                val id = audit.insert(
                    auditEvent(
                        deviceId = devices.get(),
                        action = "diagnose",
                        language = current.language,
                        region = current.regionId,
                        label = remote.labelId,
                        severity = remote.severity,
                        backend = remote.backend,
                        modelId = remote.modelId,
                        modelVersion = remote.modelVersion,
                        latencyMs = remote.latencyMs.toLong(),
                        imageSha256 = remote.imageSha256,
                        adviceSource = remote.adviceSource,
                        thumbnailPath = prepared.thumb.absolutePath,
                        displayName = remote.displayName,
                        crop = remote.crop,
                        kind = remote.kind,
                        confidence = remote.confidence,
                        adviceJson = remote.advice.toJson(),
                    ),
                )
                return DiagnoseOutcome.Ready(id)
            } catch (exc: Exception) {
                android.util.Log.e("FamAI", "pi diagnose failed", exc)
                insertNote(current, prepared, "rejected", "")
                return local(prepared, current, crop, started, note = "pi_error")
            }
        }
        return local(prepared, current, crop, started, note = "")
    }

    private suspend fun local(
        prepared: PreparedPhoto,
        current: AppSettings,
        crop: String?,
        started: Long,
        note: String,
    ): DiagnoseOutcome {
        if (vision.locate() == null) return DiagnoseOutcome.ModelMissing
        val seen = try {
            vision.ask(prepared.jpeg, kb.labels, current.language, crop)
        } catch (exc: Exception) {
            if (exc is CancellationException) throw exc
            android.util.Log.e("FamAI", "phone vision failed", exc)
            null
        }
        if (seen == null) return DiagnoseOutcome.ModelFailed
        return visionRow(prepared, current, seen, started, note)
    }

    private suspend fun visionRow(
        prepared: PreparedPhoto,
        current: AppSettings,
        seen: PhoneVisionResult,
        started: Long,
        note: String,
    ): DiagnoseOutcome {
        val reply = seen.reply
        val known = reply.known(kb.labels)
        val advice = reply.replyText(current.language)
        val kind = known?.kind ?: "disease"
        val id = audit.insert(
            auditEvent(
                deviceId = devices.get(),
                action = "local_diagnose",
                language = current.language,
                region = current.regionId,
                label = known?.id ?: "unknown",
                severity = Severity.of(
                    known?.id ?: "unknown",
                    kind,
                    prepared.sample.lesionRatio,
                    reply.confidence,
                ),
                backend = PhoneVision.BACKEND,
                modelId = seen.modelId,
                modelVersion = seen.modelVersion,
                latencyMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0),
                imageSha256 = prepared.sha256,
                adviceSource = PhoneVision.ADVICE_SOURCE,
                thumbnailPath = prepared.thumb.absolutePath,
                displayName = reply.displayName(known),
                crop = known?.crop ?: reply.plant.lowercase(),
                kind = kind,
                confidence = reply.confidence,
                lesionRatio = prepared.sample.lesionRatio,
                adviceJson = advice.toJson(),
                note = visionNote(note),
            ),
        )
        return DiagnoseOutcome.Ready(id)
    }

    private fun visionNote(note: String): String {
        return when (note) {
            "health" -> "health_vision"
            "pi_error" -> "pi_error_vision"
            else -> note
        }
    }

    private suspend fun insertNote(current: AppSettings, prepared: PreparedPhoto, action: String, backend: String) {
        audit.insert(
            auditEvent(
                deviceId = devices.get(),
                action = action,
                language = current.language,
                region = current.regionId,
                backend = backend,
                imageSha256 = prepared.sha256,
                thumbnailPath = prepared.thumb.absolutePath,
            ),
        )
    }
}

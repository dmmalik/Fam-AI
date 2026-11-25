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

enum class PhotoStep { SAVED, LEAF, REPLY, READY }

class DiagnoseRepository(context: Context) {
    private val app = context.applicationContext
    private val settings = SettingsStore(app)
    private val tokens = TokenStore(app)
    private val devices = DeviceIdStore(app)
    private val audit = AuditDatabase.get(app).auditDao()
    private val kb = KnowledgeBase.load(app.assets)
    private val pi = PiClient()
    private val vision = PhoneVision(app)
    private val leaves = LeafModel(app)
    var watch: (PhotoStep, File?) -> Unit = { _, _ -> }

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
        watch(PhotoStep.SAVED, prepared.jpeg)
        watch(PhotoStep.LEAF, prepared.jpeg)
        val leaf = try {
            leaves.predict(prepared.jpeg)
        } catch (exc: Exception) {
            if (exc is CancellationException) throw exc
            android.util.Log.e("FamAI", "leaf model failed", exc)
            null
        }
        val address = PiAddress.parse(current.address)
        val token = tokens.get()
        if (current.mode == "pi" && token.isNotBlank() && address != null) {
            val origin = address.origin()
            if (!pi.healthy(origin)) {
                insertNote(current, prepared, "health_failure", "")
                return local(prepared, current, crop, started, note = "health", leaf = leaf)
            }
            watch(PhotoStep.REPLY, prepared.jpeg)
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
                val leafCare = careFor(leaf, current, LanguagePackStore(app).read(current.language, kb))
                val written = remote.advice.toJson()
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
                        adviceJson = written,
                        note = englishNote("", current.language, remote.adviceLanguage),
                        leafLabel = leafTitle(leaf),
                        leafConfidence = leafConfidence(leaf),
                        leafNext = leafNext(leaf),
                        leafAdviceJson = leafCare,
                    ),
                )
                shareResult(prepared, current, remote.backend, remote.modelId, remote.modelVersion, leaf, leafCare, written)
                watch(PhotoStep.READY, prepared.jpeg)
                return DiagnoseOutcome.Ready(id)
            } catch (exc: Exception) {
                android.util.Log.e("FamAI", "pi diagnose failed", exc)
                insertNote(current, prepared, "rejected", "")
                return local(prepared, current, crop, started, note = "pi_error", leaf = leaf)
            }
        }
        return local(prepared, current, crop, started, note = "", leaf = leaf)
    }

    private suspend fun local(
        prepared: PreparedPhoto,
        current: AppSettings,
        crop: String?,
        started: Long,
        note: String,
        leaf: LeafRead?,
    ): DiagnoseOutcome {
        val pack = LanguagePackStore(app).read(current.language, kb)
        if (vision.locate() == null) {
            if (leaf == null) return DiagnoseOutcome.ModelMissing
            return leafOnly(prepared, current, started, note, leaf, pack)
        }
        watch(PhotoStep.REPLY, prepared.jpeg)
        val seen = try {
            vision.ask(
                prepared.jpeg,
                kb.labels,
                current.language,
                crop,
                kb.promptNotes(current.language, pack),
            )
        } catch (exc: Exception) {
            if (exc is CancellationException) throw exc
            android.util.Log.e("FamAI", "phone vision failed", exc)
            null
        }
        if (seen == null) {
            if (leaf == null) return DiagnoseOutcome.ModelFailed
            return leafOnly(prepared, current, started, note, leaf, pack)
        }
        return visionRow(prepared, current, seen, started, note, pack, leaf)
    }

    private suspend fun leafOnly(
        prepared: PreparedPhoto,
        current: AppSettings,
        started: Long,
        note: String,
        leaf: LeafRead,
        pack: LanguagePack?,
    ): DiagnoseOutcome {
        val known = kb.labels.firstOrNull { it.id == leaf.top.id }
        val care = careFor(leaf, current, pack)
        val stored = kb.storedLanguage(current.language, pack)
        val title = leafTitle(leaf)
        val id = audit.insert(
            auditEvent(
                deviceId = devices.get(),
                action = "local_diagnose",
                language = current.language,
                region = current.regionId,
                label = known?.id ?: leaf.top.id,
                severity = Severity.of(
                    known?.id ?: "unknown",
                    known?.kind ?: "disease",
                    prepared.sample.lesionRatio,
                    leaf.top.confidence.toDouble(),
                ),
                backend = "on-device efficientnetv2",
                modelId = "efficientnetv2_s",
                modelVersion = "plant-38",
                latencyMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0),
                imageSha256 = prepared.sha256,
                adviceSource = "efficientnet",
                thumbnailPath = prepared.thumb.absolutePath,
                displayName = title,
                crop = known?.crop ?: "",
                kind = known?.kind ?: "disease",
                confidence = leaf.top.confidence.toDouble(),
                lesionRatio = prepared.sample.lesionRatio,
                adviceJson = care,
                note = englishNote(note, current.language, stored),
                leafLabel = title,
                leafConfidence = leaf.top.confidence.toDouble(),
                leafNext = leafNext(leaf),
                leafAdviceJson = care,
            ),
        )
        shareResult(
            prepared,
            current,
            "on-device efficientnetv2",
            "efficientnetv2_s",
            "plant-38",
            leaf,
            care,
            care,
        )
        watch(PhotoStep.READY, prepared.jpeg)
        return DiagnoseOutcome.Ready(id)
    }

    private suspend fun visionRow(
        prepared: PreparedPhoto,
        current: AppSettings,
        seen: PhoneVisionResult,
        started: Long,
        note: String,
        pack: LanguagePack?,
        leaf: LeafRead?,
    ): DiagnoseOutcome {
        val reply = seen.reply
        val known = reply.known(kb.labels)
        val template = known?.let { kb.advice(it.id, kb.region(current.regionId), current.language, pack) }
        val filled = template?.takeIf { reply.summary.isBlank() && it.summary.isNotBlank() }
        val advice = filled ?: reply.replyText(current.language)
        val stored = if (filled != null) kb.storedLanguage(current.language, pack) else current.language
        val kind = known?.kind ?: "disease"
        val written = advice.toJson()
        val leafCare = careFor(leaf, current, pack)
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
                adviceJson = written,
                note = englishNote(visionNote(note), current.language, stored),
                leafLabel = leafTitle(leaf),
                leafConfidence = leafConfidence(leaf),
                leafNext = leafNext(leaf),
                leafAdviceJson = leafCare,
            ),
        )
        shareResult(prepared, current, PhoneVision.BACKEND, seen.modelId, seen.modelVersion, leaf, leafCare, written)
        watch(PhotoStep.READY, prepared.jpeg)
        return DiagnoseOutcome.Ready(id)
    }

    private fun shareResult(
        prepared: PreparedPhoto,
        current: AppSettings,
        backend: String,
        modelId: String,
        modelVersion: String,
        leaf: LeafRead?,
        careJson: String,
        writtenJson: String,
    ) {
        ShareSync.consider(
            app,
            prepared.jpeg,
            ShareDraft(
                capturedAt = AuditTime.now(),
                language = current.language,
                region = current.regionId,
                modelId = modelId,
                modelVersion = modelVersion,
                backend = backend,
                leafLabel = leafTitle(leaf),
                leafConfidence = leafConfidence(leaf),
                leafNext = leafNext(leaf),
                careJson = careJson,
                writtenJson = writtenJson,
            ),
        )
    }

    private fun careFor(leaf: LeafRead?, current: AppSettings, pack: LanguagePack?): String {
        val id = leaf?.top?.id ?: return ""
        val known = kb.labels.firstOrNull { it.id == id }
        if (known != null) {
            val fromKb = kb.advice(known.id, kb.region(current.regionId), current.language, pack)
            if (fromKb.summary.isNotBlank() || fromKb.immediateActions.isNotEmpty()) {
                return fromKb.toJson()
            }
        }
        return LeafCare.forClass(id, current.language).toJson()
    }

    private fun leafTitle(leaf: LeafRead?): String {
        val id = leaf?.top?.id ?: return ""
        return kb.labels.firstOrNull { it.id == id }?.displayName ?: LeafScores.readable(id)
    }

    private fun leafConfidence(leaf: LeafRead?): Double = leaf?.top?.confidence?.toDouble() ?: 0.0

    private fun leafNext(leaf: LeafRead?): String {
        if (leaf == null) return ""
        return leaf.others.joinToString(" · ") { guess ->
            val name = kb.labels.firstOrNull { it.id == guess.id }?.displayName ?: LeafScores.readable(guess.id)
            "$name ${(guess.confidence * 100).toInt()}%"
        }
    }

    private fun englishNote(note: String, language: String, storedIn: String): String {
        if (language == "en" || storedIn != "en") return note
        if (note.isEmpty()) return "english_note"
        if (note == "english_note" || note.endsWith("+english")) return note
        return "$note+english"
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

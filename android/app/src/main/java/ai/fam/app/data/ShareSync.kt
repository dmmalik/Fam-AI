package ai.fam.app.data

import ai.fam.app.BuildConfig
import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ShareSync {
    private val flushing = AtomicBoolean(false)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    fun consider(context: Context, jpeg: File, draft: ShareDraft) {
        val app = context.applicationContext
        val profile = FarmerStore(app).read()
        if (!profile.ready || !jpeg.exists()) return
        if (!ShareRules.shouldKeep(profile.shareTraining, key().isNotBlank())) return
        val meta = JSONObject()
            .put("farmer_name", profile.name)
            .put("mobile", profile.mobile)
            .put("role", profile.role)
            .put("mobile_verified", profile.mobileVerified)
            .put("share_purpose", ShareRules.PURPOSE)
            .put("consent_version", profile.consentVersion)
            .put("consent_at", profile.consentAt)
            .put("captured_at", draft.capturedAt)
            .put("leaf_label", draft.leafLabel)
            .put("leaf_confidence", draft.leafConfidence)
            .put("leaf_next", draft.leafNext)
            .put("care", jsonOrEmpty(draft.careJson))
            .put("written_reply", jsonOrEmpty(draft.writtenJson))
            .put("language", draft.language)
            .put("region", draft.region)
            .put("model_id", draft.modelId)
            .put("model_version", draft.modelVersion)
            .put("backend", draft.backend)
        ShareFolder(queueDir(app)).enqueue(jpeg, meta.toString())
        flush(app)
    }

    fun withdraw(context: Context) {
        val app = context.applicationContext
        val store = FarmerStore(app)
        val current = store.read()
        store.write(current.copy(shareTraining = false, pendingRemoteDelete = true))
        ShareFolder(queueDir(app)).clear()
        flush(app)
    }

    fun flush(context: Context) {
        val app = context.applicationContext
        if (!flushing.compareAndSet(false, true)) return
        Thread {
            try {
                drain(app)
            } finally {
                flushing.set(false)
            }
        }.start()
    }

    fun queueDir(context: Context): File = File(context.filesDir, "share-queue")

    private fun drain(context: Context) {
        val store = FarmerStore(context)
        val profile = store.read()
        val folder = ShareFolder(queueDir(context))
        if (!profile.shareTraining) folder.clear()
        if (key().isBlank() || !ModelDownload.onlineNow(context)) return
        if (profile.pendingRemoteDelete && profile.mobile.isNotBlank()) {
            if (deleteRemote(profile)) {
                store.write(store.read().copy(pendingRemoteDelete = false))
            }
        }
        if (!store.read().shareTraining) return
        for (jpeg in folder.pending()) {
            val meta = folder.metaFor(jpeg)
            if (!meta.exists()) {
                folder.remove(jpeg)
                continue
            }
            if (!upload(jpeg, meta.readText(), store.read())) break
            folder.remove(jpeg)
        }
    }

    private fun upload(jpeg: File, meta: String, profile: FarmerProfile): Boolean {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", jpeg.name, jpeg.asRequestBody("image/jpeg".toMediaType()))
            .addFormDataPart("meta", meta)
            .build()
        val builder = Request.Builder()
            .url("${ShareRules.ORIGIN}/api/fam/samples")
            .header("X-Fam-Key", key())
            .post(body)
        if (profile.role == FarmerProfile.ROLE_REGISTERED && profile.sessionToken.isNotBlank()) {
            builder.header("Authorization", "Bearer ${profile.sessionToken}")
        }
        return try {
            client.newCall(builder.build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteRemote(profile: FarmerProfile): Boolean {
        val builder = Request.Builder()
            .url("${ShareRules.ORIGIN}/api/fam/samples?mobile=${profile.mobile}")
            .header("X-Fam-Key", key())
            .delete()
        if (profile.role == FarmerProfile.ROLE_REGISTERED && profile.sessionToken.isNotBlank()) {
            builder.header("Authorization", "Bearer ${profile.sessionToken}")
        }
        return try {
            client.newCall(builder.build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun startRegistration(name: String, mobile: String): String? {
        val payload = JSONObject().put("name", name.trim()).put("mobile", mobile).toString()
        return postJson("/api/fam/register/start", payload, null)
    }

    fun checkRegistration(mobile: String, code: String): String? {
        val payload = JSONObject().put("mobile", mobile).put("code", code.trim()).toString()
        val raw = postJson("/api/fam/register/check", payload, null) ?: return null
        return try {
            val token = JSONObject(raw).optString("session_token")
            token.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun key(): String = BuildConfig.FAM_SHARE_KEY.trim()

    private fun postJson(path: String, json: String, token: String?): String? {
        if (key().isBlank()) return null
        val builder = Request.Builder()
            .url(ShareRules.ORIGIN + path)
            .header("X-Fam-Key", key())
            .post(json.toRequestBody("application/json".toMediaType()))
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) null else text
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonOrEmpty(raw: String): Any {
        if (raw.isBlank()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }
}

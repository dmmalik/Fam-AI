package ai.fam.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class PiClient {
    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.SECONDS)
        .build()

    private val diagnoseClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    fun healthy(origin: String): Boolean {
        val request = Request.Builder().url("$origin/v1/health").get().build()
        return try {
            healthClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                json.has("vision_backend") && json.has("llm_backend")
            }
        } catch (_: Exception) {
            false
        }
    }

    fun diagnose(
        origin: String,
        token: String,
        photo: File,
        language: String,
        regionId: String,
        deviceId: String,
        crop: String,
    ): RemoteDiagnosis {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "leaf.jpg", photo.asRequestBody("image/jpeg".toMediaType()))
            .addFormDataPart("language", language)
            .addFormDataPart("region_id", regionId)
            .addFormDataPart("device_id", deviceId)
            .apply {
                if (crop.isNotBlank()) addFormDataPart("crop", crop)
            }
            .build()
        val request = Request.Builder()
            .url("$origin/v1/diagnose")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        diagnoseClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val code = try {
                    JSONObject(raw).optString("error", "rejected")
                } catch (_: Exception) {
                    "rejected"
                }
                throw IOException(code)
            }
            return parse(JSONObject(raw))
        }
    }

    private fun parse(json: JSONObject): RemoteDiagnosis {
        val advice = json.getJSONObject("advice")
        return RemoteDiagnosis(
            labelId = json.getString("label_id"),
            displayName = json.getString("display_name"),
            crop = json.getString("crop"),
            kind = json.getString("kind"),
            confidence = json.getDouble("confidence"),
            severity = json.getString("severity"),
            modelId = json.getString("model_id"),
            modelVersion = json.getString("model_version"),
            backend = json.getString("backend"),
            latencyMs = json.getInt("latency_ms"),
            imageSha256 = json.getString("image_sha256"),
            adviceSource = json.getString("advice_source"),
            adviceLanguage = json.optString("advice_language", ""),
            advice = AdviceText(
                summary = advice.getString("summary"),
                immediateActions = stringList(advice, "immediate_actions"),
                treatment = stringList(advice, "treatment"),
                prevention = stringList(advice, "prevention"),
                whenToSeekHelp = advice.getString("when_to_seek_help"),
            ),
        )
    }

    private fun stringList(obj: JSONObject, key: String): List<String> {
        val array = obj.getJSONArray(key)
        return List(array.length()) { index -> array.getString(index) }
    }
}

data class RemoteDiagnosis(
    val labelId: String,
    val displayName: String,
    val crop: String,
    val kind: String,
    val confidence: Double,
    val severity: String,
    val modelId: String,
    val modelVersion: String,
    val backend: String,
    val latencyMs: Int,
    val imageSha256: String,
    val adviceSource: String,
    val adviceLanguage: String,
    val advice: AdviceText,
)

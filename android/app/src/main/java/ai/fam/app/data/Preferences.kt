package ai.fam.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

data class AppSettings(
    val mode: String,
    val address: String,
    val language: String,
    val regionId: String,
)

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): AppSettings {
        return AppSettings(
            mode = if (prefs.getString(KEY_MODE, "local") == "pi") "pi" else "local",
            address = prefs.getString(KEY_ADDRESS, PiAddress.DEFAULT) ?: PiAddress.DEFAULT,
            language = normalizeLanguage(prefs.getString(KEY_LANGUAGE, "en")),
            regionId = normalizeRegion(prefs.getString(KEY_REGION, "indo_gangetic")),
        )
    }

    fun write(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_MODE, if (settings.mode == "pi") "pi" else "local")
            .putString(KEY_ADDRESS, settings.address)
            .putString(KEY_LANGUAGE, normalizeLanguage(settings.language))
            .putString(KEY_REGION, normalizeRegion(settings.regionId))
            .commit()
    }

    private fun normalizeLanguage(value: String?): String {
        return if (value in LANGUAGES) value!! else "en"
    }

    private fun normalizeRegion(value: String?): String {
        return if (value in REGION_IDS) value!! else "indo_gangetic"
    }

    companion object {
        private const val FILE = "fam_ai_settings"
        private const val KEY_MODE = "mode"
        private const val KEY_ADDRESS = "address"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_REGION = "region"
    }
}

data class DownloadChoices(
    val background: Boolean = false,
    val mobile: Boolean = false,
)

class DownloadChoicesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): DownloadChoices {
        return DownloadChoices(
            background = prefs.getBoolean(KEY_BACKGROUND, false),
            mobile = prefs.getBoolean(KEY_MOBILE, false),
        )
    }

    fun write(choices: DownloadChoices) {
        prefs.edit()
            .putBoolean(KEY_BACKGROUND, choices.background)
            .putBoolean(KEY_MOBILE, choices.mobile)
            .commit()
    }

    companion object {
        private const val FILE = "fam_ai_settings"
        private const val KEY_BACKGROUND = "download_background"
        private const val KEY_MOBILE = "download_mobile"
    }
}

class ModelReferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun matches(name: String, bytes: Long): Boolean {
        return PhoneModelFiles.sameModel(
            prefs.getString(KEY_NAME, null),
            prefs.getLong(KEY_BYTES, -1L),
            name,
            bytes,
        )
    }

    fun remember(name: String, bytes: Long) {
        prefs.edit().putString(KEY_NAME, name).putLong(KEY_BYTES, bytes).commit()
    }

    companion object {
        private const val FILE = "fam_ai_settings"
        private const val KEY_NAME = "model_ref_name"
        private const val KEY_BYTES = "model_ref_bytes"
    }
}

class TokenStore(context: Context) {
    private val app = context.applicationContext

    @Volatile
    private var cached: SharedPreferences? = null

    fun get(): String {
        return try {
            prefs().getString(KEY, "").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun set(token: String) {
        prefs().edit().putString(KEY, token.trim()).commit()
    }

    private fun prefs(): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                app,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            cached = created
            return created
        }
    }

    companion object {
        private const val FILE = "fam_ai_token"
        private const val KEY = "token"
    }
}

class DeviceIdStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(): String {
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).commit()
        return created
    }

    companion object {
        private const val FILE = "fam_ai_device"
        private const val KEY = "device_id"
    }
}

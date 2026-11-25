package ai.fam.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.Instant

data class FarmerProfile(
    val name: String = "",
    val mobile: String = "",
    val role: String = "",
    val mobileVerified: Boolean = false,
    val sessionToken: String = "",
    val noticeAccepted: Boolean = false,
    val shareTraining: Boolean = false,
    val consentAt: String = "",
    val consentVersion: String = ShareRules.CONSENT_VERSION,
    val pendingRemoteDelete: Boolean = false,
) {
    val ready: Boolean
        get() = noticeAccepted &&
            name.isNotBlank() &&
            ShareRules.mobileOk(mobile) &&
            (role == ROLE_GUEST || (role == ROLE_REGISTERED && mobileVerified && sessionToken.isNotBlank()))

    companion object {
        const val ROLE_GUEST = "guest"
        const val ROLE_REGISTERED = "registered"
    }
}

class FarmerStore(context: Context) {
    private val app = context.applicationContext

    @Volatile
    private var cached: SharedPreferences? = null

    fun read(): FarmerProfile {
        return try {
            val prefs = prefs()
            FarmerProfile(
                name = prefs.getString(KEY_NAME, "").orEmpty(),
                mobile = prefs.getString(KEY_MOBILE, "").orEmpty(),
                role = prefs.getString(KEY_ROLE, "").orEmpty(),
                mobileVerified = prefs.getBoolean(KEY_VERIFIED, false),
                sessionToken = prefs.getString(KEY_SESSION, "").orEmpty(),
                noticeAccepted = prefs.getBoolean(KEY_NOTICE, false),
                shareTraining = prefs.getBoolean(KEY_SHARE, false),
                consentAt = prefs.getString(KEY_CONSENT_AT, "").orEmpty(),
                consentVersion = prefs.getString(KEY_CONSENT_VERSION, ShareRules.CONSENT_VERSION)
                    ?: ShareRules.CONSENT_VERSION,
                pendingRemoteDelete = prefs.getBoolean(KEY_PENDING_DELETE, false),
            )
        } catch (_: Exception) {
            FarmerProfile()
        }
    }

    fun write(profile: FarmerProfile) {
        prefs().edit()
            .putString(KEY_NAME, profile.name.trim())
            .putString(KEY_MOBILE, ShareRules.localMobile(profile.mobile))
            .putString(KEY_ROLE, profile.role)
            .putBoolean(KEY_VERIFIED, profile.mobileVerified)
            .putString(KEY_SESSION, profile.sessionToken)
            .putBoolean(KEY_NOTICE, profile.noticeAccepted)
            .putBoolean(KEY_SHARE, profile.shareTraining)
            .putString(KEY_CONSENT_AT, profile.consentAt.ifBlank { Instant.now().toString() })
            .putString(KEY_CONSENT_VERSION, profile.consentVersion)
            .putBoolean(KEY_PENDING_DELETE, profile.pendingRemoteDelete)
            .commit()
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
        private const val FILE = "fam_ai_farmer"
        private const val KEY_NAME = "name"
        private const val KEY_MOBILE = "mobile"
        private const val KEY_ROLE = "role"
        private const val KEY_VERIFIED = "verified"
        private const val KEY_SESSION = "session"
        private const val KEY_NOTICE = "notice"
        private const val KEY_SHARE = "share"
        private const val KEY_CONSENT_AT = "consent_at"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val KEY_PENDING_DELETE = "pending_delete"
    }
}

package dev.merlin.android.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speichert Nextcloud-Zugangsdaten verschlüsselt im Android Keystore –
 * Äquivalent zu `CredentialsStore.swift` (dort: iOS-Keychain mit Shared-Access-Group).
 *
 * Eine Shared-Access-Group wie auf iOS ist auf Android nicht nötig: Hauptapp und
 * Share-Target (todo.md Abschnitt 10) laufen im selben Prozess/UID und teilen sich
 * dieselbe `EncryptedSharedPreferences`-Datei automatisch.
 *
 * Implementiert [CredentialsProvider] direkt, damit der `AuthInterceptor` ohne
 * Umweg über eine Zwischen-Implementierung die echten Zugangsdaten bekommt.
 */
@Singleton
class CredentialsStore @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialsProvider {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var nextcloudUrl: String
        get() = prefs.getString(KEY_NEXTCLOUD_URL, "") ?: ""
        set(value) {
            val trimmed = value.trim().trimEnd('/')
            prefs.edit().putString(KEY_NEXTCLOUD_URL, trimmed).apply()
        }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_USERNAME, value.trim()).apply()
        }

    var appPassword: String
        get() = prefs.getString(KEY_APP_PASSWORD, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_APP_PASSWORD, value.trim()).apply()
        }

    val isConfigured: Boolean
        get() = nextcloudUrl.isNotEmpty() && username.isNotEmpty() && appPassword.isNotEmpty()

    /** Äquivalent zu CredentialsStore.BackendKind (iOS). */
    enum class BackendKind { NEXTCLOUD, STANDALONE }

    /**
     * Steuert API-URL-Präfix (siehe [BaseUrlInterceptor]), Login-Flow-Start-URL
     * und ob Nextcloud-only-Features (TTS/SSE/Settings-Sync/Public-Share/
     * YouTube-Embed-Proxy) angezeigt werden. Default NEXTCLOUD für
     * Bestandsinstallationen ohne gespeicherten Wert - keine Migration nötig.
     * Wird bewusst NICHT von [clearCredentials] gelöscht, damit die zuletzt
     * gewählte Backend-Art als Vorbelegung für die nächste Anmeldung erhalten bleibt.
     */
    var backendKind: BackendKind
        get() = runCatching { BackendKind.valueOf(prefs.getString(KEY_BACKEND_KIND, null) ?: "") }
            .getOrDefault(BackendKind.NEXTCLOUD)
        set(value) {
            prefs.edit().putString(KEY_BACKEND_KIND, value.name).apply()
        }

    /** Nextcloud-only-Features - merlin-server liefert sie (noch) nicht. */
    val supportsNextcloudOnlyFeatures: Boolean
        get() = backendKind == BackendKind.NEXTCLOUD

    /** Fertiger `Authorization`-Header-Wert, oder `null` falls noch nicht konfiguriert. */
    val basicAuthHeader: String?
        get() {
            if (!isConfigured) return null
            val token = Base64.encodeToString("$username:$appPassword".toByteArray(), Base64.NO_WRAP)
            return "Basic $token"
        }

    /** Löscht gezielt nur die Anmeldedaten - backendKind bleibt als Vorbelegung für die nächste Anmeldung erhalten. */
    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_NEXTCLOUD_URL)
            .remove(KEY_USERNAME)
            .remove(KEY_APP_PASSWORD)
            .apply()
    }

    // MARK: – CredentialsProvider (für AuthInterceptor)

    override fun username(): String? = username.ifEmpty { null }
    override fun appPassword(): String? = appPassword.ifEmpty { null }

    private companion object {
        const val PREFS_FILE_NAME = "merlin_credentials"
        const val KEY_NEXTCLOUD_URL = "nextcloud_url"
        const val KEY_USERNAME = "username"
        const val KEY_APP_PASSWORD = "app_password"
        const val KEY_BACKEND_KIND = "backend_kind"
    }
}

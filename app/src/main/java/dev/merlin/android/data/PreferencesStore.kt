package dev.merlin.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.merlin.android.models.ArticleFilter
import dev.merlin.android.models.ProgressEdge
import dev.merlin.android.models.ReaderFont
import dev.merlin.android.models.ReaderTheme
import dev.merlin.android.network.Settings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.merlinDataStore by preferencesDataStore(name = "merlin_prefs")

/**
 * Äquivalent zu `PreferencesStore.swift`: Nutzer-Einstellungen (keine
 * Credentials – dafür [CredentialsStore]) über `DataStore<Preferences>`
 * statt `UserDefaults`. Anders als UserDefaults ist DataStore async/Flow-
 * basiert; daher gibt es hier zusätzlich `suspend fun`-"Snapshot"-Getter für
 * Aufrufer, die (wie das iOS-Original) einen synchron wirkenden Einzelwert
 * brauchen (z. B. [dev.merlin.android.data.ImageCacheService] vor einem Prefetch).
 */
@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val dataStore = context.merlinDataStore

    private object Keys {
        val DEFAULT_FILTER = stringPreferencesKey("default_filter")
        val READER_FONT_SIZE = intPreferencesKey("reader_font_size")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT = stringPreferencesKey("reader_font")
        val PROGRESS_EDGE = stringPreferencesKey("progress_edge")
        val LINE_HEIGHT = doublePreferencesKey("line_height")
        val ACCENT_PROGRESS_COLOR = stringPreferencesKey("accent_progress_color")
        val SAVE_PROGRESS = booleanPreferencesKey("save_progress")
        val RESUME_ON_OPEN = booleanPreferencesKey("resume_on_open")
        val PREFETCH_WIFI_ONLY = booleanPreferencesKey("prefetch_images_wifi_only")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val IS_CARD_VIEW = booleanPreferencesKey("is_card_view")
        val EXCLUDED_TAG_IDS = stringPreferencesKey("excluded_tag_ids")
        val REPORT_BACKEND_URL = stringPreferencesKey("report_backend_url")
        val CACHE_RETENTION_DAYS = intPreferencesKey("cache_retention_days")
        val NEEDS_SETTINGS_SYNC = booleanPreferencesKey("needs_settings_sync")
    }

    private fun positionKey(articleId: Int) = doublePreferencesKey("pos_$articleId")
    private fun progressKey(articleId: Int) = doublePreferencesKey("pct_$articleId")
    /** Epoch-Millis des letzten lokalen Progress-Writes – treibt Last-Write-Wins gegen den Server. */
    private fun progressTsKey(articleId: Int) = longPreferencesKey("pctts_$articleId")

    // MARK: – App-Einstellungen (reaktiv für Compose via collectAsState)

    val defaultFilter: Flow<ArticleFilter> = dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_FILTER]?.let { runCatching { ArticleFilter.valueOf(it) }.getOrNull() } ?: ArticleFilter.ALL
    }
    suspend fun setDefaultFilter(value: ArticleFilter) = dataStore.edit { it[Keys.DEFAULT_FILTER] = value.name }

    val readerFontSize: Flow<Int> = dataStore.data.map { it[Keys.READER_FONT_SIZE] ?: 17 }
    suspend fun setReaderFontSize(value: Int) = dataStore.edit { it[Keys.READER_FONT_SIZE] = value }

    val readerTheme: Flow<ReaderTheme> = dataStore.data.map { prefs ->
        prefs[Keys.READER_THEME]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: ReaderTheme.AUTO
    }
    suspend fun setReaderTheme(value: ReaderTheme) = dataStore.edit { it[Keys.READER_THEME] = value.name }

    val readerFont: Flow<ReaderFont> = dataStore.data.map { prefs ->
        prefs[Keys.READER_FONT]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() } ?: ReaderFont.SYSTEM
    }
    suspend fun setReaderFont(value: ReaderFont) = dataStore.edit { it[Keys.READER_FONT] = value.name }

    val progressEdge: Flow<ProgressEdge> = dataStore.data.map { prefs ->
        prefs[Keys.PROGRESS_EDGE]?.let { runCatching { ProgressEdge.valueOf(it) }.getOrNull() } ?: ProgressEdge.LEFT
    }
    suspend fun setProgressEdge(value: ProgressEdge) = dataStore.edit { it[Keys.PROGRESS_EDGE] = value.name }

    val lineHeight: Flow<Double> = dataStore.data.map { it[Keys.LINE_HEIGHT] ?: 1.6 }
    suspend fun setLineHeight(value: Double) = dataStore.edit { it[Keys.LINE_HEIGHT] = value }

    val accentProgressColorHex: Flow<String> = dataStore.data.map { it[Keys.ACCENT_PROGRESS_COLOR] ?: "#FF3B30" }
    suspend fun setAccentProgressColorHex(value: String) = dataStore.edit { it[Keys.ACCENT_PROGRESS_COLOR] = value }

    /** Default `true`: konservativ, vermeidet unerwarteten Datenverbrauch. */
    val saveProgress: Flow<Boolean> = dataStore.data.map { it[Keys.SAVE_PROGRESS] ?: true }
    suspend fun setSaveProgress(value: Boolean) = dataStore.edit { it[Keys.SAVE_PROGRESS] = value }

    val resumeOnOpen: Flow<Boolean> = dataStore.data.map { it[Keys.RESUME_ON_OPEN] ?: true }
    suspend fun setResumeOnOpen(value: Boolean) = dataStore.edit { it[Keys.RESUME_ON_OPEN] = value }

    val prefetchImagesOnWifiOnly: Flow<Boolean> = dataStore.data.map { it[Keys.PREFETCH_WIFI_ONLY] ?: true }
    suspend fun setPrefetchImagesOnWifiOnly(value: Boolean) = dataStore.edit { it[Keys.PREFETCH_WIFI_ONLY] = value }
    /** Synchroner Snapshot für Aufrufer außerhalb von Compose (z. B. [ImageCacheService]). */
    suspend fun isPrefetchImagesOnWifiOnly(): Boolean = prefetchImagesOnWifiOnly.first()

    val developerMode: Flow<Boolean> = dataStore.data.map { it[Keys.DEVELOPER_MODE] ?: false }
    suspend fun setDeveloperMode(value: Boolean) = dataStore.edit { it[Keys.DEVELOPER_MODE] = value }

    /**
     * Anzahl Tage, die Artikel offline (im lokalen Room-Cache) vorgehalten werden,
     * bevor [ArticleCacheService] sie automatisch entfernt – gilt einheitlich für
     * alle Artikel (auch Favoriten/Archiv), gezählt seit dem letzten lokalen Sync
     * (Upsert), nicht seit der Erstellung auf dem Server. 0 = gar nicht offline
     * vorhalten. Bewusst NICHT server-synchronisiert (wie [prefetchImagesOnWifiOnly])
     * – Speicherkapazität/-bedarf ist pro Gerät unterschiedlich. Default 30.
     */
    val cacheRetentionDays: Flow<Int> = dataStore.data.map { it[Keys.CACHE_RETENTION_DAYS] ?: 30 }
    suspend fun setCacheRetentionDays(value: Int) = dataStore.edit { it[Keys.CACHE_RETENTION_DAYS] = value }
    /** Synchroner Snapshot für Aufrufer außerhalb von Compose (z. B. [ArticleCacheService]). */
    suspend fun getCacheRetentionDays(): Int = cacheRetentionDays.first()

    /**
     * Äquivalent zu iOS' `@AppStorage("merlinIsCardView")` (ArticleListView.swift):
     * lokale, NICHT server-synchronisierte Anzeigepräferenz (List- vs. Card-Ansicht).
     * Default `true` (Kartenansicht) – bewusste Abweichung vom iOS-Default (dort `false`),
     * auf expliziten Wunsch des Nutzers für Android.
     */
    val isCardView: Flow<Boolean> = dataStore.data.map { it[Keys.IS_CARD_VIEW] ?: true }
    suspend fun setIsCardView(value: Boolean) = dataStore.edit { it[Keys.IS_CARD_VIEW] = value }

    /** Tag-IDs, deren Artikel in der Artikelliste ausgeblendet werden. */
    val excludedTagIds: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[Keys.EXCLUDED_TAG_IDS]?.let { runCatching { json.decodeFromString<Set<Int>>(it) }.getOrNull() } ?: emptySet()
    }
    suspend fun setExcludedTagIds(value: Set<Int>) = dataStore.edit { it[Keys.EXCLUDED_TAG_IDS] = json.encodeToString(value) }

    // MARK: – Lesepositionen
    //
    // Synchronisiert wird der Fortschritt als Fraktion (0..1), nicht der
    // Pixel-Offset: Pixel sind gerätespezifisch (Bildschirmbreite/Schriftgröße),
    // die Fraktion ist portabel. Zusätzlich wird ein Epoch-Millis-Zeitstempel
    // gespeichert, der die geräteübergreifende Last-Write-Wins-Auflösung gegen
    // den Server treibt (siehe ArticleReaderViewModel).

    /** Fortschritt 0–1 (für Card-Anzeige UND Wiederherstellung der Leseposition). */
    suspend fun savedScrollProgress(articleId: Int): Float = dataStore.data.first()[progressKey(articleId)]?.toFloat() ?: 0f

    /** Epoch-Millis des letzten lokalen Progress-Writes (0 = noch nie auf diesem Gerät gespeichert). */
    suspend fun savedScrollTimestamp(articleId: Int): Long = dataStore.data.first()[progressTsKey(articleId)] ?: 0L

    /** Schreibt Fortschritt + Zeitstempel atomar in einem DataStore-Edit. */
    suspend fun saveScrollProgress(articleId: Int, progress: Float, updatedAt: Long) = dataStore.edit {
        it[progressKey(articleId)] = progress.toDouble()
        it[progressTsKey(articleId)] = updatedAt
    }

    suspend fun clearReadingPositions() = dataStore.edit { prefs ->
        val toRemove = prefs.asMap().keys.filter {
            it.name.startsWith("pos_") || it.name.startsWith("pct_") || it.name.startsWith("pctts_")
        }
        toRemove.forEach { prefs.remove(it) }
    }

    // MARK: – Server-Sync

    /**
     * Merkt sich, dass ein `updateSettings`-Push wegen fehlender Verbindung fehlgeschlagen ist
     * und noch aussteht – gelesen/geschrieben von [SettingsSyncQueue]. Persistiert (statt
     * In-Memory), damit ein Prozess-Tod zwischen Fehlschlag und WorkManager-Retry das Flag nicht
     * verliert.
     */
    suspend fun needsSettingsSync(): Boolean = dataStore.data.first()[Keys.NEEDS_SETTINGS_SYNC] ?: false
    suspend fun setNeedsSettingsSync(value: Boolean) = dataStore.edit { it[Keys.NEEDS_SETTINGS_SYNC] = value }

    /** Wendet ein vom Server geladenes Settings-Objekt an (Server gewinnt). */
    suspend fun loadFromServer(settings: Settings) {
        dataStore.edit { prefs ->
            runCatching { ReaderTheme.valueOf(settings.theme.uppercase()) }.getOrNull()?.let { prefs[Keys.READER_THEME] = it.name }
            prefs[Keys.READER_FONT] = ReaderFont.fromServerValue(settings.fontFamily).name
            settings.fontSize.toIntOrNull()?.let { prefs[Keys.READER_FONT_SIZE] = it }
            settings.lineHeight.toDoubleOrNull()?.let { prefs[Keys.LINE_HEIGHT] = it }
            prefs[Keys.PROGRESS_EDGE] = ProgressEdge.fromServerValue(settings.progressEdge).name
            prefs[Keys.DEFAULT_FILTER] = ArticleFilter.fromServerValue(settings.defaultView).name
            prefs[Keys.SAVE_PROGRESS] = settings.saveProgress == "1" || settings.saveProgress == "true"
            prefs[Keys.RESUME_ON_OPEN] = settings.resumeOnOpen == "1" || settings.resumeOnOpen == "true"
            if (settings.accentColor.isNotEmpty()) prefs[Keys.ACCENT_PROGRESS_COLOR] = settings.accentColor
            // Server gewinnt explizit auch hier: ein leerer Wert (URL gelöscht) muss
            // den lokalen Cache überschreiben, sonst bliebe ein veralteter Wert stehen.
            prefs[Keys.REPORT_BACKEND_URL] = settings.reportBackendUrl
        }
    }

    /** Serialisiert alle sync-fähigen Einstellungen für den Server-PUT. */
    suspend fun toServerSettings(): Settings {
        val prefs = dataStore.data.first()
        val theme = prefs[Keys.READER_THEME]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: ReaderTheme.AUTO
        val font = prefs[Keys.READER_FONT]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() } ?: ReaderFont.SYSTEM
        val edge = prefs[Keys.PROGRESS_EDGE]?.let { runCatching { ProgressEdge.valueOf(it) }.getOrNull() } ?: ProgressEdge.LEFT
        val filter = prefs[Keys.DEFAULT_FILTER]?.let { runCatching { ArticleFilter.valueOf(it) }.getOrNull() } ?: ArticleFilter.ALL
        return Settings(
            theme = theme.serverValue,
            fontSize = (prefs[Keys.READER_FONT_SIZE] ?: 17).toString(),
            fontFamily = font.serverValue,
            lineHeight = (prefs[Keys.LINE_HEIGHT] ?: 1.6).toString(),
            defaultView = filter.serverValue,
            progressEdge = edge.serverValue,
            saveProgress = if (prefs[Keys.SAVE_PROGRESS] ?: true) "1" else "0",
            resumeOnOpen = if (prefs[Keys.RESUME_ON_OPEN] ?: true) "1" else "0",
            accentColor = prefs[Keys.ACCENT_PROGRESS_COLOR] ?: "#FF3B30",
            // Ohne diesen Cache würde hier immer der Settings()-Klassendefault ""
            // gesendet (encodeDefaults = true in NetworkModule.kt) und so die vom
            // Web-UI gesetzte reportBackendUrl bei jedem Sync serverseitig leeren.
            reportBackendUrl = prefs[Keys.REPORT_BACKEND_URL] ?: "",
        )
    }
}

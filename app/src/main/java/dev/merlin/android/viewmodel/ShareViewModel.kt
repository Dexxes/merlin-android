package dev.merlin.android.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.merlin.android.models.Tag
import dev.merlin.android.models.TagTree
import dev.merlin.android.network.CreateArticleRequest
import dev.merlin.android.network.CreateTagRequest
import dev.merlin.android.network.CredentialsStore
import dev.merlin.android.network.MerlinApi
import dev.merlin.android.share.CompressionOption
import dev.merlin.android.share.FileUploader
import dev.merlin.android.share.ImageCompression
import dev.merlin.android.share.ImageCompressor
import dev.merlin.android.share.SharedFile
import java.io.File
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * Was eine andere App an Merlin teilt: Text (darin eine Web-Adresse) und/oder
 * Dateien (`EXTRA_STREAM`), siehe [dev.merlin.android.ui.share.ShareActivity].
 */
data class SharedContent(
    val text: String?,
    val streams: List<Uri>,
    /** MIME-Typ des Intents, Fallback, wenn der Content-Provider keinen liefert. */
    val mimeType: String?,
)

/**
 * Äquivalent zu `ShareViewController.swift` (MerlinShare-Extension), Verhalten 1:1
 * übernommen: Eine Web-Adresse wird als Artikel gespeichert; ohne Adresse landen
 * geteilte Dateien (Fotos, Videos, Audio, PDFs, Sonstiges) im Nextcloud-Ordner
 * „Merlin Dateien“ (nur merlin-nextcloud). Vor dem Speichern lassen sich Tags aus
 * dem Tag-Baum wählen (ein Unter-Tag wählt seine Eltern-Tags mit) oder neu
 * eintippen; Fotos lassen sich in drei Stufen komprimieren.
 *
 * Anders als iOS – wo die Extension in einem eigenen, eingeschränkten Prozess läuft –
 * ist `ShareActivity` eine normale Activity im selben App-Prozess: [CredentialsStore]
 * und [MerlinApi] (Hilt-Singletons) werden direkt injiziert.
 *
 * **Abweichungen von iOS:**
 * - Noch nicht angemeldet: iOS zeigt drei Textfelder (URL/Benutzername/App-Passwort),
 *   weil eine Extension keinen Login Flow starten kann. Android zeigt stattdessen den
 *   bestehenden `OnboardingScreen` (Login Flow v2), wie im Rest der App.
 * - Keine Texterkennung in Fotos (iOS: Vision). Auf Android ginge das nur mit ML Kit,
 *   einem Google-Baustein, der laut Datenschutzerklärung nicht in die App soll.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MerlinApi,
    private val credentialsStore: CredentialsStore,
    private val json: Json,
) : ViewModel() {

    enum class Mode { ONBOARDING, EXTRACTING, STAGING, SAVING, UPLOADING, ERROR, RATE_LIMITED }

    /** Fortschritt beim Hochladen: Datei [index] (ab 0) von [total], [fraction] über alle Dateien. */
    data class UploadProgress(val index: Int, val total: Int, val fraction: Double)

    private val _mode = MutableStateFlow(if (credentialsStore.isConfigured) Mode.EXTRACTING else Mode.ONBOARDING)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _pendingUrl = MutableStateFlow("")
    val pendingUrl: StateFlow<String> = _pendingUrl.asStateFlow()

    /** Geteilte Dateien, wenn der Inhalt keine Web-Adresse enthält. */
    private val _pendingFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val pendingFiles: StateFlow<List<SharedFile>> = _pendingFiles.asStateFlow()

    /** `null`, solange die Tags noch laden. */
    private val _availableTags = MutableStateFlow<List<Tag>?>(null)
    val availableTags: StateFlow<List<Tag>?> = _availableTags.asStateFlow()

    private val _selectedTagIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedTagIds: StateFlow<Set<Int>> = _selectedTagIds.asStateFlow()

    /** Zeigt die Komprimierungs-Kacheln (mindestens ein Foto unter den Dateien). */
    private val _hasPhotos = MutableStateFlow(false)
    val hasPhotos: StateFlow<Boolean> = _hasPhotos.asStateFlow()

    private val _selectedCompression = MutableStateFlow(loadCompressionChoice())
    val selectedCompression: StateFlow<ImageCompression> = _selectedCompression.asStateFlow()

    /** Fertig berechnete Stufen; fehlt eine, zeigt ihre Kachel noch den Ladekreis. */
    private val _compressionOptions = MutableStateFlow<Map<ImageCompression, CompressionOption>>(emptyMap())
    val compressionOptions: StateFlow<Map<ImageCompression, CompressionOption>> = _compressionOptions.asStateFlow()

    /** `null` = „Wird vorbereitet…“ (die gewählte Stufe wird noch berechnet). */
    private val _uploadProgress = MutableStateFlow<UploadProgress?>(null)
    val uploadProgress: StateFlow<UploadProgress?> = _uploadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** Von der UI beobachtet: wird `true`, sobald sich die Activity schließen soll. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private var didExtract = false

    /**
     * Eine Berechnung je Stufe, nacheinander (Speicher) und die gewählte zuerst.
     * Das Hochladen wartet auf die gewählte.
     */
    private val compressionJobs = mutableMapOf<ImageCompression, Deferred<CompressionOption>>()

    private var tagsLoader: Job? = null

    /** Vom `OnboardingScreen` aufgerufen, sobald Login Flow v2 erfolgreich war. */
    fun onLoginSuccess() {
        _mode.value = Mode.EXTRACTING
    }

    // ── Inhalt auslesen ─────────────────────────────────────────────────────

    /**
     * Wie `extractContent()`: Eine Web-Adresse wird als Artikel gespeichert; ohne
     * Adresse gehen geteilte Dateien nach „Merlin Dateien“.
     */
    fun extractContent(shared: SharedContent) {
        if (_mode.value != Mode.EXTRACTING || didExtract) return
        didExtract = true
        val url = shared.text?.let { extractFirstUrl(it) }
        if (url != null) {
            showStaging(url)
            return
        }
        if (shared.streams.isEmpty()) {
            showError("Keine URL im geteilten Inhalt gefunden.")
            return
        }
        if (credentialsStore.backendKind == CredentialsStore.BackendKind.STANDALONE) {
            showError("Dateien lassen sich nur mit Nextcloud speichern, nicht mit dem Merlin-Server.")
            return
        }
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { copyToCache(shared) }
            if (files.isEmpty()) {
                showError("Die geteilte Datei konnte nicht gelesen werden.")
            } else {
                showStaging(files)
            }
        }
    }

    private fun showStaging(url: String) {
        _pendingUrl.value = url
        _pendingFiles.value = emptyList()
        _hasPhotos.value = false
        _selectedTagIds.value = emptySet()
        _mode.value = Mode.STAGING
        loadTags()
    }

    private fun showStaging(files: List<SharedFile>) {
        _pendingUrl.value = ""
        _pendingFiles.value = files
        val photos = files.any(ImageCompressor::isCompressible)
        _hasPhotos.value = photos
        if (photos) startCompression(files)
        _selectedTagIds.value = emptySet()
        _mode.value = Mode.STAGING
        loadTags()
    }

    /**
     * Kopiert die geteilten Dateien nach `cacheDir/merlin-share/<uuid>/<name>`, solange
     * die Leseberechtigung der teilenden App gilt.
     */
    private fun copyToCache(shared: SharedContent): List<SharedFile> =
        shared.streams.distinct().mapNotNull { uri -> runCatching { copyToCache(uri, shared.mimeType) }.getOrNull() }

    private fun copyToCache(uri: Uri, fallbackMimeType: String?): SharedFile? {
        val resolver = context.contentResolver
        var displayName: String? = null
        if (uri.scheme == "content") {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) displayName = cursor.getString(column)
                }
            }
        }
        val mimeType = resolver.getType(uri)
            ?: fallbackMimeType?.takeIf { !it.contains('*') }
            ?: displayName?.substringAfterLast('.', "")?.let { ext ->
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            }
            ?: "application/octet-stream"

        var name = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Datei"
        name = name.replace('/', '-')
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (ext != null && name.substringAfterLast('.', "").isEmpty()) name += ".$ext"

        val folder = File(shareTemporaryFolder(), UUID.randomUUID().toString())
        if (!folder.mkdirs()) return null
        val target = File(folder, name)
        val input = resolver.openInputStream(uri) ?: return null
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return SharedFile(file = target, name = name, mimeType = mimeType, size = target.length())
    }

    private fun shareTemporaryFolder() = File(context.cacheDir, "merlin-share")

    private fun removeTemporaryFiles() {
        shareTemporaryFolder().deleteRecursively()
    }

    // ── Tags ────────────────────────────────────────────────────────────────

    private fun loadTags() {
        if (tagsLoader != null) return
        tagsLoader = viewModelScope.launch {
            _availableTags.value = runCatching { api.listTags() }.getOrDefault(emptyList())
        }
    }

    /** Ein Unter-Tag wählt seine Eltern-Tags mit aus, Abwählen nimmt die Unter-Tags mit. */
    fun toggleTag(tagId: Int) {
        val tree = TagTree(_availableTags.value.orEmpty())
        _selectedTagIds.update { tree.toggling(tagId, it) }
    }

    /**
     * Löst kommagetrennte, neu eingetippte Tag-Namen zu IDs auf. Wartet dafür auf die
     * Tag-Liste, damit keine doppelten Tags entstehen, und legt fehlende per POST an.
     */
    private suspend fun resolveTagIds(names: List<String>): List<Int> {
        if (names.isEmpty()) return emptyList()
        tagsLoader?.join()
        val cached = _availableTags.value.orEmpty().ifEmpty {
            runCatching { api.listTags() }.getOrDefault(emptyList()).also { _availableTags.value = it }
        }
        val ids = mutableListOf<Int>()
        for (name in names) {
            val match = cached.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (match != null) {
                ids.add(match.id)
            } else {
                runCatching { api.createTag(CreateTagRequest(name = name)) }.getOrNull()?.let { ids.add(it.id) }
            }
        }
        return ids
    }

    // ── Komprimierung ───────────────────────────────────────────────────────

    private fun startCompression(files: List<SharedFile>) {
        _compressionOptions.value = emptyMap()
        val selected = _selectedCompression.value
        val order = listOf(selected) + ImageCompression.entries.filter { it != selected }
        var previous: Job? = null
        for (level in order) {
            val before = previous
            val job = viewModelScope.async(Dispatchers.Default) {
                before?.join()
                ImageCompressor.option(files, level)
            }
            compressionJobs[level] = job
            previous = job
            viewModelScope.launch {
                val option = runCatching { job.await() }.getOrNull() ?: return@launch
                _compressionOptions.update { it + (level to option) }
            }
        }
    }

    fun selectCompression(level: ImageCompression) {
        _selectedCompression.value = level
        prefs().edit().putString(KEY_COMPRESSION, level.name).apply()
    }

    private fun loadCompressionChoice(): ImageCompression =
        runCatching { ImageCompression.valueOf(prefs().getString(KEY_COMPRESSION, null) ?: "") }
            .getOrDefault(ImageCompression.LIGHT)

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Die geteilten Dateien in der gewählten Stufe; wartet, bis sie berechnet ist. */
    private suspend fun filesForUpload(): List<SharedFile> {
        val job = compressionJobs[_selectedCompression.value]
        if (!_hasPhotos.value || job == null) return _pendingFiles.value
        return runCatching { job.await().files }.getOrDefault(_pendingFiles.value)
    }

    // ── Speichern ───────────────────────────────────────────────────────────

    /** Äquivalent zu `confirmSave()`. */
    fun confirmSave(newTagNamesRaw: String) {
        val url = _pendingUrl.value
        val hasFiles = _pendingFiles.value.isNotEmpty()
        if (url.isEmpty() && !hasFiles) {
            finish()
            return
        }
        val newNames = newTagNamesRaw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val existing = _selectedTagIds.value

        if (hasFiles) {
            // Die anderen Stufen werden nicht mehr gebraucht.
            compressionJobs.forEach { (level, job) -> if (level != _selectedCompression.value) job.cancel() }
            _uploadProgress.value = null
            _mode.value = Mode.UPLOADING
        } else {
            _mode.value = Mode.SAVING
        }
        viewModelScope.launch {
            val allIds = (existing + resolveTagIds(newNames)).toList()
            if (hasFiles) saveFiles(filesForUpload(), allIds) else saveArticle(url, allIds)
        }
    }

    private suspend fun saveArticle(url: String, tagIds: List<Int>) {
        try {
            api.createArticle(CreateArticleRequest(url = url, tagIds = tagIds))
            finish()
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> showError(ERROR_AUTH)
                404 -> showError("Merlin-App auf diesem Server nicht gefunden.")
                429 -> showRateLimited(ERROR_RATE_LIMITED)
                else -> {
                    val detail = serverMessage(runCatching { e.response()?.errorBody()?.string() }.getOrNull())
                    showError("Serverfehler (HTTP ${e.code()})." + (detail?.let { "\n$it" } ?: ""))
                }
            }
        } catch (e: Exception) {
            showError("Netzwerkfehler: ${e.message ?: "unbekannt"}")
        }
    }

    private suspend fun saveFiles(files: List<SharedFile>, tagIds: List<Int>) {
        val authorization = credentialsStore.basicAuthHeader
        if (authorization == null) {
            showError(ERROR_AUTH)
            return
        }
        val uploader = FileUploader(api, credentialsStore.nextcloudUrl, authorization, json)
        val totalBytes = maxOf(1L, files.sumOf { it.size }).toDouble()
        var uploadedBytes = 0L

        for ((index, file) in files.withIndex()) {
            val before = uploadedBytes.toDouble()
            _uploadProgress.value = UploadProgress(index, files.size, before / totalBytes)
            try {
                uploader.upload(file, tagIds) { fraction ->
                    _uploadProgress.value = UploadProgress(
                        index,
                        files.size,
                        ((before + fraction * file.size) / totalBytes).coerceIn(0.0, 1.0),
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { removeTemporaryFiles() }
                showUploadError(e, file)
                return
            }
            uploadedBytes += file.size
        }
        withContext(Dispatchers.IO) { removeTemporaryFiles() }
        finish()
    }

    private fun showUploadError(error: Exception, file: SharedFile) {
        val failed = "Hochladen fehlgeschlagen: ${file.name}"
        val message = when (error) {
            is FileUploader.UploadError.Http -> when (error.code) {
                401 -> ERROR_AUTH
                429 -> ERROR_RATE_LIMITED
                else -> "$failed\nServerfehler (HTTP ${error.code})." + (error.serverMessage?.let { "\n$it" } ?: "")
            }
            is FileUploader.UploadError.UnreadableFile -> "Die geteilte Datei konnte nicht gelesen werden."
            is FileUploader.UploadError.InvalidResponse -> "$failed\nKeine Antwort vom Server."
            else -> "$failed\nNetzwerkfehler: ${error.message ?: "unbekannt"}"
        }
        showError(message)
    }

    private fun serverMessage(body: String?): String? = body?.let {
        runCatching { (json.parseToJsonElement(it) as? JsonObject)?.get("error")?.jsonPrimitive?.content }.getOrNull()
    }?.takeIf { it.isNotEmpty() }

    // ── Zustände ────────────────────────────────────────────────────────────

    private fun showError(message: String) {
        _statusMessage.value = message
        _mode.value = Mode.ERROR
    }

    private fun showRateLimited(message: String) {
        _statusMessage.value = message
        _mode.value = Mode.RATE_LIMITED
    }

    /** Nach der Rate-Limit-Anzeige zurück zur Staging-Ansicht, ohne Tags neu zu laden. */
    fun backToStaging() {
        _mode.value = Mode.STAGING
    }

    fun finish() {
        compressionJobs.values.forEach { it.cancel() }
        _finished.value = true
    }

    override fun onCleared() {
        compressionJobs.values.forEach { it.cancel() }
        // Nicht auf viewModelScope (wird gerade beendet); kurzer Löschvorgang im Cache.
        Thread { removeTemporaryFiles() }.start()
        super.onCleared()
    }

    companion object {
        private const val PREFS_NAME = "merlin_share"
        private const val KEY_COMPRESSION = "shareImageCompression"
        private const val ERROR_AUTH = "Authentifizierung fehlgeschlagen.\nÜberprüfe deine Zugangsdaten in der Merlin-App."
        private const val ERROR_RATE_LIMITED =
            "Der Server begrenzt aktuell die Anfragen.\nBitte warte einen Moment und versuche es erneut."

        /**
         * Findet die erste http(s)-URL in einem beliebigen Text – Äquivalent zu
         * `extractFirstURL` (Swift), das dort `NSDataDetector` nutzt. Android hat
         * keinen direkten Link-Detector ohne UI-Kontext, daher ein Pattern analog
         * zum iOS-Fallback-Regex.
         */
        private val URL_PATTERN: Pattern = Pattern.compile("https?://\\S+")

        fun extractFirstUrl(text: String): String? {
            val matcher = URL_PATTERN.matcher(text)
            return if (matcher.find()) matcher.group() else null
        }
    }
}

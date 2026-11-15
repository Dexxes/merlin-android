package dev.merlin.android.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.merlin.android.di.ApplicationScope
import dev.merlin.android.models.Article
import dev.merlin.android.models.FileMetadataParser
import dev.merlin.android.network.MerlinApi
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Teilen eines Eintrags: Einträge aus „Merlin Dateien“ geben die Datei selbst weiter (Foto an
 * Galerie, Telegram, Signal, PDF an Dateien …) statt ihres Nextcloud-Links, alles andere den Link.
 * Port von `FileShareItem`/`FileSharePresenter` (merlin-ios).
 *
 * Die Datei wird zuerst über den signierten Download-Link aus dem Metadaten-Abschnitt geladen
 * (keine Zugangsdaten nötig, deshalb ein eigener OkHttp-Client ohne Auth-/BaseUrl-Interceptor),
 * unter dem Namen des Eintrags im Cache abgelegt und per [FileProvider] als `content://`-URI
 * geteilt. Fehlt der Inhalt (Listen-Einträge ohne `content`), wird der Artikel vorher einzeln
 * abgerufen. Der Download läuft im App-Scope, damit er das Wegscrollen einer Listenzeile übersteht;
 * Ladedialog, Fehlermeldung und Teilen-Dialog zeigt [FileShareHost] in der `MainActivity`.
 */
@Singleton
class FileShareService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MerlinApi,
    @ApplicationScope private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State
        data object Loading : State
        data object Failed : State
        /** Fertig geladen; [FileShareHost] öffnet den Teilen-Dialog und setzt auf [Idle] zurück. */
        data class Ready(val uri: Uri, val mimeType: String, val title: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Teilt [article]: bei Datei-Einträgen die Datei, sonst den Link. */
    fun share(article: Article, activityContext: Context) {
        if (article.fileId == null) {
            shareLink(article, activityContext)
            return
        }
        job?.cancel()
        _state.value = State.Loading
        job = scope.launch {
            _state.value = try {
                val file = download(article)
                State.Ready(
                    uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                    mimeType = mimeType(article, file),
                    title = article.displayTitle,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Failed
            }
        }
    }

    /** „Abbrechen“ im Ladedialog bzw. Schließen der Fehlermeldung oder nach dem Teilen. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    private suspend fun download(article: Article): File = withContext(Dispatchers.IO) {
        // Dateien früherer Teilvorgänge. Nicht direkt nach dem Teilen gelöscht, weil manche Ziele
        // die Datei noch lesen, wenn der Teilen-Dialog schon zu ist (wie unter iOS).
        downloadsFolder().deleteRecursively()
        val content = article.content?.takeIf { FileMetadataParser.downloadUrl(it) != null }
            ?: api.getArticle(article.id).content
        val downloadUrl = content?.let(FileMetadataParser::downloadUrl)
            ?: throw IOException("Kein Download-Link im Inhalt (merlin-nextcloud ≥ 1.0.18 nötig)")

        // Eigener Ordner je Vorgang unter dem Namen des Eintrags, damit das Ziel „IMG_1234.jpg“
        // statt eines Zufallsnamens erhält und den Typ an der Endung erkennt.
        val folder = File(downloadsFolder(), UUID.randomUUID().toString()).apply { mkdirs() }
        val target = File(folder, fileName(article))
        client.newCall(Request.Builder().url(downloadUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            target.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
        target
    }

    private fun downloadsFolder() = File(context.cacheDir, DOWNLOADS_FOLDER)

    private fun fileName(article: Article): String {
        val name = article.title.replace("/", "-").trim().ifEmpty { "file" }
        // Ohne Endung erkennt das Ziel den Typ nicht; dann aus dem MIME-Typ ergänzen.
        if (name.substringAfterLast('.', "").isNotEmpty()) return name
        val ext = article.fileMime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext != null) "$name.$ext" else name
    }

    private fun mimeType(article: Article, file: File): String =
        article.fileMime?.takeIf { it.contains('/') }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

    companion object {
        /** Unterordner im Cache, in `res/xml/file_paths.xml` für den [FileProvider] freigegeben. */
        const val DOWNLOADS_FOLDER = "merlin-share-out"

        /** System-Teilen-Dialog mit der Artikel-URL. */
        fun shareLink(article: Article, activityContext: Context) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, article.url)
                putExtra(Intent.EXTRA_SUBJECT, article.displayTitle)
            }
            activityContext.startActivity(Intent.createChooser(intent, null))
        }

        /** System-Teilen-Dialog mit einer geladenen Datei. */
        fun shareFile(ready: State.Ready, activityContext: Context) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = ready.mimeType
                putExtra(Intent.EXTRA_STREAM, ready.uri)
                // ClipData, damit die Leseberechtigung auch über den Chooser hinweg beim Ziel ankommt.
                clipData = android.content.ClipData.newRawUri(ready.title, ready.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activityContext.startActivity(
                Intent.createChooser(intent, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FileShareEntryPoint {
    fun fileShareService(): FileShareService
}

/**
 * Teilen-Aktion für Zeilen, Karten und den Reader: Datei-Einträge teilen die Datei, alles
 * andere den Link (Äquivalent zu `ArticleShareLink` in merlin-ios).
 */
@Composable
fun rememberArticleSharer(): (Article) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val service = EntryPointAccessors.fromApplication(context.applicationContext, FileShareEntryPoint::class.java)
            .fileShareService()
        val share: (Article) -> Unit = { article -> service.share(article, context) }
        share
    }
}

package dev.merlin.android.viewmodel

import dev.merlin.android.models.Comment
import dev.merlin.android.models.CommentAnchor
import dev.merlin.android.models.CommentApiException
import dev.merlin.android.models.CommentCreateRequest
import dev.merlin.android.models.CommentErrorBody
import dev.merlin.android.models.CommentUpdateRequest
import dev.merlin.android.models.CommentsPayload
import dev.merlin.android.models.Highlight
import dev.merlin.android.network.CommentStreamClient
import dev.merlin.android.network.MerlinApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Kommentare und Markierungen des offenen Artikels, live gehalten über den Push-Kanal des Servers
 * ([CommentStreamClient]). Äquivalent zu `CommentStore.swift`.
 *
 * Schreibt ein Gast hinter dem Share-Link (oder der Besitzer auf einem anderen Gerät) einen
 * Kommentar, schickt der Server ihn innerhalb von etwa einer Sekunde über die offene Verbindung;
 * gepollt wird nicht. Die Verbindung läuft ~50 s und wird dann mit der letzten Änderungsmarke neu
 * aufgebaut. Ohne Share-Link beendet der Server den Kanal (`closed`) – dann kann ohnehin niemand
 * außer dem Besitzer schreiben; [reconnect] nach dem Schließen des Share-Dialogs öffnet ihn wieder.
 * Im Hintergrund trennt der Reader die Verbindung ([stop]) und baut sie beim Zurückkehren neu auf.
 *
 * merlin-server kennt keine Kommentare: dort bleibt [isAvailable] false und die Oberfläche blendet
 * alles Kommentarbezogene aus.
 */
class CommentStore(
    private val api: MerlinApi,
    private val streamClient: CommentStreamClient,
    private val scope: CoroutineScope,
    private val json: Json,
) {
    data class State(
        /** Thread-Wurzeln (Antworten in `replies`), Reihenfolge wie vom Server. */
        val threads: List<Comment> = emptyList(),
        /** Alle Markierungen des Artikels, auch die von Gästen und nur kommentierte Stellen. */
        val highlights: List<Highlight> = emptyList(),
        /** true, sobald der Server Kommentare unterstützt (erster Abruf geklappt). */
        val isAvailable: Boolean = false,
        /** Server kann Markierungen ändern (`PUT api/highlights/{id}`). */
        val canUpdateHighlights: Boolean = false,
        /** Zählt jede inhaltliche Änderung hoch. */
        val revision: Int = 0,
    ) {
        /** Sichtbare Kommentare insgesamt (gelöschte Platzhalter zählen nicht). */
        val totalCount: Int get() = threads.sumOf { it.visibleCount }

        /** Kommentare je Markierung (Zähler im Text, Rückfrage beim Entfernen). */
        val countsByHighlight: Map<Int, Int>
            get() = buildMap {
                for (thread in threads) {
                    val id = thread.highlightId ?: continue
                    put(id, (get(id) ?: 0) + thread.visibleCount)
                }
            }

        fun highlight(id: Int): Highlight? = highlights.firstOrNull { it.id == id }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var signature = ""
    /** `generatedAt` des gezeigten Stands; ältere Stände werden verworfen. */
    private var generatedAt = 0L
    private var articleId: Int? = null
    private var streamJob: Job? = null
    private val applyMutex = Mutex()

    // MARK: – Verbindung

    fun start(articleId: Int) {
        stop()
        if (this.articleId != articleId) {
            signature = ""
            generatedAt = 0
            _state.value = State(revision = _state.value.revision + 1)
        }
        this.articleId = articleId
        streamJob = scope.launch { runLoop(articleId) }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    /** Nach Änderungen am Share-Link: Kanal neu öffnen (er wurde evtl. mit `closed` beendet). */
    fun reconnect() {
        val id = articleId ?: return
        start(id)
    }

    private suspend fun runLoop(articleId: Int) {
        var backoffMs = 1_000L
        // Beim Öffnen (und nach jedem Verbindungsabbruch) einmal direkt abfragen, statt sich
        // darauf zu verlassen, dass der Kanal den verpassten Stand sofort schickt.
        var needsFetch = true
        while (scope.isActive) {
            if (needsFetch || signature.isEmpty()) {
                try {
                    applyPayload(api.getComments(articleId))
                    _state.value = _state.value.copy(isAvailable = true)
                    needsFetch = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isUnsupported(e)) return
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000)
                    continue
                }
            }
            try {
                var closed = false
                streamClient.stream(articleId, signature).collect { event ->
                    when (event) {
                        is CommentStreamClient.Event.Update -> applyPayload(event.payload)
                        CommentStreamClient.Event.Closed -> closed = true
                    }
                }
                if (closed) return
                backoffMs = 1_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isUnsupported(e)) return
                // Netz weg: mit wachsender Pause neu verbinden; die Marke sorgt dafür, dass
                // Verpasstes beim ersten Ereignis nachkommt.
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000)
                needsFetch = true
            }
        }
    }

    private fun isUnsupported(e: Exception): Boolean =
        (e is HttpException && e.code() == 404) ||
            (e is CommentStreamClient.StreamException && e.code == 404)

    private suspend fun applyPayload(payload: CommentsPayload) = applyMutex.withLock {
        // Älter als der gezeigte Stand (überholte Antwort oder verspätetes Push-Ereignis):
        // verwerfen, sonst taucht Gelöschtes kurz wieder auf.
        payload.generatedAt?.let { at ->
            if (at < generatedAt) return@withLock
            generatedAt = at
        }
        val current = _state.value
        val canUpdate = payload.highlightUpdate == true
        if (payload.signature == signature) {
            if (current.canUpdateHighlights != canUpdate) _state.value = current.copy(canUpdateHighlights = canUpdate)
            return@withLock
        }
        signature = payload.signature
        _state.value = current.copy(
            threads = payload.comments,
            highlights = payload.highlights,
            canUpdateHighlights = canUpdate,
            revision = current.revision + 1,
        )
    }

    /** Sofort neu laden (nach eigenen Änderungen – kommt bei offenem Kanal ohnehin, bei geschlossenem nicht). */
    suspend fun refresh() {
        val id = articleId ?: return
        val payload = runCatching { api.getComments(id) }.getOrNull() ?: return
        _state.value = _state.value.copy(isAvailable = true)
        applyPayload(payload)
    }

    // MARK: – Schreiben

    /**
     * Mit [anchor] legt der Server die kommentierte Stelle gleich mit an; ihre id steht dann in
     * `highlightId` des Ergebnisses.
     *
     * @throws CommentApiException mit dem Fehlercode des Servers
     */
    suspend fun create(body: String, highlightId: Int?, parentId: Int?, anchor: CommentAnchor? = null): Comment? {
        val id = articleId ?: return null
        val saved = mapErrors {
            api.createComment(
                id,
                CommentCreateRequest(body = body, highlightId = highlightId, parentId = parentId, anchor = anchor),
            )
        }
        refresh()
        return saved
    }

    suspend fun update(commentId: Int, body: String) {
        mapErrors { api.updateComment(commentId, CommentUpdateRequest(body)) }
        refresh()
    }

    suspend fun delete(commentId: Int) {
        mapErrors {
            val response = api.deleteComment(commentId)
            if (!response.isSuccessful) throw HttpException(response)
        }
        refresh()
    }

    /** HTTP-Fehler mit `{error: code}` in [CommentApiException] übersetzen (z. B. "zu lang" statt "HTTP 400"). */
    private suspend fun <T> mapErrors(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val code = runCatching {
            e.response()?.errorBody()?.string()?.let { json.decodeFromString<CommentErrorBody>(it).error }
        }.getOrNull().orEmpty()
        throw CommentApiException(code.ifEmpty { "http_${e.code()}" }, e.code())
    }
}

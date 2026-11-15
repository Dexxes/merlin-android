package dev.merlin.android.network

import dev.merlin.android.models.CommentsPayload
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Push-Kanal für Kommentare und Markierungen eines Artikels (Server-Sent Events,
 * `GET api/articles/{id}/comments/stream`, nur merlin-nextcloud). Äquivalent zu
 * `MerlinAPI.commentStream` in `MerlinAPI.swift`.
 *
 * Liefert sofort ein [Event.Update], sobald sich auf dem Server etwas gegenüber `since` ändert –
 * egal ob der Besitzer selbst (auf einem anderen Gerät) oder ein Gast hinter dem Share-Link
 * schreibt. Der Server hält die Verbindung ~50 s und beendet sie dann; der Flow endet dann ohne
 * Fehler, und der Aufrufer verbindet mit der zuletzt gesehenen Marke neu (siehe `CommentStore`).
 * [Event.Closed] heißt: gar nicht mehr verbinden, bis sich am Share-Link etwas ändert.
 *
 * Läuft über den normalen Merlin-Client (Server-URL und Login aus den Interceptors), nur mit
 * längerem Lese-Timeout: der Server schickt alle ~15 s einen Heartbeat.
 */
@Singleton
class CommentStreamClient @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
) {
    sealed interface Event {
        data class Update(val payload: CommentsPayload) : Event
        data object Closed : Event
    }

    /** HTTP-Fehler des Kanals; 404 heißt: Server kennt keine Kommentare (merlin-server). */
    class StreamException(val code: Int) : IOException("HTTP $code")

    private val streamClient: OkHttpClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    fun stream(articleId: Int, since: String): Flow<Event> = flow {
        val url = NetworkModule.PLACEHOLDER_BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/articles/$articleId/comments/stream")
            .addQueryParameter("since", since)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .apply { if (since.isNotEmpty()) header("Last-Event-ID", since) }
            .build()
        val call = streamClient.newCall(request)
        // Ein blockierendes `readUtf8Line` merkt die Coroutine-Abbruch nicht von selbst.
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw StreamException(response.code)
                val source = response.body?.source() ?: return@use
                // Der Server schreibt je Ereignis `id:`, `event:` und genau eine `data:`-Zeile;
                // ausgewertet wird direkt an der data-Zeile (wie auf iOS).
                var eventType = ""
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("event:") -> eventType = line.substring(6).trim()
                        line.startsWith("data:") -> {
                            val data = line.substring(5).trim()
                            if (eventType == "closed") {
                                emit(Event.Closed)
                                return@use
                            }
                            if (eventType == "comments") {
                                runCatching { json.decodeFromString<CommentsPayload>(data) }
                                    .getOrNull()
                                    ?.let { emit(Event.Update(it)) }
                            }
                            eventType = ""
                        }
                        // `:`-Zeilen (Padding, Heartbeat), `id:` und `retry:` ignorieren.
                    }
                }
            }
        } finally {
            handle.dispose()
        }
    }.flowOn(Dispatchers.IO)
}

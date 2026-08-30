package dev.merlin.android.network

import dev.merlin.android.models.Article
import dev.merlin.android.models.FavoritedAtSerializer
import dev.merlin.android.models.Highlight
import dev.merlin.android.models.HighlightCreate
import dev.merlin.android.models.SiteCredentialInfo
import dev.merlin.android.models.SiteCredentialUpdateRequest
import dev.merlin.android.models.SiteCredentialsResponse
import dev.merlin.android.models.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Äquivalent zu `MerlinAPI.swift`, auf Basis von `merlin-api.yaml`.
 * Alle Pfade relativ zu `{nextcloudUrl}/index.php/apps/merlin`
 * (siehe [AuthInterceptor]/Retrofit-Baseurl-Konfiguration). Auth via
 * HTTP Basic (Nextcloud-Nutzername + App-Passwort) – siehe [AuthInterceptor].
 *
 * SSE-Stream (`articleUpdateStream`) ist hier bewusst NICHT enthalten –
 * dafür ist ein eigener OkHttp-EventSource-Client vorgesehen (separater Task).
 */
interface MerlinApi {

    @GET("api/articles/counts")
    suspend fun getArticleCounts(): ArticleCounts

    @GET("api/articles")
    suspend fun listArticles(
        @Query("isRead") isRead: Int? = null,
        @Query("isFavorite") isFavorite: Int? = null,
        @Query("isArchived") isArchived: Int? = null,
        @Query("tagId") tagId: Int? = null,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): List<Article>

    @POST("api/articles")
    suspend fun createArticle(@Body body: CreateArticleRequest): Article

    @GET("api/articles/search")
    suspend fun searchArticles(
        @Query("query") query: String,
        @Query("limit") limit: Int = 50,
    ): List<Article>

    @GET("api/articles/{id}")
    suspend fun getArticle(@Path("id") id: Int): Article

    @PUT("api/articles/{id}")
    suspend fun updateArticle(@Path("id") id: Int, @Body body: UpdateArticleRequest): Article

    @DELETE("api/articles/{id}")
    suspend fun deleteArticle(@Path("id") id: Int): SuccessResponse

    @PUT("api/articles/{id}/read")
    suspend fun toggleRead(@Path("id") id: Int): ToggleReadResponse

    @PUT("api/articles/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: Int): ToggleFavoriteResponse

    @PUT("api/articles/{id}/archive")
    suspend fun toggleArchive(@Path("id") id: Int): ToggleArchiveResponse

    /** Geräteübergreifende Leseposition (Fraktion 0..1 + Client-Zeitstempel für Last-Write-Wins). */
    @PUT("api/articles/{id}/progress")
    suspend fun updateProgress(@Path("id") id: Int, @Body body: UpdateProgressRequest): ProgressResponse

    @GET("api/tags")
    suspend fun listTags(): List<Tag>

    @POST("api/tags")
    suspend fun createTag(@Body body: CreateTagRequest): Tag

    @PUT("api/tags/{id}")
    suspend fun updateTag(@Path("id") id: Int, @Body body: UpdateTagRequest): Tag

    @DELETE("api/tags/{id}")
    suspend fun deleteTag(@Path("id") id: Int): SuccessResponse

    @POST("api/articles/{articleId}/tags/{tagId}")
    suspend fun addTagToArticle(@Path("articleId") articleId: Int, @Path("tagId") tagId: Int): SuccessResponse

    @DELETE("api/articles/{articleId}/tags/{tagId}")
    suspend fun removeTagFromArticle(@Path("articleId") articleId: Int, @Path("tagId") tagId: Int): SuccessResponse

    @GET("api/articles/{articleId}/highlights")
    suspend fun getHighlights(@Path("articleId") articleId: Int): List<Highlight>

    @POST("api/articles/{articleId}/highlights")
    suspend fun createHighlight(@Path("articleId") articleId: Int, @Body body: HighlightCreate): Highlight

    @DELETE("api/highlights/{id}")
    suspend fun deleteHighlight(@Path("id") id: Int): SuccessResponse

    @GET("api/settings")
    suspend fun getSettings(): Settings

    @PUT("api/settings")
    suspend fun updateSettings(@Body body: Settings): SuccessResponse

    /** Server-Capabilities (z.B. ob TTS eingerichtet & erreichbar ist) – nach dem Login abgefragt. */
    @GET("api/capabilities")
    suspend fun getCapabilities(): Capabilities

    // ── Public Share ─────────────────────────────────────────────────────
    // Ein Artikel hat höchstens einen Share-Link (siehe ShareController im
    // Backend: "Regenerieren" tauscht nur den Token aus statt einen zweiten
    // Datensatz anzulegen).

    /** Aktueller Share-Status ({ enabled: false }, falls noch kein Link existiert). */
    @GET("api/articles/{articleId}/share")
    suspend fun getShare(@Path("articleId") articleId: Int): ArticleShareResponse

    /** Legt einen Share-Link an (idempotent). password/expiresAt sind optional. */
    @POST("api/articles/{articleId}/share")
    suspend fun createShare(@Path("articleId") articleId: Int, @Body body: CreateShareRequest): ArticleShareResponse

    /**
     * Passwort/Ablaufdatum ändern. `body` enthält NUR die Felder, die geändert
     * werden sollen – ein Feld mit Wert `null` entfernt Passwort/Ablauf
     * explizit, ein im Map fehlendes Feld lässt es unverändert (Map<String, String?>
     * serialisiert vorhandene Keys auch bei null-Wert, im Gegensatz zu einem
     * Datenklassen-Feld mit Default). Analog zum Sentinel-Muster in
     * ShareController::update() auf dem Server.
     */
    @PUT("api/articles/{articleId}/share")
    suspend fun updateShare(@Path("articleId") articleId: Int, @Body body: Map<String, String?>): ArticleShareResponse

    /** Token austauschen — alter Link wird sofort ungültig, Passwort/Ablauf bleiben erhalten. */
    @POST("api/articles/{articleId}/share/regenerate")
    suspend fun regenerateShare(@Path("articleId") articleId: Int): ArticleShareResponse

    /** Share-Link widerrufen. */
    @DELETE("api/articles/{articleId}/share")
    suspend fun deleteShare(@Path("articleId") articleId: Int): SuccessResponse

    // ── Paywall-Site-Credentials ────────────────────────────────────────────
    // Äquivalent zu `SiteCredentialsView.swift`/`SiteCredentialsViewModel.swift`
    // (siehe `SiteCredentialsScreen.kt`/`SiteCredentialsViewModel.kt`). Domain-basierte
    // Login-Zugangsdaten, mit denen der Server Paywall-Artikel (z.B. Tagesspiegel Plus)
    // vollständig extrahieren kann. `update`/`delete` werfen bei Fehlern `HttpException`
    // (siehe [SiteCredentialsViewModel.save] für die 400/401-Fehler-Body-Auswertung,
    // analog zu `ShareViewModel.saveArticle`).

    /** Liefert bereits verbundene Domains ([SiteCredentialInfo.status]) sowie alle serverseitig unterstützten Domains. */
    @GET("api/user/site-credentials")
    suspend fun getSiteCredentials(): SiteCredentialsResponse

    /** Legt Zugangsdaten für `domain` an oder ersetzt sie. Antwort enthält nie das Passwort. */
    @PUT("api/user/site-credentials/{domain}")
    suspend fun updateSiteCredential(
        @Path("domain") domain: String,
        @Body body: SiteCredentialUpdateRequest,
    ): SiteCredentialInfo

    /**
     * Entfernt die Zugangsdaten für `domain` wieder. Anders als die übrigen `DELETE`s
     * dieses Interfaces liefert dieser Endpunkt laut `merlin-api.yaml` **keinen** Body
     * (Erfolg = beliebiger 2xx-Status) – daher `Response<Unit>` statt `SuccessResponse`,
     * damit ein leerer Response-Body den JSON-Konverter nicht zum Scheitern bringt.
     */
    @DELETE("api/user/site-credentials/{domain}")
    suspend fun deleteSiteCredential(@Path("domain") domain: String): Response<Unit>
}

@Serializable
data class ArticleCounts(val total: Int, val unread: Int, val favorites: Int, val archived: Int)

@Serializable
data class CreateArticleRequest(val url: String, val tagIds: List<Int> = emptyList())

@Serializable
data class UpdateArticleRequest(
    val title: String? = null,
    val isRead: Boolean? = null,
    val isFavorite: Boolean? = null,
    val isArchived: Boolean? = null,
)

@Serializable
data class CreateTagRequest(val name: String, val color: String? = null)

@Serializable
data class UpdateTagRequest(val name: String? = null, val color: String? = null)

@Serializable
data class SuccessResponse(val success: Boolean)

@Serializable
data class ToggleReadResponse(val isRead: Boolean)

@Serializable
data class ToggleFavoriteResponse(
    // Server liefert false | ISO8601-Zeitstempel – siehe FavoritedAtSerializer
    // in Article.kt für den vollen Kontext (kein separates Feld für den Zeitpunkt).
    @Serializable(with = FavoritedAtSerializer::class)
    @SerialName("isFavorite")
    val favoritedAt: String? = null,
) {
    val isFavorite: Boolean get() = favoritedAt != null
}

@Serializable
data class ToggleArchiveResponse(val isArchived: Boolean, val archivedAt: String? = null)

@Serializable
data class UpdateProgressRequest(val progress: Float, val updatedAt: Long)

@Serializable
data class ProgressResponse(val scrollProgress: Float = 0f, val scrollUpdatedAt: Long = 0L)

/**
 * Settings-Payload für `GET`/`PUT /api/settings`.
 *
 * Alle Felder sind `String` (so parst sie [dev.merlin.android.data.PreferencesStore]),
 * der Server antwortet aber mit gemischten JSON-Typen. Deshalb hängt an jedem Feld
 * [CoercingStringSerializer] – bewusst an *allen*, nicht nur an den heute
 * betroffenen (fontSize/lineHeight/saveProgress/resumeOnOpen): so bricht eine
 * künftige Typänderung in `SettingsController::SETTINGS_TYPES` den Client nicht
 * erneut.
 */
@Serializable
data class Settings(
    @Serializable(with = CoercingStringSerializer::class)
    val theme: String = "auto",
    // Default korrigiert: der Server liefert/erwartet hier eine px-Zahl, kein
    // Label. "medium" hätte `toIntOrNull()` in loadFromServer() still verschluckt.
    @Serializable(with = CoercingStringSerializer::class)
    val fontSize: String = "17",
    @Serializable(with = CoercingStringSerializer::class)
    val fontFamily: String = "default",
    // maxWidth/articlesPerPage existieren serverseitig nicht (nicht in
    // DEFAULT_SETTINGS) – sie werden beim PUT ignoriert und kommen beim GET
    // nie zurück, greifen also immer auf den Default hier zurück.
    @Serializable(with = CoercingStringSerializer::class)
    val maxWidth: String = "800",
    @Serializable(with = CoercingStringSerializer::class)
    val lineHeight: String = "1.6",
    @Serializable(with = CoercingStringSerializer::class)
    val defaultView: String = "unread",
    @Serializable(with = CoercingStringSerializer::class)
    val articlesPerPage: String = "50",
    // Zusätzlich zum bisherigen Schema: Felder, die `PreferencesStore.swift`
    // im `toServerDict()` mitschickt (progressEdge/saveProgress/resumeOnOpen/
    // accentColor). Additiv mit Defaults, damit bestehende Aufrufer/Server-
    // Antworten ohne diese Felder weiter kompatibel bleiben.
    @Serializable(with = CoercingStringSerializer::class)
    val progressEdge: String = "left",
    @Serializable(with = CoercingStringSerializer::class)
    val saveProgress: String = "1",
    @Serializable(with = CoercingStringSerializer::class)
    val resumeOnOpen: String = "1",
    @Serializable(with = CoercingStringSerializer::class)
    val accentColor: String = "#FF3B30",
    // Additiv: Backend-URL für die Report-Funktion (Äquivalent zu
    // `settings["reportBackendUrl"]` im rohen Dictionary, das
    // `ReportService.swift` aus `MerlinAPI.shared.getSettings()` liest).
    // Default "" = nicht konfiguriert, siehe ReportService.kt.
    @Serializable(with = CoercingStringSerializer::class)
    val reportBackendUrl: String = "",
)

/** Antwort von `GET /api/capabilities` (identisch auf Nextcloud und merlin-server). */
@Serializable
data class Capabilities(
    val tts: TtsCapability = TtsCapability(available = false),
) {
    @Serializable
    data class TtsCapability(val available: Boolean)
}

@Serializable
data class CreateShareRequest(val password: String? = null, val expiresAt: String? = null)

@Serializable
data class ArticleShareResponse(
    val enabled: Boolean,
    val articleId: Int? = null,
    val token: String? = null,
    val hasPassword: Boolean? = null,
    val expiresAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val url: String? = null,
)

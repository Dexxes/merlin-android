package dev.merlin.android.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * isFavorite kommt vom Server entweder als `false` (nicht favorisiert) oder
 * als ISO8601-String (Favorisierungszeitpunkt) – bewusst kein separates Feld
 * für den Zeitpunkt (analog `Article.swift`/`Article.php`). Bildet das
 * Wire-Feld auf `String?` ab: null = nicht favorisiert, String = Zeitpunkt.
 */
object FavoritedAtSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FavoritedAt", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return null
        val element = json.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return null
        return if (primitive.booleanOrNull == false) null else primitive.content
    }

    override fun serialize(encoder: Encoder, value: String?) {
        val json = encoder as? JsonEncoder
            ?: error("FavoritedAtSerializer unterstützt nur JSON")
        json.encodeJsonElement(if (value != null) JsonPrimitive(value) else JsonPrimitive(false))
    }
}

/**
 * Äquivalent zu `Article.swift` (merlin-ios).
 *
 * `equals`/`hashCode` werden bewusst NICHT auf alle Felder, sondern nur auf
 * [id], [isProcessing], [updatedAt] und die sortierten Tag-IDs gestützt –
 * gleiches Muster wie im iOS-Original (siehe Kommentar dort): so erkennt
 * Compose zuverlässig, wann sich eine Zeile nach einem Server-Update
 * tatsächlich geändert hat (z. B. wenn die Verarbeitung eines Artikels
 * abgeschlossen wurde), ohne bei jedem Recomposition-Check alle Felder zu
 * vergleichen.
 */
@Serializable
data class Article(
    val id: Int,
    val url: String,
    val title: String,
    val content: String? = null,
    val excerpt: String? = null,
    val author: String? = null,
    val siteName: String? = null,
    val imageUrl: String? = null,
    @Serializable(with = FavoritedAtSerializer::class)
    @SerialName("isFavorite")
    val favoritedAt: String? = null,
    val isArchived: Boolean,
    val readingTime: Int,
    val publishedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null,
    val tags: List<Tag> = emptyList(),
    val isProcessing: Boolean = false,
    val category: String? = null,
    // Geräteübergreifende Leseposition (siehe ArticleReaderViewModel). Fraktion
    // 0..1 statt Pixel (portabel), plus Epoch-Millis-Zeitstempel des letzten
    // Schreibens für Last-Write-Wins. Default 0 hält die Abwärtskompatibilität
    // zu älteren Servern ohne diese Felder (kotlinx.serialization ignoreUnknownKeys
    // + Default greift, kein Crash).
    val scrollProgress: Float = 0f,
    val scrollUpdatedAt: Long = 0L,
) {
    /** true, sobald [favoritedAt] gesetzt ist – Bool-Convenience für bestehenden UI-Code. */
    val isFavorite: Boolean
        get() = favoritedAt != null

    val displayTitle: String
        get() = title.ifEmpty { url }

    val displaySiteName: String
        get() = siteName ?: runCatching { java.net.URI(url).host }.getOrNull() ?: url

    /** DuckDuckGo-Favicon-Service – funktioniert für praktisch jede Domain. */
    val faviconUrl: String?
        get() {
            val host = runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: return null
            return "https://icons.duckduckgo.com/ip3/$host.ico"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Article) return false
        return id == other.id &&
            isProcessing == other.isProcessing &&
            updatedAt == other.updatedAt &&
            tags.map { it.id }.sorted() == other.tags.map { it.id }.sorted()
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + isProcessing.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + tags.map { it.id }.sorted().hashCode()
        return result
    }
}

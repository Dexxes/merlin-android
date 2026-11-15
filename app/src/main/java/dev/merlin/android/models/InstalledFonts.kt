package dev.merlin.android.models

import android.graphics.fonts.SystemFonts
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Eine auf dem Gerät installierte Schriftfamilie mit ihren Schnitten (Dateien).
 * Gegenstück zu `UIFont.familyNames` unter iOS.
 */
data class InstalledFontFamily(
    val name: String,
    val faces: List<InstalledFontFace>,
) {
    /** Datei des Normalschnitts (für die Vorschau in der Auswahl). */
    val previewFace: InstalledFontFace
        get() = faces.filter { !it.italic }.minByOrNull { kotlin.math.abs(it.minWeight - 400) } ?: faces.first()
}

/**
 * Ein Schnitt einer [InstalledFontFamily]. Variable Fonts (z. B. Roboto ab Android 12) decken
 * mit einer Datei einen Gewichtsbereich ab, daher `minWeight..maxWeight`.
 */
data class InstalledFontFace(
    val path: String,
    val minWeight: Int,
    val maxWeight: Int,
    val italic: Boolean,
)

/**
 * Listet die auf dem Gerät installierten Schriften für die freie Schriftwahl im
 * Erscheinungsbild-Sheet.
 *
 * Android hat – anders als iOS (`UIFont.familyNames`) – keine öffentliche API, die Schriften
 * nach Familiennamen liefert:
 * - ab Android 10 (API 29) liefert [SystemFonts.getAvailableFonts] die Schrift-*Dateien*,
 * - darunter lesen wir die Verzeichnisse `/system/fonts` und `/product/fonts` direkt.
 *
 * Den Familiennamen lesen wir in beiden Fällen selbst aus der `name`-Tabelle der Datei
 * (Typographic Family, sonst Font Family), Gewicht/Kursiv aus der `OS/2`-Tabelle.
 *
 * Bewusste Einschränkungen:
 * - Nur Schriften mit lateinischen Grundzeichen (OS/2-Unicode-Range "Basic Latin"); die vielen
 *   Noto-Schriften für andere Schriftsysteme sowie Emoji würden die Liste sonst unbrauchbar
 *   machen und sind als Artikelschrift ohnehin nicht nutzbar.
 * - Aus TrueType-Collections (`.ttc`) nur der erste Schnitt: CSS-`@font-face` kann keinen
 *   Collection-Index adressieren.
 * - Android kennt keine vom Nutzer installierbaren Schriften; Hersteller-Schriften (z. B. Samsung
 *   "Schriftstil") liegen oft außerhalb dieser Verzeichnisse und tauchen dann nicht auf.
 */
object InstalledFonts {
    @Volatile
    private var cache: List<InstalledFontFamily>? = null

    /** Blockiert (Datei-I/O) – nur von einem Hintergrund-Thread aufrufen. Ergebnis wird gecacht. */
    fun families(): List<InstalledFontFamily> {
        cache?.let { return it }
        val loaded = load()
        cache = loaded
        return loaded
    }

    /** Familie nach Namen (Groß-/Kleinschreibung egal); `null`, wenn sie nicht (mehr) installiert ist. */
    fun family(name: String): InstalledFontFamily? =
        families().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun load(): List<InstalledFontFamily> {
        val files: List<Pair<File, IntRange?>> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                SystemFonts.getAvailableFonts()
                    .filter { it.ttcIndex == 0 }
                    .mapNotNull { font -> font.file?.let { it to (font.style.weight..font.style.weight) } }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }.ifEmpty {
            listOf("/system/fonts", "/product/fonts", "/system/product/fonts")
                .flatMap { dir -> File(dir).listFiles()?.toList().orEmpty() }
                .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc") }
                .map { it to null }
        }

        // Gleiche Datei kann mehrfach auftauchen (variable Fonts mit mehreren Gewichten).
        val byPath = LinkedHashMap<String, Pair<FontInfo, IntRange?>>()
        for ((file, weights) in files) {
            val path = file.absolutePath
            val existing = byPath[path]
            if (existing != null) {
                val merged = weights?.let { w ->
                    existing.second?.let { minOf(it.first, w.first)..maxOf(it.last, w.last) } ?: w
                }
                byPath[path] = existing.first to merged
                continue
            }
            val info = runCatching { readFontInfo(file) }.getOrNull() ?: continue
            if (!info.hasBasicLatin || info.family.isBlank()) continue
            byPath[path] = info to weights
        }

        return byPath.entries
            .groupBy { it.value.first.family.trim() }
            .map { (family, entries) ->
                InstalledFontFamily(
                    name = family,
                    faces = entries.map { (path, value) ->
                        val (info, weights) = value
                        val range = weights ?: (info.weight..info.weight)
                        InstalledFontFace(path, range.first, range.last, info.italic)
                    }.sortedWith(compareBy({ it.italic }, { it.minWeight })),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    internal data class FontInfo(
        val family: String,
        val weight: Int,
        val italic: Boolean,
        val hasBasicLatin: Boolean,
    )

    /** Liest Familienname, Gewicht, Kursiv und Latin-Abdeckung direkt aus dem SFNT-Header. */
    internal fun readFontInfo(file: File): FontInfo? = RandomAccessFile(file, "r").use { raf ->
        var base = 0L
        val tag = raf.readInt()
        if (tag == TAG_TTCF) {
            raf.seek(12)
            base = raf.readInt().toLong() and 0xFFFFFFFFL
        }
        raf.seek(base + 4)
        val numTables = raf.readUnsignedShort()
        if (numTables == 0 || numTables > 512) return null
        var nameOffset = -1L
        var os2Offset = -1L
        for (i in 0 until numTables) {
            raf.seek(base + 12 + i * 16L)
            val t = raf.readInt()
            raf.readInt() // checksum
            val off = raf.readInt().toLong() and 0xFFFFFFFFL
            when (t) {
                TAG_NAME -> nameOffset = off
                TAG_OS2 -> os2Offset = off
            }
        }
        if (nameOffset < 0) return null

        var weight = 400
        var italic = false
        // Ohne OS/2-Tabelle (selten, alte Mac-Fonts) nicht ausfiltern.
        var basicLatin = true
        if (os2Offset >= 0) {
            raf.seek(os2Offset + 4)
            weight = raf.readUnsignedShort().coerceIn(1, 1000)
            raf.seek(os2Offset + 42)
            basicLatin = (raf.readInt() and 1) != 0
            raf.seek(os2Offset + 62)
            italic = (raf.readUnsignedShort() and 1) != 0
        }

        raf.seek(nameOffset + 2)
        val count = raf.readUnsignedShort()
        val stringBase = nameOffset + raf.readUnsignedShort()
        // Rang: Typographic Family (16) vor Family (1); Windows/Englisch vor Mac vor Rest.
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (i in 0 until count) {
            raf.seek(nameOffset + 6 + i * 12L)
            val platform = raf.readUnsignedShort()
            val encoding = raf.readUnsignedShort()
            val language = raf.readUnsignedShort()
            val nameId = raf.readUnsignedShort()
            val length = raf.readUnsignedShort()
            val offset = raf.readUnsignedShort()
            if (nameId != 1 && nameId != 16) continue
            val charset: Charset = when {
                platform == 3 && (encoding == 1 || encoding == 10) -> Charsets.UTF_16BE
                platform == 0 -> Charsets.UTF_16BE
                platform == 1 && encoding == 0 -> Charsets.ISO_8859_1
                else -> continue
            }
            val score = (if (nameId == 16) 0 else 10) + when {
                platform == 3 && language == 0x409 -> 0
                platform == 3 -> 2
                platform == 0 -> 3
                else -> 4
            }
            if (score >= bestScore || length == 0) continue
            val bytes = ByteArray(length)
            raf.seek(stringBase + offset)
            raf.readFully(bytes)
            best = String(bytes, charset)
            bestScore = score
        }
        best?.let { FontInfo(it, weight, italic, basicLatin) }
    }

    private const val TAG_TTCF = 0x74746366 // 'ttcf'
    private const val TAG_NAME = 0x6E616D65 // 'name'
    private const val TAG_OS2 = 0x4F532F32 // 'OS/2'
}

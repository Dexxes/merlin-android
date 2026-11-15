package dev.merlin.android.ui.reader

import dev.merlin.android.models.Article
import dev.merlin.android.models.Highlight
import dev.merlin.android.models.ReaderTheme
import dev.merlin.android.models.SupportBox
import dev.merlin.android.viewmodel.ArticleReaderViewModel

/**
 * Äquivalent zum HTML/CSS/JS-Template-Teil von `ArticleReaderView.swift`.
 * Jede Appearance-Änderung (Theme/Font/Größe/Zeilenhöhe/Akzentfarbe) baut wie
 * im iOS-Original das komplette HTML neu auf statt inkrementeller CSS-
 * Injection – der Aufrufer ([dev.merlin.android.ui.reader.ReaderWebView])
 * lädt das Ergebnis bei jeder Änderung per `loadDataWithBaseURL` neu.
 *
 * **Bewusste Vereinfachung gegenüber iOS:** Das iOS-Original schreibt
 * Artikel-Bilder vorab in ein lokales Verzeichnis und lädt die Seite per
 * `loadFileURL`, damit Bilder offline aus dem Cache kommen. Android nutzt
 * stattdessen Coils Disk-Cache nur fürs Vorwärmen (siehe [dev.merlin.android.data.ImageCacheService]) –
 * die WebView selbst lädt Bild-URLs weiterhin direkt über ihren eigenen
 * Netzwerk-Stack, nicht aus dem Coil-Cache. Offline sind Artikelbilder im
 * Reader also (anders als auf iOS) nicht garantiert verfügbar; als
 * Polish-Punkt zurückgestellt, siehe `todo.md` Abschnitt 9.
 */
object ReaderHtmlBuilder {

    /** Papierstruktur sichtbar: Schalter an und Theme hell bzw. Sepia (Dunkel bleibt reines Schwarz). */
    fun showsPaperTexture(appearance: ArticleReaderViewModel.Appearance, isSystemDark: Boolean): Boolean =
        appearance.paperTexture && when (appearance.theme) {
            ReaderTheme.DARK -> false
            ReaderTheme.AUTO -> !isSystemDark
            ReaderTheme.LIGHT, ReaderTheme.SEPIA -> true
        }

    /**
     * Ein Segment der Metazeile im Plakat-Header (`buildHeaderHtml`); `tapKind` != null macht es per
     * `READER_JS` antippbar (Popover mit [popoverLabel]/[popoverValue]). Werte sind unescaped.
     */
    private data class MetaSegment(
        val text: String,
        val kind: String,
        val tapKind: String? = null,
        val popoverLabel: String? = null,
        val popoverValue: String? = null,
    )

    /**
     * Vorab aufgelöste Texte der Support-Infobox. Der Builder ist ein `object` ohne Context (kein
     * `stringResource`), der Aufrufer ([ReaderWebView]) löst sie deshalb über Android-Ressourcen auf.
     * `both`/`subscribeOnly`/`donateOnly` enthalten die Platzhalter `{subscribe}`/`{donate}` (der
     * Aufrufer setzt sie beim Formatieren selbst ein); [title] ist bereits mit dem Seitennamen befüllt.
     */
    data class SupportBoxTexts(
        val title: String,
        val both: String,
        val subscribeOnly: String,
        val donateOnly: String,
        val subscribeLabel: String,
        val donateLabel: String,
    )

    fun build(
        article: Article,
        appearance: ArticleReaderViewModel.Appearance,
        // System-Dark-Mode-Signal von außen (Compose `isSystemInDarkTheme()`) – die WebView selbst
        // kennt den App-weiten Dark-Mode-Status nicht, siehe `buildCss`-Kommentar zu AUTO.
        isSystemDark: Boolean,
        // Texte der Support-Infobox; null (oder fehlendes `article.supportBox`) = keine Box.
        supportBoxTexts: SupportBoxTexts? = null,
        // Seite ohne eigenen Hintergrund: die Papierstruktur liegt in Compose hinter der
        // durchsichtigen WebView (siehe `PaperBackground.kt`), wie unter iOS.
        transparentBackground: Boolean = false,
    ): String {
        // Reihenfolge wie im iOS-Template (`buildReaderHTML` in ArticleReaderView.swift):
        // Titelbild einfügen → Titelbild-Entfernung bei ARD/ZDF/3sat/Arte → Aufmacher-Video →
        // YouTube-Karten.
        val bodyHtml = ReaderMediaRewriter.rewriteYouTubeEmbeds(
            promoteHeroVideo(
                stripHeroImageIfShownAsVideoCover(
                    injectHeroImageIfNeeded(
                        article.content ?: "<p>${escapeHtml(article.excerpt ?: "")}</p>",
                        article,
                    ),
                    article.url,
                ),
                article,
            ),
        )
        val leadsWithHero = leadsWithHero(bodyHtml, article)
        val css = buildCss(appearance, isSystemDark, transparentBackground)
        val headerHtml = buildHeaderHtml(article, leadsWithHero)
        val footerHtml = buildFooterHtml(article)
        // Markierungen kommen erst nach dem Laden per [highlightsScript] (siehe ReaderWebView), damit
        // neue Markierungen und Kommentar-Pushes die Seite nicht neu laden. Die Support-Infobox ist ein
        // eigenes Element und verschiebt keinen XPath-Index (siehe `buildSupportBoxScript`).
        val supportBoxScript = article.supportBox
            ?.let { box -> supportBoxTexts?.let { buildSupportBoxScript(box, it, article.id) } }
            ?.let { "<script>$it</script>" }
            .orEmpty()

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <style>$css</style>
            </head>
            <body${if (leadsWithHero) " class=\"merlin-leads-hero\"" else ""}>
              $headerHtml
              <div id="merlin-content">$bodyHtml$footerHtml</div>
              <script>$READER_JS</script>
              <script>${ReaderMediaRewriter.YOUTUBE_JS}</script>
              <script>${ReaderMediaRewriter.INLINE_MEDIA_JS}</script>
              $supportBoxScript
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Entfernt das erste Bild aus dem gerenderten Artikeltext, wenn ARD/ZDF/3sat/Arte bereits als
     * Player-Cover dasselbe Titelbild zeigt (siehe [NativeVideoPlayerCard]) - sonst erscheint es
     * doppelt: einmal als Cover, einmal im Text darunter. Äquivalent zu
     * `stripHeroImageIfShownAsVideoCover` in `ArticleReaderView.swift`.
     *
     * Matched absichtlich NICHT über die exakte Bild-URL: ARD liefert für dasselbe Foto im
     * Artikeltext oft eine andere Auflösungs-/Query-Variante als für das separat gespeicherte
     * Teaser-Bild (`article.imageUrl`), ein URL-Abgleich schlug deshalb in der Praxis fehl. Das
     * erste Bild im Text ist bei gescrapten ARD/ZDF/3sat/Arte-Seiten zuverlässig genau das Titelbild.
     */
    private fun stripHeroImageIfShownAsVideoCover(content: String, articleUrl: String): String {
        if (!NativeVideoHost.matches(articleUrl)) return content
        val match = firstImageOrFigureRegex.find(content) ?: return content
        return content.removeRange(match.range)
    }

    /**
     * Aufmacher-Video eines Textartikels, siehe [ReaderMediaRewriter.promoteHeroVideo]. ARD/ZDF/
     * 3sat/Arte haben ihren eigenen nativen Player ([NativeVideoPlayerCard]), PDFs laufen gar
     * nicht durch die WebView – Äquivalent zu `promoteHeroVideo(in:)` in `ArticleReaderView.swift`.
     */
    private fun promoteHeroVideo(content: String, article: Article): String {
        if (NativeVideoHost.matches(article.url) || article.isPdf) return content
        return ReaderMediaRewriter.promoteHeroVideo(content)
    }

    private val firstImageOrFigureRegex = Regex(
        """<figure>\s*<img\b[^>]*>\s*(?:<figcaption>.*?</figcaption>\s*)?</figure>|<img\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * Nennt am Ende des Artikeltexts noch einmal "Autor, Medium" (z.B. "Hans Müller, taz.de") –
     * praktisch, wenn man beim Lesen den Anfang/Header schon nach oben weggescrollt hat und nicht
     * mehr weiß, von wem/woher der Artikel stammt. Reiner Text (keine eigene Verlinkung), fällt
     * weg, wenn weder Autor noch Site-Name bekannt sind.
     */
    private fun buildFooterHtml(article: Article): String {
        val parts = listOfNotNull(
            article.author?.takeIf { it.isNotBlank() },
            article.displaySiteName.takeIf { it.isNotBlank() },
        )
        if (parts.isEmpty()) return ""
        return "<div class=\"merlin-footer-byline\">${escapeHtml(parts.joinToString(", "))}</div>"
    }

    /**
     * Hintergrund-/Vordergrund-/gedämpfte Vordergrundfarbe je Reader-Theme – Grundlage für
     * sowohl [buildCss] (Artikeltext) als auch [buildHeaderHtml] (Meta-Zeile). AUTO folgt dem
     * System-Dark-Mode (per `isSystemInDarkTheme()` vom Aufrufer durchgereicht), statt fix auf
     * Light zu stehen – sonst bleibt der Artikeltext bei System-Dark-Mode hell, während die
     * umgebende Compose-Chrome (TopAppBar, Drawer, Bottom-Bar) bereits auf MaterialTheme-Dark-
     * Farben umschaltet.
     */
    private fun themeColors(theme: ReaderTheme, isSystemDark: Boolean): Triple<String, String, String> =
        when (theme) {
            // Echtes Schwarz (#000000) statt des früheren #121212 – konsistent mit
            // `DarkColors`/`rememberReaderChromeColors` (Theme.kt/ArticleReaderScreen.kt).
            ReaderTheme.AUTO -> if (isSystemDark) Triple("#000000", "#E8E8E8", "#98989D") else Triple("#FFFFFF", "#1C1C1E", "#6E6E73")
            ReaderTheme.LIGHT -> Triple("#FFFFFF", "#1C1C1E", "#6E6E73")
            ReaderTheme.DARK -> Triple("#000000", "#E8E8E8", "#98989D")
            ReaderTheme.SEPIA -> Triple("#F5ECD9", "#3A2E1F", "#7A6350")
        }

    /**
     * Plakat-Header wie `articleHeader` in `ArticleReaderView.swift` (Design „Plakat-Header mit
     * Tonstufen“): eine Fläche in der Akzentfarbe des Users mit Topline (Site links, erster Tag
     * rechts, 2px-Linie darunter), Titel, Teaser und einer einzeiligen Metazeile (2px-Linie
     * darüber). Info-Card und Tag-Pills gibt es nicht mehr.
     *
     * Unter iOS ist der Header eine native SwiftUI-View über der WebView, hier Teil des
     * WebView-Inhalts (scrollt mit dem Text mit). Beginnt der Artikel mit einem Titelbild
     * ([leadsWithHero]), setzt das CSS die Fläche randlos um das Bild fort und zeichnet die
     * Tonstufen unter der Bildunterschrift; sonst zeichnet der Header die Stufen selbst.
     */
    private fun buildHeaderHtml(article: Article, leadsWithHero: Boolean): String {
        val site = article.displaySiteName.trim()
        val firstTag = article.tags.firstOrNull()?.name?.trim().orEmpty()
        val toplineHtml = if (site.isNotEmpty() || firstTag.isNotEmpty()) {
            // Tipp auf den Sitenamen öffnet den Artikel im Browser (iOS: Link-Dialog).
            val siteHtml = if (site.isNotEmpty()) {
                val url = safeHttpUrl(article.url)
                if (url != null) {
                    "<span class=\"merlin-poster-site\" data-open-url=\"${escapeHtmlAttr(url)}\">${escapeHtml(site)}</span>"
                } else {
                    "<span class=\"merlin-poster-site\">${escapeHtml(site)}</span>"
                }
            } else "<span></span>"
            val tagHtml = if (firstTag.isNotEmpty()) "<span class=\"merlin-poster-tag\">${escapeHtml(firstTag)}</span>" else ""
            "<div class=\"merlin-poster-top\">$siteHtml$tagHtml</div>"
        } else ""

        val excerptHtml = if (!article.excerpt.isNullOrBlank()) {
            "<div class=\"merlin-poster-excerpt\">${escapeHtml(article.excerpt)}</div>"
        } else ""

        // Metazeile „Von Max Muster · 4 min · 24.09.26“ (iOS: `metaLineRow`). Der Autor weicht bei
        // Platzmangel zuerst (einzeilig abgeschnitten, Tipp zeigt dann den vollen Namen); „Erschienen“
        // öffnet per Tipp das Gespeichert-am-Popover. Fehlt `publishedAt`, steht direkt das
        // Gespeichert-Datum da, ein Popover wäre dort redundant (wie iOS).
        val segments = mutableListOf<MetaSegment>()
        article.author?.trim()?.takeIf { it.isNotEmpty() }?.let { author ->
            segments.add(MetaSegment("Von $author", kind = "author", tapKind = "author", popoverLabel = "Von", popoverValue = author))
        }
        if (article.readingTime > 0) segments.add(MetaSegment("${article.readingTime} min", kind = "time"))
        val publishedFormatted = formatHeaderDate(article.publishedAt)
        val createdFormatted = formatHeaderDate(article.createdAt)
        if (publishedFormatted != null) {
            segments.add(
                MetaSegment(
                    publishedFormatted,
                    kind = "date",
                    tapKind = if (createdFormatted != null) "date" else null,
                    popoverLabel = "Gespeichert",
                    popoverValue = createdFormatted,
                ),
            )
        } else if (createdFormatted != null) {
            segments.add(MetaSegment(createdFormatted, kind = "date"))
        }
        val metaHtml = if (segments.isNotEmpty()) {
            val parts = segments.mapIndexed { idx, seg ->
                val attrs = buildString {
                    if (seg.tapKind != null && seg.popoverValue != null) {
                        append(" data-tap-kind=\"${seg.tapKind}\"")
                        append(" data-popover-label=\"${escapeHtmlAttr(seg.popoverLabel.orEmpty())}\"")
                        append(" data-popover-value=\"${escapeHtmlAttr(seg.popoverValue)}\"")
                    }
                }
                val segHtml = "<span class=\"merlin-poster-meta-${seg.kind}\"$attrs>${escapeHtml(seg.text)}</span>"
                // Punkt hinter dem Autor nur bei Trunkierung sichtbar (wie iOS, Platz bleibt reserviert).
                val dot = if (idx < segments.size - 1) {
                    val dotClass = if (seg.kind == "author") "merlin-poster-dot merlin-poster-dot-author" else "merlin-poster-dot"
                    "<span class=\"$dotClass\">·</span>"
                } else ""
                segHtml + dot
            }
            "<div class=\"merlin-poster-meta\">${parts.joinToString("")}</div>"
        } else ""

        val stepsHtml = if (!leadsWithHero) "<div class=\"merlin-poster-steps\"></div>" else ""

        return """
            <div id="merlin-header">
              <div class="merlin-poster">
                $toplineHtml
                <div class="merlin-poster-title">${escapeHtml(article.displayTitle)}</div>
                $excerptHtml
                $metaHtml
              </div>
              $stepsHtml
            </div>
        """.trimIndent()
    }

    /**
     * Fügt das Titelbild (`article.imageUrl`) als erste `<figure>` ein, wenn der Artikeltext nicht
     * schon in den ersten 500 Zeichen ein Bild hat - wie `injectHeroImageIfNeeded` in
     * `ArticleReaderView.swift`. So beginnt der Reader wie unter iOS mit dem Bild auf der
     * Akzentfläche. Bei ARD/ZDF/3sat/Arte zeigt der native Player das Bild bereits als Cover.
     */
    private fun injectHeroImageIfNeeded(content: String, article: Article): String {
        if (NativeVideoHost.matches(article.url) || article.isPdf) return content
        val prefix = content.take(500).lowercase()
        if (prefix.contains("<img") || prefix.contains("<figure") || prefix.contains("<picture")) return content
        val imageUrl = article.imageUrl?.let(::safeHttpUrl) ?: return content
        // Bewusst ohne Zeilenumbruch dahinter: ein zusätzlicher Textknoten verschöbe die XPaths der Markierungen.
        return "<figure><img src=\"${escapeHtmlAttr(imageUrl)}\" alt=\"\"></figure>$content"
    }

    /** true, wenn der Artikeltext direkt mit dem Titelbild beginnt (siehe [buildHeaderHtml]). */
    private fun leadsWithHero(bodyHtml: String, article: Article): Boolean {
        if (NativeVideoHost.matches(article.url)) return false
        val head = bodyHtml.trimStart().take(8).lowercase()
        return head.startsWith("<figure") || head.startsWith("<img")
    }

    /** `dd.MM.yy` analog zur Kurzdatumsdarstellung im iOS-Original (`shortDate`). */
    private val headerDateFormatter = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.GERMANY)

    private fun formatHeaderDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        // Das PHP-Backend liefert Daten über `DateTime::format('c')`, also mit echtem
        // Zonen-Offset (z. B. "+02:00"), nicht mit 'Z'-Suffix. `Instant.parse` versteht nur
        // 'Z' und würfe hier sonst immer eine DateTimeParseException – deshalb zuerst über
        // `OffsetDateTime` parsen (versteht beliebige Offsets) und erst danach in einen
        // Instant für die Formatierung wandeln.
        val instant = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }
            .getOrElse { runCatching { java.time.Instant.parse(iso) }.getOrNull() }
            ?: return null
        return runCatching { headerDateFormatter.format(java.util.Date.from(instant)) }.getOrNull()
    }

    private fun buildCss(
        appearance: ArticleReaderViewModel.Appearance,
        isSystemDark: Boolean,
        transparentBackground: Boolean,
    ): String {
        val (bg, fg, mutedFg) = themeColors(appearance.theme, isSystemDark)
        // Papierstruktur: Hintergrund durchsichtig, die Fläche dahinter zeichnet Compose.
        val paperCss = if (transparentBackground) "html, body { background-color: transparent; }" else ""
        val isDark = bg == "#000000"
        val isSepia = appearance.theme == ReaderTheme.SEPIA
        val accent = appearance.accentColorHex
        val onAccent = PosterColors.onAccentHex(accent)
        val titleSize = String.format(java.util.Locale.US, "%.1f", appearance.fontSize * 2.1)
        // Tonstufen (14/12/10/8 px, 75/55/35/15 % Akzent) gegen die Theme-Hintergrundfarbe.
        val stepColors = PosterColors.stepColors(accent, bg)
        val stepsHeight = PosterColors.stepsHeight
        var y = 0
        val gradientStops = PosterColors.steps.zip(stepColors).joinToString(", ") { (step, color) ->
            val from = y
            y += step.first
            "$color ${from}px ${y}px"
        }
        val stepsGradient = "linear-gradient(to bottom, $gradientStops)"
        var shadowY = 0
        val stepsShadow = PosterColors.steps.zip(stepColors).joinToString(", ") { (step, color) ->
            shadowY += step.first
            "0 ${shadowY}px 0 $color"
        }
        val codeBg = when { isSepia -> "#e8d9be"; isDark -> "#2c2c2e"; else -> "#f2f2f7" }
        val hairline = if (isDark) "#2c2c2e" else "#e5e5ea"
        val tableBorder = if (isDark) "#3a3a3c" else "#d1d1d6"
        val infoboxBg = when { isSepia -> "#e8d9be"; isDark -> "#1e2d3d"; else -> "#f0f7ff" }
        // Akzent als Textfarbe (Captions, Zitate, Infokasten-Links) im Dark Mode auf WCAG AA
        // aufhellen: ein dunkler Akzent (z. B. Lila) ist auf Schwarz bzw. dem dunkelblauen
        // Infokasten sonst kaum lesbar. Hell/Sepia bleiben unverändert (wie iOS).
        val accentText = if (isDark) PosterColors.readableTextHex(accent, bg) else accent
        val accentTextInfobox = if (isDark) PosterColors.readableTextHex(accent, infoboxBg) else accent
        val citeOpacity = if (isDark) "0.75" else "0.5"
        // Gewählte installierte Schrift (siehe `ReaderLocalFonts`); Voreinstellung bleibt Fallback.
        val fontFaces = ReaderLocalFonts.fontFaceCss(appearance.customFont)
        val bodyFontFamily = ReaderLocalFonts.fontFamilyCss(appearance.font, appearance.customFont)
        return """
            $fontFaces
            html, body {
              margin: 0; padding: 0;
              background-color: $bg;
              color: $fg;
              font-family: $bodyFontFamily;
              font-size: ${appearance.fontSize}px;
              line-height: ${appearance.lineHeight};
              -webkit-text-size-adjust: 100%;
            }
            $paperCss
            /* ── Plakat-Header (wie `articleHeader` in ArticleReaderView.swift) ── */
            #merlin-header { margin: 0; }
            .merlin-poster {
              background: $accent; color: $onAccent;
              padding: 28px 20px 22px;
              display: flex; flex-direction: column; gap: 18px;
            }
            .merlin-poster-top {
              display: flex; align-items: baseline; justify-content: space-between; gap: 8px;
              font-size: 11px; font-weight: 700; letter-spacing: 2px; line-height: 1.3;
              padding-bottom: 8px; border-bottom: 2px solid $onAccent;
            }
            .merlin-poster-site { flex: 0 1 auto; min-width: 0; overflow-wrap: anywhere; }
            .merlin-poster-site[data-open-url] { cursor: pointer; }
            .merlin-poster-tag {
              flex: 0 1 auto; min-width: 0; text-align: right;
              white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
            }
            .merlin-poster-title {
              font-size: ${titleSize}px; font-weight: 800; letter-spacing: -1px; line-height: 1.15;
              overflow-wrap: break-word; hyphens: auto;
            }
            .merlin-poster-excerpt { font-size: ${appearance.fontSize}px; font-weight: 500; line-height: 1.35; }
            /* Niemals zweizeilig: der Autor weicht zuerst (einzeilig abgeschnitten, Tipp zeigt
               dann den vollen Namen), Lesezeit und Datum behalten ihre Breite. */
            .merlin-poster-meta {
              display: flex; align-items: baseline; gap: 6px;
              font-size: 11px; font-weight: 700; letter-spacing: 1px; line-height: 1.3;
              white-space: nowrap; padding-top: 8px; border-top: 2px solid $onAccent;
            }
            .merlin-poster-meta > span { flex: none; }
            .merlin-poster-meta > .merlin-poster-meta-author {
              flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis;
            }
            .merlin-poster-meta [data-tap-kind] { cursor: pointer; }
            /* Punkt hinter dem Autor nur, wenn der Name abgeschnitten ist (READER_JS setzt die Klasse). */
            .merlin-poster-dot-author { visibility: hidden; }
            .merlin-poster-meta.merlin-poster-meta--truncated .merlin-poster-dot-author { visibility: visible; }
            /* Tonstufen Akzent → Hintergrund, wenn kein Titelbild die Fläche fortsetzt. */
            .merlin-poster-steps { height: ${stepsHeight}px; background: $stepsGradient; }
            /* Unten liegt die Bottom-Bar (bzw. Freihand-Leiste) als Overlay über der WebView und
               blendet am Artikelende immer ein: Ihre Höhe kommt als --merlin-bottom-bar aus
               ArticleReaderScreen (CSS-px = dp), 54px ist die feste Leistenhöhe bis dahin. Ohne
               diesen Abstand war die Quellenangabe am Ende halb verdeckt. */
            #merlin-content { padding: 16px 20px calc(40px + var(--merlin-bottom-bar, 54px)); }
            .merlin-leads-hero #merlin-content { padding-top: 0; }
            /* Erstes Element direkt unter dem Header bekommt keinen eigenen Top-Margin. */
            #merlin-content > *:first-child { margin-top: 0; }
            #merlin-content > *:first-child > *:first-child { margin-top: 0; }
            img, video { max-width: 100%; height: auto; border-radius: 8px; display: block; }
            img { margin: 8px 0; }
            figure { margin: 1em 0 0; }
            figure img { margin-bottom: 0; }
            figcaption {
              font-size: 0.75em; line-height: 1.4; color: $accentText; text-align: left;
              margin-top: 2px; margin-bottom: 1em;
            }
            /* Bildquelle (vom Server als <cite> markiert): nicht fett, kursiv, halbtransparent */
            figcaption cite { font-weight: 400; font-style: italic; opacity: $citeOpacity; }
            figcaption cite * { font-weight: inherit; font-style: inherit; }
            /* Links in Bildunterschriften wie der übrige Caption-Text */
            figcaption a { color: inherit !important; text-decoration: none; }
            /* p { margin-top: 0 } ließe den ersten Absatz sonst direkt am Bild darüber kleben. */
            img + p, figure + p { margin-top: 1em; }
            /* Links in Textfarbe, Unterstreichung in der gedämpften Farbe (wie iOS). */
            a { color: $fg; text-decoration: underline; text-decoration-color: $mutedFg; }
            p { margin: 0 0 1em 0; }
            h1, h2, h3 { line-height: 1.3; margin-top: 1.6em; margin-bottom: 0.4em; }
            h1 { font-size: 1.5em; }
            h2 { font-size: 1.25em; }
            h3 { font-size: 1.1em; }
            ul, ol { padding-left: 1.5em; }
            li { margin: 0 0 0.3em 0; }
            hr { border: none; border-top: 1px solid $hairline; margin: 2em 0; }
            table {
              border-collapse: collapse; width: 100%; max-width: 100%; font-size: 0.9em;
              display: block; overflow-x: auto;
            }
            th, td { padding: 8px 12px; border: 1px solid $tableBorder; text-align: left; }
            th { background: $codeBg; font-weight: 600; }
            pre, code { background: $codeBg; border-radius: 6px; font-size: 0.9em; }
            code { padding: 2px 5px; }
            pre { padding: 12px; overflow-x: auto; white-space: pre; }
            pre code { background: none; padding: 0; }
            .merlin-infobox {
              background: $infoboxBg; border-left: 4px solid $accent;
              border-radius: 0 8px 8px 0; padding: 14px 16px; margin: 1.5em 0;
              font-size: 0.93em; line-height: 1.6; color: $fg;
            }
            .merlin-infobox > *:first-child { margin-top: 0; }
            .merlin-infobox > *:last-child { margin-bottom: 0; }
            .merlin-infobox a { color: $accentTextInfobox !important; }
            .merlin-infobox figcaption { color: $accentTextInfobox; }
            /* Querformat: Bilder (insb. das Hero-Bild aus ContentExtractorService.php,
               <figure class="merlin-hero-image">) sollen fast die gesamte Bildschirmbreite
               einnehmen statt vom 20px-Innenabstand von #merlin-content eingeengt zu werden.
               Negative Margins kompensieren den Innenabstand bis auf einen schmalen 6px-Rand. */
            @media (orientation: landscape) {
              img, video {
                width: calc(100% + 28px);
                max-width: calc(100% + 28px);
                margin-left: -14px;
                margin-right: -14px;
                border-radius: 4px;
              }
            }
            /* ── Titelbild auf Akzentfläche, randlos, danach Tonstufen (wie iOS) ── */
            #merlin-content > figure:first-child,
            #merlin-content > img:first-child {
              display: block;
              width: calc(100% + 40px) !important;
              max-width: none !important;
              margin: 0 -20px 0 !important;
              background: $accent;
              border-radius: 0 !important;
            }
            #merlin-content > figure:first-child img {
              width: 100% !important; max-width: 100% !important;
              margin: 0 !important; border-radius: 0 !important;
            }
            #merlin-content > figure:first-child figcaption {
              margin: 0; padding: 10px 20px 14px;
              color: $onAccent;
              font-size: 11px; font-weight: 700; letter-spacing: 1px;
            }
            #merlin-content > figure:first-child::after {
              content: ""; display: block; height: ${stepsHeight}px;
              background: $stepsGradient;
            }
            /* nacktes <img> ohne <figure>: Stufen per box-shadow */
            #merlin-content > img:first-child {
              margin-bottom: ${stepsHeight}px !important;
              box-shadow: $stepsShadow;
            }
            #merlin-content > figure.merlin-inline-media:first-child merlin-inline-player video { border-radius: 0; }
            #merlin-content > figure.merlin-inline-media:first-child > .merlin-inline-media-source { padding: 0 20px; }
            blockquote {
              margin: 1.5em 0; padding: 0;
              text-align: center;
              font-family: ${dev.merlin.android.models.ReaderFont.SERIF.cssValue};
              font-size: 1.3em;
              font-style: italic;
              line-height: 1.45;
              color: $accentText;
            }
            blockquote p { margin: 0 0 0.4em; }
            blockquote cite, blockquote footer {
              display: block;
              font-family: ${dev.merlin.android.models.ReaderFont.SYSTEM.cssValue};
              font-size: 0.6em;
              font-style: normal;
              text-align: center !important;
              margin-top: 0.3em;
              color: $accentText;
            }
            /* Some sources place the attribution in a paragraph right after the
               blockquote (e.g. <blockquote>…</blockquote><p><cite>Name</cite></p>)
               instead of nesting it inside the blockquote itself. */
            blockquote + p:has(> cite:only-child) {
              display: block;
              text-align: center !important;
              font-family: ${dev.merlin.android.models.ReaderFont.SYSTEM.cssValue};
              font-size: 0.85em;
              color: $accentText;
            }
            blockquote + p:has(> cite:only-child) cite, blockquote + p:has(> cite:only-child) cite * { font-style: normal; }
            pre, code { font-family: ${dev.merlin.android.models.ReaderFont.MONO.cssValue}; }
            .merlin-highlight { border-radius: 2px; padding: 0 1px; cursor: pointer; box-decoration-break: clone; -webkit-box-decoration-break: clone; }
            .merlin-highlight--comment {
              background-color: transparent; color: inherit; padding: 0; border-radius: 0;
              text-decoration-line: underline; -webkit-text-decoration-line: underline;
              text-decoration-color: var(--mh-author, #c2410c); -webkit-text-decoration-color: var(--mh-author, #c2410c);
              text-decoration-thickness: 2px; text-underline-offset: 3px;
            }
            mark.merlin-highlight[data-comment-count]::after {
              content: attr(data-comment-count); display: inline-block; margin-left: 3px; padding: 0 5px;
              min-width: 8px; border-radius: 8px; background: var(--mh-author, #1c1c1e); color: #fff;
              font-size: 0.68em; font-weight: 700; line-height: 1.5; text-align: center; vertical-align: super;
            }
            .merlin-highlight--flash { outline: 2px solid var(--mh-author, #f59e0b); outline-offset: 1px; }
            .merlin-img-error {
              display: flex; align-items: center; justify-content: center;
              background: rgba(127,127,127,0.15); border-radius: 8px;
              min-height: 80px; color: $fg; opacity: 0.6; font-size: 0.85em;
            }
            ${ReaderMediaRewriter.CSS}
            /* Metadaten unter Dateien aus „Merlin Dateien“ (MerlinFileService::metadataHtml):
               <section class="merlin-file-metadata"><h2>…</h2><details open><summary>…</summary>
               <table><tr><th>…</th><td>…</td></tr>…</table></details>…</section>. Wie im
               iOS-Reader-CSS (ArticleReaderView.swift); Gruppen per Tipp auf- und zuklappbar. */
            .merlin-file-metadata { margin-top: 2.5em; font-size: 0.8em; }
            .merlin-file-metadata h2 { font-size: 1.3em; margin: 0 0 0.5em; }
            .merlin-file-metadata details { margin: 0.75em 0; }
            .merlin-file-metadata summary { font-weight: 600; cursor: pointer; }
            .merlin-file-metadata table {
              width: 100%; border-collapse: collapse; table-layout: fixed; margin-top: 0.5em;
            }
            .merlin-file-metadata th {
              width: 38%; font-weight: 500; text-align: left; color: $mutedFg;
            }
            .merlin-file-metadata th, .merlin-file-metadata td {
              padding: 5px 8px; vertical-align: top; overflow-wrap: anywhere;
              border: 1px solid rgba(127,127,127,0.25);
            }
            .merlin-footer-byline {
              margin-top: 32px; padding-top: 16px;
              border-top: 1px solid rgba(127,127,127,0.25);
              font-size: 0.9em; font-style: italic; color: $mutedFg;
            }
            /* Instagram-/X-/Bluesky-Embeds (siehe ContentExtractorService.php,
               isAllowedWidgetScriptSrc()) rendern sich nach dem Laden ihres
               Widget-Skripts selbst neu - der generische blockquote-Style oben
               (Akzent-Randlinie, gedimmt) würde nur bis zum Laden sichtbar sein
               und dann falsch wirken, deshalb hier zurückgesetzt. */
            blockquote.instagram-media, blockquote.twitter-tweet, blockquote.bluesky-embed {
              margin: 1em 0; padding-left: 0; border-left: none; opacity: 1; color: $fg;
            }
            /* Mastodon-Post-Karte (siehe MastodonPostResolverService/
               buildMastodonThreadHtml()): kein Drittanbieter-Widget wie
               Instagram/X/Bluesky (föderiert, kein zentraler Embed-Host),
               sondern eigenes, statisches Markup - braucht deshalb echtes
               Styling statt nur eines Platzhalter-Resets. */
            .merlin-mastodon-post {
              display: block; border: 1px solid rgba(127,127,127,0.25);
              border-radius: 8px; padding: 12px 16px; margin: 1em 0; color: $fg;
            }
            .merlin-mastodon-post + .merlin-mastodon-post { margin-top: 8px; }
            .merlin-mastodon-post__header {
              display: flex; align-items: center; gap: 10px;
              text-decoration: none; color: $fg; margin-bottom: 10px;
            }
            .merlin-mastodon-post__avatar {
              width: 40px; height: 40px; border-radius: 50%;
              object-fit: cover; flex-shrink: 0; margin: 0;
            }
            .merlin-mastodon-post__author {
              display: flex; flex-direction: column; line-height: 1.3; min-width: 0;
            }
            .merlin-mastodon-post__name {
              font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
            }
            .merlin-mastodon-post__handle {
              color: $mutedFg; font-size: 0.9em; overflow: hidden;
              text-overflow: ellipsis; white-space: nowrap;
            }
            .merlin-mastodon-post__content p { margin: 0.5em 0; }
            .merlin-mastodon-post__content p:first-child { margin-top: 0; }
            .merlin-mastodon-post__content p:last-child { margin-bottom: 0; }
            .merlin-mastodon-post__media {
              display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
              gap: 6px; margin-top: 10px;
            }
            .merlin-mastodon-post__media-item {
              width: 100%; height: 140px; object-fit: cover; border-radius: 6px; margin: 0;
            }
        """.trimIndent()
    }

    /** Nur absolute http(s)-URLs; landet in einem `href`/`src`. */
    private fun safeHttpUrl(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val uri = runCatching { java.net.URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()) uri.toString() else null
    }

    /**
     * JSON-String-Literal, sicher zum Einbetten in ein inline `<script>`: zusätzlich zum üblichen
     * Escaping werden `<`, `>`, `&` (sonst bricht ein Seitenname wie `</script>` aus dem Script-Block
     * aus) sowie U+2028/U+2029 (in älteren JS-Engines Zeilenumbrüche) als \uXXXX geschrieben.
     */
    private fun jsonForScript(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when {
                c == '\\' -> sb.append("\\\\")
                c == '"' -> sb.append("\\\"")
                c == '<' || c == '>' || c == '&' || c == '\u2028' || c == '\u2029' || c < ' ' ->
                    sb.append("\\u").append(String.format("%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    /**
     * JS für die Support-Infobox ("Dir gefällt der Artikel von …? Überlege ein Abo abzuschließen
     * oder zu spenden"), Äquivalent zu `supportBoxScript(for:seed:)` in `ArticleReaderView.swift`
     * (iOS): nach einem pseudo-zufälligen Top-Level-`<p>` (Seed = Artikel-ID, damit die Position
     * stabil bleibt), nur ab 4 Absätzen. Als eigenes Element `<merlin-support-box>` gesetzt: der
     * XPath-Zähler der Highlights (`getXPath`) zählt Geschwister je Tag-Name, ein unbekannter Tag
     * verschiebt daher keinen Index des Artikeltextes und Highlights lösen weiter
     * plattformübergreifend gleich auf. Links öffnen über die Bridge im externen Browser (ein
     * normaler Klick würde die WebView selbst wegnavigieren und den Artikel ersetzen); das Icon
     * ist vom Lightbox-Tap und vom "Bild nicht verfügbar"-Platzhalter ausgenommen (`READER_JS`).
     */
    private fun buildSupportBoxScript(box: SupportBox, texts: SupportBoxTexts, articleId: Int): String? {
        val subscribeUrl = safeHttpUrl(box.subscribeUrl)
        val donationsUrl = safeHttpUrl(box.donationsUrl)
        val template = when {
            subscribeUrl != null && donationsUrl != null -> texts.both
            subscribeUrl != null -> texts.subscribeOnly
            donationsUrl != null -> texts.donateOnly
            else -> return null
        }
        val accent = if (Regex("^#[0-9a-fA-F]{6}$").matches(box.accentColor)) box.accentColor else "#FF3B30"
        val links = buildList {
            if (subscribeUrl != null) add("\"subscribe\":{\"href\":${jsonForScript(subscribeUrl)},\"label\":${jsonForScript(texts.subscribeLabel)}}")
            if (donationsUrl != null) add("\"donate\":{\"href\":${jsonForScript(donationsUrl)},\"label\":${jsonForScript(texts.donateLabel)}}")
        }.joinToString(",")
        val icon = safeHttpUrl(box.iconUrl)?.let { jsonForScript(it) } ?: "null"
        val config = "{\"seed\":${jsonForScript(articleId.toString())},\"accent\":${jsonForScript(accent)}," +
            "\"title\":${jsonForScript(texts.title)},\"template\":${jsonForScript(template)}," +
            "\"links\":{$links},\"iconUrl\":$icon}"
        return "(function(cfg){" + SUPPORT_BOX_JS + "})($config);"
    }

    /** Highlight-Farbpalette – fix im Client, identisch zu iOS. */
    val HIGHLIGHT_COLORS = mapOf(
        "yellow" to "#fde68a",
        "green" to "#bbf7d0",
        "blue" to "#bfdbfe",
        "pink" to "#fbcfe8",
        "orange" to "#fed7aa",
    )

    /**
     * JS-Aufruf, der Markierungen und Kommentar-Zähler im Text auf [highlights] bzw. [counts] setzt
     * (Äquivalent zu `CommentStore.webViewScript()` auf iOS). PDF-Markierungen überspringt das
     * Skript selbst.
     */
    fun highlightsScript(highlights: List<Highlight>, counts: Map<Int, Int>): String {
        val items = highlights.filter { !it.isPdf }.joinToString(",") { h ->
            val author = h.authorColor?.takeIf { HEX_COLOR.matches(it) }?.let(::jsString) ?: "null"
            """{"id":${h.id},"startXpath":${jsString(h.startXpath)},"startOffset":${h.startOffset},"endXpath":${jsString(h.endXpath)},"endOffset":${h.endOffset},"color":${jsString(h.color)},"authorColor":$author}"""
        }
        val countJson = counts.entries.joinToString(",") { (id, n) -> "\"$id\":$n" }
        return "window.MerlinReader && MerlinReader.setHighlights([$items], {$countJson});"
    }

    private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")

    private fun jsString(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029") + "\""

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** Wie [escapeHtml], zusätzlich Anführungszeichen-sicher für die Verwendung in `data-*`-Attributwerten. */
    private fun escapeHtmlAttr(value: String): String = escapeHtml(value).replace("\"", "&quot;")

    /**
     * Rumpf der Support-Infobox-Funktion (`cfg` = {seed, accent, title, template, links, iconUrl}, siehe
     * [buildSupportBoxScript]). Bewusst ohne Dollarzeichen: das ist ein Kotlin-Raw-String. Alles wird per
     * `textContent`/DOM gebaut, nie per `innerHTML`.
     */
    private const val SUPPORT_BOX_JS = """
var old = document.querySelector('merlin-support-box'); if (old) old.remove();
// Readability liefert den Text meist in einem äußeren <div>/<article>: Container mit den meisten
// direkten <p>-Kindern wählen (ggf. <body>), nicht in Zitaten/Listen/Figures/Infoboxen.
var ps = [];
var cs = [document.body].concat(Array.prototype.slice.call(document.body.querySelectorAll('div,section,article,main')));
cs.forEach(function(c) {
  if (c !== document.body && c.closest('blockquote,figure,ul,ol,table,aside,.merlin-infobox,merlin-support-box')) return;
  var list = Array.prototype.filter.call(c.children, function(e) { return e.tagName === 'P' && e.textContent.trim() !== ''; });
  if (list.length > ps.length) ps = list;
});
if (ps.length < 4) return;
var h = 0x811c9dc5 >>> 0, s = cfg.seed;
for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193); }
var idx = 1 + ((h >>> 0) % (ps.length - 2));
var box = document.createElement('merlin-support-box');
box.setAttribute('role', 'note');
box.style.cssText = 'display:flex;align-items:stretch;gap:0.9em;margin:1.5em 0;padding:0.85em 1em;border-left:4px solid ' + cfg.accent + ';border-radius:0 8px 8px 0;background:rgba(128,128,128,0.1);background:color-mix(in srgb,' + cfg.accent + ' 12%,transparent);font-size:0.93em;line-height:1.6;-webkit-user-select:none;user-select:none;';
// Zwei Spalten: links das Icon der Seite über die volle Höhe der Box (fehlt es, entfällt die Spalte),
// rechts Titel und Satz.
if (cfg.iconUrl) {
  var img = document.createElement('img');
  img.alt = '';
  img.referrerPolicy = 'no-referrer';
  // Inline-Stil schlägt die globalen img-Regeln (auch die Querformat-Regel mit negativen Rändern und
  // calc-Breite); align-self:stretch + height:auto macht die Spalte so hoch wie die Box.
  img.style.cssText = 'display:block;flex:none;align-self:stretch;width:2.5em;height:auto;max-width:2.5em;min-height:0;margin:0;padding:0.3em;box-sizing:border-box;object-fit:contain;border-radius:6px;';
  img.addEventListener('error', function() { img.remove(); });
  img.src = cfg.iconUrl;
  box.appendChild(img);
}
var body = document.createElement('div');
// Text vertikal zentriert (Flex-Spalte), ohne Absatzabstände - wie merlin-nextcloud/iOS.
body.style.cssText = 'flex:1;min-width:0;display:flex;flex-direction:column;justify-content:center;';
var title = document.createElement('div');
title.style.cssText = 'font-weight:600;margin:0;';
title.textContent = cfg.title;
body.appendChild(title);
var text = document.createElement('div');
text.style.cssText = 'margin:0;';
cfg.template.split(/(\{subscribe\}|\{donate\})/).forEach(function(part) {
  var m = /^\{(subscribe|donate)\}/.exec(part);
  var l = m && cfg.links[m[1]];
  if (l) {
    var a = document.createElement('a');
    a.href = l.href;
    a.textContent = l.label;
    a.style.cssText = 'color:inherit;font-weight:600;text-decoration:underline;text-decoration-color:' + cfg.accent + ';text-decoration-thickness:2px;text-underline-offset:2px;';
    // Ein normaler Klick würde die WebView selbst wegnavigieren (Artikel weg): extern öffnen.
    a.addEventListener('click', function(ev) { ev.preventDefault(); MerlinHighlightBridge.onOpenExternalLink(l.href); });
    text.appendChild(a);
  } else if (part) {
    text.appendChild(document.createTextNode(part));
  }
});
body.appendChild(text);
box.appendChild(body);
ps[idx].after(box);
"""

    /**
     * JS-Laufzeit im WebView: XPath-Generierung/-Auflösung (`tag[n]`/`text()[n]`
     * relativ zu `#merlin-content`, das entspricht dem `<body>` bei iOS und im Web-Reader; ältere
     * Android-Markierungen ab `<body>` werden per Textvergleich noch gefunden), Markierungen in
     * Erstellungsreihenfolge wie iOS, Tap-Handling für Markierungen und Bilder.
     */
    private const val READER_JS = """
window.MerlinReader = (function() {
  // XPaths zählen ab dem Artikelinhalt (#merlin-content), nicht ab <body>: bei iOS und im
  // Web-Reader steht der Inhalt direkt im <body>, hier davor noch der Kopf. Nur so finden
  // alle Apps dieselbe Stelle.
  function contentRoot() {
    return document.getElementById('merlin-content') || document.body;
  }

  function getXPath(node) {
    var root = contentRoot();
    if (node === root) return '';
    var parts = [];
    var current = node;
    while (current && current !== root) {
      var parent = current.parentNode;
      if (!parent) break;
      var siblings = Array.prototype.filter.call(parent.childNodes, function(n) { return n.nodeType === current.nodeType && (current.nodeType !== 1 || n.tagName === current.tagName); });
      var index = siblings.indexOf(current) + 1;
      if (current.nodeType === 1) {
        parts.unshift(current.tagName.toLowerCase() + '[' + index + ']');
      } else if (current.nodeType === 3) {
        parts.unshift('text()[' + index + ']');
      }
      current = parent;
    }
    return parts.join('/');
  }

  function resolveXPath(xpath, base) {
    var node = base || contentRoot();
    if (!xpath) return node;
    var parts = xpath.split('/');
    for (var i = 0; i < parts.length; i++) {
      var m = parts[i].match(/^(.+)\[(\d+)\]$/);
      if (!m) return null;
      var name = m[1], index = parseInt(m[2], 10);
      var children = Array.prototype.filter.call(node.childNodes, function(n) {
        if (name === 'text()') return n.nodeType === 3;
        return n.nodeType === 1 && n.tagName.toLowerCase() === name;
      });
      node = children[index - 1];
      if (!node) return null;
    }
    return node;
  }

  // ── Markierungen ───────────────────────────────────────────────────────
  // Wie im iOS-Reader (`merlinHighlightJS`): je Textknoten ein <mark>, gesetzt in
  // Erstellungsreihenfolge (die XPaths einer Markierung wurden im DOM berechnet, in dem alle
  // älteren Markierungen schon als <mark> standen). Farbe `comment` = nur kommentierte Stelle:
  // unterstrichen in der Farbe des Verfassers statt hinterlegt. Der Kommentar-Zähler hängt als
  // CSS-::after am letzten Stück, ändert also weder Text noch XPaths.
  var COLORS = { yellow: '#fde68a', green: '#bbf7d0', blue: '#bfdbfe', pink: '#fbcfe8', orange: '#fed7aa' };
  var COMMENT_COLOR = 'comment';
  var currentHighlights = [], commentCounts = {}, deferred = null;

  function authorColorOf(h) {
    return h && typeof h.authorColor === 'string' && /^#[0-9a-f]{6}$/i.test(h.authorColor) ? h.authorColor : null;
  }

  function makeMark(h) {
    var m = document.createElement('mark');
    m.className = 'merlin-highlight';
    m.setAttribute('data-highlight-id', String(h.id));
    m.setAttribute('data-highlight-color', h.color);
    var author = authorColorOf(h);
    if (author) m.style.setProperty('--mh-author', author);
    if (h.color === COMMENT_COLOR) {
      m.className += ' merlin-highlight--comment';
    } else {
      // Alle Farben sind helle Pastelltöne: feste dunkle Schrift, sonst ist der Text im
      // Dunkel-Theme auf hellem Grund unlesbar.
      m.style.backgroundColor = COLORS[h.color] || COLORS.yellow;
      m.style.color = '#1c1c1e';
    }
    return m;
  }

  function wrapRange(range, h) {
    if (range.collapsed) return;
    var root = range.commonAncestorContainer.nodeType === 3 ? range.commonAncestorContainer.parentNode : range.commonAncestorContainer;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    var nodes = [], n;
    while ((n = walker.nextNode())) { if (range.intersectsNode(n)) nodes.push(n); }
    for (var i = 0; i < nodes.length; i++) {
      var tn = nodes[i];
      var startOff = (i === 0 && tn === range.startContainer) ? range.startOffset : 0;
      var endOff = (i === nodes.length - 1 && tn === range.endContainer) ? range.endOffset : tn.length;
      if (startOff >= endOff) continue;
      if (endOff < tn.length) tn.splitText(endOff);
      var slice = startOff > 0 ? tn.splitText(startOff) : tn;
      var mark = makeMark(h);
      slice.parentNode.insertBefore(mark, slice);
      mark.appendChild(slice);
    }
  }

  function restoreHighlight(h) {
    if (h.pdfPage != null) return;
    var r = rangeFor(h, contentRoot());
    // Ältere Android-Versionen zählten ab <body>: dann passt der Text nicht, also dort versuchen.
    if (!r || !sameText(r, h.highlightedText)) {
      var legacy = rangeFor(h, document.body);
      if (legacy && sameText(legacy, h.highlightedText)) r = legacy;
    }
    if (r && !r.collapsed) wrapRange(r, h);
  }

  function rangeFor(h, base) {
    var sn = resolveXPath(h.startXpath, base), en = resolveXPath(h.endXpath, base);
    if (!sn || !en) return null;
    try {
      var r = document.createRange();
      r.setStart(sn, h.startOffset);
      r.setEnd(en, h.endOffset);
      return r;
    } catch (e) { return null; }
  }

  function sameText(range, text) {
    var norm = function(s) { return String(s || '').replace(/\s+/g, ' ').trim(); };
    return !text || norm(range.toString()) === norm(text);
  }

  function unwrapAllMarks() {
    var parents = [];
    Array.prototype.forEach.call(document.querySelectorAll('mark.merlin-highlight'), function(el) {
      var p = el.parentNode; if (!p) return;
      while (el.firstChild) p.insertBefore(el.firstChild, el);
      p.removeChild(el);
      if (parents.indexOf(p) < 0) parents.push(p);
    });
    parents.forEach(function(p) { if (p.isConnected) p.normalize(); });
  }

  function marksOf(id) {
    return Array.prototype.slice.call(document.querySelectorAll('mark.merlin-highlight[data-highlight-id="' + String(id) + '"]'));
  }

  function applyCommentCounts() {
    Array.prototype.forEach.call(document.querySelectorAll('mark.merlin-highlight[data-comment-count]'), function(el) {
      el.removeAttribute('data-comment-count');
    });
    Object.keys(commentCounts).forEach(function(id) {
      var count = commentCounts[id]; if (!count) return;
      var spans = marksOf(id);
      if (spans.length) spans[spans.length - 1].setAttribute('data-comment-count', String(count));
    });
  }

  function hasSelection() {
    var sel = window.getSelection();
    return !!sel && !sel.isCollapsed && sel.rangeCount > 0;
  }

  function render(highlights) {
    currentHighlights = highlights;
    unwrapAllMarks();
    // Erstellungsreihenfolge = aufsteigende Server-ID; offline angelegte (negative ID) zuletzt.
    var order = function(h) { return h.id > 0 ? h.id : Number.MAX_SAFE_INTEGER; };
    highlights.slice().sort(function(a, b) { return order(a) - order(b); }).forEach(restoreHighlight);
    applyCommentCounts();
  }

  // Neu zeichnen bei jeder Änderung (eigene, Push vom Kommentar-Kanal). Solange eine Auswahl offen
  // ist, nur vormerken – sonst ginge sie verloren.
  function setHighlights(highlights, counts) {
    commentCounts = counts || {};
    if (hasSelection()) { deferred = highlights; return; }
    deferred = null;
    render(highlights);
  }

  function flushDeferred() {
    if (deferred === null || hasSelection()) return;
    var list = deferred; deferred = null;
    render(list);
  }

  function textOf(id) {
    return marksOf(id).map(function(e) { return e.textContent; }).join('');
  }

  function rectOf(nodes) {
    var top = Infinity, left = Infinity, bottom = -Infinity, right = -Infinity;
    nodes.forEach(function(n) {
      var r = n.getBoundingClientRect();
      top = Math.min(top, r.top); left = Math.min(left, r.left);
      bottom = Math.max(bottom, r.bottom); right = Math.max(right, r.right);
    });
    return { x: left, y: top, width: right - left, height: bottom - top };
  }

  function onSelectionChange() {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
      MerlinHighlightBridge.onSelectionCleared();
      flushDeferred();
      return;
    }
    var range = sel.getRangeAt(0);
    var rect = range.getBoundingClientRect();
    MerlinHighlightBridge.onSelectionChanged(JSON.stringify({ x: rect.left, y: rect.top, width: rect.width, height: rect.height }));
  }

  function selectionAnchor() {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) return null;
    var range = sel.getRangeAt(0);
    var text = range.toString();
    if (!text.trim()) return null;
    return {
      highlightedText: text,
      startXpath: getXPath(range.startContainer),
      startOffset: range.startOffset,
      endXpath: getXPath(range.endContainer),
      endOffset: range.endOffset
    };
  }

  function createHighlightFromSelection(color) {
    var anchor = selectionAnchor();
    if (!anchor) return;
    anchor.color = color;
    window.getSelection().removeAllRanges();
    MerlinHighlightBridge.onCreateHighlight(JSON.stringify(anchor));
  }

  // „Kommentieren“ an einer frischen Auswahl: noch nichts einfärben, Kotlin öffnet das
  // Kommentarfeld mit der zitierten Stelle; erst der abgeschickte Kommentar legt sie an.
  function commentFromSelection() {
    var anchor = selectionAnchor();
    if (!anchor) return;
    window.getSelection().removeAllRanges();
    MerlinHighlightBridge.onCommentAnchor(JSON.stringify(anchor));
  }

  // „Im Text zeigen“ aus dem Kommentar-Sheet.
  function reveal(id) {
    var spans = marksOf(id);
    if (!spans.length) return;
    spans[0].scrollIntoView({ block: 'center', behavior: 'smooth' });
    spans.forEach(function(s) { s.classList.add('merlin-highlight--flash'); });
    setTimeout(function() { spans.forEach(function(s) { s.classList.remove('merlin-highlight--flash'); }); }, 1200);
  }

  document.addEventListener('click', function(ev) {
    var mark = ev.target.closest && ev.target.closest('mark.merlin-highlight');
    if (!mark || hasSelection()) return;
    ev.preventDefault();
    ev.stopPropagation();
    var id = mark.getAttribute('data-highlight-id');
    // Unterstrichene Kommentarstelle: gleich die Kommentare, es gibt keine Farbe zu ändern.
    if (mark.getAttribute('data-highlight-color') === COMMENT_COLOR) {
      MerlinHighlightBridge.onOpenComments(parseInt(id, 10), textOf(id));
      return;
    }
    MerlinHighlightBridge.onHighlightTap(parseInt(id, 10), JSON.stringify(rectOf(marksOf(id))));
  }, true);

  document.addEventListener('selectionchange', onSelectionChange);

  document.addEventListener('click', function(ev) {
    var img = ev.target.closest && ev.target.closest('img');
    if (img) {
      // Das Icon der Support-Infobox ist kein Artikelbild: kein Lightbox-Tap, nicht in der Bildliste.
      // Ebenso kein Artikelbild: Thumbnail einer YouTube-Karte (eigener Tap in YOUTUBE_JS) und das
      // ausgeblendete Vorschaubild eines Inline-Videos, über dem bereits der Player liegt.
      var notLightbox = 'merlin-support-box,.merlin-yt-embed,.merlin-inline-media--playable';
      if (img.closest(notLightbox)) return;
      var imgs = Array.prototype.slice.call(document.querySelectorAll('img')).filter(function(i) { return !i.closest(notLightbox); });
      var index = imgs.indexOf(img);
      var srcs = imgs.map(function(i) { return i.src; });
      MerlinHighlightBridge.onImageTap(JSON.stringify({ index: index, srcs: srcs }));
    }
  });

  // Metazeilen-Popover im Plakat-Header (Äquivalent zu iOS' `.popover`-Flyouts): "Von" nur bei
  // tatsächlicher Truncation (scrollWidth>clientWidth), "Erschienen" (data-tap-kind="date")
  // immer – siehe `buildHeaderHtml`-Kommentar für die Auswahllogik.
  document.addEventListener('click', function(ev) {
    if (!ev.target.closest) return;
    var site = ev.target.closest('#merlin-header [data-open-url]');
    if (site) {
      MerlinHighlightBridge.onOpenExternalLink(site.getAttribute('data-open-url'));
      return;
    }
    var cell = ev.target.closest('#merlin-header [data-tap-kind]');
    if (!cell) return;
    var kind = cell.getAttribute('data-tap-kind');
    if (kind === 'author' && cell.scrollWidth <= cell.clientWidth + 1) return;
    var label = cell.getAttribute('data-popover-label') || '';
    var value = cell.getAttribute('data-popover-value') || '';
    if (!value) return;
    // Rect des angetippten Segments (CSS-px, bereits scroll-relativ wie bei `onSelectionChange`
    // oben) – Kotlin braucht die Position, um das Popover über/unter dem Segment zu verankern
    // statt es wirkungslos zentriert anzuzeigen (siehe `InfoPopoverOverlay`).
    var rect = cell.getBoundingClientRect();
    MerlinHighlightBridge.onInfoPopover(JSON.stringify({
      label: label, value: value,
      x: rect.left, y: rect.top, width: rect.width, height: rect.height
    }));
  });

  // Plakat-Header: Punkt hinter dem Autor nur bei abgeschnittenem Namen zeigen und die Unterkante
  // der Akzentfläche (Header bzw. führendes Titelbild samt Bildunterschrift) an Kotlin melden –
  // dort färbt sich die Statusleiste in der Akzentfarbe, solange die Fläche darunter liegt.
  function layoutPoster() {
    var author = document.querySelector('.merlin-poster-meta-author');
    if (author) {
      author.parentNode.classList.toggle('merlin-poster-meta--truncated', author.scrollWidth > author.clientWidth + 1);
    }
    var poster = document.querySelector('#merlin-header .merlin-poster');
    if (!poster) return;
    var bottom = poster.getBoundingClientRect().bottom;
    if (document.body.classList.contains('merlin-leads-hero')) {
      var hero = document.querySelector('#merlin-content > figure:first-child, #merlin-content > img:first-child');
      if (hero) bottom = hero.getBoundingClientRect().bottom;
    }
    if (MerlinHighlightBridge.onPosterBottom) {
      MerlinHighlightBridge.onPosterBottom(bottom + (window.scrollY || 0));
    }
  }
  layoutPoster();
  window.addEventListener('load', layoutPoster);
  window.addEventListener('resize', layoutPoster);
  document.addEventListener('load', function(ev) {
    if (ev.target && ev.target.tagName === 'IMG') layoutPoster();
  }, true);

  document.addEventListener('error', function(ev) {
    var el = ev.target;
    if (el && el.tagName === 'IMG') {
      // Kaputtes Support-Box-Icon einfach weglassen statt "Bild nicht verfügbar" zu zeigen.
      if (el.closest('merlin-support-box')) { el.remove(); return; }
      // YouTube-Karte ohne Thumbnail: schwarze Fläche + Play-Button reichen.
      if (el.closest('.merlin-yt-embed')) { el.remove(); return; }
      var placeholder = document.createElement('div');
      placeholder.className = 'merlin-img-error';
      placeholder.textContent = 'Bild nicht verfügbar';
      el.replaceWith(placeholder);
    }
  }, true);

  return {
    setHighlights: setHighlights,
    createHighlightFromSelection: createHighlightFromSelection,
    commentFromSelection: commentFromSelection,
    reveal: reveal
  };
})();
"""
}

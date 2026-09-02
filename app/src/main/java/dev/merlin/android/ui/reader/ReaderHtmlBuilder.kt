package dev.merlin.android.ui.reader

import dev.merlin.android.models.Article
import dev.merlin.android.models.Highlight
import dev.merlin.android.models.ReaderTheme
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

    /** Eine Zelle der Info-Card (`buildHeaderHtml`); `tapKind` != null macht sie per `READER_JS` antippbar. */
    private data class InfoCell(
        val label: String,
        val value: String,
        val tapKind: String? = null,
        val popoverLabel: String? = null,
        val popoverValue: String? = null,
    )

    fun build(
        article: Article,
        highlights: List<Highlight>,
        appearance: ArticleReaderViewModel.Appearance,
        // System-Dark-Mode-Signal von außen (Compose `isSystemInDarkTheme()`) – die WebView selbst
        // kennt den App-weiten Dark-Mode-Status nicht, siehe `buildCss`-Kommentar zu AUTO.
        isSystemDark: Boolean,
    ): String {
        val bodyHtml = article.content ?: "<p>${escapeHtml(article.excerpt ?: "")}</p>"
        val (_, fg, mutedFg) = themeColors(appearance.theme, isSystemDark)
        val css = buildCss(appearance, isSystemDark)
        val headerHtml = buildHeaderHtml(article, fg, mutedFg, appearance.accentColorHex)
        val footerHtml = buildFooterHtml(article)
        val highlightsJson = encodeHighlightsForJs(highlights)

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <style>$css</style>
            </head>
            <body>
              $headerHtml
              <div id="merlin-content">$bodyHtml$footerHtml</div>
              <script>$READER_JS</script>
              <script>window.__MERLIN_INIT_HIGHLIGHTS__ = $highlightsJson; MerlinReader.restoreHighlights(window.__MERLIN_INIT_HIGHLIGHTS__);</script>
            </body>
            </html>
        """.trimIndent()
    }

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
     * Äquivalent zu `articleHeader` im iOS-Original (ArticleReaderView.swift, Zeilen ~1628–1812):
     * Site-Zeile (Akzent-Balken + Sitename), Titel, Teaser/Excerpt, Info-Card
     * (Autor · Lesezeit · Erschienen/Gespeichert) und Tags – in dieser Reihenfolge.
     * Lebte ursprünglich als eigene, sticky Compose-Zeile *über* der WebView; jetzt Teil des
     * WebView-eigenen Scroll-Inhalts, damit der komplette Header (inkl. Titel + Teaser) wie auf
     * iOS ganz normal mit dem Artikeltext mitscrollt statt fixiert zu bleiben. Die native
     * TopAppBar zeigt den Titel deshalb nicht mehr an (siehe `ArticleReaderScreen.kt`) – er
     * existiert nur noch einmal, hier im scrollenden Header.
     */
    private fun buildHeaderHtml(article: Article, fg: String, mutedFg: String, accentColorHex: String): String {
        val siteHtml = if (article.displaySiteName.isNotBlank()) {
            """
            <div class="merlin-header-site">
              <span class="merlin-header-accent"></span>
              <span>${escapeHtml(article.displaySiteName.uppercase())}</span>
            </div>
            """.trimIndent()
        } else ""

        val excerptHtml = if (!article.excerpt.isNullOrBlank()) {
            "<div class=\"merlin-header-excerpt\">${escapeHtml(article.excerpt)}</div>"
        } else ""

        // `tapKind`/`popoverLabel`/`popoverValue` treiben den Klick-Handler in `READER_JS`
        // (Äquivalent zu iOS' `.popover`-Flyouts in ArticleReaderView.swift, Zeilen ~1683–1781):
        // "author" zeigt den vollen Wert nur, wenn die einzeilige CSS-Truncation (s.u.) wirklich
        // greift; "date" (nur gesetzt, wenn sowohl `publishedAt` als auch `createdAt` bekannt
        // sind, also die Zelle "ERSCHIENEN" zeigt) öffnet immer ein Popover mit dem
        // Hinzugefügt-Datum – fehlt `publishedAt`, zeigt die Zelle ohnehin schon `createdAt`
        // direkt ("GESPEICHERT"), ein Popover wäre dort redundant (1:1 iOS-Logik).
        val infoCells = mutableListOf<InfoCell>()
        if (!article.author.isNullOrBlank()) {
            infoCells.add(InfoCell("VON", escapeHtml(article.author), tapKind = "author"))
        }
        if (article.readingTime > 0) infoCells.add(InfoCell("LESEZEIT", "${article.readingTime} min"))
        val publishedFormatted = formatHeaderDate(article.publishedAt)
        val createdFormatted = formatHeaderDate(article.createdAt)
        if (publishedFormatted != null) {
            infoCells.add(
                InfoCell(
                    "ERSCHIENEN",
                    publishedFormatted,
                    tapKind = if (createdFormatted != null) "date" else null,
                    popoverLabel = "Hinzugefügt am",
                    popoverValue = createdFormatted,
                )
            )
        } else if (createdFormatted != null) {
            infoCells.add(InfoCell("GESPEICHERT", createdFormatted))
        }

        val infoCardHtml = if (infoCells.isNotEmpty()) {
            "<div class=\"merlin-header-info\">" +
                infoCells.joinToString("") { cell ->
                    val attrs = if (cell.tapKind != null) {
                        " data-tap-kind=\"${cell.tapKind}\"" +
                            " data-popover-label=\"${escapeHtmlAttr(cell.popoverLabel ?: cell.label)}\"" +
                            " data-popover-value=\"${escapeHtmlAttr(cell.popoverValue ?: cell.value)}\""
                    } else ""
                    "<div class=\"merlin-header-info-cell\"$attrs><span class=\"merlin-header-info-label\">${cell.label}</span><span class=\"merlin-header-info-value\">${cell.value}</span></div>"
                } +
                "</div>"
        } else ""

        val tagsHtml = if (article.tags.isNotEmpty()) {
            "<div class=\"merlin-header-tags\">" +
                article.tags.joinToString("") { tag ->
                    val hex = tag.color?.takeIf { it.isNotBlank() } ?: "#8E8E93"
                    val bg = hexToRgba(hex, 0.12)
                    val border = hexToRgba(hex, 0.3)
                    "<span style=\"color:$hex;background:$bg;border-color:$border;\">${escapeHtml(tag.name)}</span>"
                } +
                "</div>"
        } else ""

        return """
            <div id="merlin-header">
              $siteHtml
              <div class="merlin-header-title">${escapeHtml(article.displayTitle)}</div>
              $excerptHtml
              $infoCardHtml
              $tagsHtml
            </div>
        """.trimIndent()
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

    /** Hex-Farbe (`#RRGGBB`) → `rgba(r,g,b,alpha)`-CSS-String für die Tag-Pills. */
    private fun hexToRgba(hex: String, alpha: Double): String = runCatching {
        val color = android.graphics.Color.parseColor(hex)
        "rgba(${android.graphics.Color.red(color)},${android.graphics.Color.green(color)},${android.graphics.Color.blue(color)},$alpha)"
    }.getOrDefault("rgba(142,142,147,$alpha)")

    private fun buildCss(appearance: ArticleReaderViewModel.Appearance, isSystemDark: Boolean): String {
        val (bg, fg, mutedFg) = themeColors(appearance.theme, isSystemDark)
        return """
            html, body {
              margin: 0; padding: 0;
              background-color: $bg;
              color: $fg;
              font-family: ${appearance.font.cssValue};
              font-size: ${appearance.fontSize}px;
              line-height: ${appearance.lineHeight};
              -webkit-text-size-adjust: 100%;
            }
            #merlin-header { padding: 16px 20px 8px; }
            .merlin-header-site { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
            .merlin-header-accent { width: 18px; height: 3px; border-radius: 2px; background: ${appearance.accentColorHex}; flex-shrink: 0; }
            .merlin-header-site span:last-child { font-size: 12px; font-weight: 600; letter-spacing: 1.2px; color: $mutedFg; }
            .merlin-header-title { font-size: ${appearance.fontSize + 5}px; font-weight: 700; line-height: 1.25; color: $fg; }
            .merlin-header-excerpt { margin-top: 8px; font-size: ${appearance.fontSize - 1}px; line-height: 1.4; color: $mutedFg; }
            .merlin-header-info {
              margin-top: 14px; display: flex; border: 1px solid rgba(127,127,127,0.25);
              border-radius: 10px; overflow: hidden;
            }
            .merlin-header-info-cell {
              flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; padding: 8px 10px;
              border-left: 1px solid rgba(127,127,127,0.25);
            }
            .merlin-header-info-cell:first-child { border-left: none; }
            .merlin-header-info-cell[data-tap-kind] { cursor: pointer; }
            .merlin-header-info-label { font-size: 9px; font-weight: 600; letter-spacing: 1px; color: $mutedFg; }
            /* Niemals zweizeilig (Äquivalent zu iOS' `.lineLimit(1)`): einzeilig abschneiden statt
               umzubrechen – der Klick-Handler in READER_JS erkennt die Truncation per
               scrollWidth/clientWidth und bietet dann (nur für "Von") ein natives Popover an. */
            .merlin-header-info-value {
              font-size: 12px; font-weight: 600; color: $fg;
              display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
            }
            .merlin-header-tags { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px; }
            .merlin-header-tags span {
              font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 12px;
              border: 1px solid transparent;
            }
            #merlin-content { padding: 8px 20px 48px; }
            /* Erstes Element direkt unter dem Header (i. d. R. das Hero-Bild) bekommt keinen
               eigenen Top-Margin – sonst summiert sich der Innenabstand von #merlin-header,
               #merlin-content und der figure-Margin zu einem unnötig großen Abstand. */
            #merlin-content > *:first-child { margin-top: 0; }
            img, video { max-width: 100%; height: auto; border-radius: 8px; display: block; }
            figure { margin: 1em 0; }
            figcaption { color: ${appearance.accentColorHex}; font-size: 0.85em; margin-top: 6px; }
            /* Links in Textfarbe statt Akzentfarbe (Unterstreichung bleibt einziges
               Unterscheidungsmerkmal zu normalem Fließtext). */
            a { color: $fg; text-decoration: underline; }
            p, li { margin: 0 0 1em 0; }
            h1, h2, h3 { line-height: 1.25; }
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
            blockquote {
              margin: 1em 0; padding-left: 1em;
              border-left: 3px solid ${appearance.accentColorHex};
              opacity: 0.85;
            }
            .merlin-infobox {
              background: ${hexToRgba(appearance.accentColorHex, 0.1)};
              border-left: 4px solid ${appearance.accentColorHex};
              border-radius: 0 8px 8px 0;
              padding: 14px 16px;
              margin: 1.5em 0;
              font-size: 0.93em;
              line-height: 1.6;
              color: $fg;
            }
            .merlin-infobox > *:first-child { margin-top: 0; }
            .merlin-infobox > *:last-child { margin-bottom: 0; }
            .merlin-infobox a { color: ${appearance.accentColorHex} !important; }
            pre, code { font-family: ${dev.merlin.android.models.ReaderFont.MONO.cssValue}; }
            .merlin-highlight { border-radius: 2px; padding: 0 1px; cursor: pointer; }
            .merlin-img-error {
              display: flex; align-items: center; justify-content: center;
              background: rgba(127,127,127,0.15); border-radius: 8px;
              min-height: 80px; color: $fg; opacity: 0.6; font-size: 0.85em;
            }
            .merlin-footer-byline {
              margin-top: 32px; padding-top: 16px;
              border-top: 1px solid rgba(127,127,127,0.25);
              font-size: 0.9em; font-style: italic; color: $mutedFg;
            }
        """.trimIndent()
    }

    /** Highlight-Farbpalette – fix im Client, identisch zu iOS. */
    val HIGHLIGHT_COLORS = mapOf(
        "yellow" to "#fde68a",
        "green" to "#bbf7d0",
        "blue" to "#bfdbfe",
        "pink" to "#fbcfe8",
        "orange" to "#fed7aa",
    )

    private fun encodeHighlightsForJs(highlights: List<Highlight>): String {
        val items = highlights.joinToString(",") { h ->
            """{"id":${h.id},"startXpath":${jsString(h.startXpath)},"startOffset":${h.startOffset},"endXpath":${jsString(h.endXpath)},"endOffset":${h.endOffset},"color":${jsString(h.color)}}"""
        }
        return "[$items]"
    }

    private fun jsString(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** Wie [escapeHtml], zusätzlich Anführungszeichen-sicher für die Verwendung in `data-*`-Attributwerten. */
    private fun escapeHtmlAttr(value: String): String = escapeHtml(value).replace("\"", "&quot;")

    /**
     * JS-Laufzeit im WebView: XPath-Generierung/-Auflösung (`tag[n]`/`text()[n]`
     * relativ zu `document.body`, wie im iOS-Original), Highlight-Erstellung
     * über Text-Selektion, Restore in umgekehrter Dokumentreihenfolge (damit
     * `Range.splitText` spätere Highlights nicht vor früheren XPaths
     * invalidiert), Tap-Handling für bestehende Highlights (Löschen) und
     * Bilder (Lightbox – aktuell nur Platzhalter-Stub, siehe `todo.md`).
     */
    private const val READER_JS = """
window.MerlinReader = (function() {
  function getXPath(node) {
    if (node === document.body) return '';
    var parts = [];
    var current = node;
    while (current && current !== document.body) {
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

  function resolveXPath(xpath) {
    if (!xpath) return document.body;
    var node = document.body;
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

  function rangeFromHighlight(h) {
    var startNode = resolveXPath(h.startXpath);
    var endNode = resolveXPath(h.endXpath);
    if (!startNode || !endNode) return null;
    var range = document.createRange();
    try {
      range.setStart(startNode, h.startOffset);
      range.setEnd(endNode, h.endOffset);
    } catch (e) { return null; }
    return range;
  }

  function wrapRange(range, color, id) {
    var span = document.createElement('span');
    span.className = 'merlin-highlight';
    span.style.backgroundColor = color;
    // Highlight-Farben sind alle helle Pastelltöne, daher fixe dunkle
    // Schrift statt geerbter Textfarbe – im Dark-Theme ist die geerbte
    // Schrift sonst fast weiß und auf dem hellen Hintergrund unlesbar.
    span.style.color = '#1c1c1e';
    span.setAttribute('data-highlight-id', id);
    try {
      range.surroundContents(span);
    } catch (e) {
      // surroundContents schlägt fehl, wenn die Range mehrere Elementgrenzen
      // überspannt – Fallback: Inhalt extrahieren und in den Span einfügen.
      var content = range.extractContents();
      span.appendChild(content);
      range.insertNode(span);
    }
    span.addEventListener('click', function(ev) {
      ev.stopPropagation();
      MerlinHighlightBridge.onHighlightTap(id);
    });
  }

  function restoreHighlights(highlights) {
    // Reverse Dokumentreihenfolge: spätere Highlights zuerst anwenden, damit
    // ihr DOM-Splitting frühere XPaths nicht verschiebt (1:1 vom iOS-Original).
    var sorted = highlights.slice().reverse();
    sorted.forEach(function(h) {
      var range = rangeFromHighlight(h);
      if (range) wrapRange(range, h.color, h.id);
    });
  }

  function onSelectionChange() {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
      MerlinHighlightBridge.onSelectionCleared();
      return;
    }
    var range = sel.getRangeAt(0);
    var rect = range.getBoundingClientRect();
    MerlinHighlightBridge.onSelectionChanged(JSON.stringify({ x: rect.left, y: rect.top, width: rect.width, height: rect.height }));
  }

  function createHighlightFromSelection(color) {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) return;
    var range = sel.getRangeAt(0);
    var text = range.toString();
    var startXpath = getXPath(range.startContainer);
    var endXpath = getXPath(range.endContainer);
    var payload = {
      highlightedText: text,
      startXpath: startXpath,
      startOffset: range.startOffset,
      endXpath: endXpath,
      endOffset: range.endOffset,
      color: color
    };
    sel.removeAllRanges();
    MerlinHighlightBridge.onCreateHighlight(JSON.stringify(payload));
  }

  document.addEventListener('selectionchange', onSelectionChange);

  document.addEventListener('click', function(ev) {
    var img = ev.target.closest && ev.target.closest('img');
    if (img) {
      var imgs = Array.prototype.slice.call(document.querySelectorAll('img'));
      var index = imgs.indexOf(img);
      var srcs = imgs.map(function(i) { return i.src; });
      MerlinHighlightBridge.onImageTap(JSON.stringify({ index: index, srcs: srcs }));
    }
  });

  // Info-Card-Popover (Äquivalent zu iOS' `.popover`-Flyouts): "Von" nur bei
  // tatsächlicher Truncation (scrollWidth>clientWidth), "Erschienen" (data-tap-kind="date")
  // immer – siehe `buildHeaderHtml`-Kommentar für die Auswahllogik.
  document.addEventListener('click', function(ev) {
    var cell = ev.target.closest && ev.target.closest('.merlin-header-info-cell[data-tap-kind]');
    if (!cell) return;
    var kind = cell.getAttribute('data-tap-kind');
    if (kind === 'author') {
      var valueEl = cell.querySelector('.merlin-header-info-value');
      if (!valueEl || valueEl.scrollWidth <= valueEl.clientWidth + 1) return;
    }
    var label = cell.getAttribute('data-popover-label') || '';
    var value = cell.getAttribute('data-popover-value') || '';
    if (!value) return;
    // Rect der angetippten Zelle (CSS-px, bereits scroll-relativ wie bei `onSelectionChange`
    // oben) – Kotlin braucht die Position, um das Popover über/unter der Zelle zu verankern
    // statt es wirkungslos zentriert anzuzeigen (siehe `InfoPopoverOverlay`).
    var rect = cell.getBoundingClientRect();
    MerlinHighlightBridge.onInfoPopover(JSON.stringify({
      label: label, value: value,
      x: rect.left, y: rect.top, width: rect.width, height: rect.height
    }));
  });

  document.addEventListener('error', function(ev) {
    var el = ev.target;
    if (el && el.tagName === 'IMG') {
      var placeholder = document.createElement('div');
      placeholder.className = 'merlin-img-error';
      placeholder.textContent = 'Bild nicht verfügbar';
      el.replaceWith(placeholder);
    }
  }, true);

  return {
    restoreHighlights: restoreHighlights,
    createHighlightFromSelection: createHighlightFromSelection
  };
})();
"""
}

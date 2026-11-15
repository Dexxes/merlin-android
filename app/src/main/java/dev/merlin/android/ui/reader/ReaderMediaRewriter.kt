package dev.merlin.android.ui.reader

/**
 * HTML-Umbauten für Videos im Artikeltext – Port der entsprechenden Teile von
 * `ArticleReaderView.swift` (`rewriteYouTubeEmbeds`) und `MediaMarker.swift`
 * (`parse`/`promoteHeroVideo`) aus merlin-ios. Die Ausgabe ist bewusst Markup-identisch zu iOS,
 * damit die Highlight-XPaths (`tag[n]`/`text()[n]`, siehe `READER_JS`) auf beiden Plattformen
 * gegen denselben DOM aufgelöst werden.
 *
 * Abgedeckte Video-Quellen (zusammen mit [NativeVideoPlayerCard] für ARD/ZDF/3sat/Arte):
 *  - YouTube-`<iframe>`-Embeds → tippbare Vorschau-Karte (`.merlin-yt-embed`), die `YOUTUBE_JS`
 *    beim Antippen durch den Player ersetzt.
 *  - Videos mitten im Text (`figure.merlin-inline-media`, vom Server per `InlineMediaService`
 *    erzeugt, z. B. der ARD-Player bei rbb24.de) → `INLINE_MEDIA_JS` legt ein `<video>` darüber.
 *  - Aufmacher-Video eines Textartikels (Medien-Marker `div.merlin-media` mit `kind == video` und
 *    https-Datei/HLS, z. B. das JSON-LD-VideoObject bei tagesschau.de) → [promoteHeroVideo] macht
 *    das führende Hero Image zur Inline-Medien-Figure, damit derselbe Player mit dem Hero Image als
 *    Poster darauf landet.
 */
object ReaderMediaRewriter {

    private val dotAll = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    // MARK: – Medien-Marker (div.merlin-media)

    private val markerDivRegex = Regex("""<div\b[^>]*\bmerlin-media\b[^>]*>.*?</div>""", dotAll)
    private val fallbackLinkRegex = Regex("""<a\b[^>]*\bmerlin-media-fallback-link\b[^>]*>.*?</a>""", dotAll)

    /** Inhalt des ersten Medien-Markers; `delivery`/`src` nur bei https-Quelle mit bekannter Auslieferung. */
    data class Marker(val kind: String, val delivery: String?, val src: String?)

    fun parseMarker(html: String): Marker? {
        if (!html.contains("merlin-media")) return null
        val tag = markerDivRegex.find(html)?.value ?: return null
        val kind = attribute("data-media-kind", tag)?.takeIf { it == "audio" || it == "video" } ?: return null
        val delivery = attribute("data-media-delivery", tag)
        val src = attribute("data-media-src", tag)
        if (delivery == null || delivery !in setOf("hls", "file", "embed") || src == null || !src.startsWith("https://")) {
            return Marker(kind, null, null)
        }
        return Marker(kind, delivery, src)
    }

    private fun attribute(name: String, tag: String): String? =
        Regex(Regex.escape(name) + """\s*=\s*"([^"]*)"""").find(tag)?.groupValues?.get(1)?.replace("&amp;", "&")

    // MARK: – Aufmacher-Video

    private val leadingHeroRegex = Regex("""^\s*(?:<figure\b[^>]*>.*?</figure>|<img\b[^>]*>)""", dotAll)
    private val figureOpenTagRegex = Regex("""^\s*<figure\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val classAttributeRegex = Regex("""\bclass\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val figureInsertRegex = Regex("""<figcaption\b|</figure>""", RegexOption.IGNORE_CASE)

    /**
     * Port von `MediaMarker.promoteHeroVideo(in:)`: Das führende Hero Image wird zur
     * Inline-Medien-Figure (`figure.merlin-inline-media` mit `div.merlin-inline-media-source`), auf
     * die `INLINE_MEDIA_JS` den Player mit dem Hero Image als Poster legt. Der ursprüngliche Marker
     * bleibt im DOM und wird nur per `display:none` ausgeblendet (Tag-Zähler der Highlight-XPaths
     * bleiben stabil); seinen „Zum Video“-Link übernimmt die Figure.
     */
    fun promoteHeroVideo(html: String): String {
        val marker = parseMarker(html) ?: return html
        if (marker.kind != "video") return html
        val delivery = marker.delivery?.takeIf { it == "file" || it == "hls" } ?: return html
        val src = marker.src ?: return html
        val markerMatch = markerDivRegex.find(html) ?: return html
        val link = fallbackLinkRegex.find(markerMatch.value)?.value.orEmpty()

        fun escaped(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;")
        val sourceDiv = "<div class=\"merlin-inline-media-source\" data-media-kind=\"video\"" +
            " data-media-delivery=\"$delivery\" data-media-src=\"${escaped(src)}\">" +
            link + "</div>"

        // Marker ausblenden (vor dem Umbau der Figure, die davor steht – Offsets bleiben gültig).
        val markerStart = markerMatch.range.first
        var result = html.replaceRange(markerStart, markerStart + 4, "<div style=\"display:none\"")

        val hero = leadingHeroRegex.find(result)
        val openTag = hero?.let { figureOpenTagRegex.find(it.value) }
        if (hero == null || hero.range.last + 1 > markerStart || openTag == null) {
            // Kein führendes Hero-Figure: Player ohne Poster ganz oben.
            return "<figure class=\"merlin-inline-media\">$sourceDiv</figure>$result"
        }

        // sourceDiv vor der figcaption (bzw. vor </figure>) einsetzen …
        figureInsertRegex.find(hero.value)?.let { insert ->
            val at = hero.range.first + insert.range.first
            result = result.substring(0, at) + sourceDiv + result.substring(at)
        }

        // … und die Figure als Inline-Medien-Figure markieren (das Open-Tag liegt vor der
        // Einfügestelle, sein Bereich ist also unverändert).
        val tagStart = hero.range.first + openTag.range.first
        val tagEnd = hero.range.first + openTag.range.last + 1
        val tag = result.substring(tagStart, tagEnd)
        val cls = classAttributeRegex.find(tag)
        val newTag = if (cls != null) {
            val at = cls.groups[1]!!.range.first
            tag.substring(0, at) + "merlin-inline-media " + tag.substring(at)
        } else {
            val at = tag.indexOf("<figure", ignoreCase = true) + "<figure".length
            tag.substring(0, at) + " class=\"merlin-inline-media\"" + tag.substring(at)
        }
        return result.substring(0, tagStart) + newTag + result.substring(tagEnd)
    }

    // MARK: – YouTube-Embeds

    private val iframeTagRegex = Regex("""<iframe\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** `(?<![\w-])` verhindert, dass z. B. "data-src" fälschlich als "src" erkannt wird. */
    private val srcAttrRegex = Regex("""(?<![\w-])src\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

    /** Video-ID (Gruppe 1) + optionaler Query-String (Gruppe 2); `-nocookie` optional wie im Backend-Sanitizer. */
    private val youtubeEmbedSrcRegex = Regex(
        """^https://(?:www\.)?youtube(?:-nocookie)?\.com/embed/([A-Za-z0-9_-]+)(?:\?(.*))?$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Ersetzt jedes `<iframe src="https://[www.]youtube[-nocookie].com/embed/ID…">` durch eine
     * tippbare Vorschau-Karte (`.merlin-yt-embed`, Thumbnail + Play-Button) – 1:1 das Markup von
     * `rewriteYouTubeEmbeds` in `ArticleReaderView.swift`.
     *
     * **Abweichung von iOS beim Abspielen:** iOS kann das YouTube-`<iframe>` in seiner
     * `file://`-Reader-Seite nicht einbetten (Error 153, kein gültiger Referrer) und legt deshalb
     * eine eigene Top-Level-`WKWebView` als Overlay über die Karte. Der Android-Reader lädt den
     * Artikel per `loadDataWithBaseURL(article.url, …)` mit echter https-Origin; dort darf das
     * `<iframe>` direkt in der Seite sitzen (`YOUTUBE_JS` setzt es beim Antippen in die Karte ein,
     * mit `referrerpolicy="strict-origin-when-cross-origin"`, damit YouTube die Origin prüfen kann).
     * Die Karte statt des Original-`<iframe>`s bleibt trotzdem: keine YouTube-Requests/Cookies,
     * bevor der Nutzer das Video wirklich abspielen will.
     */
    fun rewriteYouTubeEmbeds(content: String): String = iframeTagRegex.replace(content) { tagMatch ->
        val tag = tagMatch.value
        val rawSrc = srcAttrRegex.find(tag)?.groupValues?.get(1) ?: return@replace tag
        val decodedSrc = rawSrc
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        // Kein YouTube-Embed: unverändert lassen (die Backend-Allowlist lässt weitere Anbieter wie
        // Vimeo durch, die als normales <iframe> in der https-Seite ohnehin funktionieren).
        val embed = youtubeEmbedSrcRegex.find(decodedSrc) ?: return@replace tag
        val videoId = embed.groupValues[1]

        // Startzeit best-effort aus der Original-Query (?start=90 oder ?t=90 / ?t=90s).
        val startSeconds = embed.groups[2]?.value
            ?.split("&")
            ?.mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2 && (parts[0] == "start" || parts[0] == "t")) {
                    parts[1].filter(Char::isDigit).takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
            ?.firstOrNull()
            .orEmpty()

        // videoId/startSeconds bestehen nur aus [A-Za-z0-9_-] bzw. Ziffern – kein Escaping nötig.
        "<div class=\"merlin-yt-embed\" data-yt-id=\"$videoId\" data-yt-start=\"$startSeconds\" " +
            "style=\"position:relative;cursor:pointer;border-radius:8px;overflow:hidden;background:#000;aspect-ratio:16/9;margin:8px 0;\">" +
            "<img src=\"https://i.ytimg.com/vi/$videoId/hqdefault.jpg\" alt=\"\" " +
            "style=\"width:100%;height:100%;object-fit:cover;display:block;margin:0;border-radius:0;\">" +
            "<div style=\"position:absolute;inset:0;display:flex;align-items:center;justify-content:center;\">" +
            "<div style=\"width:56px;height:56px;border-radius:50%;background:rgba(0,0,0,0.65);display:flex;align-items:center;justify-content:center;\">" +
            "<svg width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"white\"><path d=\"M8 5v14l11-7z\"/></svg>" +
            "</div></div></div>"
    }

    // MARK: – Laufzeit-JS

    /**
     * Antippen einer `.merlin-yt-embed`-Karte: Thumbnail + Play-Button ausblenden (bleiben im DOM)
     * und den youtube-nocookie-Player als `<iframe>` in die Karte setzen, die ihre 16:9-Fläche
     * weiter reserviert.
     */
    const val YOUTUBE_JS = """
(function(){
  document.querySelectorAll('.merlin-yt-embed').forEach(function(card){
    card.addEventListener('click',function(e){
      if(card.getAttribute('data-yt-active'))return;
      e.stopPropagation();
      e.preventDefault();
      var id=card.getAttribute('data-yt-id')||'';
      if(!id)return;
      card.setAttribute('data-yt-active','1');
      var src='https://www.youtube-nocookie.com/embed/'+encodeURIComponent(id)
        +'?autoplay=1&playsinline=1&controls=1&modestbranding=1&rel=0';
      var start=card.getAttribute('data-yt-start')||'';
      if(start)src+='&start='+encodeURIComponent(start);
      Array.prototype.forEach.call(card.children,function(c){c.style.display='none';});
      var f=document.createElement('iframe');
      f.setAttribute('allow','autoplay; encrypted-media; picture-in-picture; fullscreen');
      f.setAttribute('allowfullscreen','');
      f.setAttribute('referrerpolicy','strict-origin-when-cross-origin');
      f.style.cssText='position:absolute;inset:0;width:100%;height:100%;border:0;';
      f.src=src;
      card.style.cursor='default';
      card.appendChild(f);
    });
  });
})();
"""

    /**
     * Port von `merlinInlineMediaJS` aus `ArticleReaderView.swift` (bzw. `src/inline-media.js` im
     * Web): legt auf jede `figure.merlin-inline-media` einen nativen `<video>`/`<audio>`-Player mit
     * dem Vorschaubild als Poster. Bild, Marker und figcaption bleiben im DOM und werden nur per CSS
     * ausgeblendet; der Player steckt in einem eigenen Element `<merlin-inline-player>`, das die
     * Tag-Zähler der Highlight-XPaths für img/div/figcaption nicht verschiebt. Scheitert die
     * Wiedergabe, verschwindet der Player wieder und Bild samt „Zum Video“-Link sind sichtbar.
     */
    const val INLINE_MEDIA_JS = """
(function(){
  var PLAYABLE='merlin-inline-media--playable';
  document.querySelectorAll('figure.merlin-inline-media').forEach(function(figure){
    if(figure.querySelector('merlin-inline-player'))return;
    var source=figure.querySelector('div.merlin-inline-media-source[data-media-kind]');
    if(!source)return;
    var kind=source.getAttribute('data-media-kind');
    var delivery=source.getAttribute('data-media-delivery');
    var src=source.getAttribute('data-media-src')||'';
    if((kind!=='video'&&kind!=='audio')||(delivery!=='file'&&delivery!=='hls')||src.indexOf('https://')!==0)return;

    var media=document.createElement(kind);
    media.controls=true;
    media.preload='none';
    media.setAttribute('playsinline','');
    var poster=figure.querySelector('img');
    if(kind==='video'&&poster&&(poster.currentSrc||poster.src))media.poster=poster.currentSrc||poster.src;
    media.src=src;

    var player=document.createElement('merlin-inline-player');
    player.className='merlin-inline-player--'+kind;
    player.appendChild(media);
    figure.insertBefore(player,figure.firstChild);
    figure.classList.add(PLAYABLE);

    media.addEventListener('error',function(){
      if(player.parentNode)player.parentNode.removeChild(player);
      figure.classList.remove(PLAYABLE);
    },{once:true});
  });
})();
"""

    /** CSS zu [INLINE_MEDIA_JS] (aus dem iOS-Reader-Template übernommen). */
    const val CSS = """
            figure.merlin-inline-media.merlin-inline-media--playable > img,
            figure.merlin-inline-media.merlin-inline-media--playable > .merlin-img-error,
            figure.merlin-inline-media.merlin-inline-media--playable > .merlin-inline-media-source {
              display: none;
            }
            figure.merlin-inline-media > .merlin-inline-media-source {
              margin: 4px 0 1em; font-size: 0.85em;
            }
            merlin-inline-player { display: block; margin: 8px 0 0; }
            merlin-inline-player video {
              display: block; width: 100%; height: auto;
              aspect-ratio: auto 16 / 9; max-height: 125vw;
              background: #000; border-radius: 8px; object-fit: contain;
            }
            merlin-inline-player audio { display: block; width: 100%; }
            #merlin-content > figure.merlin-inline-media:first-child merlin-inline-player { margin: 0; }
"""
}

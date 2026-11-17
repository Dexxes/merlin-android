package dev.merlin.android.ui.reader

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.merlin.android.R
import dev.merlin.android.data.QuotePrefs
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: – Zitat-Tafel
//
// Gegenstück zu `QuoteMode.swift` (merlin-ios). Der Knopf „Zitat-Tafel“ über der Farbleiste legt
// den markierten Text allein auf weichgezeichnetes Glas über den ganzen Bildschirm; Leisten und
// Statusleiste sind weg. Darunter „Design“ (Schrift, Größe, Farbe, Weichzeichner, Leuchten), die
// Kästchen Autor/Quelle sowie „Bild speichern“ und „Teilen“. Horizontal wischen wechselt den
// Hintergrund, der Text lässt sich ziehen und mit zwei Fingern zoomen (ohne die Schriftgröße zu
// ändern). Ein Tipp aufs Glas, ✕ oder Zurück schließt die Tafel.
//
// Unterschied zu iOS: dort ist „Original“ ein freigestellter Snapshot der Schrift aus dem
// WKWebView. Android-WebViews lassen sich nicht ohne Hintergrund abfotografieren, daher setzt
// „Original“ den Text neu in Schrift, Größe und Farbe des Readers. Den Hintergrund „Artikel“
// liefert ein Abzug des sichtbaren WebView-Ausschnitts. Das Bild zum Speichern/Teilen wird aus
// denselben Bitmaps zusammengesetzt, die auf dem Bildschirm liegen, sieht also gleich aus.

/** Farben und Angaben aus dem Reader, mit denen die Tafel geöffnet wird. */
data class QuoteStyle(
    /** Reader-Hintergrundfarbe (Glas bei fehlendem Hintergrund, Leuchten). */
    val tint: Int,
    val isDark: Boolean,
    /** Reader-Schriftfarbe (Bedienelemente, „Wie im Artikel“, Zeile mit Autor/Quelle). */
    val textColor: Int,
    val author: String?,
    /** Quelle als Domain ohne „www.“, z. B. „spiegel.de“. */
    val source: String?,
    /** Schriftgröße des Readers in CSS-px (= dp). */
    val fontSize: Float,
    /** Schrift des Readers, für „Original“. */
    val readerTypeface: Typeface,
) {
    companion object {
        fun source(url: String): String? = runCatching { Uri.parse(url).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.removePrefix("www.")
    }
}

/** Eine geöffnete Tafel: der Text und wo er im Artikel stand (Fenster-Pixel, für die Animation). */
data class QuoteSession(
    val text: String,
    val sourceInWindow: Rect?,
    val style: QuoteStyle,
)

/**
 * Schriften für das Zitat. `ORIGINAL` ist die Schrift des Readers. Die iOS-Schriften „Rund“ und
 * Georgia gibt es auf Android nicht, sie fehlen hier; Schreibmaschine/Handschrift/Schreibschrift
 * nutzen die Systemfamilien `serif-monospace`/`casual`/`cursive`, die praktisch jedes Gerät hat.
 */
enum class QuoteFont(@StringRes val titleRes: Int) {
    ORIGINAL(R.string.articleReader_quote_font_original),
    SYSTEM(R.string.articleReader_quote_font_system),
    SERIF(R.string.articleReader_quote_font_serif),
    TYPEWRITER(R.string.articleReader_quote_font_typewriter),
    HANDWRITING(R.string.articleReader_quote_font_handwriting),
    SCRIPT(R.string.articleReader_quote_font_script),
    ;

    fun typeface(reader: Typeface): Typeface = when (this) {
        ORIGINAL -> reader
        SYSTEM -> Typeface.SANS_SERIF
        SERIF -> Typeface.SERIF
        TYPEWRITER -> family("serif-monospace") ?: Typeface.MONOSPACE
        HANDWRITING -> family("casual") ?: Typeface.SANS_SERIF
        SCRIPT -> family("cursive") ?: Typeface.SERIF
    }

    /** Schreibschrift läuft klein, darum etwas größer (wie auf iOS). */
    val sizeFactor: Float get() = if (this == SCRIPT) 1.2f else 1f

    companion object {
        private fun family(name: String): Typeface? =
            Typeface.create(name, Typeface.NORMAL).takeIf { it != Typeface.DEFAULT }

        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

/** Schriftfarben für das Zitat. `AUTO` ist die Schriftfarbe des Readers. */
enum class QuoteTextColor(private val argb: Long, @StringRes val titleRes: Int) {
    AUTO(0, R.string.articleReader_quote_color_auto),
    WHITE(0xFFFFFFFF, R.string.articleReader_quote_color_white),
    BLACK(0xFF121214, R.string.articleReader_quote_color_black),
    RED(0xFFE01C47, R.string.articleReader_quote_color_red),
    AMBER(0xFFF59E0A, R.string.articleReader_quote_color_amber),
    GREEN(0xFF0F9966, R.string.articleReader_quote_color_green),
    BLUE(0xFF2663EB, R.string.articleReader_quote_color_blue),
    VIOLET(0xFF7D3BED, R.string.articleReader_quote_color_violet),
    ;

    fun color(auto: Int): Int = if (this == AUTO) auto else argb.toInt()

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * Wählbare Hintergründe hinter dem Glas. Alle werden gezeichnet (keine Bild-Dateien) und liegen
 * verschwommen unter dem Glas; Formen sind darum groß und kräftig. Zufall mit festem Startwert,
 * damit ein Hintergrund immer gleich aussieht. Farben wie in `QuoteBackdrop` auf iOS.
 */
enum class QuoteBackdrop(@StringRes val titleRes: Int) {
    ARTICLE(R.string.articleReader_quote_backdrop_article),
    BUBBLES(R.string.articleReader_quote_backdrop_bubbles),
    SUNSET(R.string.articleReader_quote_backdrop_sunset),
    OCEAN(R.string.articleReader_quote_backdrop_ocean),
    HEARTS(R.string.articleReader_quote_backdrop_hearts),
    RAINBOW(R.string.articleReader_quote_backdrop_rainbow),
    AURORA(R.string.articleReader_quote_backdrop_aurora),
    CANDY(R.string.articleReader_quote_backdrop_candy),
    ;

    fun step(delta: Int): QuoteBackdrop = entries[(ordinal + delta + entries.size) % entries.size]

    /** Hintergrund in [w]×[h] (in dp, also „1×“ wie auf iOS); `null` für [ARTICLE]. */
    fun render(w: Int, h: Int): Bitmap? {
        if (this == ARTICLE || w <= 0 || h <= 0) return null
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val rng = Random(ordinal * 7919L + 17)
        val fw = w.toFloat()
        val fh = h.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun rnd(from: Float, to: Float) = from + rng.nextFloat() * (to - from)
        when (this) {
            ARTICLE -> Unit
            BUBBLES -> {
                linear(c, intArrayOf(0x312E81, 0x581C87, 0x0F172A), 0f, 0f, fw, fh)
                val palette = intArrayOf(0xF472B6, 0x22D3EE, 0xFACC15, 0xA3E635, 0xFB923C, 0xA78BFA, 0x34D399)
                repeat(26) {
                    val r = rnd(28f, 110f)
                    val cx = rnd(0f, fw)
                    val cy = rnd(0f, fh)
                    paint.color = rgb(palette[rng.nextInt(palette.size)], 0.8f)
                    c.drawCircle(cx, cy, r, paint)
                    // Glanzlicht oben links, damit es nach Blase aussieht.
                    paint.color = rgb(0xFFFFFF, 0.35f)
                    c.drawOval(RectF(cx - r * 0.55f, cy - r * 0.6f, cx - r * 0.05f, cy - r * 0.25f), paint)
                }
            }
            SUNSET -> {
                linear(c, intArrayOf(0x3B0764, 0xBE185D, 0xF97316, 0xFDE68A), 0f, 0f, 0f, fh)
                blob(c, fw * 0.5f, fh * 0.72f, fw * 0.55f, 0xFFF7AE, 0.9f)
                blob(c, fw * 0.1f, fh * 0.25f, fw * 0.5f, 0xF472B6, 0.5f)
            }
            OCEAN -> {
                linear(c, intArrayOf(0x67E8F9, 0x0EA5E9, 0x1D4ED8, 0x1E1B4B), fw, 0f, 0f, fh)
                blob(c, fw * 0.8f, fh * 0.2f, fw * 0.6f, 0xA7F3D0, 0.55f)
                blob(c, fw * 0.15f, fh * 0.8f, fw * 0.55f, 0x6366F1, 0.6f)
            }
            HEARTS -> {
                linear(c, intArrayOf(0xFFE4E6, 0xFBCFE8, 0xF9A8D4), 0f, 0f, fw, fh)
                val palette = intArrayOf(0xE11D48, 0xF43F5E, 0xDB2777, 0xEC4899, 0xBE123C, 0xFB7185)
                repeat(20) {
                    val s = rnd(50f, 140f)
                    val cx = rnd(0f, fw)
                    val cy = rnd(0f, fh)
                    val angle = rnd(-0.45f, 0.45f)
                    paint.color = rgb(palette[rng.nextInt(palette.size)], 0.85f)
                    c.save()
                    c.translate(cx, cy)
                    c.rotate(Math.toDegrees(angle.toDouble()).toFloat())
                    c.drawPath(heart(RectF(-s / 2, -s / 2, s / 2, -s / 2 + s * 0.9f)), paint)
                    c.restore()
                }
            }
            RAINBOW -> {
                linear(c, intArrayOf(0x7DD3FC, 0xBAE6FD, 0xF0F9FF), 0f, 0f, 0f, fh)
                val colors = intArrayOf(0xEF4444, 0xF97316, 0xFACC15, 0x22C55E, 0x3B82F6, 0x6366F1, 0xA855F7)
                val cx = fw / 2
                val cy = fh * 0.78f
                val band = max(fw, fh) * 0.055f
                val outer = max(fw, fh) * 0.62f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = band + 1
                colors.forEachIndexed { i, color ->
                    val r = outer - i * band
                    paint.color = rgb(color)
                    c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false, paint)
                }
                // Wolken an den Enden.
                paint.style = Paint.Style.FILL
                paint.color = android.graphics.Color.WHITE
                for (x in floatArrayOf(cx - outer + 3.5f * band, cx + outer - 3.5f * band)) {
                    for ((dx, dy, r) in listOf(Triple(-0.9f, 0.2f, 0.9f), Triple(0f, 0f, 1.2f), Triple(0.9f, 0.25f, 0.85f))) {
                        c.drawCircle(x + dx * band * 2.2f, cy + dy * band * 2.2f, r * band * 2.2f, paint)
                    }
                }
            }
            AURORA -> {
                c.drawColor(rgb(0x0B1026))
                blob(c, fw * 0.2f, fh * 0.25f, fw * 0.75f, 0x22C55E, 0.75f)
                blob(c, fw * 0.85f, fh * 0.4f, fw * 0.7f, 0x14B8A6, 0.7f)
                blob(c, fw * 0.4f, fh * 0.7f, fw * 0.8f, 0x8B5CF6, 0.75f)
                blob(c, fw * 0.9f, fh * 0.9f, fw * 0.6f, 0xEC4899, 0.6f)
            }
            CANDY -> {
                c.drawColor(android.graphics.Color.WHITE)
                blob(c, fw * 0.15f, fh * 0.15f, fw * 0.8f, 0xF9A8D4, 0.95f)
                blob(c, fw * 0.9f, fh * 0.3f, fw * 0.75f, 0x93C5FD, 0.95f)
                blob(c, fw * 0.25f, fh * 0.75f, fw * 0.8f, 0xFDE68A, 0.95f)
                blob(c, fw * 0.85f, fh * 0.85f, fw * 0.7f, 0x86EFAC, 0.95f)
            }
        }
        return bitmap
    }

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ARTICLE

        private fun rgb(hex: Int, alpha: Float = 1f): Int =
            ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or (hex and 0xFFFFFF)

        private fun linear(c: Canvas, colors: IntArray, x0: Float, y0: Float, x1: Float, y1: Float) {
            val paint = Paint().apply {
                shader = LinearGradient(x0, y0, x1, y1, colors.map { rgb(it) }.toIntArray(), null, Shader.TileMode.CLAMP)
            }
            c.drawPaint(paint)
        }

        /** Weicher Farbfleck: radialer Verlauf von [color] nach durchsichtig. */
        private fun blob(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) {
            if (radius <= 0f) return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, radius, rgb(color, alpha), rgb(color, 0f), Shader.TileMode.CLAMP)
            }
            c.drawCircle(cx, cy, radius, paint)
        }

        /** Herz in [r] (Spitze unten). */
        private fun heart(r: RectF): Path {
            val x = r.left
            val y = r.top
            val w = r.width()
            val h = r.height()
            return Path().apply {
                moveTo(x + w / 2, y + h)
                cubicTo(x + w * 0.1f, y + h * 0.75f, x, y + h * 0.5f, x, y + h * 0.3f)
                arcTo(RectF(x, y + h * 0.3f - w * 0.25f, x + w * 0.5f, y + h * 0.3f + w * 0.25f), 180f, 180f, false)
                arcTo(RectF(x + w * 0.5f, y + h * 0.3f - w * 0.25f, x + w, y + h * 0.3f + w * 0.25f), 180f, 180f, false)
                cubicTo(x + w, y + h * 0.5f, x + w * 0.9f, y + h * 0.75f, x + w / 2, y + h)
                close()
            }
        }
    }
}

// MARK: – Bilder

internal object QuoteRenderer {

    /**
     * Setzt den Text neu: ein Bild mit durchsichtigem Hintergrund, höchstens [maxWidthPx] breit.
     * [glowColor] legt einen weichen Schein in Reader-Farbe hinter die Schrift (wie `.shadow` auf
     * iOS); er ist Teil des Bildes, damit Bildschirm und gespeichertes Bild gleich aussehen.
     */
    fun renderText(
        text: String,
        typeface: Typeface,
        sizePx: Float,
        color: Int,
        maxWidthPx: Int,
        glowColor: Int?,
        glowRadiusPx: Float,
    ): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = sizePx
            this.color = color
            if (glowColor != null) setShadowLayer(glowRadiusPx, 0f, 0f, glowColor)
        }
        val width = maxWidthPx.coerceAtLeast(1)
        fun build(w: Int) = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(sizePx * 0.25f, 1f)
            .setIncludePad(true)
            .build()
        var layout = build(width)
        // Auf die längste Zeile zuschneiden, damit kurze Zitate mittig stehen.
        val used = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
        val tight = ceil(used).toInt().coerceIn(1, width)
        if (tight < width) layout = build(tight)
        val pad = ceil(glowRadiusPx * 1.5f + 4f).toInt()
        val bitmap = Bitmap.createBitmap(tight + 2 * pad, layout.height + 2 * pad, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(pad.toFloat(), pad.toFloat())
        layout.draw(canvas)
        return bitmap
    }

    /**
     * Sichtbarer Teil des Artikels als Bild in [width]×[height] (dp-Raster, „1×“), so verschoben,
     * wie die WebView unter der Tafel liegt. Darunter die Reader-Farbe (die WebView ist bei Papier
     * durchsichtig).
     */
    fun captureArticle(webView: WebView, width: Int, height: Int, overlayOrigin: Offset, density: Float, background: Int): Bitmap? {
        if (width <= 0 || height <= 0 || webView.width <= 0) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            val loc = IntArray(2)
            webView.getLocationInWindow(loc)
            canvas.scale(1f / density, 1f / density)
            canvas.translate(loc[0] - overlayOrigin.x - webView.scrollX, loc[1] - overlayOrigin.y - webView.scrollY)
            webView.draw(canvas)
            bitmap
        }.getOrNull()
    }

    /**
     * Weichgezeichnet (dreifacher Box-Blur ≈ Gauß) in halber Auflösung, etwas kräftigere Farben
     * wie auf iOS. [radius] im Raster von [source] (dp).
     */
    fun blur(source: Bitmap, radius: Float): Bitmap {
        val factor = 2
        val w = max(1, source.width / factor)
        val h = max(1, source.height / factor)
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(
            source, null, android.graphics.Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.15f) })
            },
        )
        val r = (radius / factor * 0.75f).roundToInt()
        if (r < 1) return small
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxBlur(pixels, tmp, w, h, r, horizontal = true)
            boxBlur(tmp, pixels, w, h, r, horizontal = false)
        }
        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return small
    }

    /** Ein Durchgang Box-Blur mit Randwiederholung (laufende Summe, O(n) je Zeile/Spalte). */
    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val p = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + p else p * w + line
            }
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = src[idx(i)]
                sa += c ushr 24; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[idx(i + r + 1)]
                val sub = src[idx(i - r)]
                sa += (add ushr 24) - (sub ushr 24)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }

    /**
     * Bild in Bildschirmgröße zum Speichern/Teilen: Glas, Zitat an der Stelle wie auf dem
     * Bildschirm, darunter ggf. die Zeile mit Autor/Quelle – ohne Knöpfe.
     */
    fun compose(
        width: Int,
        height: Int,
        tint: Int,
        glass: Bitmap?,
        quote: Bitmap,
        frame: Rect,
        caption: String?,
        captionColor: Int,
        captionSizePx: Float,
        captionTopPx: Float,
        captionWidthPx: Float,
        glowColor: Int?,
        glowRadiusPx: Float,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(tint)
        val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        glass?.let { c.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), filter) }
        c.drawBitmap(quote, null, RectF(frame.left, frame.top, frame.right, frame.bottom), filter)
        if (caption != null) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textSize = captionSizePx
                color = captionColor
                if (glowColor != null) setShadowLayer(glowRadiusPx, 0f, 0f, glowColor)
            }
            val line = TextUtils.ellipsize(caption, paint, captionWidthPx.coerceAtLeast(1f), TextUtils.TruncateAt.END).toString()
            val fm = paint.fontMetrics
            c.drawText(line, frame.left, captionTopPx - fm.ascent, paint)
        }
        return bitmap
    }
}

// MARK: – Speichern und Teilen

internal object QuoteImageActions {
    enum class SaveResult { SAVED, DENIED, FAILED }

    /** Unterordner im Cache für das geteilte Bild, in `res/xml/file_paths.xml` freigegeben. */
    private const val SHARE_FOLDER = "merlin-quote-out"

    fun needsStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    /** Legt das Bild als PNG in „Bilder/Merlin“ ab (ab Android 10 ohne Berechtigung). */
    suspend fun save(context: Context, bitmap: Bitmap): SaveResult = withContext(Dispatchers.IO) {
        if (needsStoragePermission(context)) return@withContext SaveResult.DENIED
        val resolver = context.contentResolver
        val name = "Merlin-Quote-${System.currentTimeMillis()}.png"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Merlin")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                val ok = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
                if (ok) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                } else {
                    resolver.delete(uri, null, null)
                }
                ok
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Merlin")
                dir.mkdirs()
                val file = File(dir, name)
                val ok = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (ok) {
                    @Suppress("DEPRECATION")
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.DATA, file.absolutePath)
                    }
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }
                ok
            }
        }.getOrDefault(false).let { if (it) SaveResult.SAVED else SaveResult.FAILED }
    }

    /** System-Teilen-Dialog mit dem Bild (PNG im Cache, per FileProvider freigegeben). */
    suspend fun share(context: Context, bitmap: Bitmap) {
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, SHARE_FOLDER).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "Merlin-Quote-${System.currentTimeMillis()}.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        } ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }
}

// MARK: – Knopf an der Markierungsleiste

/** Knopf „Zitat-Tafel“ über der Farbleiste: öffnet die Tafel für die Auswahl bzw. Markierung. */
@Composable
fun QuoteOpenButton(background: Color, foreground: Color, border: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(background, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .height(40.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.FormatQuote, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.articleReader_quote_open), color = foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: – Tafel

private val BUTTON_HEIGHT = 46.dp
private val TOGGLE_HEIGHT = 34.dp
private val TOGGLE_SPACING = 12.dp
private val DOTS_HEIGHT = 20.dp
private val CAPTION_GAP = 10.dp
private val CAPTION_HEIGHT = 22.dp
private val GLOW_RADIUS = 3.dp
private val MAX_TEXT_WIDTH = 620.dp
private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 4f

/**
 * Die Tafel über dem ganzen Reader. [onPrefsChange] merkt Gestaltung und Kästchen (lokal);
 * [onClosed] kommt nach der Schließ-Animation.
 */
@Composable
fun QuoteCardOverlay(
    session: QuoteSession,
    webView: WebView?,
    prefs: QuotePrefs,
    onPrefsChange: (QuotePrefs) -> Unit,
    showHint: Boolean,
    accent: Color,
    onAccent: Color,
    onClosed: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val style = session.style
    val tint = Color(style.tint)
    val fg = Color(style.textColor)

    // Gestaltung: lokal gehalten, jede Änderung zusätzlich gemerkt.
    var font by remember { mutableStateOf(QuoteFont.of(prefs.font)) }
    var textSize by remember { mutableFloatStateOf(prefs.fontSize.toFloat()) }
    var textColor by remember { mutableStateOf(QuoteTextColor.of(prefs.textColor)) }
    var glow by remember { mutableStateOf(prefs.glow) }
    var blur by remember { mutableFloatStateOf(prefs.blur.toFloat()) }
    var backdrop by remember { mutableStateOf(QuoteBackdrop.of(prefs.backdrop)) }
    var showAuthor by remember { mutableStateOf(prefs.showAuthor) }
    var showSource by remember { mutableStateOf(prefs.showSource) }
    fun persist() = onPrefsChange(
        QuotePrefs(
            showAuthor = showAuthor, showSource = showSource, backdrop = backdrop.name, font = font.name,
            fontSize = textSize.toDouble(), textColor = textColor.name, glow = glow, blur = blur.toDouble(),
        ),
    )

    // Lage: verschoben und gezoomt gilt nur für dieses Zitat.
    var textOffset by remember { mutableStateOf(Offset.Zero) }
    var textScale by remember { mutableFloatStateOf(1f) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var showStylePanel by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }
    var showSaveFailed by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val lift = remember { Animatable(0f) }

    var overlayOrigin by remember { mutableStateOf<Offset?>(null) }
    var article by remember { mutableStateOf<Bitmap?>(null) }
    var captured by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
        val canvasW = constraints.maxWidth
        val canvasH = constraints.maxHeight
        val dpW = with(density) { canvasW.toDp().value }.roundToInt()
        val dpH = with(density) { canvasH.toDp().value }.roundToInt()

        // Artikel abfotografieren, sobald Leisten und Auswahl weg sind (bei jeder Größenänderung neu).
        LaunchedEffect(canvasW, canvasH, overlayOrigin != null) {
            val origin = overlayOrigin ?: return@LaunchedEffect
            delay(if (article == null) 250 else 150)
            article = webView?.let {
                QuoteRenderer.captureArticle(it, dpW, dpH, overlayOrigin ?: origin, density.density, style.tint)
            }
            captured = true
        }
        // Glas: Hintergrund (Artikel oder gezeichnet) weichgezeichnet, ohne Tönung.
        val glass by produceState<Bitmap?>(null, backdrop, article, blur.roundToInt(), dpW, dpH) {
            val source = if (backdrop == QuoteBackdrop.ARTICLE) article else null
            val radius = blur.roundToInt().toFloat()
            value = withContext(Dispatchers.Default) {
                val src = source ?: backdrop.render(dpW, dpH) ?: return@withContext null
                if (radius > 0f) QuoteRenderer.blur(src, radius) else src
            }
        }
        // Klappt der Abzug nicht, bleibt das Glas einfach in Reader-Farbe.
        val ready = captured || webView == null || backdrop != QuoteBackdrop.ARTICLE

        // Text als Bild, in der Auflösung des Zooms (damit er gezoomt scharf bleibt).
        val readerSizeDp = style.fontSize
        val effectiveSize = if (font == QuoteFont.ORIGINAL || textSize <= 0f) readerSizeDp else textSize
        val colorArgb = if (font == QuoteFont.ORIGINAL) style.textColor else textColor.color(style.textColor)
        val sideInset = 16.dp
        val maxWidthDp = min(max(120f, dpW - 2 * sideInset.value - 8f), MAX_TEXT_WIDTH.value)
        val zoomBucket = (min(max(textScale, 1f), 3f) * 2f).let { ceil(it) / 2f }
        val glowArgb = if (glow) tint.copy(alpha = 0.9f).toArgb() else null
        val quoteBitmap = remember(session.text, font, effectiveSize, colorArgb, maxWidthDp, glowArgb, zoomBucket) {
            val px = density.density * zoomBucket
            QuoteRenderer.renderText(
                text = session.text,
                typeface = font.typeface(style.readerTypeface),
                sizePx = effectiveSize * font.sizeFactor * px,
                color = colorArgb,
                maxWidthPx = (maxWidthDp * px).roundToInt(),
                glowColor = glowArgb,
                glowRadiusPx = GLOW_RADIUS.value * px,
            )
        }
        // Logische Größe des Textes in Pixeln (die Bitmap ist um `zoomBucket` größer gerechnet).
        val quoteSize = Size(quoteBitmap.width / zoomBucket, quoteBitmap.height / zoomBucket)

        val caption = listOfNotNull(style.author.takeIf { showAuthor }, style.source.takeIf { showSource })
            .takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "— ")

        // Fläche zwischen oberem Rand (Punkte) und den Knöpfen.
        val cutoutTop = WindowInsets.displayCutout.getTop(density).toFloat()
        val px = density.density
        val top = max(cutoutTop, 12 * px) + 6 * px + (DOTS_HEIGHT.value + 8) * px + 16 * px
        val bottom = 16 * px + 12 * px + BUTTON_HEIGHT.value * px + (TOGGLE_HEIGHT.value + TOGGLE_SPACING.value) * px + 24 * px
        val reserve = if (caption != null) (CAPTION_GAP.value + CAPTION_HEIGHT.value) * px else 0f
        val area = Rect(sideInset.value * px, top, canvasW - sideInset.value * px, canvasH - bottom - reserve)
        val fitted = fitted(quoteSize, area)
        val limit = maxScale(fitted.size, canvasW.toFloat(), canvasH.toFloat())
        val target = scaled(fitted, min(textScale * pinchScale, limit))
        val settled = clamped(target.translate(textOffset), canvasW.toFloat(), canvasH.toFloat())
        val moving = clamped(settled.translate(dragOffset), canvasW.toFloat(), canvasH.toFloat())
        val source = session.sourceInWindow?.let { r -> overlayOrigin?.let { r.translate(-it) } } ?: settled
        val p = lift.value
        val frame = Rect(
            lerp(source.left, settled.left, p), lerp(source.top, settled.top, p),
            lerp(source.right, settled.right, p), lerp(source.bottom, settled.bottom, p),
        )
        val shift = if (p > 0f) moving.topLeft - settled.topLeft else Offset.Zero

        val currentSettled by rememberUpdatedState(settled)
        val currentTarget by rememberUpdatedState(target)
        val currentLimit by rememberUpdatedState(limit)

        fun close() {
            if (closing) return
            closing = true
            scope.launch {
                lift.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow))
                onClosed()
            }
        }
        BackHandler { if (showStylePanel) showStylePanel = false else close() }

        // Einblenden, sobald der Hintergrund da ist; Hinweis beim ersten Mal kurz zeigen.
        LaunchedEffect(ready) {
            if (!ready || closing) return@LaunchedEffect
            lift.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow))
            if (showHint) {
                hintVisible = true
                delay(1500)
                hintVisible = false
            }
        }

        fun stepBackdrop(delta: Int) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            backdrop = backdrop.step(delta)
            persist()
        }

        fun exportImage(): Bitmap {
            val captionTop = settled.bottom + CAPTION_GAP.value * px
            return QuoteRenderer.compose(
                width = canvasW, height = canvasH, tint = style.tint, glass = glass, quote = quoteBitmap, frame = settled,
                caption = caption,
                captionColor = Color(colorArgb).copy(alpha = 0.75f).toArgb(),
                captionSizePx = 15.sp.value * density.fontScale * px,
                captionTopPx = captionTop,
                captionWidthPx = canvasW - settled.left - 16 * px,
                glowColor = glowArgb,
                glowRadiusPx = GLOW_RADIUS.value * px,
            )
        }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) showDenied = true
        }

        fun save() {
            if (saving || saved) return
            if (QuoteImageActions.needsStoragePermission(context)) {
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
            saving = true
            scope.launch {
                when (QuoteImageActions.save(context, exportImage())) {
                    QuoteImageActions.SaveResult.SAVED -> {
                        saved = true
                        saving = false
                        delay(2000)
                        saved = false
                    }
                    QuoteImageActions.SaveResult.DENIED -> { saving = false; showDenied = true }
                    QuoteImageActions.SaveResult.FAILED -> { saving = false; showSaveFailed = true }
                }
            }
        }

        val closeLabel = stringResource(R.string.articleReader_quote_close)
        val swipe = with(density) { 50.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { customActions = listOf(CustomAccessibilityAction(closeLabel) { close(); true }) }
                // Ein Finger auf dem Text: verschieben; sonst waagrecht wischen: Hintergrund; zwei
                // Finger: zoomen (nur die Darstellung); Tipp: Design-Feld bzw. Tafel schließen.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (closing) return@awaitEachGesture
                        val onText = currentSettled.translate(dragOffset).contains(down.position)
                        var pan = Offset.Zero
                        var moved = false
                        var pinched = false
                        // Knöpfe, Regler und Felder verarbeiten ihre Gesten selbst.
                        var handledByChild = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (!moved && !pinched && event.changes.any { it.isConsumed }) handledByChild = true
                            if (event.changes.none { it.pressed }) break
                            if (handledByChild) continue
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2) {
                                pinched = true
                                dragOffset = Offset.Zero
                                dragging = false
                                pinchScale *= event.calculateZoom()
                            } else if (!pinched) {
                                pan += event.calculatePan()
                                if (!moved && pan.getDistance() > viewConfiguration.touchSlop) moved = true
                                if (moved && onText) {
                                    dragging = true
                                    dragOffset = pan
                                }
                            }
                            if (moved || pinched) event.changes.forEach { it.consume() }
                        }
                        when {
                            handledByChild -> Unit
                            pinched -> {
                                textScale = (textScale * pinchScale).coerceIn(MIN_SCALE, min(MAX_SCALE, currentLimit))
                                pinchScale = 1f
                            }
                            moved && onText -> {
                                val end = clamped(currentSettled.translate(pan), size.width.toFloat(), size.height.toFloat())
                                textOffset = end.topLeft - currentTarget.topLeft
                                dragOffset = Offset.Zero
                                dragging = false
                            }
                            moved -> if (abs(pan.x) > swipe && abs(pan.x) > abs(pan.y)) stepBackdrop(if (pan.x < 0) 1 else -1)
                            showStylePanel -> showStylePanel = false
                            else -> close()
                        }
                    }
                },
        ) {
            // Glas.
            Box(modifier = Modifier.fillMaxSize().alpha(p).background(tint)) {
                glass?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Zitat.
            if (ready) {
                Image(
                    bitmap = quoteBitmap.asImageBitmap(),
                    contentDescription = session.text,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .offset { IntOffset((frame.left + shift.x).roundToInt(), (frame.top + shift.y).roundToInt()) }
                        .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })
                        .graphicsLayer {
                            val s = if (dragging) 1.03f else 1f
                            scaleX = s
                            scaleY = s
                        },
                )
                if (caption != null) {
                    val width = with(density) { (canvasW - frame.left - 16 * px).coerceAtLeast(0f).toDp() }
                    Text(
                        caption,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(colorArgb).copy(alpha = 0.75f),
                            shadow = glowArgb?.let { Shadow(Color(it), blurRadius = GLOW_RADIUS.value * px) },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (frame.left + shift.x).roundToInt(),
                                    (frame.bottom + shift.y + CAPTION_GAP.value * px).roundToInt(),
                                )
                            }
                            .size(width, CAPTION_HEIGHT)
                            .alpha(p),
                    )
                }
            }

            val controlsAlpha = p
            // Oben: Punkte für die Hintergründe, beim ersten Mal der Hinweis.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { max(cutoutTop, 12 * px).toDp() } + 6.dp)
                    .alpha(controlsAlpha),
            ) {
                BackdropDots(backdrop = backdrop, tint = tint, fg = fg, onStep = ::stepBackdrop)
                AnimatedVisibility(visible = hintVisible, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        stringResource(R.string.articleReader_quote_hint),
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(tint.copy(alpha = 0.75f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // ✕ oben rechts: zurück zum Artikel (wie ein Tipp aufs Glas).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = with(density) { max(cutoutTop, 12 * px).toDp() } - 1.dp, end = 12.dp)
                    .alpha(controlsAlpha)
                    .size(44.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(6.dp, CircleShape)
                        .background(tint.copy(alpha = 0.8f), CircleShape),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = closeLabel, tint = fg, modifier = Modifier.size(18.dp))
                }
            }

            // Unten: Design-Feld, Kästchen und Knöpfe.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TOGGLE_SPACING),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
                    .alpha(controlsAlpha),
            ) {
                AnimatedVisibility(
                    visible = showStylePanel,
                    enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
                    exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
                ) {
                    StylePanel(
                        style = style,
                        tint = tint,
                        fg = fg,
                        accent = accent,
                        font = font,
                        effectiveSize = effectiveSize,
                        textColor = textColor,
                        blur = blur,
                        glow = glow,
                        onFont = { font = it; persist() },
                        onSize = {
                            // „Original“ hat keine eigene Größe: auf neu gesetzten Text wechseln.
                            if (font == QuoteFont.ORIGINAL) font = QuoteFont.SYSTEM
                            textSize = it.roundToInt().toFloat()
                        },
                        onSizeDone = ::persist,
                        onColor = {
                            if (font == QuoteFont.ORIGINAL) font = QuoteFont.SYSTEM
                            textColor = it
                            persist()
                        },
                        onBlur = { blur = it.roundToInt().toFloat() },
                        onBlurDone = ::persist,
                        onGlow = { glow = it; persist() },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuoteChip(
                        title = stringResource(R.string.articleReader_quote_style),
                        icon = Icons.Outlined.Brush,
                        isOn = showStylePanel,
                        tint = tint,
                        fg = fg,
                    ) { showStylePanel = !showStylePanel }
                    if (style.author != null) {
                        QuoteChip(
                            title = stringResource(R.string.articleReader_quote_showAuthor),
                            icon = if (showAuthor) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            isOn = false,
                            tint = tint,
                            fg = fg,
                        ) { showAuthor = !showAuthor; persist() }
                    }
                    if (style.source != null) {
                        QuoteChip(
                            title = stringResource(R.string.articleReader_quote_showSource),
                            icon = if (showSource) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            isOn = false,
                            tint = tint,
                            fg = fg,
                        ) { showSource = !showSource; persist() }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuoteActionButton(
                        title = stringResource(if (saved) R.string.articleReader_quote_saved else R.string.articleReader_quote_save),
                        icon = if (saved) Icons.Filled.Check else Icons.Filled.Download,
                        accent = accent,
                        onAccent = onAccent,
                        onClick = ::save,
                    )
                    QuoteActionButton(
                        title = stringResource(R.string.articleReader_quote_share),
                        icon = Icons.Filled.Share,
                        accent = accent,
                        onAccent = onAccent,
                    ) {
                        scope.launch { QuoteImageActions.share(context, exportImage()) }
                    }
                }
            }
        }
    }

    if (showDenied) {
        AlertDialog(
            onDismissRequest = { showDenied = false },
            title = { Text(stringResource(R.string.articleReader_quote_photosDenied_title)) },
            text = { Text(stringResource(R.string.articleReader_quote_photosDenied_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDenied = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                }) { Text(stringResource(R.string.articleReader_quote_photosDenied_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showDenied = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    if (showSaveFailed) {
        AlertDialog(
            onDismissRequest = { showSaveFailed = false },
            title = { Text(stringResource(R.string.articleReader_quote_saveFailed)) },
            confirmButton = {
                TextButton(onClick = { showSaveFailed = false }) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }
}

/** Punkte für die Hintergründe; TalkBack wechselt über die Aktionen „weiter“/„zurück“. */
@Composable
private fun BackdropDots(backdrop: QuoteBackdrop, tint: Color, fg: Color, onStep: (Int) -> Unit) {
    val label = stringResource(R.string.articleReader_quote_backdropPicker)
    val value = stringResource(backdrop.titleRes)
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(DOTS_HEIGHT)
            .background(tint.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = value
                customActions = listOf(
                    CustomAccessibilityAction("›") { onStep(1); true },
                    CustomAccessibilityAction("‹") { onStep(-1); true },
                )
            },
    ) {
        QuoteBackdrop.entries.forEach { item ->
            Box(
                Modifier
                    .size(7.dp)
                    .background(fg.copy(alpha = if (item == backdrop) 0.9f else 0.3f), CircleShape),
            )
        }
    }
}

@Composable
private fun StylePanel(
    style: QuoteStyle,
    tint: Color,
    fg: Color,
    accent: Color,
    font: QuoteFont,
    effectiveSize: Float,
    textColor: QuoteTextColor,
    blur: Float,
    glow: Boolean,
    onFont: (QuoteFont) -> Unit,
    onSize: (Float) -> Unit,
    onSizeDone: () -> Unit,
    onColor: (QuoteTextColor) -> Unit,
    onBlur: (Float) -> Unit,
    onBlurDone: () -> Unit,
    onGlow: (Boolean) -> Unit,
) {
    val muted = fg.copy(alpha = 0.6f)
    val sliderColors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = fg.copy(alpha = 0.2f))
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.94f), RoundedCornerShape(20.dp))
            // Tipps ins Feld schließen weder Feld noch Tafel.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            QuoteFont.entries.forEach { item ->
                val selected = item == font
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(34.dp)
                        .background(if (selected) accent.copy(alpha = 0.35f) else fg.copy(alpha = 0.07f), RoundedCornerShape(50))
                        .border(1.5.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(50))
                        .clickable { onFont(item) }
                        .padding(horizontal = 12.dp)
                        .semantics { this.selected = selected },
                ) {
                    Text(
                        stringResource(item.titleRes),
                        color = fg,
                        maxLines = 1,
                        fontSize = if (item == QuoteFont.ORIGINAL) 13.sp else 15.sp,
                        fontWeight = if (item == QuoteFont.ORIGINAL) FontWeight.SemiBold else null,
                        fontFamily = if (item == QuoteFont.ORIGINAL) null else FontFamily(item.typeface(style.readerTypeface)),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.TextDecrease, contentDescription = null, tint = muted)
            val sizeLabel = stringResource(R.string.articleReader_quote_size)
            Slider(
                value = effectiveSize.coerceIn(12f, 48f),
                onValueChange = onSize,
                onValueChangeFinished = onSizeDone,
                valueRange = 12f..48f,
                colors = sliderColors,
                modifier = Modifier.weight(1f).semantics { contentDescription = sizeLabel },
            )
            Icon(Icons.Filled.TextIncrease, contentDescription = null, tint = muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuoteTextColor.entries.forEach { item ->
                val selected = item == textColor && font != QuoteFont.ORIGINAL
                val label = stringResource(item.titleRes)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .border(if (selected) 3.dp else 0.dp, if (selected) accent else Color.Transparent, CircleShape)
                        .padding(3.dp)
                        .background(Color(item.color(style.textColor)), CircleShape)
                        .border(1.dp, fg.copy(alpha = 0.25f), CircleShape)
                        .clickable { onColor(item) }
                        .semantics {
                            contentDescription = label
                            this.selected = selected
                        },
                ) {
                    if (item == QuoteTextColor.AUTO) {
                        Text("A", color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = muted)
            val blurLabel = stringResource(R.string.articleReader_quote_blur)
            Slider(
                value = blur,
                onValueChange = onBlur,
                onValueChangeFinished = onBlurDone,
                valueRange = 0f..40f,
                colors = sliderColors,
                modifier = Modifier.weight(1f).semantics { contentDescription = blurLabel },
            )
            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.articleReader_quote_glow),
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = glow,
                onCheckedChange = onGlow,
                colors = SwitchDefaults.colors(checkedTrackColor = accent),
            )
        }
    }
}

/** „Design“ und die Kästchen Autor/Quelle über den Knöpfen. */
@Composable
private fun QuoteChip(title: String, icon: ImageVector, isOn: Boolean, tint: Color, fg: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(TOGGLE_HEIGHT)
            .background(tint.copy(alpha = 0.75f), RoundedCornerShape(50))
            .border(1.5.dp, fg.copy(alpha = if (isOn) 0.6f else 0f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuoteActionButton(title: String, icon: ImageVector, accent: Color, onAccent: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(BUTTON_HEIGHT)
            .shadow(6.dp, RoundedCornerShape(50))
            .background(accent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = onAccent, modifier = Modifier.size(20.dp))
        Text(title, color = onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: – Geometrie

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Größe [size] mittig in [area], nur verkleinert, nie vergrößert. */
private fun fitted(size: Size, area: Rect): Rect {
    if (size.width <= 0f || size.height <= 0f || area.width <= 0f || area.height <= 0f) {
        return Rect(area.center, Size.Zero)
    }
    val s = min(1f, min(area.width / size.width, area.height / size.height))
    val w = size.width * s
    val h = size.height * s
    return Rect(area.center.x - w / 2, area.center.y - h / 2, area.center.x + w / 2, area.center.y + h / 2)
}

/** [rect] um seine Mitte vergrößert bzw. verkleinert. */
private fun scaled(rect: Rect, scale: Float): Rect {
    if (scale == 1f) return rect
    val w = rect.width * scale
    val h = rect.height * scale
    return Rect(rect.center.x - w / 2, rect.center.y - h / 2, rect.center.x + w / 2, rect.center.y + h / 2)
}

/** Größter Zoom, bei dem ein Zitat der Größe [size] noch ganz zu sehen ist. */
private fun maxScale(size: Size, w: Float, h: Float): Float {
    if (size.width <= 0f || size.height <= 0f) return MAX_SCALE
    val fit = min((w - 16f) / size.width, (h - 16f) / size.height)
    return min(MAX_SCALE, max(1f, fit))
}

/** Hält das Zitat mit etwas Rand im Bildschirm. */
private fun clamped(rect: Rect, w: Float, h: Float): Rect {
    val margin = 8f
    val x = min(max(margin, rect.left), max(margin, w - margin - rect.width))
    val y = min(max(margin, rect.top), max(margin, h - margin - rect.height))
    return Rect(Offset(x, y), rect.size)
}

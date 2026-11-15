package dev.merlin.android.ui.reader

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Farben des Plakat-Headers im Reader (Äquivalent zu `onAccentHex` und `accentSteps` in
 * `ArticleReaderView.swift`): die Fläche in der Akzentfarbe des Users, die darauf lesbare
 * Vordergrundfarbe und die vier Tonstufen von der Akzentfarbe zum Reader-Hintergrund.
 *
 * Die Stufen werden hier (statt per CSS `color-mix(in oklch, …)` wie unter iOS) in Kotlin
 * gemischt, damit sie auch auf älteren Android-System-WebViews ohne `color-mix()` stimmen.
 * Gemischt wird wie bei iOS (`Color.mix(…, in: .perceptual)`) im OKLab-Farbraum.
 */
object PosterColors {

    /** Tonstufen Akzent → Hintergrund: Höhe (CSS-px bzw. dp) und Akzentanteil. Wie iOS. */
    val steps: List<Pair<Int, Double>> = listOf(14 to 0.75, 12 to 0.55, 10 to 0.35, 8 to 0.15)

    /** Gesamthöhe der Tonstufen (44). */
    val stepsHeight: Int = steps.sumOf { it.first }

    /** Lesbare Vordergrundfarbe auf der Akzentfläche: weiß, bei sehr hellen Akzenten dunkel. */
    fun onAccentHex(accentHex: String): String {
        val rgb = parse(accentHex) ?: return "#ffffff"
        fun lin(c: Int): Double {
            val x = c / 255.0
            return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        val l = 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2])
        return if (l > 0.35) "#1c1c1e" else "#ffffff"
    }

    /** Die vier Stufenfarben (Hex) zwischen [accentHex] und [bgHex], in Reihenfolge von oben. */
    fun stepColors(accentHex: String, bgHex: String): List<String> =
        steps.map { (_, amount) -> mix(accentHex, bgHex, amount) }

    /** [amount] Anteil von [aHex], Rest [bHex], gemischt in OKLab. */
    fun mix(aHex: String, bHex: String, amount: Double): String {
        val a = parse(aHex) ?: return bHex
        val b = parse(bHex) ?: return aHex
        val la = toOklab(a)
        val lb = toOklab(b)
        val m = DoubleArray(3) { i -> la[i] * amount + lb[i] * (1 - amount) }
        return toHex(fromOklab(m))
    }

    private fun parse(hex: String): IntArray? {
        val s = hex.trim().removePrefix("#")
        if (s.length < 6) return null
        val v = s.take(6).toIntOrNull(16) ?: return null
        return intArrayOf(v shr 16 and 0xff, v shr 8 and 0xff, v and 0xff)
    }

    private fun toLinear(c: Int): Double {
        val x = c / 255.0
        return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }

    private fun fromLinear(x: Double): Int {
        val v = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
        return (v * 255).roundToInt().coerceIn(0, 255)
    }

    private fun toOklab(rgb: IntArray): DoubleArray {
        val r = toLinear(rgb[0]); val g = toLinear(rgb[1]); val b = toLinear(rgb[2])
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun fromOklab(lab: DoubleArray): IntArray {
        val l = (lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]).pow(3)
        val m = (lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]).pow(3)
        val s = (lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2]).pow(3)
        return intArrayOf(
            fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
        )
    }

    private fun toHex(rgb: IntArray): String = "#%02x%02x%02x".format(rgb[0], rgb[1], rgb[2])
}

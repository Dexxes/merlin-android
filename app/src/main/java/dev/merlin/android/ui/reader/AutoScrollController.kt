package dev.merlin.android.ui.reader

import android.webkit.WebView
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Etwas, das der Freihand-Lesemodus nach unten schieben kann: die WebView des Readers oder die
 * `LazyColumn` des PDF-Readers.
 */
interface AutoScrollTarget {
    /** Aktueller Scroll-Offset in Pixeln (nur zum Erkennen fremder Bewegung, z.B. eines Flings). */
    val offsetPx: Int

    /** Zieht oder flingt der Nutzer gerade selbst? (WebView: nicht abfragbar, siehe [offsetPx].) */
    val isUserScrolling: Boolean get() = false

    fun canScrollForward(): Boolean

    fun scrollByPx(px: Int)
}

/** WebView des Readers: sie scrollt selbst (siehe [ReaderWebView]). */
class WebViewAutoScrollTarget(private val webView: WebView) : AutoScrollTarget {
    override val offsetPx: Int get() = webView.scrollY
    override fun canScrollForward(): Boolean = webView.canScrollVertically(1)
    override fun scrollByPx(px: Int) = webView.scrollBy(0, px)
}

/** Seitenliste des PDF-Readers (siehe [PdfReaderView]). */
class LazyListAutoScrollTarget(
    private val listState: LazyListState,
    private val offset: () -> Float,
) : AutoScrollTarget {
    override val offsetPx: Int get() = offset().toInt()
    override val isUserScrolling: Boolean get() = listState.isScrollInProgress
    override fun canScrollForward(): Boolean = listState.canScrollForward

    // `dispatchRawDelta` umgeht den Scroll-Mutex: `isScrollInProgress` bleibt dadurch false und
    // zeigt weiterhin nur Ziehen/Fling des Nutzers an.
    override fun scrollByPx(px: Int) {
        listState.dispatchRawDelta(px.toFloat())
    }
}

/**
 * Freihand-Lesemodus: scrollt den Reader gleichmäßig nach unten. Äquivalent zu
 * `AutoScrollController.swift` (merlin-ios).
 *
 * [run] schiebt das [target] in jedem Frame (`withFrameNanos`, wie `CADisplayLink` unter iOS) um
 * `Tempo × Δt` weiter, so ist das Tempo auf 60-Hz- und 120-Hz-Displays gleich. Bruchteile eines
 * Pixels werden gesammelt, sonst blieben langsame Stufen stehen.
 *
 * Zustände:
 * - [isActive]: der Modus ist an (der Reader zeigt statt der Bottom-Bar die Lesemodus-Leiste).
 * - [isPaused]: angehalten, weiter erst per ▶ ([resume]). Das passiert durch den Pause-Button,
 *   automatisch bei Auswahl/Sheets/Hintergrund ([pause]) und am Artikelende ([reachedEnd]).
 * - Berührt der Nutzer selbst den Inhalt, hält der Motor nur kurz an und läuft
 *   [TOUCH_RESUME_DELAY_NANOS] nach dem Loslassen (und Auslaufen eines Flings) von der neuen
 *   Stelle weiter, ohne dass [isPaused] gesetzt wird.
 *
 * Performance: nur [isActive]/[isPaused]/[slider] sind Compose-State und ändern sich selten (bzw.
 * nur beim Ziehen am Slider). Der Frame-Loop selbst liest und schreibt keinen State, der eine
 * Recomposition des Readers auslöst – der Fortschrittsbalken liest den Offset weiter erst beim
 * Zeichnen (siehe `ProgressBarOverlay`).
 */
@Stable
class AutoScrollController {

    companion object {
        /** Langsamste und schnellste Stufe in dp pro Sekunde (iOS: pt/s). */
        const val MIN_SPEED_DP = 10f
        const val MAX_SPEED_DP = 150f

        /** Slider-Stellung für ~35 dp/s (siehe [speedDpPerSecond]). */
        const val DEFAULT_SLIDER = 0.42f

        /** Pause nach dem Loslassen bzw. nach dem Ende eines Flings, bevor es von selbst weitergeht. */
        const val TOUCH_RESUME_DELAY_NANOS = 1_500_000_000L

        /**
         * Quadratisch statt linear: die langsamen, viel genutzten Stufen liegen auf einem größeren
         * Teil des Sliders.
         */
        fun speedDpPerSecond(slider: Float): Float {
            val v = slider.coerceIn(0f, 1f)
            return MIN_SPEED_DP + (MAX_SPEED_DP - MIN_SPEED_DP) * v * v
        }
    }

    var isActive by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var reachedEnd by mutableStateOf(false)
        private set

    /** Slider-Stellung 0…1; wirkt sofort, auch während des Scrollens. */
    var slider by mutableFloatStateOf(DEFAULT_SLIDER)

    val isRunning: Boolean get() = isActive && !isPaused

    /** Was bewegt wird; setzt der Reader, sobald WebView bzw. PDF-Liste bereit ist. */
    var target: AutoScrollTarget? = null

    /**
     * Liegt gerade ein Finger auf dem Inhalt? Setzt ein Pointer-Beobachter im Reader (bewusst
     * kein Compose-State: wird nur im Frame-Loop gelesen).
     */
    var userTouching: Boolean = false

    /** Schaltet den Modus ein und startet sofort. */
    fun start() {
        isActive = true
        isPaused = false
        reachedEnd = false
    }

    /** Schaltet den Modus aus (zurück zur normalen Bottom-Bar). */
    fun stop() {
        isActive = false
        isPaused = false
        reachedEnd = false
    }

    /** Anhalten bis [resume]; ohne Wirkung, wenn der Modus aus ist. */
    fun pause() {
        if (!isActive || isPaused) return
        isPaused = true
    }

    /**
     * Weiter von der aktuellen Stelle. Am Artikelende passiert nichts Sichtbares: der Motor merkt
     * im ersten Frame, dass er am Ende steht.
     */
    fun resume() {
        if (!isActive) return
        isPaused = false
        reachedEnd = false
    }

    fun togglePause() {
        if (isPaused) resume() else pause()
    }

    /**
     * Frame-Loop; läuft, bis die aufrufende Coroutine abgebrochen wird (der Reader startet ihn per
     * `LaunchedEffect(isRunning)`) oder das Artikelende erreicht ist.
     *
     * @param pxPerDp `LocalDensity.current.density`
     */
    suspend fun run(pxPerDp: Float) {
        var lastFrame: Long? = null
        var accumulator = 0f
        var wasTouching = false
        // Nach dem Loslassen: Zeitpunkt, ab dem es weitergeht, und der zuletzt gesehene Offset –
        // bewegt sich der Inhalt in dieser Zeit noch (Fling), wird die Wartezeit verlängert.
        var resumeAt: Long? = null
        var lastSeenOffset = 0

        while (true) {
            val now = withFrameNanos { it }
            val previous = lastFrame
            lastFrame = now
            val t = target ?: continue

            // Nutzer berührt den Inhalt oder flingt (PDF-Liste): kurz anhalten.
            if (userTouching || t.isUserScrolling) {
                wasTouching = true
                resumeAt = null
                accumulator = 0f
                continue
            }
            if (wasTouching) {
                wasTouching = false
                resumeAt = now + TOUCH_RESUME_DELAY_NANOS
                lastSeenOffset = t.offsetPx
            }
            val waitUntil = resumeAt
            if (waitUntil != null) {
                // In der Wartezeit scrollt der Motor nicht selbst: jede Bewegung ist ein auslaufender
                // Fling der WebView (deren Fling-Zustand ist nicht abfragbar).
                val offset = t.offsetPx
                if (offset != lastSeenOffset) {
                    lastSeenOffset = offset
                    resumeAt = now + TOUCH_RESUME_DELAY_NANOS
                } else if (now >= waitUntil) {
                    resumeAt = null
                    accumulator = 0f
                }
                continue
            }

            if (previous == null) continue

            // Ende jedes Mal neu prüfen: nachladende Bilder lassen den Inhalt wachsen.
            if (!t.canScrollForward()) {
                reachedEnd = true
                isPaused = true
                return
            }

            // Nach einem Hänger nicht mit einem großen Satz nachholen.
            val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.1f)
            accumulator += speedDpPerSecond(slider) * pxPerDp * dt
            val step = accumulator.toInt()
            if (step <= 0) continue
            accumulator -= step
            t.scrollByPx(step)
        }
    }
}

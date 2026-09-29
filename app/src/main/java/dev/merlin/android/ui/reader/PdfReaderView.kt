package dev.merlin.android.ui.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Öffnet eine lokale PDF-Datei mit dem Framework-[PdfRenderer] (ab API 21, keine Zusatzabhängigkeit).
 *
 * `PdfRenderer` ist nicht thread-sicher und erlaubt nur eine gleichzeitig offene Seite – deshalb
 * läuft jeder Zugriff unter [mutex]. Die Seitenverhältnisse werden einmal beim Öffnen gelesen, damit
 * die Liste ihre Höhen kennt, bevor eine Seite gerendert ist (stabile Scrollposition, exakter Fortschritt).
 */
private class PdfPageSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Höhe/Breite je Seite. */
    val aspects: List<Float>,
) : Closeable {

    private val mutex = Mutex()

    val pageCount: Int get() = aspects.size

    /** Rendert Seite [index] mit weißem Hintergrund (PdfRenderer liefert sonst transparente Pixel). */
    suspend fun render(index: Int, widthPx: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx * page.height / page.width.toFloat()).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    /** Schließt erst, wenn kein Render mehr läuft – ein Schließen mitten in `render` würde abstürzen. */
    override fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
            }
        }
    }

    companion object {
        /**
         * @throws SecurityException bei passwortgeschützter PDF
         * @throws java.io.IOException bei beschädigter Datei
         */
        suspend fun open(file: File): PdfPageSource = withContext(Dispatchers.IO) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                try {
                    val aspects = (0 until renderer.pageCount).map { i ->
                        renderer.openPage(i).use { page -> page.height / page.width.toFloat().coerceAtLeast(1f) }
                    }
                    PdfPageSource(descriptor, renderer, aspects)
                } catch (e: Throwable) {
                    renderer.close()
                    throw e
                }
            } catch (e: Throwable) {
                descriptor.close()
                throw e
            }
        }
    }
}

private sealed interface PdfPhase {
    data object Loading : PdfPhase
    data object Failed : PdfPhase
    data object Encrypted : PdfPhase
    class Ready(val source: PdfPageSource) : PdfPhase
}

/**
 * Zeigt die PDF eines PDF-Artikels ([dev.merlin.android.models.Article.isPdf]) im Reader: lädt sie über
 * [loadPdf] von der Quell-URL (bzw. aus dem [dev.merlin.android.data.PdfCacheService]) und rendert
 * die Seiten als Bitmaps in einer `LazyColumn`. Nur sichtbare Seiten sind gerendert; verlässt eine
 * Seite den Bildschirm, wird ihr Bitmap freigegeben. Fortschritt, Bottom-Bar-Autohide und
 * Positionswiederherstellung laufen über dieselben Callbacks wie bei [ReaderWebView].
 * Einschränkungen: kein Pinch-Zoom, keine Textauswahl/-suche, keine Highlights.
 */
@Composable
fun PdfReaderView(
    sourceUrl: String,
    loadPdf: suspend (String) -> File,
    discardPdf: suspend (String) -> Unit,
    initialScrollProgress: Float,
    foregroundColor: Color,
    onOpenInBrowser: () -> Unit,
    onScrollPositionChanged: (Float) -> Unit,
    onScrollProgress: (Float) -> Unit,
    onScrollMetrics: (offsetPx: Float, scrollableRangePx: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var phase by remember(sourceUrl) { mutableStateOf<PdfPhase>(PdfPhase.Loading) }
    var attempt by remember(sourceUrl) { mutableIntStateOf(0) }

    LaunchedEffect(sourceUrl, attempt) {
        phase = PdfPhase.Loading
        val file = try {
            loadPdf(sourceUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            phase = PdfPhase.Failed
            return@LaunchedEffect
        }
        phase = try {
            PdfPhase.Ready(PdfPageSource.open(file))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            PdfPhase.Encrypted
        } catch (e: Exception) {
            // Unlesbare Datei verwerfen, damit "Erneut versuchen" eine frische Kopie lädt.
            discardPdf(sourceUrl)
            PdfPhase.Failed
        }
    }

    val current = phase
    DisposableEffect(current) {
        onDispose { (current as? PdfPhase.Ready)?.source?.close() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (current) {
            PdfPhase.Loading -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("PDF wird geladen…", color = foregroundColor)
            }

            PdfPhase.Failed -> PdfMessage(
                text = "Die PDF konnte nicht geladen werden.",
                color = foregroundColor,
                onRetry = { attempt++ },
                onOpenInBrowser = onOpenInBrowser,
                modifier = Modifier.align(Alignment.Center),
            )

            PdfPhase.Encrypted -> PdfMessage(
                text = "Diese PDF ist passwortgeschützt und kann hier nicht angezeigt werden.",
                color = foregroundColor,
                onRetry = null,
                onOpenInBrowser = onOpenInBrowser,
                modifier = Modifier.align(Alignment.Center),
            )

            is PdfPhase.Ready -> PdfPages(
                source = current.source,
                initialScrollProgress = initialScrollProgress,
                onScrollPositionChanged = onScrollPositionChanged,
                onScrollProgress = onScrollProgress,
                onScrollMetrics = onScrollMetrics,
            )
        }
    }
}

@Composable
private fun PdfMessage(
    text: String,
    color: Color,
    onRetry: (() -> Unit)?,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = color)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text("Erneut versuchen") }
        }
        TextButton(onClick = onOpenInBrowser) { Text("Im Browser öffnen") }
    }
}

@Composable
private fun PdfPages(
    source: PdfPageSource,
    initialScrollProgress: Float,
    onScrollPositionChanged: (Float) -> Unit,
    onScrollProgress: (Float) -> Unit,
    onScrollMetrics: (Float, Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidth = maxWidth - 32.dp
        val pageWidthPx = with(density) { pageWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val viewportPx = constraints.maxHeight.toFloat()
        val spacingPx = with(density) { 12.dp.toPx() }
        val topPaddingPx = with(density) { 8.dp.toPx() }
        // Platz für die Bottom-Bar, damit die letzte Seite nicht darunter verschwindet.
        val bottomPaddingPx = with(density) { 96.dp.toPx() }

        // prefix[i] = Oberkante von Seite i im Inhalt (ohne Top-Padding) – daraus Offset, Fortschritt, Restore.
        val prefix = remember(source, pageWidthPx, spacingPx) {
            FloatArray(source.pageCount + 1).also { p ->
                for (i in 0 until source.pageCount) p[i + 1] = p[i] + pageWidthPx * source.aspects[i] + spacingPx
            }
        }
        val contentPx = prefix.last() - spacingPx + topPaddingPx + bottomPaddingPx
        val rangePx = (contentPx - viewportPx).coerceAtLeast(0f)

        val listState = rememberLazyListState()
        fun offsetPx(): Float =
            prefix[listState.firstVisibleItemIndex.coerceIn(0, source.pageCount)] + listState.firstVisibleItemScrollOffset
        fun progress(): Float = if (rangePx > 0f) (offsetPx() / rangePx).coerceIn(0f, 1f) else 0f

        // Erst nach der Wiederherstellung darf gespeichert werden, sonst überschreibt der Startwert 0 die gespeicherte Position.
        var restored by remember(source) { mutableStateOf(initialScrollProgress <= 0.001f) }
        LaunchedEffect(source, rangePx) {
            if (!restored && rangePx > 0f) {
                val target = initialScrollProgress * rangePx
                var index = 0
                while (index + 1 < source.pageCount && prefix[index + 1] <= target) index++
                listState.scrollToItem(index, (target - prefix[index]).roundToInt().coerceAtLeast(0))
                restored = true
            }
        }

        LaunchedEffect(listState, rangePx) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect {
                    onScrollProgress(progress())
                    onScrollMetrics(offsetPx(), rangePx)
                }
        }
        LaunchedEffect(listState, rangePx) {
            // Nach jedem Scroll-Ende speichern (true → false); drop(1) überspringt den Startwert.
            snapshotFlow { listState.isScrollInProgress }
                .drop(1)
                .filter { !it && restored }
                .collect { onScrollPositionChanged(progress()) }
        }
        DisposableEffect(listState) {
            onDispose { if (restored) onScrollPositionChanged(progress()) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                top = with(density) { topPaddingPx.toDp() },
                bottom = with(density) { bottomPaddingPx.toDp() },
            ),
        ) {
            items(count = source.pageCount, key = { it }) { index ->
                PdfPageItem(
                    source = source,
                    index = index,
                    widthPx = pageWidthPx,
                    modifier = Modifier
                        .width(pageWidth)
                        .height(with(density) { (pageWidthPx * source.aspects[index]).toDp() }),
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    source: PdfPageSource,
    index: Int,
    widthPx: Int,
    modifier: Modifier = Modifier,
) {
    // Item-Komposition endet, sobald die Seite aus dem Sichtbereich scrollt – das Bitmap geht dann an die GC.
    var bitmap by remember(source, index, widthPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source, index, widthPx) {
        bitmap = try {
            source.render(index, widthPx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .shadow(2.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val rendered = bitmap
        if (rendered != null) {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = "Seite ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Medium,
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
    }
}

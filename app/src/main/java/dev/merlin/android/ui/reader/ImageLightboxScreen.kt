package dev.merlin.android.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.merlin.android.viewmodel.ArticleReaderViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Äquivalent zu `LightboxState`/`ImageLightboxView.swift`, getriggert vom
 * `imageTap`-JS-Event im Reader ([ReaderJsBridge.onImageTap]).
 */
data class LightboxState(val initialIndex: Int, val imageURLs: List<String>)

private const val PAGE_MULTIPLIER = 500

/**
 * Volle Parität zum iOS-Original: endloses horizontales Karussell
 * ([HorizontalPager] mit Index-Multiplikator statt iOS' `TabView`+Tag-Trick),
 * Pinch-Zoom/Pan/Doppeltap-Zoom pro Bild (`detectTransformGestures`/
 * `detectTapGestures` statt `MagnificationGesture`/`DragGesture`),
 * vertikaler Drag-to-dismiss mit Backdrop-Fade. Als [Dialog] statt eigener
 * Navigation-Route komponiert – entspricht iOS' modalem Full-Screen-Cover,
 * ohne den Reader-`NavHost` mit einer Lightbox-Route zu verkomplizieren.
 *
 * Anders als iOS' `TabViewScrollEnabler`-UIKit-Hack (View-Hierarchie-
 * Traversal, um das UIScrollView hinter der TabView zu sperren) blockiert
 * hier schlicht `HorizontalPager(userScrollEnabled = !isZoomed)` das Paging
 * während des Zooms.
 *
 * Vereinfachung ggü. iOS: die Pinch-Geste klemmt den Scale direkt auf
 * `[1, maxScale]`, statt während der Geste kurz darunter zuzulassen und erst
 * am Gestenende zurückzuschnappen (`MagnificationGesture.onEnded` im
 * Original) – visuell praktisch identisch, ohne zusätzliches
 * Gesten-Ende-Tracking.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageLightboxScreen(
    state: LightboxState,
    onDismiss: () -> Unit,
    viewModel: ArticleReaderViewModel = hiltViewModel(),
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        LightboxContent(state = state, onDismiss = onDismiss, imageLoader = viewModel.imageLoader)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LightboxContent(state: LightboxState, onDismiss: () -> Unit, imageLoader: ImageLoader) {
    val imageCount = state.imageURLs.size
    val totalPages = imageCount * PAGE_MULTIPLIER * 2
    val initialPage = remember { state.initialIndex + imageCount * PAGE_MULTIPLIER }
    val pagerState = rememberPagerState(initialPage = initialPage) { totalPages }

    // Ein Animatable statt zwei (committed/live) wie im iOS-Original – snapTo
    // während des Drags, animateTo für Zurückfedern/Dismiss-Flug.
    val dragOffsetY = remember { Animatable(0f) }
    var isZoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    fun dismissAnimated(upward: Boolean) {
        scope.launch {
            dragOffsetY.animateTo(
                targetValue = if (upward) -2200f else 2200f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 220f),
            )
            onDismiss()
        }
    }

    val backdropAlpha by remember {
        derivedStateOf { if (isZoomed) 1f else (1f - abs(dragOffsetY.value) / 1000f).coerceIn(0.15f, 1f) }
    }
    val contentScale by remember {
        derivedStateOf { if (isZoomed) 1f else (1f - abs(dragOffsetY.value) / 3750f).coerceIn(0.88f, 1f) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha)),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffsetY.value
                    scaleX = contentScale
                    scaleY = contentScale
                }
                // Vertikaler Drag → Dismiss; blockiert während gezoomt (wie iOS' `guard !isZoomed`).
                .pointerInput(isZoomed) {
                    if (isZoomed) return@pointerInput
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val offset = dragOffsetY.value
                            val far = abs(offset) > with(density) { 90.dp.toPx() }
                            if (far) {
                                dismissAnimated(upward = offset < 0)
                            } else {
                                scope.launch { dragOffsetY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 300f)) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragOffsetY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 300f)) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { dragOffsetY.snapTo(dragOffsetY.value + dragAmount) }
                    }
                },
        ) { page ->
            val imageIndex = ((page % imageCount) + imageCount) % imageCount
            ZoomableImage(
                url = state.imageURLs[imageIndex],
                imageLoader = imageLoader,
                onScaleChange = { scale -> if (page == pagerState.currentPage) isZoomed = scale > 1.05f },
            )
        }

        IconButton(
            onClick = { dismissAnimated(upward = false) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Schließen",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }

        if (imageCount > 1) {
            val visibleIndex = ((pagerState.currentPage % imageCount) + imageCount) % imageCount
            Text(
                "${visibleIndex + 1} / $imageCount",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            )
        }
    }
}

/** Äquivalent zu `ZoomableImageView` (iOS): Pinch-Zoom, Pan (nur gezoomt), Doppeltap-Zoom. */
@Composable
private fun ZoomableImage(
    url: String,
    imageLoader: ImageLoader,
    onScaleChange: (Float) -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val maxScale = 5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, maxScale)
                    scale = newScale
                    offset = if (newScale > 1.05f) offset + pan else Offset.Zero
                    onScaleChange(newScale)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1.05f) 1f else 2.5f
                        offset = Offset.Zero
                        onScaleChange(scale)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

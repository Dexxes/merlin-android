package dev.merlin.android.ui.reader

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.merlin.android.R
import dev.merlin.android.network.VideoStreamVariant
import dev.merlin.android.viewmodel.ArticleReaderViewModel

/**
 * ARD-, ZDF- und Arte-Mediathek-Artikel können vom Server (siehe `VideoStreamResolverService` in
 * merlin-nextcloud/merlin-server) in eine direkt abspielbare HLS-Stream-URL aufgelöst werden.
 * Dieser Host-Check entscheidet, ob es sich überhaupt lohnt, den `/video-stream`-Endpunkt für
 * einen Artikel anzufragen. Äquivalent zu `NativeVideoHost` in `NativeVideoPlayerView.swift`.
 */
object NativeVideoHost {
    private val hosts = listOf("ardmediathek.de", "zdf.de", "arte.tv")

    fun matches(urlString: String): Boolean {
        val host = runCatching { Uri.parse(urlString)?.host }.getOrNull()?.lowercase() ?: return false
        return hosts.any { host == it || host.endsWith(".$it") }
    }
}

/**
 * Lädt und spielt den nativen ARD/ZDF/Arte-Stream für einen Artikel ab. Fragt den
 * `/video-stream`-Endpunkt nur einmal pro Artikel ab ([LaunchedEffect] auf `articleId`) und
 * bleibt unsichtbar (`variants.isEmpty()`), wenn kein Stream verfügbar ist (z. B. Sendung nicht
 * mehr online) – analog zu `VideoPlayer.vue`, das bei `available == false` ebenfalls nichts
 * rendert, und zu `NativeVideoPlayerCard` in `NativeVideoPlayerView.swift`.
 *
 * **Architekturabweichung von iOS:** dort sitzt die Karte als scrollendes Element *innerhalb* der
 * äußeren `ScrollView`, direkt über dem `ArticleWebView`. Der Android-Reader rendert Header +
 * Artikeltext dagegen komplett als HTML in einer einzigen, selbst scrollenden `WebView` (siehe
 * `ReaderWebView`-Klassenkommentar) – ein natives Compose-Element lässt sich dort nicht
 * einbetten. Die Karte ist deshalb hier ein fixiertes Element *über* der `WebView`
 * ([ArticleReaderScreen]), analog zum bereits bestehenden `PaywallWarningBanner`-Overlay.
 */
@Composable
fun NativeVideoPlayerCard(
    articleId: Int,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    viewModel: ArticleReaderViewModel = hiltViewModel(),
) {
    var variants by remember(articleId) { mutableStateOf<List<VideoStreamVariant>>(emptyList()) }
    var selectedIndex by remember(articleId) { mutableStateOf(0) }
    // Player-Cover (Poster) analog zum iOS-Original: bleibt sichtbar, bis der Nutzer auf Play
    // tippt, statt sofort einen schwarzen Player-Rahmen zu zeigen.
    var showCover by remember(articleId) { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showVariantMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // Ein einziger ExoPlayer wird zwischen Inline- und Vollbild-`PlayerView` geteilt (siehe
    // `NativeVideoFullScreenView`-Kommentar im iOS-Original zum selben Muster mit `AVPlayer`),
    // damit Wiedergabeposition/-status beim Auf-/Zuklappen erhalten bleiben.
    val player = remember(articleId) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(articleId) {
        val response = runCatching { viewModel.getVideoStream(articleId) }.getOrNull()
        val responseVariants = response?.variants
        if (response != null && response.available && !responseVariants.isNullOrEmpty()) {
            variants = responseVariants
            selectedIndex = (response.defaultIndex ?: 0).coerceIn(0, responseVariants.size - 1)
        }
    }

    LaunchedEffect(variants, selectedIndex) {
        val variant = variants.getOrNull(selectedIndex) ?: return@LaunchedEffect
        showCover = true
        player.setMediaItem(MediaItem.fromUri(variant.url))
        player.prepare()
        // Arte liefert mehrsprachige Untertitelspuren im selben Manifest — ohne explizite Auswahl
        // spielt ExoPlayer sonst keine oder die falsche Spur (siehe hls.js-Handling in
        // VideoPlayer.vue bzw. AVMediaSelectionGroup-Auswahl im iOS-Original, die dasselbe Ziel
        // mit AVKit-eigenen Mitteln erreicht).
        if (variant.subtitleLanguage != null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setPreferredTextLanguage(variant.subtitleLanguage)
                .build()
        }
    }

    if (variants.isEmpty()) return

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = true } },
                // Nur EIN `PlayerView` darf den geteilten `player` gleichzeitig halten – sonst
                // reißen sich Inline- und Vollbild-Ansicht bei jeder Recomposition gegenseitig die
                // Video-Surface weg (`PlayerView.setPlayer` bindet sie exklusiv an sich). Während
                // `isFullScreen` gehört sie dem Dialog unten, hier also `null`.
                update = { it.player = if (isFullScreen) null else player },
                modifier = Modifier.fillMaxSize(),
            )

            if (showCover) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        imageLoader = viewModel.imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                }
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(54.dp)
                        .clickable {
                            showCover = false
                            player.play()
                        },
                )
            } else {
                // Vollbild-Umschalter erst sichtbar, sobald die Wiedergabe begonnen hat - auf dem
                // Cover würde er nur den Play-Tap stören (1:1 wie im iOS-Original).
                IconButton(
                    onClick = { isFullScreen = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = null, tint = Color.White)
                }
            }
        }

        if (variants.size > 1) {
            Box {
                TextButton(onClick = { showVariantMenu = true }) {
                    Text("${stringResource(R.string.articleReader_video_variant)}: ${variants[selectedIndex].label}")
                }
                DropdownMenu(expanded = showVariantMenu, onDismissRequest = { showVariantMenu = false }) {
                    variants.forEachIndexed { index, variant ->
                        DropdownMenuItem(
                            text = { Text(variant.label) },
                            onClick = {
                                selectedIndex = index
                                showVariantMenu = false
                            },
                        )
                    }
                }
            }
        }
    }

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { PlayerView(it).apply { useController = true } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = { isFullScreen = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_done),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

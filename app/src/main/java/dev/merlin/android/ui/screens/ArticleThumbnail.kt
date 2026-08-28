package dev.merlin.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.merlin.android.R

/**
 * Äquivalent zu `CachedAsyncImage`/`NoImageView.swift` aus dem iOS-Original.
 * Anders als dort gibt es hier kein eigenes Disk-Cache-Lookup vor dem Laden –
 * Coils [ImageLoader] (mit `RefererInterceptor`, siehe `ImageModule`) übernimmt
 * Cache-First-Verhalten transparent: ein bereits gecachtes Bild wird ohne
 * Netzwerk-Request aus dem Disk-Cache angezeigt. Bei `null`/leerem [imageUrl]
 * oder Ladefehler erscheint – 1:1 wie `NoImageView.swift` – das transparente
 * Merlin-Logo (`R.drawable.no_img`, 1:1-Kopie von `no-img.png`) auf einem
 * Hintergrund in der vom Nutzer im Erscheinungsbild-Menü gewählten Akzentfarbe
 * ([accentColorHex], aus `PreferencesStore`), damit es in Light- und Darkmode
 * gleich aussieht.
 */
@Composable
fun ArticleThumbnail(
    imageUrl: String?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    accentColorHex: String = "#FF3B30",
) {
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrEmpty() && !loadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageUrl).build(),
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val accentColor = remember(accentColorHex) {
                runCatching { Color(android.graphics.Color.parseColor(accentColorHex)) }
                    .getOrDefault(Color(0xFFFF3B30))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.no_img),
                    contentDescription = contentDescription ?: "Kein Vorschaubild",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

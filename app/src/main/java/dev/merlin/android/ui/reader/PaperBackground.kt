package dev.merlin.android.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext

/**
 * Papierstruktur-Kachel in den Assets: die 512×512-Kachel aus merlin-ios
 * (`scripts/generate-paper-texture.py`), umgerechnet in schwarze Pixel mit Alpha = 1 − Grauwert.
 * Normal über die Theme-Farbe gelegt ergibt das genau das Multiply von iOS (Farbe × Grauwert),
 * ohne Blend-Modus; Sepia behält so seinen Farbton.
 */
private const val PAPER_TEXTURE_ASSET = "paper-texture.png"

/** Geladene Kachel als wiederholender Pinsel samt Kachelhöhe in Pixeln. */
class PaperTile(val brush: ShaderBrush, val heightPx: Float)

/** Lädt die Kachel einmal pro Composition; `null`, wenn das Asset fehlt. */
@Composable
fun rememberPaperTile(): PaperTile? {
    val context = LocalContext.current
    return remember {
        runCatching {
            val bitmap = context.assets.open(PAPER_TEXTURE_ASSET).use { BitmapFactory.decodeStream(it) }
            val image = bitmap.asImageBitmap()
            PaperTile(ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated)), image.height.toFloat())
        }.getOrNull()
    }
}

/**
 * Reader-Hintergrund wie `PaperBackground` in ArticleReaderView.swift: Theme-Farbe, darüber
 * (wenn [tile] gesetzt) die Papierstruktur. Ein Texel ist ein Gerätepixel. Die Fläche bleibt
 * bildschirmfest und wird um [scrollOffsetPx] modulo Kachelhöhe verschoben – die Fasern wandern
 * so mit dem Text wie bei echtem Papier. Gedacht für die durchsichtige [ReaderWebView] davor.
 */
fun Modifier.paperBackground(color: Color, tile: PaperTile?, scrollOffsetPx: () -> Float): Modifier =
    clipToBounds().drawBehind {
        drawRect(color)
        if (tile != null) {
            val shift = scrollOffsetPx() % tile.heightPx
            translate(top = -shift) {
                drawRect(tile.brush, size = Size(size.width, size.height + tile.heightPx))
            }
        }
    }

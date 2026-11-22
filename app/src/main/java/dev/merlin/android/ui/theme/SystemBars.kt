package dev.merlin.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

/**
 * Farben hinter Status- und Navigationsleiste. Seit `targetSdk 35` läuft jede App edge-to-edge,
 * `Window.statusBarColor`/`navigationBarColor` wirken nicht mehr. Stattdessen zeichnet
 * [SystemBarsFrame] die Flächen unter den Leisten selbst; Screens wie der Reader setzen hier
 * nur die gewünschte Farbe (`null` = App-Hintergrund).
 */
@Stable
class SystemBarColors {
    /** Fläche unter der Statusleiste (und der Kamera-Aussparung oben). */
    var statusBar by mutableStateOf<Color?>(null)

    /** Fläche unter der Navigationsleiste, seitliche Leisten im Querformat und hinter der Tastatur. */
    var navigationBar by mutableStateOf<Color?>(null)

    /** `true`: Inhalt randlos bis unter die Leisten (Zitat-Tafel), ohne Inset-Padding. */
    var fullBleed by mutableStateOf(false)
}

/** `null` außerhalb von [SystemBarsFrame], etwa in der ShareActivity. */
val LocalSystemBarColors = staticCompositionLocalOf<SystemBarColors?> { null }

/**
 * Hält den Inhalt wie vor edge-to-edge aus Status-, Navigationsleiste, Kamera-Aussparung und
 * Tastatur heraus (`safeDrawing`, verbraucht die Insets, damit Scaffolds/TopAppBars darin sie
 * nicht ein zweites Mal anwenden) und füllt die Flächen darunter mit [SystemBarColors].
 */
@Composable
fun SystemBarsFrame(
    colors: SystemBarColors,
    background: Color,
    content: @Composable () -> Unit,
) {
    val statusColor = colors.statusBar ?: background
    val navColor = colors.navigationBar ?: background
    val topPx = WindowInsets.systemBars.union(WindowInsets.displayCutout).getTop(LocalDensity.current).toFloat()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(navColor)
            .drawBehind { drawRect(statusColor, size = Size(size.width, topPx)) }
            .then(if (colors.fullBleed) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)),
    ) {
        content()
    }
}

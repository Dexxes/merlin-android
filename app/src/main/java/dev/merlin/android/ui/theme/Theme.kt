package dev.merlin.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.merlin.android.models.ReaderTheme

// Vollständige Material3-Schemata (Light/Dark/Sepia) statt der früheren Platzhalter, die nur
// `primary` definierten – dadurch fielen Surface/Background/OnSurface etc. (und damit auch
// Menüs, Karten in der Artikelübersicht) auf die Compose-Stock-Defaults zurück und wirkten im
// Dark Mode inkonsistent. Sepia nutzt bewusst dieselben Töne wie `rememberReaderChromeColors`
// (ArticleReaderScreen.kt), damit der Sepia-Look zwischen Lese-Ansicht und restlicher App-Chrome
// (Menüs, Artikelübersicht) konsistent bleibt.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0082C9),
    onPrimary = Color.White,
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFC6C6C8),
    error = Color(0xFFD32F2F),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FA8E0),
    onPrimary = Color.Black,
    secondary = Color(0xFF7A79E0),
    onSecondary = Color.Black,
    // Echtes Schwarz (#000000) statt des früheren #1C1C1E-Surface-Tons – sowohl background als
    // auch surface, damit TopAppBar/Scaffold/Cards (Material3 hebt Surfaces standardmäßig nicht
    // farblich hervor, nur per Elevation-Overlay) keinen sichtbar helleren Grauton zeigen.
    // surfaceVariant bleibt bewusst dunkelgrau (#333333), sonst verschwimmen Tag-Chips/Dividers
    // unsichtbar vor dem reinen Schwarz.
    background = Color.Black,
    onBackground = Color(0xFFE5E5EA),
    surface = Color.Black,
    onSurface = Color(0xFFE5E5EA),
    surfaceVariant = Color(0xFF333333),
    onSurfaceVariant = Color(0xFF98989D),
    outline = Color(0xFF48484A),
    error = Color(0xFFEF5350),
    onError = Color.Black,
)

private val SepiaColors = lightColorScheme(
    primary = Color(0xFF0082C9),
    onPrimary = Color.White,
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    background = Color(0xFFF5ECD9),
    onBackground = Color(0xFF3B2F1E),
    surface = Color(0xFFF5ECD9),
    onSurface = Color(0xFF3B2F1E),
    surfaceVariant = Color(0xFFD1C2A8),
    onSurfaceVariant = Color(0xFF7A6350),
    outline = Color(0xFFAE9678),
    outlineVariant = Color(0xFFD1C2A8),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    // Material3 färbt Seitenmenü (ModalDrawerSheet), Karten, Dropdown-Menüs und Bottom Sheets mit
    // den surfaceContainer-Tönen, nicht mit `surface`. Ohne eigene Werte blieben dort die hellen,
    // leicht lila Standardtöne stehen – das Seitenmenü und die Karten in der Artikelübersicht
    // sahen in Sepia aus wie in Hell. Abgestuft zwischen Hintergrund und `surfaceVariant`.
    surfaceTint = Color(0xFFAE9678),
    surfaceBright = Color(0xFFF9F2E4),
    surfaceDim = Color(0xFFE2D4B9),
    surfaceContainerLowest = Color(0xFFF9F2E4),
    surfaceContainerLow = Color(0xFFF0E5CF),
    surfaceContainer = Color(0xFFECE0C8),
    surfaceContainerHigh = Color(0xFFE7DAC0),
    surfaceContainerHighest = Color(0xFFE2D4B9),
    primaryContainer = Color(0xFFE2D4B9),
    onPrimaryContainer = Color(0xFF3B2F1E),
    secondaryContainer = Color(0xFFE2D4B9),
    onSecondaryContainer = Color(0xFF3B2F1E),
    inverseSurface = Color(0xFF3B2F1E),
    inverseOnSurface = Color(0xFFF5ECD9),
)

/**
 * App-weites Theme, gesteuert über [theme] (Äquivalent zu `PreferencesStore.readerTheme`,
 * bisher nur für die Lese-Ansicht genutzt – jetzt zusätzlich für Menüs, Artikelübersicht etc.).
 * `AUTO` folgt dem System; `SEPIA` hat kein System-Äquivalent und wird daher immer als
 * eigenständiges (helles, warmtoniges) Schema behandelt statt auf `LIGHT` abgebildet zu werden.
 */
@Composable
fun MerlinTheme(
    theme: ReaderTheme = ReaderTheme.AUTO,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = theme == ReaderTheme.DARK || (theme == ReaderTheme.AUTO && systemDark)
    val colors = when (theme) {
        ReaderTheme.SEPIA -> SepiaColors
        else -> if (isDark) DarkColors else LightColors
    }

    // Status- UND Navigationsleiste an den App-Hintergrund anpassen (hell im Light-/Sepia-,
    // dunkel im Dark-Theme) statt des grauen/weißen Plattform-Scrims aus dem alten
    // android:Theme.Material.Light-Basistheme (siehe themes.xml, dort bewusst nur als Fenster-/
    // Splash-Theme belassen). Vorher wurde hier nur `statusBarColor` gesetzt – die untere
    // Systemleiste (`navigationBarColor`) blieb dadurch außerhalb des Readers (der das schon
    // separat selbst fixt, siehe ArticleReaderScreen.kt) permanent auf dem hellen Fenster-Theme
    // stehen, sichtbar als weiße Leiste am unteren Bildschirmrand z.B. in der Artikelübersicht.
    // Icon-/Uhrzeitfarbe beider Leisten entsprechend mitschalten, sonst wären helle Icons auf
    // hellem Grund (oder umgekehrt) unlesbar.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}

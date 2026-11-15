package dev.merlin.android.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.merlin.android.R

/**
 * Äquivalent zu iOS' `SplashView` (`MerlinApp.swift`): weißer Grund, das Merlin-Logo in voller
 * Form (96 dp, pixelgenau ohne Interpolation, an derselben Stelle wie im System-Splash), darunter
 * ein Ladeindikator und ein von Anfang an reservierter Platz für die Frage
 * "Im Offlinemodus starten?", die nach einigen Sekunden ohne fertigen ersten Ladevorgang
 * eingeblendet wird (siehe [dev.merlin.android.MainActivity]).
 *
 * Die Farben sind bewusst fest (Weiß/Grau) statt aus dem MaterialTheme, wie auf iOS – der Splash
 * sieht unabhängig vom Erscheinungsbild gleich aus und schließt nahtlos an den System-Splash
 * (`Theme.Merlin.Starting`, gleiche Hintergrundfarbe, gleiches Logo) an.
 */
@Composable
fun MerlinSplash(
    showOfflinePrompt: Boolean,
    onStartOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val promptAlpha by animateFloatAsState(
        targetValue = if (showOfflinePrompt) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "offlinePromptAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.splash_background))
            // Touches nicht an die darunterliegende Liste durchreichen.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        // Logo exakt mittig – an derselben Stelle und in derselben Größe wie im System-Splash
        // (drawable/splash_icon.xml), damit der Übergang nicht springt. Spinner und Offline-Frage
        // hängen darunter, statt wie bei iOS mit dem Logo zusammen zentriert zu werden.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.merlin_logo),
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(LOGO_SIZE),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 24.dp),
            ) {
                // Ladeindikator bleibt die ganze Splash-Zeit sichtbar (iOS: `ProgressView().controlSize(.large)`).
                CircularProgressIndicator(color = Color.Gray)

                // Immer Platz reservieren, damit nichts springt.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .alpha(promptAlpha)
                        .then(if (showOfflinePrompt) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    Text(
                        text = stringResource(R.string.splash_slowConnection_message),
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onStartOffline, enabled = showOfflinePrompt) {
                        Text(
                            text = stringResource(R.string.splash_slowConnection_offline),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** Logo-Größe wie iOS' `SplashView` und wie `drawable/splash_icon.xml` (1/3 der 288-dp-Icon-Fläche des System-Splashs). */
private val LOGO_SIZE = 96.dp

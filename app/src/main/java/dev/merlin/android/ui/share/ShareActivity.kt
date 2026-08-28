package dev.merlin.android.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import dev.merlin.android.ui.theme.MerlinTheme

/**
 * Äquivalent zur `MerlinShare`-Extension (iOS): nimmt `ACTION_SEND`-Intents
 * (z.B. „Teilen“ aus dem Browser) entgegen und zeigt [ShareScreen] als
 * halbtransparentes Overlay über der teilenden App – Äquivalent zum
 * `UIColor.black.withAlphaComponent(0.4)`-Hintergrund im iOS-Original.
 *
 * Anders als iOS' Extension-Prozess ist dies eine normale Activity im
 * Haupt-App-Prozess (siehe `CredentialsStore`-Kommentar) – daher kein
 * eigener Keychain-Zugriff/Netzwerk-Stack nötig, alles läuft über die
 * bestehenden Hilt-Singletons.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = extractSharedText(intent)
        setContent {
            MerlinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.4f),
                ) {
                    ShareScreen(sharedText = sharedText, onClose = { finish() })
                }
            }
        }
    }

    /**
     * Liest den geteilten Text aus `EXTRA_TEXT` (Standard für `ACTION_SEND`
     * mit `text/plain`). Manche Apps legen die URL zusätzlich/alternativ in
     * `EXTRA_SUBJECT` ab – als Fallback ebenfalls berücksichtigt, analog zum
     * Zwei-Pass-Ansatz in `findURL(in:)` (Swift).
     */
    private fun extractSharedText(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) return text
        return intent.getStringExtra(Intent.EXTRA_SUBJECT)
    }
}

package dev.merlin.android.ui.share

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.merlin.android.ui.theme.MerlinTheme
import dev.merlin.android.viewmodel.SharedContent

/**
 * Äquivalent zur `MerlinShare`-Extension (iOS): nimmt `ACTION_SEND`/
 * `ACTION_SEND_MULTIPLE` entgegen – Links und Text ebenso wie Fotos, Videos,
 * Audio, PDFs und andere Dateien – und zeigt [ShareScreen] als Sheet am unteren
 * Rand über der teilenden App, die abgedunkelt durchscheint (transparentes
 * Fenster, `Theme.Merlin.Share`).
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
        // Ab targetSdk 35 Pflicht: randlos. Durchsichtige Leisten, Abdunkelung und Sheet zeichnet
        // ShareScreen selbst darunter (siehe dort die Inset-Paddings).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val shared = extractSharedContent(intent)
        setContent {
            MerlinTheme {
                ShareScreen(sharedContent = shared, onClose = { finish() })
            }
        }
    }

    /**
     * Text aus `EXTRA_TEXT` (Standard für `text/plain`), Fallback `EXTRA_SUBJECT` –
     * manche Apps legen die URL dort ab. Dateien aus `EXTRA_STREAM` (eine bei
     * `ACTION_SEND`, mehrere bei `ACTION_SEND_MULTIPLE`), ergänzt um die `ClipData`
     * des Intents, über die manche Apps ihre Dateien nur übergeben.
     */
    private fun extractSharedContent(intent: Intent): SharedContent {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return SharedContent(text = null, streams = emptyList(), mimeType = null)
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }

        val streams = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(streams::addAll)
        } else {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(streams::add)
        }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(streams::add)
        }
        return SharedContent(
            text = text,
            // Nur content:-URIs: eine file:-URI könnte auf Merlins eigene, private Dateien zeigen.
            streams = streams.filter { it.scheme == "content" }.distinct(),
            mimeType = intent.type,
        )
    }
}

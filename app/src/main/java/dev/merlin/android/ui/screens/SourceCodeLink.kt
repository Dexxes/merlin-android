package dev.merlin.android.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import dev.merlin.android.R

private const val SOURCE_CODE_URL = "https://github.com/dexxes/merlin-android"
private const val PRIVACY_URL = "https://github.com/Dexxes/merlin-android/blob/main/PRIVACY.md"
private const val CONTACT_URL = "mailto:jvb@live.de?subject=Merlin%2FAndroid%3A%20"

/**
 * Link auf das Git-Repository der App, in allen Einstellungs-Screens eingebunden
 * ([SettingsScreen], [SiteCredentialsScreen]). Öffnet die URL im System-Browser.
 */
@Composable
fun SourceCodeLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(SOURCE_CODE_URL) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_about_sourceCodeLabel))
    }
}

/**
 * Link auf die Datenschutzerklärung (`PRIVACY.md` im Repository), unter Einstellungen → Über –
 * wie `settings.about.privacyLabel` in `SettingsView.swift`. Öffnet die URL im System-Browser.
 */
@Composable
fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(PRIVACY_URL) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_about_privacyLabel))
    }
}

/**
 * „Entwickler kontaktieren“ unter Einstellungen → Über: öffnet die Mail-App mit vorbelegtem
 * Empfänger und Betreff „Merlin/Android: “, wie `settings.about.contactDeveloperLabel` in
 * `SettingsView.swift`. Ohne Mail-App passiert nichts.
 */
@Composable
fun ContactDeveloperLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { runCatching { uriHandler.openUri(CONTACT_URL) } },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_about_contactDeveloperLabel))
    }
}

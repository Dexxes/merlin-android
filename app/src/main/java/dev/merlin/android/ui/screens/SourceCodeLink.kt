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

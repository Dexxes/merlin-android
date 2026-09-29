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

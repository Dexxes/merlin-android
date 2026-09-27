package dev.merlin.android.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Äquivalent zum "unsupportedSiteBanner" in `ArticleReaderView.swift`: erscheint, sobald
 * `Article.unsupportedSiteDomain` gesetzt ist (Server hat den Fetch abgelehnt, siehe
 * `UnsupportedSiteException` in merlin-nextcloud - die Domain liefert grundsätzlich keinen
 * scrapbaren Artikeltext, z. B. PressReader). Anders als [PaywallWarningBanner] gibt es hier
 * keinen "Verbinden"/"Erneut versuchen"-Button: ein Login oder Retry würde am Ergebnis nichts
 * ändern. "Im Browser öffnen" für die Originalseite steht bereits im Menü der Reader-Toolbar
 * zur Verfügung, daher hier keine weitere Aktion. "X" blendet den Banner nur für diese
 * Reader-Sitzung aus (kein persistenter Dismiss-State, analog zu [PaywallWarningBanner]).
 */
@Composable
fun UnsupportedSiteBanner(
    domain: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                Icons.Filled.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            )
            Text(
                "Diese Seite ($domain) wird von Merlin nicht unterstützt: sie liefert keinen " +
                    "lesbaren Artikeltext, den Merlin abrufen könnte.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Ausblenden",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

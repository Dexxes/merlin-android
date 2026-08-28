package dev.merlin.android.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Äquivalent zum Paywall-Hinweis-Banner in `ArticleReaderView.swift`: erscheint, sobald
 * `Article.requiresLoginDomain` gesetzt ist (Extraktion an einer Paywall gescheitert, keine
 * gültigen Site-Credentials vorhanden – siehe `Article.kt`). "Verbinden" navigiert zu
 * [dev.merlin.android.ui.screens.SiteCredentialsScreen] mit vorausgewählter Domain, "Erneut
 * versuchen" ruft [dev.merlin.android.viewmodel.ArticleReaderViewModel.retryPaywall] auf
 * (löscht + legt den Artikel neu an), "X" blendet den Banner nur für diese Reader-Sitzung aus
 * (kein persistenter Dismiss-State, analog zum iOS-Original – beim nächsten Öffnen des Artikels
 * erscheint er wieder, solange `requiresLoginDomain` weiterhin gesetzt ist).
 */
@Composable
fun PaywallWarningBanner(
    domain: String,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                )
                Text(
                    "Dieser Artikel liegt möglicherweise hinter einer Paywall auf $domain. " +
                        "Verbinde dein Abo, um ihn freizuschalten.",
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(onClick = onConnect) { Text("Verbinden") }
                TextButton(onClick = onRetry) { Text("Erneut versuchen") }
            }
        }
    }
}

package dev.merlin.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.merlin.android.R
import dev.merlin.android.network.CredentialsStore
import dev.merlin.android.ui.components.LoginWebViewDialog
import dev.merlin.android.viewmodel.OnboardingViewModel

/**
 * Äquivalent zum Server-URL-Eingabeschritt in `SettingsView.swift`/`LoginFlowService.swift`:
 * Nutzer gibt die Nextcloud-URL ein, der Login selbst läuft – wie auf iOS – in einer
 * App-eigenen WebView im Vollbild ([LoginWebViewDialog]) über Nextclouds Login Flow v2 –
 * Merlin bekommt App-Passwort/Username erst über das Polling-Ergebnis zurück, sieht das Nextcloud-Passwort also nie direkt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onLoginSuccess: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    // "https://" vorbelegt, damit nur noch die Domain getippt werden muss –
    // wie `SettingsView.swift` auf iOS. Löscht der Nutzer das Schema, ergänzt
    // `LoginFlowService.normalizeBaseUrl` es wieder.
    var serverUrl by remember { mutableStateOf("https://") }
    var backendKind by remember { mutableStateOf(viewModel.initialBackendKind) }

    val isLoading by viewModel.isLoading.collectAsState()
    val loginUrl by viewModel.loginUrl.collectAsState()
    val error by viewModel.error.collectAsState()
    val loginSuccess by viewModel.loginSuccess.collectAsState()

    // Sobald der Server eine Login-URL liefert, die Login-Seite im Vollbild öffnen –
    // das eigentliche Anmelden (Passworteingabe, 2FA) übernimmt Nextcloud selbst.
    // Verschwindet automatisch, wenn das Polling erfolgreich war oder abgebrochen wurde.
    loginUrl?.let { url ->
        LoginWebViewDialog(url = url, onCancel = { viewModel.cancelLogin() })
    }

    LaunchedEffect(loginSuccess) {
        if (loginSuccess) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.android_settings_account_connectHeadline),
            style = MaterialTheme.typography.headlineSmall,
        )

        // "Nextcloud" ist ein Eigenname und bleibt unübersetzt.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            SegmentedButton(
                selected = backendKind == CredentialsStore.BackendKind.NEXTCLOUD,
                onClick = { backendKind = CredentialsStore.BackendKind.NEXTCLOUD },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Nextcloud") }
            SegmentedButton(
                selected = backendKind == CredentialsStore.BackendKind.STANDALONE,
                onClick = { backendKind = CredentialsStore.BackendKind.STANDALONE },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.settings_account_backendStandalone)) }
        }

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text(stringResource(R.string.settings_account_urlLabel)) },
            placeholder = { Text(stringResource(R.string.settings_account_urlPlaceholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )

        Button(
            onClick = { viewModel.startLogin(serverUrl, backendKind) },
            enabled = !isLoading && serverUrl.isNotBlank() && serverUrl.trim() != "https://",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(
                stringResource(
                    if (backendKind == CredentialsStore.BackendKind.STANDALONE) {
                        R.string.settings_account_loginButtonStandalone
                    } else {
                        R.string.settings_account_loginButton
                    },
                ),
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            Text(
                text = stringResource(R.string.settings_account_waitingForBrowser),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

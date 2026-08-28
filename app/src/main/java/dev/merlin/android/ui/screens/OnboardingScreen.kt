package dev.merlin.android.ui.screens

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import dev.merlin.android.R
import dev.merlin.android.network.CredentialsStore
import dev.merlin.android.viewmodel.OnboardingViewModel

/**
 * Äquivalent zum Server-URL-Eingabeschritt in `SettingsView.swift`/`LoginFlowService.swift`:
 * Nutzer gibt die Nextcloud-URL ein, der Login selbst läuft im System-Browser (Custom
 * Tabs) über Nextclouds Login Flow v2 – Merlin bekommt App-Passwort/Username erst
 * über das Polling-Ergebnis zurück, sieht das Nextcloud-Passwort also nie direkt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onLoginSuccess: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf("") }
    var backendKind by remember { mutableStateOf(viewModel.initialBackendKind) }

    val isLoading by viewModel.isLoading.collectAsState()
    val loginUrl by viewModel.loginUrl.collectAsState()
    val error by viewModel.error.collectAsState()
    val loginSuccess by viewModel.loginSuccess.collectAsState()

    // Sobald der Server eine Login-URL liefert, im System-Browser öffnen –
    // das eigentliche Anmelden (Passworteingabe, 2FA) übernimmt Nextcloud selbst.
    LaunchedEffect(loginUrl) {
        loginUrl?.let { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
        }
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
            text = stringResource(R.string.settings_account_connectHeadline),
            style = MaterialTheme.typography.headlineSmall,
        )

        // Backend-Namen ("Nextcloud"/"Standalone-Server") bewusst nicht lokalisiert -
        // Eigennamen, analog zu SettingsView.swift's `Picker("Backend", ...)`.
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
            ) { Text("Standalone-Server") }
        }

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text(stringResource(R.string.settings_account_urlLabel)) },
            placeholder = { Text(stringResource(R.string.settings_account_urlPlaceholder)) },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )

        Button(
            onClick = { viewModel.startLogin(serverUrl, backendKind) },
            enabled = !isLoading && serverUrl.isNotBlank(),
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

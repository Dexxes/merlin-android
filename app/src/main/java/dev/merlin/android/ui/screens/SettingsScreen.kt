package dev.merlin.android.ui.screens

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import dev.merlin.android.BuildConfig
import dev.merlin.android.models.ArticleFilter
import dev.merlin.android.models.ProgressEdge
import dev.merlin.android.network.CredentialsStore
import dev.merlin.android.viewmodel.SettingsViewModel

/**
 * Äquivalent zu `SettingsView.swift`: Account (Login/Logout per Nextcloud Login Flow v2),
 * Verbindungstest, App-Präferenzen (mit Server-Sync), Cache, Über, Entwickler.
 *
 * Statt iOS' `Form`/`Section`-Liste mit Sheet-Präsentation ein einfaches gescrolltes
 * `Column` in einem eigenen `Scaffold` mit Zurück-Pfeil (eigene Navigation-Route statt
 * `.sheet`+`dismiss()`, analog zu `RemindersScreen`). [onLoggedOut] wird aufgerufen, sobald
 * `isConfigured` von `true` auf `false` wechselt (Logout oder Cache leeren) – der Aufrufer
 * (`MainActivity`) navigiert dann zurück zum `OnboardingScreen`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val nextcloudUrl by viewModel.nextcloudUrl.collectAsState()
    val backendKind by viewModel.backendKind.collectAsState()
    val username by viewModel.username.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val isLoginLoading by viewModel.isLoginLoading.collectAsState()
    val loginUrl by viewModel.loginUrl.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val loginSuccess by viewModel.loginSuccess.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val cacheCleared by viewModel.cacheCleared.collectAsState()

    val defaultFilter by viewModel.preferencesStore.defaultFilter.collectAsState(initial = ArticleFilter.ALL)
    val progressEdge by viewModel.preferencesStore.progressEdge.collectAsState(initial = ProgressEdge.LEFT)
    val saveProgress by viewModel.preferencesStore.saveProgress.collectAsState(initial = true)
    val resumeOnOpen by viewModel.preferencesStore.resumeOnOpen.collectAsState(initial = true)
    val prefetchWifiOnly by viewModel.preferencesStore.prefetchImagesOnWifiOnly.collectAsState(initial = true)
    val cacheRetentionDays by viewModel.preferencesStore.cacheRetentionDays.collectAsState(initial = 30)
    val developerMode by viewModel.preferencesStore.developerMode.collectAsState(initial = false)

    var serverUrlInput by remember(nextcloudUrl) { mutableStateOf(nextcloudUrl.ifEmpty { "https://" }) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    // Sobald der Server eine Login-URL liefert, im System-Browser öffnen (siehe `OnboardingScreen`).
    LaunchedEffect(loginUrl) {
        loginUrl?.let { url -> CustomTabsIntent.Builder().build().launchUrl(context, url.toUri()) }
    }

    // War der Nutzer angemeldet und ist es jetzt nicht mehr (Logout/Cache leeren) →
    // Aufrufer informieren, der zurück zum Onboarding navigiert.
    var wasConfigured by remember { mutableStateOf(isConfigured) }
    LaunchedEffect(isConfigured) {
        if (wasConfigured && !isConfigured) onLoggedOut()
        wasConfigured = isConfigured
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // MARK: – Nextcloud-Konto
            SectionHeader("Konto")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = backendKind == CredentialsStore.BackendKind.NEXTCLOUD,
                    onClick = { viewModel.setBackendKind(CredentialsStore.BackendKind.NEXTCLOUD) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = !isLoginLoading,
                ) { Text("Nextcloud") }
                SegmentedButton(
                    selected = backendKind == CredentialsStore.BackendKind.STANDALONE,
                    onClick = { viewModel.setBackendKind(CredentialsStore.BackendKind.STANDALONE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = !isLoginLoading,
                ) { Text("Standalone-Server") }
            }
            OutlinedTextField(
                value = serverUrlInput,
                onValueChange = { serverUrlInput = it },
                label = { Text("Server-URL") },
                singleLine = true,
                enabled = !isLoginLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.startLogin(serverUrlInput) },
                enabled = !isLoginLoading && serverUrlInput.isNotBlank() && serverUrlInput != "https://",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (backendKind == CredentialsStore.BackendKind.STANDALONE) "Mit Standalone-Server anmelden" else "Mit Nextcloud anmelden")
            }
            if (isLoginLoading) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Warte auf Login im Browser …", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { viewModel.cancelLogin() }) { Text("Abbrechen") }
                }
            }
            if (loginSuccess) {
                Text(
                    "Erfolgreich angemeldet!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            loginError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (isConfigured) {
                Text(
                    "Angemeldet als $username",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Abmelden", color = MaterialTheme.colorScheme.error)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // MARK: – Verbindung testen
            SectionHeader("Verbindung testen")
            Button(
                onClick = { viewModel.testConnection() },
                enabled = !isTestingConnection && isConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Verbindung testen")
            }
            testResult?.let { result ->
                val (text, color) = when (result) {
                    is SettingsViewModel.TestResult.Success -> result.message to MaterialTheme.colorScheme.primary
                    is SettingsViewModel.TestResult.Failure -> result.message to MaterialTheme.colorScheme.error
                }
                Text(text, color = color, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // MARK: – Präferenzen
            SectionHeader("Präferenzen")
            Text("Standardansicht", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArticleFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter == defaultFilter,
                        onClick = { viewModel.setDefaultFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            Text(
                "Fortschrittsbalken",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressEdge.entries.forEach { edge ->
                    FilterChip(
                        selected = edge == progressEdge,
                        onClick = { viewModel.setProgressEdge(edge) },
                        label = { Text(edge.label()) },
                    )
                }
            }
            SettingsToggleRow(
                label = "Leseposition speichern",
                checked = saveProgress,
                onCheckedChange = { viewModel.setSaveProgress(it) },
            )
            SettingsToggleRow(
                label = "Beim Öffnen fortsetzen",
                checked = resumeOnOpen,
                onCheckedChange = { viewModel.setResumeOnOpen(it) },
            )
            Text(
                "Einstellungen synchronisieren automatisch mit deinem Nextcloud-Konto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // MARK: – Cache
            SectionHeader("Cache")
            SettingsToggleRow(
                label = "Bilder nur über WLAN vorladen",
                checked = prefetchWifiOnly,
                onCheckedChange = { viewModel.setPrefetchWifiOnly(it) },
            )
            Text("Artikel offline speichern")
            Slider(
                value = cacheRetentionDays.toFloat(),
                onValueChange = { viewModel.setCacheRetentionDays(it.toInt()) },
                valueRange = 0f..365f,
                steps = 364,
            )
            Text(
                if (cacheRetentionDays == 0) "Artikel nicht offline speichern" else "$cacheRetentionDays Tage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { showClearCacheConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cache leeren", color = MaterialTheme.colorScheme.error)
            }
            if (cacheCleared) {
                Text(
                    "Cache erfolgreich gelöscht.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Entfernt alle zwischengespeicherten Artikel, Bilder, Leseposition, das Web-" +
                    "Cache des Readers und deine Nextcloud-Zugangsdaten. Du wirst dadurch abgemeldet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // MARK: – Über
            SectionHeader("Über")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Version")
                Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // MARK: – Entwickler
            SectionHeader("Entwickler")
            SettingsToggleRow(
                label = "Entwicklermodus",
                checked = developerMode,
                onCheckedChange = { viewModel.setDeveloperMode(it) },
            )
            if (developerMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Text("nextcloudUrl: $nextcloudUrl", style = MaterialTheme.typography.bodySmall)
                    Text("username: ${username.ifEmpty { "<leer>" }}", style = MaterialTheme.typography.bodySmall)
                    Text("isConfigured: $isConfigured", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Abmelden?") },
            text = { Text("Deine Nextcloud-Zugangsdaten werden von diesem Gerät entfernt. Zwischengespeicherte Artikel und Lesepositionen bleiben erhalten.") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; viewModel.logout() }) { Text("Abmelden") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Abbrechen") }
            },
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Cache leeren?") },
            text = { Text("Alle zwischengespeicherten Artikel, Bilder, Lesepositionen, das Reader-Web-Cache und deine Nextcloud-Zugangsdaten werden gelöscht. Du wirst abgemeldet.") },
            confirmButton = {
                TextButton(onClick = { showClearCacheConfirm = false; viewModel.clearCache() }) { Text("Leeren") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ProgressEdge.label(): String = when (this) {
    ProgressEdge.LEFT -> "Links"
    ProgressEdge.RIGHT -> "Rechts"
    ProgressEdge.TOP -> "Oben"
    ProgressEdge.BOTTOM -> "Unten"
    ProgressEdge.OFF -> "Aus"
}

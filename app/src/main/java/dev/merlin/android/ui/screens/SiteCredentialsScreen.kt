package dev.merlin.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.merlin.android.R
import dev.merlin.android.models.SiteCredentialInfo
import dev.merlin.android.viewmodel.SiteCredentialsViewModel
import kotlinx.coroutines.launch

/**
 * Äquivalent zu `SiteCredentialsView.swift`: Übersicht + Verwaltung der Paywall-Site-
 * Zugangsdaten (getrennt vom Nextcloud-/Server-Login in `SettingsScreen`). "Verbunden"-
 * Sektion zeigt bereits gespeicherte Domains mit Status, "Hinzufügen"-Sektion die vom
 * Server unterstützten, aber noch nicht verbundenen Domains
 * ([SiteCredentialsViewModel.connectableDomains]). Ein Tap auf beide öffnet denselben
 * [SiteCredentialEditDialog].
 *
 * [preselectedDomain] – gesetzt, wenn von [dev.merlin.android.ui.reader.PaywallWarningBanner]
 * aus navigiert wurde – öffnet den Dialog für diese Domain automatisch beim Betreten des
 * Screens (Äquivalent zu iOS' `SiteCredentialsView(preselectedDomain:)`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteCredentialsScreen(
    onBack: () -> Unit,
    preselectedDomain: String? = null,
    viewModel: SiteCredentialsViewModel = hiltViewModel(),
) {
    val credentials by viewModel.credentials.collectAsState()
    val connectableDomains by viewModel.connectableDomains.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var editDomain by remember { mutableStateOf<String?>(null) }

    // Öffnet den Bearbeiten-Dialog für `preselectedDomain` beim ersten Betreten (nur einmal,
    // nicht bei jeder Recomposition – daher `Unit` als Key statt `preselectedDomain`).
    LaunchedEffect(Unit) {
        preselectedDomain?.let { editDomain = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.siteCredentials_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        bottomBar = { SourceCodeLink() },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading && credentials.isEmpty() && connectableDomains.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                credentials.isEmpty() && connectableDomains.isEmpty() -> {
                    EmptySiteCredentialsState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        errorMessage?.let { message ->
                            item {
                                Text(
                                    message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        if (credentials.isNotEmpty()) {
                            item {
                                SectionHeader(stringResource(R.string.siteCredentials_connectedSection))
                            }
                            items(credentials, key = { it.domain }) { credential ->
                                ConnectedSiteCredentialRow(
                                    credential = credential,
                                    onEdit = { editDomain = credential.domain },
                                    onDelete = { viewModel.delete(credential.domain) },
                                )
                            }
                        }
                        if (connectableDomains.isNotEmpty()) {
                            item {
                                SectionHeader(stringResource(R.string.siteCredentials_addSection), modifier = Modifier.padding(top = if (credentials.isNotEmpty()) 8.dp else 0.dp))
                            }
                            items(connectableDomains, key = { it }) { domain ->
                                ConnectableDomainRow(domain = domain, onClick = { editDomain = domain })
                            }
                        }
                    }
                }
            }
        }
    }

    editDomain?.let { domain ->
        SiteCredentialEditDialog(
            domain = domain,
            errorMessage = errorMessage,
            onDismiss = {
                editDomain = null
                viewModel.clearError()
            },
            onSave = { username, password -> viewModel.save(domain, username, password) },
        )
    }
}

@Composable
private fun EmptySiteCredentialsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.android_siteCredentials_noDomainsTitle),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.android_siteCredentials_noDomainsMessage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedSiteCredentialRow(
    credential: SiteCredentialInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.android_common_remove),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.align(
                            if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                Alignment.CenterStart
                            } else {
                                Alignment.CenterEnd
                            },
                        ),
                    )
                }
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(credential.domain, style = MaterialTheme.typography.bodyMedium)
                    val (statusLabelRes, statusColor) = statusLabelAndColor(credential.statusEnum)
                    val statusLabel = stringResource(statusLabelRes)
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.android_siteCredentials_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.android_common_remove))
                }
            }
        }
    }
}

@Composable
private fun ConnectableDomainRow(domain: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(domain, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun statusLabelAndColor(status: SiteCredentialInfo.Status): Pair<Int, Color> = when (status) {
    SiteCredentialInfo.Status.OK -> R.string.siteCredentials_status_ok to Color(0xFF34C759)
    SiteCredentialInfo.Status.INVALID_CREDENTIALS -> R.string.siteCredentials_status_invalidCredentials to Color(0xFFFF9800)
    SiteCredentialInfo.Status.LOGIN_FLOW_BROKEN -> R.string.siteCredentials_status_loginFlowBroken to Color(0xFFFF9800)
    SiteCredentialInfo.Status.PENDING -> R.string.siteCredentials_status_pending to Color.Gray
}

/**
 * Bearbeiten-Dialog für Username/Passwort einer Domain (neu oder bestehend, gleicher Dialog –
 * das Passwortfeld ist immer leer, da der Server es nie zurückliefert, siehe `SiteCredentialInfo`).
 * Zeigt nach einem Speicherversuch inline entweder eine Erfolgsmeldung oder [errorMessage] aus
 * dem ViewModel an.
 */
@Composable
private fun SiteCredentialEditDialog(
    domain: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: suspend (username: String, password: String) -> Boolean,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var savedSuccessfully by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(domain) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; savedSuccessfully = false },
                    label = { Text(stringResource(R.string.siteCredentials_usernamePlaceholder)) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; savedSuccessfully = false },
                    label = { Text(stringResource(R.string.siteCredentials_passwordPlaceholder)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (savedSuccessfully) {
                    Text(
                        stringResource(R.string.siteCredentials_savedHint),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        savedSuccessfully = onSave(username, password)
                        isSaving = false
                    }
                },
                enabled = !isSaving && username.isNotBlank() && password.isNotBlank(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.common_close)) }
        },
    )
}

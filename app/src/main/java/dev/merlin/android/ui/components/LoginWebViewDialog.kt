package dev.merlin.android.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.merlin.android.R

/**
 * Port von `SafariLoginView` + `.fullScreenCover` aus `SettingsView.swift`:
 * Die Login-Seite von Nextclouds Login Flow v2 läuft in einer App-eigenen [WebView]
 * im Vollbild statt in Custom Tabs/im System-Browser.
 *
 * Wie auf iOS (`WKWebsiteDataStore.nonPersistent()`) startet jeder Login ohne
 * Cookies/Web-Storage aus früheren Sitzungen, damit keine alte Nextcloud-Session
 * oder ein abgelaufener Flow-Zustand die Login-Maske verhindert und beim
 * nächsten Login ein anderer Nutzer gewählt werden kann.
 *
 * "Abbrechen" in der Leiste beendet den Login Flow ([onCancel]); geschlossen wird der
 * Dialog vom Aufrufer, sobald `loginUrl` wieder `null` ist (Erfolg oder Abbruch).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginWebViewDialog(
    url: String,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.common_login)) },
                    navigationIcon = {
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            },
        ) { padding ->
            AndroidView(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                factory = { context ->
                    clearLoginWebSession()
                    @SuppressLint("SetJavaScriptEnabled")
                    val webView = WebView(context).apply {
                        // Nextclouds Login-Seite ist eine JS-App – ohne JS/DOM-Storage bleibt sie leer.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Alle Navigationen (Login, 2FA, SSO-Redirects, "Zugriff gewähren")
                        // bleiben in dieser WebView statt in einen externen Browser zu springen.
                        webViewClient = WebViewClient()
                        loadUrl(url)
                    }
                    webView
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

/**
 * Löscht Cookies und Web-Storage der App-eigenen WebViews – Äquivalent zu
 * `WKWebsiteDataStore.default().removeData(...)` in iOS' `logout()`.
 * Betrifft nur die WebViews dieser App, nicht den System-Browser.
 */
fun clearLoginWebSession() {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
    }
    WebStorage.getInstance().deleteAllData()
}

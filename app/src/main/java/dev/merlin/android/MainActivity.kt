package dev.merlin.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dev.merlin.android.data.PreferencesStore
import dev.merlin.android.models.ReaderTheme
import dev.merlin.android.nav.AppNavigator
import dev.merlin.android.network.CredentialsStore
import dev.merlin.android.ui.reader.ArticleReaderScreen
import dev.merlin.android.ui.screens.ArticleListScreen
import dev.merlin.android.ui.screens.OnboardingScreen
import dev.merlin.android.ui.screens.RemindersScreen
import dev.merlin.android.ui.screens.SettingsScreen
import dev.merlin.android.ui.screens.SiteCredentialsScreen
import dev.merlin.android.ui.theme.MerlinTheme
import dev.merlin.android.viewmodel.ArticlesViewModel
import javax.inject.Inject

/**
 * Äquivalent zum App-Einstieg aus `MerlinApp.swift`/`ArticleListView.swift`.
 * Zeigt den [OnboardingScreen], solange [CredentialsStore.isConfigured] `false`
 * ist; danach ein kleines `NavHost` mit den Routen `list`/`reader/{articleId}`/
 * `reminders` als Äquivalent zu iOS' `NavigationStack` zwischen `ArticleListView`,
 * `ArticleReaderView` und der per Sheet geöffneten `RemindersView`.
 *
 * `launchMode="singleTop"` (siehe AndroidManifest.xml) sorgt dafür, dass ein
 * Reminder-Tap bei bereits laufender App [onNewIntent] statt einer zweiten
 * Instanz auslöst; [AppNavigator.articleIdToOpen] wird im `NavHost` per
 * `LaunchedEffect` konsumiert und navigiert direkt zum Reader.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var credentialsStore: CredentialsStore

    @Inject
    lateinit var appNavigator: AppNavigator

    @Inject
    lateinit var preferencesStore: PreferencesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Muss vor super.onCreate() laufen (siehe SplashScreen-API-Doku) – ersetzt das
        // Theme.Merlin.Starting-Fenster-Theme (AndroidManifest.xml) automatisch durch
        // Theme.Merlin, sobald der erste Compose-Frame gezeichnet ist.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            // Bisher fehlte diese Verbindung komplett: `MerlinTheme` fiel ohne Parameter immer
            // auf `isSystemInDarkTheme()` zurück, die "Erscheinungsbild"-Auswahl (AppearanceSheet)
            // wirkte sich nur auf die Lese-Ansicht aus, nicht auf Menüs/Artikelübersicht/Settings.
            val appTheme by preferencesStore.readerTheme.collectAsState(initial = ReaderTheme.AUTO)
            MerlinTheme(theme = appTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var isConfigured by remember { mutableStateOf(credentialsStore.isConfigured) }
                    if (isConfigured) {
                        val navController = rememberNavController()

                        // Deep-Link aus Reminder-Notification (siehe AppNavigator/handleIntent):
                        // sobald ein articleId ankommt, direkt zum Reader navigieren und den
                        // Wert konsumieren, damit ein Konfigurationswechsel (z.B. Rotation)
                        // nicht erneut navigiert.
                        val pendingArticleId by appNavigator.articleIdToOpen.collectAsState()
                        LaunchedEffect(pendingArticleId) {
                            val id = pendingArticleId ?: return@LaunchedEffect
                            navController.navigate("reader/$id")
                            appNavigator.consume()
                        }

                        NavHost(navController = navController, startDestination = "list") {
                            composable("list") {
                                val articlesViewModel: ArticlesViewModel = hiltViewModel()
                                val settingsLoaded by articlesViewModel.settingsLoaded.collectAsState()
                                if (settingsLoaded) {
                                    ArticleListScreen(
                                        viewModel = articlesViewModel,
                                        onArticleClick = { articleId -> navController.navigate("reader/$articleId") },
                                        onRemindersClick = { navController.navigate("reminders") },
                                        onSettingsClick = { navController.navigate("settings") },
                                    )
                                } else {
                                    // Hält den ersten Render zurück, bis der Server-Settings-Fetch aus
                                    // `ArticlesViewModel.init` (Erfolg oder Fehler) durch ist – sonst blitzt
                                    // kurz Theme/Akzentfarbe/Schrift aus den lokalen DataStore-Defaults auf,
                                    // bevor "Server gewinnt" greift. Äquivalent zum Splash-Gate in iOS'
                                    // `MerlinApp.swift` (`.task`-Block vor `splashVisible = false`).
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                            composable(
                                route = "reader/{articleId}",
                                arguments = listOf(navArgument("articleId") { type = NavType.IntType }),
                            ) { backStackEntry ->
                                // Äquivalent zu iOS' `nextArticle(after:)`-Closure (ArticleListView.swift):
                                // der Reader selbst hat keine Artikelliste, daher holen wir hier dieselbe
                                // `ArticlesViewModel`-Instanz, die auf "list" lebt (bleibt im Back-Stack,
                                // da "reader/{articleId}" nur darüber gepusht wird, nicht ersetzt), und
                                // berechnen den nächsten Artikel anhand des Index in `filteredArticles`.
                                val articleId = backStackEntry.arguments?.getInt("articleId") ?: -1
                                // Auf `backStackEntry` gekeyt gemerkt: `getBackStackEntry` darf nicht
                                // bei jeder Recomposition neu aufgerufen werden (Compose-Lint) – pro
                                // Reader-Eintrag genügt eine stabile Referenz auf den "list"-Eintrag.
                                val listEntry = remember(backStackEntry) { navController.getBackStackEntry("list") }
                                val articlesViewModel: ArticlesViewModel = hiltViewModel(listEntry)
                                val filteredArticles by articlesViewModel.filteredArticles.collectAsState()
                                val nextArticleId = remember(articleId, filteredArticles) {
                                    val index = filteredArticles.indexOfFirst { it.id == articleId }
                                    if (index in 0 until filteredArticles.size - 1) filteredArticles[index + 1].id else null
                                }
                                ArticleReaderScreen(
                                    onBack = { navController.popBackStack() },
                                    // Hält die Liste auf dem Laufenden, wenn im Reader archiviert/favorisiert/
                                    // getaggt/gelöscht wird (siehe Parameter-Kommentar in ArticleReaderScreen.kt) –
                                    // dieselbe `articlesViewModel`-Instanz, die oben schon für `nextArticleId` genutzt wird.
                                    onArticleChanged = articlesViewModel::applyExternalUpdate,
                                    onArticleDeleted = articlesViewModel::removeExternally,
                                    onNavigateNext = nextArticleId?.let { nextId ->
                                        {
                                            // Ersetzt den aktuellen Reader-Eintrag statt ihn zu stapeln –
                                            // Äquivalent zu iOS' In-Place-Wechsel von `selectedArticle`
                                            // (kein wachsender Back-Stack pro "Weiter"-Tap).
                                            navController.navigate("reader/$nextId") {
                                                popUpTo("reader/{articleId}") { inclusive = true }
                                            }
                                        }
                                    },
                                    onNavigateToSiteCredentials = { domain ->
                                        navController.navigate("site-credentials?domain=$domain")
                                    },
                                )
                            }
                            composable("reminders") {
                                RemindersScreen(
                                    onBack = { navController.popBackStack() },
                                    onArticleClick = { articleId ->
                                        navController.navigate("reader/$articleId")
                                    },
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onLoggedOut = {
                                        // Zugangsdaten wurden entfernt (Logout/Cache leeren) → zurück zum
                                        // Onboarding, analog zu iOS' `logout()`/`clearCache()` in `SettingsView`.
                                        isConfigured = false
                                        navController.popBackStack("list", inclusive = true)
                                    },
                                    onSiteCredentialsClick = { navController.navigate("site-credentials") },
                                )
                            }
                            composable(
                                route = "site-credentials?domain={domain}",
                                arguments = listOf(
                                    navArgument("domain") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                ),
                            ) { backStackEntry ->
                                SiteCredentialsScreen(
                                    onBack = { navController.popBackStack() },
                                    preselectedDomain = backStackEntry.arguments?.getString("domain"),
                                )
                            }
                        }
                    } else {
                        OnboardingScreen(onLoginSuccess = { isConfigured = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Liest den Reminder-Deep-Link-Extra (siehe [ReminderBroadcastReceiver]) und reicht ihn an [AppNavigator] weiter. */
    private fun handleIntent(intent: Intent) {
        val articleId = intent.getIntExtra(EXTRA_OPEN_ARTICLE_ID, -1)
        if (articleId != -1) appNavigator.open(articleId)
    }

    companion object {
        const val EXTRA_OPEN_ARTICLE_ID = "open_article_id"
    }
}

package dev.merlin.android.ui.reader

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import dev.merlin.android.R
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.merlin.android.models.Article
import dev.merlin.android.models.FileMetadataParser
import dev.merlin.android.models.Highlight
import dev.merlin.android.models.HighlightCreate
import dev.merlin.android.models.ProgressEdge
import dev.merlin.android.models.ReaderFont
import dev.merlin.android.models.ReaderTheme
import dev.merlin.android.share.rememberArticleSharer
import dev.merlin.android.ui.screens.EditTagsDialog
import dev.merlin.android.ui.screens.RenameFileDialog
import dev.merlin.android.viewmodel.ArticleReaderViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Äquivalent zu `ArticleReaderView.swift`. Die Meta-Zeile (Site/Autor/
 * Lesezeit) + Tags ist kein eigener, fixierter Compose-Header mehr, sondern Teil
 * des HTML-Inhalts in [ReaderWebView] (siehe `ReaderHtmlBuilder.buildHeaderHtml`)
 * und scrollt dadurch wie im iOS-Original ganz normal mit. Der
 * Fortschrittsbalken ([ProgressBarOverlay]) liegt als Overlay über dem
 * *gesamten* Bildschirm (siehe Kommentar an dessen Aufrufstelle), und ein
 * seitlicher [ModalNavigationDrawer] dient als Äquivalent zum iOS-Action-Sheet/-Menü
 * (volle Aktionsliste statt Swipe/Long-Press, siehe `ArticleCard`-Kommentar
 * für dieselbe Designentscheidung).
 *
 * Der Menüpunkt zum Erinnern öffnet [ReminderSheet], der zum Melden des Artikels öffnet
 * [ReportArticleSheet], ein Tap auf ein Bild im Reader öffnet [ImageLightboxScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    // Kein sichtbarer Zurück-Button mehr (weder oben links noch in der Bottom-Bar): Zurück läuft
    // über die System-Zurück-Geste/-Taste (NavHost → `popBackStack`) sowie über "Archivieren und
    // zurück" und das Löschen eines Artikels.
    onBack: () -> Unit,
    // Unterschied zu iOS: dort teilen sich Liste und Reader dieselbe `ArticlesViewModel`-
    // Instanz, hier nicht (siehe Architektur-Hinweis in Structure.md). Damit archivierte/
    // favorisierte/getaggte/gelöschte Artikel beim Zurücknavigieren sofort aus der Liste
    // verschwinden (statt erst beim nächsten `load()`), meldet der Reader jede Änderung über
    // diese beiden Callbacks an den Aufrufer zurück (`MainActivity` verdrahtet sie auf die
    // geteilte `ArticlesViewModel`-Instanz der "list"-Route).
    onArticleChanged: (Article) -> Unit = {},
    onArticleDeleted: (Int) -> Unit = {},
    // Äquivalent zur iOS-Navigation aus dem Paywall-Banner zu `SiteCredentialsView`: der Reader
    // kennt keine eigene Navigationsroute für `SiteCredentialsScreen`, daher reicht `MainActivity`
    // hier nur die Sprung-Aktion durch.
    onNavigateToSiteCredentials: (domain: String) -> Unit = {},
    viewModel: ArticleReaderViewModel = hiltViewModel(),
) {
    val article by viewModel.article.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val commentState by viewModel.comments.state.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val progressEdge by viewModel.progressEdge.collectAsState()
    val accentColorHex by viewModel.accentColorHex.collectAsState()
    val quoteMode by viewModel.quoteMode.collectAsState()
    val quotePrefs by viewModel.quotePrefs.collectAsState()

    // Native Chrome (ReaderHeader/ReaderBottomBar) folgt bewusst dem gewählten
    // Reader-Theme statt `MaterialTheme.colorScheme` (System-Theme) – sonst klafft die Chrome
    // weiß/hell auseinander, wenn der Nutzer z.B. "Dunkel" als Reader-Theme wählt, während das
    // System-Theme hell ist (oder umgekehrt bei "Hell" auf einem Dark-Mode-Gerät). Siehe
    // `rememberReaderChromeColors`-Kommentar/iOS-Äquivalent (`readerBgColor` & Co.).
    val chromeColors = rememberReaderChromeColors(appearance.theme)

    // Status- und Navigationsleiste folgen normalerweise `MerlinTheme`/`Theme.kt`, also dem
    // System-Theme – exakt derselbe Bug wie bei Header/Bottom-Bar oben, nur auf
    // Systemleisten-Ebene: weicht das gewählte Reader-Theme vom System-Theme ab, bleiben Status-
    // und Navigationsleiste in der falschen (System-)Farbe, während die restliche Reader-Chrome
    // schon auf `chromeColors` umgestellt ist – sichtbar als weißer Streifen oben/unten im
    // Screenshot. Für die Dauer des Readers daher explizit auf `chromeColors.background`
    // umfärben und beim Verlassen wieder auf die System-Theme-Farbe zurücksetzen.
    val isChromeDark = chromeColors.background.luminance() < 0.5f
    val view = LocalView.current
    // Rücksetzwert beim Verlassen: bewusst NICHT `isSystemInDarkTheme()`, sondern das App-Theme
    // selbst (`MaterialTheme.colorScheme`, von der umschließenden `MerlinTheme` in Theme.kt anhand
    // der Nutzer-Auswahl gesetzt). Vorher wurde hier auf den rohen System-Modus zurückgesetzt –
    // weicht die Nutzer-Auswahl vom System ab (z.B. App-Theme "Dunkel" bei hellem System-Theme),
    // landete man nach dem Verlassen des Readers in genau der falschen Hell/Dunkel-Kombination
    // (Hintergrund vs. Icon-Kontrast), die als helle/weiße Leiste sichtbar wird.
    val appBackgroundArgb = MaterialTheme.colorScheme.background.toArgb()
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Die Statusleiste färbt ein eigener SideEffect weiter unten (Akzentfarbe hinter dem Plakat-Header).
    SideEffect {
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            window.setNavigationBarColorCompat(chromeColors.background.toArgb())
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightNavigationBars = !isChromeDark
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!view.isInEditMode) {
                val window = (view.context as Activity).window
                window.setStatusBarColorCompat(appBackgroundArgb)
                window.setNavigationBarColorCompat(appBackgroundArgb)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !appIsDark
                insetsController.isAppearanceLightNavigationBars = !appIsDark
            }
        }
    }

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    // Wiederherzustellende Leseposition (Fraktion 0..1) – wird vom ViewModel erst
    // nach dem Artikel-Load gesetzt (geräteübergreifende Last-Write-Wins-Auflösung,
    // siehe ArticleReaderViewModel). `null` = noch nicht ermittelt → Ladeindikator.
    val initialScrollProgress by viewModel.initialScrollProgress.collectAsState()
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showFileMetadata by remember { mutableStateOf(false) }
    val shareArticle = rememberArticleSharer()
    val pdfFileMetadata = remember(article?.id, article?.content) {
        article?.takeIf { it.fileId != null && it.isPdf }?.content?.let(FileMetadataParser::parse)
    }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var showTagsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showShareLinkSheet by remember { mutableStateOf(false) }
    val allTags by viewModel.allTags.collectAsState()
    var lightboxState by remember { mutableStateOf<LightboxState?>(null) }
    // Farbleiste: sichtbar bei einer Auswahl ([selectionRect]) oder einer angetippten Markierung
    // ([tappedHighlightId], dann mit Kommentieren/Entfernen statt neu Markieren).
    var selectionRect by remember { mutableStateOf<ReaderJsBridge.SelectionRect?>(null) }
    var tappedHighlightId by remember { mutableStateOf<Int?>(null) }
    // Markierung mit Kommentaren: erst nach Rückfrage entfernen (wie iOS).
    var highlightToDelete by remember { mutableStateOf<Highlight?>(null) }
    // Zitat-Tafel (siehe QuoteCard.kt): offen, solange eine Sitzung besteht (im ViewModel, damit
    // sie eine Drehung übersteht).
    val quoteSession = viewModel.quoteSession
    var commentFocus by remember { mutableStateOf<CommentFocus?>(null) }
    // Markieren, Kommentieren, Suchen: an die WebView des Text- bzw. PDF-Readers.
    var documentActions by remember { mutableStateOf<ReaderDocumentActions?>(null) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findResult by remember { mutableStateOf<FindResult?>(null) }
    var infoPopover by remember { mutableStateOf<ReaderJsBridge.InfoPopover?>(null) }
    var scrollProgress by remember { mutableFloatStateOf(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // Nicht-persistenter Dismiss-State für PaywallWarningBanner (siehe dortiger Kommentar) –
    // pro Artikel zurückgesetzt, damit ein Wechsel zu einem anderen Artikel den Banner wieder zeigt.
    var paywallBannerDismissed by remember(viewModel.articleId) { mutableStateOf(false) }

    // Bottom-Bar-Sichtbarkeit – Äquivalent zum `onScrollGeometryChange`-Paar unter iOS in
    // ArticleReaderView.swift: beim Scrollen nach unten wird die Leiste ausgeblendet, außer man
    // ist nah am Artikelende (dort soll sie sichtbar bleiben, damit man archivieren/weiterspringen
    // kann). Scrollen nach oben (oder Stillstand) zeigt sie immer.
    var lastScrollOffsetPx by remember(viewModel.articleId) { mutableFloatStateOf(0f) }
    // Papierstruktur hinter der durchsichtigen WebView (nur Hell/Sepia, Schalter im
    // Erscheinungsbild-Sheet); `paperScrollPx` verschiebt die Fasern mit dem Text.
    val showsPaper = ReaderHtmlBuilder.showsPaperTexture(appearance, isSystemInDarkTheme())
    val paperTile = rememberPaperTile()
    var paperScrollPx by remember(viewModel.articleId) { mutableFloatStateOf(0f) }
    var scrollingDown by remember(viewModel.articleId) { mutableStateOf(false) }

    // Plakat-Header (siehe ReaderHtmlBuilder.buildHeaderHtml): Die Akzentfläche reicht unter iOS bis
    // unter die Statusleiste. Merlin läuft hier nicht edge-to-edge, die Statusleiste nimmt deshalb die
    // Akzentfarbe an, solange die Fläche direkt darunter liegt, sonst die Reader-Hintergrundfarbe.
    // `posterBottomPx` meldet die WebView (CSS-px ≈ dp, hier schon in echte Px umgerechnet);
    // derivedStateOf komponiert nur neu, wenn der Zustand umkippt, nicht bei jedem Scroll-Frame.
    var posterBottomPx by remember(viewModel.articleId) { mutableFloatStateOf(0f) }
    val statusBarOnAccent by remember(viewModel.articleId) {
        derivedStateOf { posterBottomPx > 0f && paperScrollPx < posterBottomPx }
    }
    val accentColor = remember(accentColorHex) {
        runCatching { Color(accentColorHex.toColorInt()) }.getOrDefault(Color.Red)
    }
    val onAccentColor = remember(accentColorHex) { Color(PosterColors.onAccentHex(accentColorHex).toColorInt()) }
    val statusBarColor = if (statusBarOnAccent) accentColor else chromeColors.background
    val statusBarIsDark = statusBarColor.luminance() < 0.5f
    SideEffect {
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            window.setStatusBarColorCompat(statusBarColor.toArgb())
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !statusBarIsDark
        }
    }
    var showBottomBar by remember(viewModel.articleId) { mutableStateOf(true) }
    // Höhe der gerade sichtbaren Bottom- bzw. Lesemodus-Leiste (0, solange sie ausgeblendet ist):
    // die Farbleiste sitzt darüber statt darunter.
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    // Zuletzt gemessene Höhe der eingeblendeten Leiste (ausgeblendet misst `AnimatedVisibility` 0).
    // Der Artikeltext reserviert sie unten als Abstand, siehe `--merlin-bottom-bar` in ReaderHtmlBuilder.
    var readerBarReservedPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val deltaThresholdPx = with(density) { 4.dp.toPx() }
    val downThresholdPx = with(density) { 40.dp.toPx() }
    val nearBottomThresholdPx = with(density) { 160.dp.toPx() }

    // Freihand-Lesemodus (Äquivalent zu `autoScroll`/`handsFreeButton` in ArticleReaderView.swift).
    val handsFreeButton by viewModel.handsFreeButton.collectAsState()
    val autoScroll = remember(viewModel.articleId) { AutoScrollController() }
    LaunchedEffect(autoScroll) { autoScroll.slider = viewModel.handsFreeSpeed() }
    LaunchedEffect(webViewRef, autoScroll) {
        webViewRef?.let { autoScroll.target = WebViewAutoScrollTarget(it) }
    }
    val handsFreeRunning = autoScroll.isRunning
    LaunchedEffect(handsFreeRunning, autoScroll) {
        if (handsFreeRunning) autoScroll.run(density.density)
    }
    // Bildschirm bleibt an, solange der Motor läuft (iOS: `isIdleTimerDisabled`).
    DisposableEffect(handsFreeRunning) {
        view.keepScreenOn = handsFreeRunning
        onDispose { view.keepScreenOn = false }
    }
    // Im Erscheinungsbild ausgeschaltet → Modus beenden.
    LaunchedEffect(handsFreeButton) {
        if (!handsFreeButton) autoScroll.stop()
    }

    // Kommentar-Kanal (nur merlin-nextcloud) nur offen halten, solange der Reader sichtbar ist.
    val commentsLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(commentsLifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startComments()
                Lifecycle.Event.ON_STOP -> viewModel.stopComments()
                else -> Unit
            }
        }
        commentsLifecycle.addObserver(observer)
        onDispose {
            commentsLifecycle.removeObserver(observer)
            viewModel.stopComments()
        }
    }
    // Höhe der Bottom-Bar an den Artikeltext weitergeben (CSS-px entsprechen dp). Nach einem Neuladen
    // greift bis zur nächsten Messung der CSS-Standardwert, der der festen Leistenhöhe entspricht.
    LaunchedEffect(webViewRef, readerBarReservedPx) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (readerBarReservedPx <= 0) return@LaunchedEffect
        val cssPx = readerBarReservedPx / density.density
        webView.evaluateJavascript(
            "document.documentElement.style.setProperty('--merlin-bottom-bar', '${cssPx}px')",
            null,
        )
    }
    LaunchedEffect(webViewRef) {
        webViewRef?.let { webView -> documentActions = WebViewDocumentActions(webView) { findResult = it } }
    }
    // Suche: erst nach einer kurzen Tipp-Pause suchen (wie `scheduleFind` auf iOS).
    LaunchedEffect(findQuery, showFind, documentActions) {
        if (!showFind) return@LaunchedEffect
        delay(300)
        if (findQuery.isBlank()) {
            documentActions?.clearFind()
            findResult = null
        } else {
            documentActions?.find(findQuery)
        }
    }

    // Meldet jede Artikeländerung (Favorit/Archiv/Tags) an die geteilte `ArticlesViewModel`-
    // Instanz der Liste zurück, damit z.B. ein archivierter Artikel beim Zurücknavigieren
    // sofort aus "Ungelesen" verschwindet, statt erst beim nächsten `load()` (siehe Parameter-
    // Kommentar oben). `onArticleChanged` ist ein No-Op, falls der Artikel in der Liste aktuell
    // nicht geladen ist – sicher also auch für Deep-Links/Reminder-Öffnungen.
    LaunchedEffect(article) {
        article?.let(onArticleChanged)
    }

    LaunchedEffect(deleted) {
        if (deleted) {
            onArticleDeleted(viewModel.articleId)
            onBack()
        }
    }

    // Äußerste Hülle: Äquivalent zum iOS-`GeometryReader` mit `.ignoresSafeArea()` für den
    // Fortschrittsbalken in ArticleReaderView.swift – der Balken liegt dort über dem *gesamten*
    // Bildschirm (inkl. der Fläche unter der Statusleiste), nicht nur über dem Artikelinhalt.
    // Damit der Balken die komplette Bildschirmhöhe einnimmt statt nur den (um die System-Insets
    // verkleinerten) Scaffold-Content-Bereich, sitzt er als Geschwister-Overlay außerhalb des
    // Scaffold in dieser Box, nicht innerhalb des Scaffold-Contents.
    Box(modifier = Modifier.fillMaxSize()) {
    // ModalNavigationDrawer öffnet in Material3 ausschließlich von der Start-Kante (links in LTR) –
    // es gibt keinen nativen "DrawerPosition"-Parameter für eine rechtsseitige Variante. Trick:
    // den Drawer in einem RTL-LocalLayoutDirection einbetten (Start-Kante wird dadurch rechts),
    // und sowohl drawerContent als auch den Hauptinhalt innen wieder auf LTR zurücksetzen, damit
    // Texte/Icons nicht gespiegelt werden – nur der Drawer selbst öffnet von rechts.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Standardmäßig öffnet ModalNavigationDrawer per Edge-Swipe – das kollidiert mit dem
        // WebView-Scrollen (jede Geste in Bildschirmnähe wird als Drawer-Aufziehen interpretiert).
        // Das Action-Sheet unter iOS (ArticleReaderView.swift) öffnet ausschließlich per Button-Tap, nie
        // per Wisch-Geste – daher hier ebenfalls deaktiviert; Öffnen weiterhin über den Menü-Button in der Bottom-Bar.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    NavigationDrawerItem(
                        label = { Text(stringResource(if (article?.isFavorite == true) R.string.articleReader_sideMenu_removeFavorite else R.string.articleReader_sideMenu_addFavorite)) },
                        icon = { Icon(if (article?.isFavorite == true) Icons.Filled.Star else Icons.Outlined.Star, contentDescription = null) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; viewModel.toggleFavorite() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (article?.isPdf != true) {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.articleReader_sideMenu_appearance)) },
                            icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; showAppearanceSheet = true },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    // Datei-Einträge teilen die Datei selbst, alles andere den Link (FileShareService).
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.common_share)) },
                        icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            article?.let(shareArticle)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    // Metadaten von PDF-Datei-Einträgen: die WebView zeigt den Abschnitt bei allen
                    // anderen Einträgen unter dem Inhalt, der native PDF-Reader nicht (wie iOS).
                    pdfFileMetadata?.let { metadata ->
                        NavigationDrawerItem(
                            label = { Text(metadata.title.ifEmpty { stringResource(R.string.android_articleReader_metadata) }) },
                            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                showFileMetadata = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.articleReader_sideMenu_openInBrowser)) },
                        icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            val url = article?.url ?: return@NavigationDrawerItem
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (article?.isPdf != true) {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.articleReader_sideMenu_openViaArchive)) },
                            icon = { Icon(Icons.Filled.History, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                val url = article?.url ?: return@NavigationDrawerItem
                                val archiveUrl = "https://archive.ph/newest/$url"
                                context.startActivity(Intent(Intent.ACTION_VIEW, archiveUrl.toUri()))
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.articleReader_sideMenu_copyLink)) },
                        icon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            article?.url?.let { url ->
                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Link", url))) }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.android_shareLink_menu)) },
                        icon = { Icon(Icons.Filled.Public, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showShareLinkSheet = true
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    // Kommentare (nur merlin-nextcloud): alle Threads des Artikels, Zähler wie iOS.
                    if (commentState.isAvailable) {
                        val count = commentState.totalCount
                        NavigationDrawerItem(
                            label = { Text(if (count > 0) stringResource(R.string.articleReader_comments_countLabel, count) else stringResource(R.string.articleReader_comments_title)) },
                            icon = { Icon(Icons.AutoMirrored.Outlined.Comment, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                commentFocus = CommentFocus()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    // Suche im Artikel: Text-Artikel mit Inhalt oder PDF (wie `canFind` auf iOS).
                    val canFind = article?.let { if (it.isPdf) it.pdfSourceUrl != null else !it.content.isNullOrEmpty() } == true
                    if (canFind) {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.articleReader_sideMenu_find)) },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                selectionRect = null
                                tappedHighlightId = null
                                showFind = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text(stringResource(if (article?.isArchived == true) R.string.articleReader_sideMenu_moveToReadingList else R.string.articleReader_sideMenu_archive)) },
                        icon = { Icon(if (article?.isArchived == true) Icons.Filled.Inventory2 else Icons.Filled.Archive, contentDescription = null) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; viewModel.toggleArchive() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.articleReader_sideMenu_editTags)) },
                        icon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.loadTags()
                            showTagsSheet = true
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (article?.fileId != null) {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.fileRename_menu)) },
                            icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; showRenameDialog = true },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.articleReader_sideMenu_setReminder)) },
                        icon = { Icon(Icons.Filled.NotificationAdd, contentDescription = null) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; showReminderSheet = true },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.articleReader_sideMenu_reportArticle)) },
                        icon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; showReportSheet = true },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.common_delete)) },
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; viewModel.delete() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            }
        },
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
            containerColor = chromeColors.background,
            // Keine zusätzlichen Insets: Merlin läuft (noch) nicht edge-to-edge, das Fenster liegt
            // also bereits unterhalb der Statusleiste und oberhalb der Navigationsleiste. Compose
            // meldet die System-Insets trotzdem, sodass `Scaffold` sie standardmäßig ein zweites
            // Mal als Padding addiert hätte – sichtbar als leerer Streifen in Statusleistenhöhe
            // über dem Artikel, seit die TopAppBar (die diese Insets vorher selbst geschluckt hat)
            // entfallen ist. Beim Umstieg auf edge-to-edge (targetSdk 35) muss das wieder rein.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // Bewusst keine TopAppBar mehr: kein Zurück-Button (System-Zurück genügt), kein
            // Favoriten-Stern (Favorisieren über das Seitenmenü) und das Menü sitzt rechts in der
            // [ReaderBottomBar]. Der Titel existiert nur im scrollenden Header
            // (siehe ReaderHtmlBuilder.buildHeaderHtml).
            snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } },
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize().background(chromeColors.background)) {
                val currentArticle = article
                when {
                    isLoading && currentArticle == null -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    currentArticle == null -> {
                        Text(
                            error ?: stringResource(R.string.android_articleReader_loadFailed),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                    initialScrollProgress == null -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        currentArticle.requiresLoginDomain?.takeIf { !paywallBannerDismissed }?.let { domain ->
                            PaywallWarningBanner(
                                domain = domain,
                                onConnect = { onNavigateToSiteCredentials(domain) },
                                onRetry = { viewModel.retryPaywall() },
                                onDismiss = { paywallBannerDismissed = true },
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                        // Kein eigener, fixierter Header mehr über der WebView (Äquivalent zum iOS-
                        // `articleHeader`, ArticleReaderView.swift): die Meta-Zeile (Site/Autor/
                        // Lesezeit) + Tags werden jetzt von `ReaderHtmlBuilder` direkt in den
                        // WebView-HTML-Inhalt gerendert (siehe `buildHeaderHtml` dort) und scrollen
                        // dadurch ganz normal mit dem Artikeltext mit, statt fixiert zu bleiben.
                        Box(modifier = Modifier.fillMaxSize()) {
                                // Bottom-Bar-Autohide/Scroll-Metriken: gemeinsam für WebView- und PDF-Reader.
                                val onReaderScrollMetrics: (Float, Float) -> Unit = { newOffset, scrollableRange ->
                                    val delta = newOffset - lastScrollOffsetPx
                                    val isNearBottom = if (scrollableRange > 0f) {
                                        (scrollableRange - newOffset) < nearBottomThresholdPx
                                    } else {
                                        true
                                    }
                                    // Debounce wie im iOS-Original: nur "echte" Scroll-Schübe (>4dp)
                                    // werten Richtung/Sichtbarkeit neu, kleines Zittern wird ignoriert.
                                    // Im Freihand-Lesemodus bleibt die Lesemodus-Leiste stehen.
                                    if (abs(delta) > deltaThresholdPx && !autoScroll.isActive) {
                                        scrollingDown = delta > 0f && newOffset > downThresholdPx
                                        showBottomBar = !scrollingDown || isNearBottom
                                    }
                                    lastScrollOffsetPx = newOffset
                                }
                                // Video-Karte + Reader werden vertikal gestapelt (eigene innere `Column`), die
                                // restlichen Geschwister unten (Highlight-Toolbar, Info-Popover, Bottom-Bar)
                                // bleiben direkte Kinder der äußeren `Box`, da sie `Modifier.align(...)`
                                // benötigen (nur in `BoxScope` verfügbar).
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // Freihand-Lesemodus: Finger auf dem Inhalt → Motor hält kurz an
                                        // (siehe AutoScrollController). Nur beobachten, nichts verbrauchen;
                                        // `Initial` sieht die Events vor der WebView/LazyColumn.
                                        .pointerInput(autoScroll) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val pressed = event.changes.any { it.pressed }
                                                    // Neuer Tipp schließt die Leiste einer angetippten Markierung;
                                                    // trifft er wieder eine, öffnet ihr Klick sie gleich neu.
                                                    if (pressed && !autoScroll.userTouching && tappedHighlightId != null) {
                                                        tappedHighlightId = null
                                                        selectionRect = null
                                                    }
                                                    autoScroll.userTouching = pressed
                                                }
                                            }
                                        },
                                ) {
                                    if (NativeVideoHost.matches(currentArticle.url)) {
                                        NativeVideoPlayerCard(
                                            articleId = currentArticle.id,
                                            posterUrl = currentArticle.imageUrl,
                                        )
                                    }
                                    val pdfSource = currentArticle.pdfSourceUrl
                                    if (currentArticle.isPdf && pdfSource != null) {
                                        // PDF-Artikel: der Server speichert nur die URL, die PDF wird hier von der Quelle
                                        // geladen und seitenweise gerendert (kein WebView, keine Highlights/Textauswahl).
                                        // Bei Datei-Einträgen ist die Quelle der signierte Link aus dem Inhalt.
                                        PdfReaderView(
                                            sourceUrl = pdfSource,
                                            loadPdf = { url -> viewModel.loadPdf(url) },
                                            discardPdf = { url -> viewModel.discardPdf(url) },
                                            initialScrollProgress = initialScrollProgress ?: 0f,
                                            foregroundColor = chromeColors.foreground,
                                            backgroundColor = chromeColors.background,
                                            onOpenInBrowser = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, currentArticle.url.toUri()),
                                                    )
                                                }
                                            },
                                            onScrollPositionChanged = { progress -> viewModel.saveScrollProgress(progress) },
                                            onScrollProgress = { scrollProgress = it },
                                            onScrollMetrics = onReaderScrollMetrics,
                                            onAutoScrollTarget = { autoScroll.target = it },
                                            highlights = highlights,
                                            commentCounts = commentState.countsByHighlight,
                                            onCreateHighlight = { payload -> viewModel.createHighlight(payload) },
                                            onHighlightTap = { id, rect ->
                                                tappedHighlightId = id
                                                selectionRect = rect ?: ReaderJsBridge.SelectionRect(0f, 0f, 0f, 0f)
                                            },
                                            onOpenComments = { id, text ->
                                                if (commentState.isAvailable) commentFocus = CommentFocus(highlightId = id, quote = text)
                                            },
                                            onCommentAnchor = { anchor ->
                                                commentFocus = CommentFocus(quote = anchor.highlightedText, anchor = anchor)
                                            },
                                            onSelectionChanged = { rect ->
                                                if (rect != null) {
                                                    tappedHighlightId = null
                                                    selectionRect = rect
                                                } else if (tappedHighlightId == null) {
                                                    selectionRect = null
                                                }
                                            },
                                            onOpenLink = { url ->
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                                            },
                                            onFindResult = { findResult = it },
                                            onDocumentActions = { documentActions = it },
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                        )
                                    } else {
                                        ReaderWebView(
                                            article = currentArticle,
                                            highlights = highlights,
                                            appearance = appearance,
                                            initialScrollProgress = initialScrollProgress ?: 0f,
                                            onCreateHighlight = { payload: HighlightCreate -> viewModel.createHighlight(payload) },
                                            onHighlightTap = { id, rect ->
                                                tappedHighlightId = id
                                                selectionRect = rect ?: ReaderJsBridge.SelectionRect(0f, 0f, 0f, 0f)
                                            },
                                            onImageTap = { index, imageUrls -> lightboxState = LightboxState(initialIndex = index, imageURLs = imageUrls) },
                                            onSelectionChanged = { rect ->
                                                if (rect != null) {
                                                    tappedHighlightId = null
                                                    selectionRect = rect
                                                } else if (tappedHighlightId == null) {
                                                    selectionRect = null
                                                }
                                            },
                                            commentCounts = commentState.countsByHighlight,
                                            onOpenComments = { id, text ->
                                                if (commentState.isAvailable) commentFocus = CommentFocus(highlightId = id, quote = text)
                                            },
                                            onCommentAnchor = { anchor ->
                                                commentFocus = CommentFocus(quote = anchor.highlightedText, anchor = anchor)
                                            },
                                            onInfoPopover = { popover -> infoPopover = popover },
                                            onScrollPositionChanged = { progress -> viewModel.saveScrollProgress(progress) },
                                            onWebViewReady = { webViewRef = it },
                                            onScrollProgress = { scrollProgress = it },
                                            onScrollMetrics = { offset, range ->
                                                paperScrollPx = offset
                                                onReaderScrollMetrics(offset, range)
                                            },
                                            transparentBackground = showsPaper,
                                            onPosterBottom = { cssPx ->
                                                // Bei ARD/ZDF/3sat/Arte steht der native Player über der
                                                // WebView, die Akzentfläche liegt dann nicht unter der Statusleiste.
                                                posterBottomPx = if (NativeVideoHost.matches(currentArticle.url)) {
                                                    0f
                                                } else {
                                                    with(density) { cssPx.dp.toPx() }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .paperBackground(
                                                    color = chromeColors.background,
                                                    tile = paperTile.takeIf { showsPaper },
                                                    scrollOffsetPx = { paperScrollPx },
                                                ),
                                        )
                                    }
                                }

                                val toolbarBottomPadding by animateDpAsState(
                                    targetValue = 16.dp + with(density) { bottomBarHeightPx.toDp() },
                                    animationSpec = tween(200),
                                    label = "toolbarBottomPadding",
                                )
                                selectionRect?.let { rect ->
                                    val tapped = tappedHighlightId?.let { id -> highlights.firstOrNull { it.id == id } }
                                    fun closeToolbar() {
                                        selectionRect = null
                                        tappedHighlightId = null
                                    }
                                    // Zitat-Tafel für die Auswahl bzw. die angetippte Markierung (wie auf
                                    // iOS nur bei Text-Artikeln). Die Lage im Artikel (CSS-px ≈ dp, relativ
                                    // zur WebView) dient als Startpunkt der Animation.
                                    fun openQuote(text: String) {
                                        val webView = webViewRef ?: return
                                        val loc = IntArray(2).also { webView.getLocationInWindow(it) }
                                        val d = density.density
                                        val source = androidx.compose.ui.geometry.Rect(
                                            loc[0] + rect.x * d, loc[1] + rect.y * d,
                                            loc[0] + (rect.x + rect.width) * d, loc[1] + (rect.y + rect.height) * d,
                                        ).takeIf { rect.width > 0f && rect.height > 0f }
                                        closeToolbar()
                                        viewModel.quoteSession = QuoteSession(
                                            text = text,
                                            sourceInWindow = source,
                                            style = QuoteStyle(
                                                tint = chromeColors.background.toArgb(),
                                                isDark = chromeColors.background.luminance() < 0.5f,
                                                textColor = chromeColors.foreground.toArgb(),
                                                author = currentArticle.author?.trim()?.takeIf { it.isNotEmpty() },
                                                source = QuoteStyle.source(currentArticle.url),
                                                fontSize = appearance.fontSize.toFloat(),
                                                readerTypeface = readerTypeface(appearance),
                                            ),
                                        )
                                        scope.launch { viewModel.quoteHint = viewModel.consumeQuoteHint() }
                                    }
                                    val onQuote: (() -> Unit)? = if (quoteMode && !currentArticle.isPdf && webViewRef != null) {
                                        {
                                            if (tapped != null) {
                                                openQuote(tapped.highlightedText)
                                            } else {
                                                documentActions?.quoteSelection { text -> text?.let(::openQuote) }
                                            }
                                        }
                                    } else {
                                        null
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = toolbarBottomPadding),
                                    ) {
                                    if (onQuote != null) {
                                        QuoteOpenButton(
                                            background = MaterialTheme.colorScheme.surfaceVariant,
                                            foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                                            border = MaterialTheme.colorScheme.outlineVariant,
                                            onClick = onQuote,
                                        )
                                    }
                                    HighlightColorToolbar(
                                        // Gast-Markierungen und Kommentarstellen haben keine eigene Farbe zum Ändern.
                                        showColors = tapped == null || (!tapped.isGuest && !tapped.isComment),
                                        selectedColor = tapped?.color,
                                        onColorSelected = { color ->
                                            if (tapped != null) viewModel.recolorHighlight(tapped, color) else documentActions?.mark(color)
                                            closeToolbar()
                                        },
                                        onComment = if (commentState.isAvailable) {
                                            {
                                                if (tapped != null) {
                                                    commentFocus = CommentFocus(highlightId = tapped.id, quote = tapped.highlightedText)
                                                } else {
                                                    documentActions?.comment()
                                                }
                                                closeToolbar()
                                            }
                                        } else {
                                            null
                                        },
                                        onDelete = tapped?.let { highlight ->
                                            {
                                                if ((commentState.countsByHighlight[highlight.id] ?: 0) > 0) {
                                                    highlightToDelete = highlight
                                                } else {
                                                    viewModel.deleteHighlight(highlight)
                                                }
                                                closeToolbar()
                                            }
                                        },
                                    )
                                    }
                                }

                                if (showFind) {
                                    FindBar(
                                        query = findQuery,
                                        onQueryChange = { findQuery = it },
                                        result = findResult,
                                        onStep = { forward -> documentActions?.findStep(forward) },
                                        onClose = {
                                            showFind = false
                                            findQuery = ""
                                            findResult = null
                                            documentActions?.clearFind()
                                        },
                                        modifier = Modifier.align(Alignment.TopCenter),
                                    )
                                }

                                // Äquivalent zum iOS-`.popover`-Flyout für die Info-Card (Von/Erschienen),
                                // siehe `ReaderHtmlBuilder` (CSS-Truncation + Klick-Handler in READER_JS).
                                // Anders als bei `HighlightColorToolbar` oben WIRD hier die Position der
                                // angetippten Zelle (CSS-px-Rect aus `getBoundingClientRect()`) verwendet,
                                // damit das Popover sichtbar an der "Erschienen"/"Von"-Zelle andockt, statt
                                // wirkungslos zentriert zu erscheinen – siehe `InfoPopoverOverlay`.
                                infoPopover?.let { popover ->
                                    InfoPopoverOverlay(
                                        popover = popover,
                                        onDismiss = { infoPopover = null },
                                    )
                                }

                                // Äquivalent zur iOS-`bottomBar` (ArticleReaderView.swift): als Overlay
                                // positioniert (nicht den Scroll-Inhalt verdrängend), per
                                // `.move(edge: .bottom).combined(with: .opacity)` ein-/ausgeblendet.
                                // Hier ist nur `BoxScope` als impliziter Receiver aktiv (kein
                                // `ColumnScope`/`RowScope`), daher greift ohne Paketqualifizierung
                                // direkt die Top-Level-Überladung von `AnimatedVisibility`.
                                AnimatedVisibility(
                                    // Im Freihand-Lesemodus steht statt der Bottom-Bar die Lesemodus-Leiste.
                                    visible = showBottomBar || autoScroll.isActive,
                                    enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(tween(200)),
                                    exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(tween(200)),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .onSizeChanged {
                                            bottomBarHeightPx = it.height
                                            if (it.height > 0) readerBarReservedPx = it.height
                                        },
                                ) {
                                    if (autoScroll.isActive) {
                                        HandsFreeBar(
                                            controller = autoScroll,
                                            onSpeedChangeFinished = { viewModel.saveHandsFreeSpeed(autoScroll.slider) },
                                            onStop = {
                                                autoScroll.stop()
                                                showBottomBar = true
                                            },
                                            accentColor = accentColor,
                                            onAccentColor = onAccentColor,
                                        )
                                    } else {
                                        ReaderBottomBar(
                                            onArchiveAndBack = {
                                                if (!currentArticle.isArchived) viewModel.toggleArchive()
                                                onBack()
                                            },
                                            onStartHandsFree = if (handsFreeButton) {
                                                {
                                                    selectionRect = null
                                                    infoPopover = null
                                                    showBottomBar = true
                                                    autoScroll.start()
                                                }
                                            } else {
                                                null
                                            },
                                            onOpenMenu = { scope.launch { drawerState.open() } },
                                            accentColor = accentColor,
                                            onAccentColor = onAccentColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Freihand-Lesemodus: alles, was den Motor anhalten soll, bis man selbst ▶ tippt – Auswahl bzw.
    // Farbleiste, Popover, Menü, Sheets, Dialoge, Lightbox und App im Hintergrund (Äquivalent zu
    // `handsFreeShouldPause` in ArticleReaderView.swift).
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val handsFreeShouldPause = selectionRect != null || infoPopover != null ||
        drawerState.targetValue == DrawerValue.Open || showAppearanceSheet || showReminderSheet ||
        showReportSheet || showTagsSheet || showRenameDialog || showShareLinkSheet ||
        lightboxState != null || highlightToDelete != null || commentFocus != null || quoteSession != null ||
        !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(handsFreeShouldPause) {
        if (handsFreeShouldPause) autoScroll.pause()
    }

    // System-Navigationsleiste an die eigene Bottom bar koppeln: Sie verschwindet beim
    // Runterscrollen zusammen mit der Leiste und kommt beim Hochscrollen mit ihr zurück. Ein Wisch
    // vom unteren Rand zeigt sie solange kurz an. Beim Verlassen des Readers ist sie wieder da.
    val readerBarVisible = showBottomBar || autoScroll.isActive
    LaunchedEffect(readerBarVisible) {
        if (view.isInEditMode) return@LaunchedEffect
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (readerBarVisible) {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!view.isInEditMode) {
                WindowCompat.getInsetsController((view.context as Activity).window, view)
                    .show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    // Zitat-Tafel: Status- und Navigationsleiste weg, solange sie offen ist (wie auf iOS).
    val quoteActive = quoteSession != null
    LaunchedEffect(quoteActive) {
        if (view.isInEditMode) return@LaunchedEffect
        val window = (view.context as Activity).window
        // Ohne Statusleiste blieb oben der Streifen der Kamera-Aussparung leer (weiß): solange die
        // Tafel offen ist, darf das Fenster bis in die Aussparung reichen.
        window.setCutoutShortEdges(quoteActive)
        val controller = WindowCompat.getInsetsController(window, view)
        if (quoteActive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
            if (readerBarVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!view.isInEditMode) {
                val window = (view.context as Activity).window
                window.setCutoutShortEdges(false)
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // Fortschrittsbalken als Geschwister-Overlay der Drawer/Scaffold-Hülle (siehe Kommentar an der
    // äußeren Box oben) – `Modifier.fillMaxSize()` bezieht sich hier auf die volle Bildschirmgröße
    // dieses Composables, nicht auf den um die System-Insets verkleinerten Scaffold-Content-Bereich.
    if (progressEdge != ProgressEdge.OFF) {
        ProgressBarOverlay(
            edge = progressEdge,
            progress = { scrollProgress },
            colorHex = accentColorHex,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showFileMetadata) {
        pdfFileMetadata?.let { FileMetadataSheet(metadata = it, onDismiss = { showFileMetadata = false }) }
    }

    if (showAppearanceSheet) {
        AppearanceSheet(onDismiss = { showAppearanceSheet = false }, viewModel = viewModel)
    }

    if (showReminderSheet) {
        ReminderSheet(onDismiss = { showReminderSheet = false }, viewModel = viewModel)
    }

    if (showReportSheet) {
        article?.url?.let { url ->
            ReportArticleSheet(articleUrl = url, onDismiss = { showReportSheet = false }, viewModel = viewModel)
        }
    }

    if (showTagsSheet) {
        article?.let { currentArticle ->
            EditTagsDialog(
                article = currentArticle,
                allTags = allTags,
                onDismiss = { showTagsSheet = false },
                onSave = { selectedIds, pendingNames, parentId -> viewModel.saveTags(selectedIds, pendingNames, parentId) },
                canNestTags = viewModel.supportsNextcloudOnlyFeatures,
            )
        }
    }

    if (showRenameDialog) {
        article?.let { currentArticle ->
            RenameFileDialog(
                article = currentArticle,
                onRename = { name -> viewModel.renameFile(name) },
                onDismiss = { showRenameDialog = false },
            )
        }
    }

    if (showShareLinkSheet) {
        ShareLinkSheet(
            onDismiss = {
                showShareLinkSheet = false
                // Der Kanal wurde evtl. geschlossen, weil es keinen Link gab.
                viewModel.comments.reconnect()
            },
            viewModel = viewModel,
            commentsAvailable = commentState.isAvailable,
        )
    }

    commentFocus?.let { focus ->
        CommentsSheet(
            store = viewModel.comments,
            state = commentState,
            initialFocus = focus,
            onShowInText = { id ->
                commentFocus = null
                documentActions?.reveal(id)
            },
            onDismiss = { commentFocus = null },
        )
    }

    lightboxState?.let { lbState ->
        ImageLightboxScreen(state = lbState, onDismiss = { lightboxState = null }, viewModel = viewModel)
    }

    highlightToDelete?.let { highlight ->
        AlertDialog(
            onDismissRequest = { highlightToDelete = null },
            title = { Text(stringResource(R.string.articleReader_comments_deleteHighlightTitle)) },
            text = { Text(stringResource(R.string.articleReader_comments_deleteHighlightMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHighlight(highlight)
                    highlightToDelete = null
                }) { Text(stringResource(R.string.articleReader_comments_removeHighlight), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { highlightToDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    // Zitat-Tafel über allem (auch Bottom-Bar und Fortschrittsbalken), siehe QuoteCard.kt.
    quoteSession?.let { session ->
        QuoteCardOverlay(
            session = session,
            webView = webViewRef,
            prefs = quotePrefs,
            onPrefsChange = viewModel::saveQuotePrefs,
            showHint = viewModel.quoteHint,
            accent = accentColor,
            onAccent = onAccentColor,
            // Eingeblendet: nach einer Drehung nicht noch einmal aus dem Artikel gleiten und
            // die Hinweise nicht wiederholen.
            onShown = {
                viewModel.quoteSession = session.copy(sourceInWindow = null, hintsShown = true)
                viewModel.quoteHint = false
            },
            onClosed = {
                viewModel.quoteSession = null
                viewModel.quoteHint = false
            },
        )
    }
    } // Box (Modifier.fillMaxSize()) – siehe Kommentar an deren Öffnung weiter oben
}

/** Schrift des Readers als Typeface (für „Original“ auf der Zitat-Tafel). */
private fun readerTypeface(appearance: ArticleReaderViewModel.Appearance): Typeface =
    appearance.customFont?.previewFace?.path
        ?.let { path -> runCatching { Typeface.createFromFile(path) }.getOrNull() }
        ?: when (appearance.font) {
            ReaderFont.SERIF -> Typeface.SERIF
            ReaderFont.MONO -> Typeface.MONOSPACE
            ReaderFont.SYSTEM, ReaderFont.SANS_SERIF -> Typeface.SANS_SERIF
        }

/**
 * Dünner Fortschrittsbalken entlang der per Einstellung gewählten Kante.
 *
 * [progress] ist bewusst ein Lambda und wird erst in der Draw-Phase (`drawBehind`) gelesen: der
 * Fortschritt ändert sich bei jedem Scroll-Event. Würde der Wert in einer Composition gelesen,
 * müsste bei jedem Frame der ganze `ArticleReaderScreen` (Drawer, Bottom-Bar, Reader-Wrapper)
 * neu komponiert werden – das war eine Hauptursache für ruckelndes Scrollen. So wird pro Frame
 * nur dieser Balken neu gezeichnet.
 */
@Composable
private fun ProgressBarOverlay(edge: ProgressEdge, progress: () -> Float, colorHex: String, modifier: Modifier = Modifier) {
    val color = remember(colorHex) { runCatching { Color(colorHex.toColorInt()) }.getOrDefault(Color.Red) }
    Spacer(
        modifier = modifier.drawBehind {
            val fraction = progress().coerceIn(0f, 1f)
            if (fraction <= 0f) return@drawBehind
            val thickness = 3.dp.toPx()
            when (edge) {
                // Äquivalent zu `progressBar(in:)` unter iOS (ArticleReaderView.swift): oben verankert,
                // wächst nach unten mit zunehmendem Scroll-Fortschritt (nicht von unten nach oben).
                ProgressEdge.LEFT -> drawRect(color, Offset.Zero, Size(thickness, size.height * fraction))
                ProgressEdge.RIGHT -> drawRect(color, Offset(size.width - thickness, 0f), Size(thickness, size.height * fraction))
                ProgressEdge.TOP -> drawRect(color, Offset.Zero, Size(size.width * fraction, thickness))
                ProgressEdge.BOTTOM -> drawRect(color, Offset(0f, size.height - thickness), Size(size.width * fraction, thickness))
                ProgressEdge.OFF -> {}
            }
        },
    )
}

/**
 * Reader-Bottom-Bar (abgeleitet von der iOS-`bottomBar` in ArticleReaderView.swift): gleich
 * breite Icon-Buttons ("Archivieren und zurück" links, optional Freihand-Lesen in der Mitte,
 * Menü rechts), getrennt durch Haarlinien, 54dp hoch. Wie unter iOS in der Akzentfarbe des Users
 * eingefärbt, Icons und Trennlinien in der darauf lesbaren Vordergrundfarbe (wie der Plakat-Header).
 * Der frühere Zurück-Button und "Archivieren und weiter" wurden entfernt; das Menü kam aus der
 * früheren TopAppBar hierher und öffnet den (rechtsseitigen) Drawer. Archiviert nur einseitig (nie
 * un-archivieren), wie im iOS-Original. [onStartHandsFree] == null blendet den Freihand-Button aus
 * (Schalter im Erscheinungsbild-Sheet).
 */
@Composable
private fun ReaderBottomBar(
    onArchiveAndBack: () -> Unit,
    onStartHandsFree: (() -> Unit)?,
    onOpenMenu: () -> Unit,
    accentColor: Color,
    onAccentColor: Color,
    modifier: Modifier = Modifier,
) {
    val divider = onAccentColor.copy(alpha = 0.25f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(accentColor.copy(alpha = 0.97f)),
    ) {
        BottomBarButton(
            icon = Icons.Filled.Archive,
            contentDescription = stringResource(R.string.android_articleReader_archiveAndBack),
            onClick = onArchiveAndBack,
            tint = onAccentColor,
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(thickness = 0.5.dp, color = divider)
        if (onStartHandsFree != null) {
            BottomBarButton(
                icon = Icons.Filled.KeyboardDoubleArrowDown,
                contentDescription = stringResource(R.string.articleReader_handsFree_start),
                onClick = onStartHandsFree,
                tint = onAccentColor,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(thickness = 0.5.dp, color = divider)
        }
        BottomBarButton(
            icon = Icons.Filled.Menu,
            contentDescription = stringResource(R.string.android_common_menu),
            onClick = onOpenMenu,
            tint = onAccentColor,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Freihand-Lesemodus: ersetzt die [ReaderBottomBar], solange der Modus an ist, in derselben Form
 * und Akzentfarbe (Äquivalent zur iOS-`handsFreeBar`):
 *
 *   ⏸ | 🚶 ━━━━━●━━━━━ 🏃 | ✕
 *
 * Der Slider wirkt sofort und wird beim Loslassen gespeichert; ✕ beendet den Modus. Nur diese
 * Leiste liest [AutoScrollController.slider]/[AutoScrollController.isPaused], ein Ziehen am Slider
 * komponiert also nicht den ganzen Reader neu.
 */
@Composable
private fun HandsFreeBar(
    controller: AutoScrollController,
    onSpeedChangeFinished: () -> Unit,
    onStop: () -> Unit,
    accentColor: Color,
    onAccentColor: Color,
    modifier: Modifier = Modifier,
) {
    val divider = onAccentColor.copy(alpha = 0.25f)
    val paused = controller.isPaused
    val speedDescription = stringResource(
        when {
            controller.slider < 0.34f -> R.string.articleReader_handsFree_slow
            controller.slider < 0.67f -> R.string.articleReader_handsFree_medium
            else -> R.string.articleReader_handsFree_fast
        },
    )
    val speedLabel = stringResource(R.string.articleReader_handsFree_speed)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(accentColor.copy(alpha = 0.97f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomBarButton(
            icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = stringResource(if (paused) R.string.articleReader_handsFree_resume else R.string.articleReader_handsFree_pause),
            onClick = { controller.togglePause() },
            tint = onAccentColor,
            modifier = Modifier.width(60.dp),
        )
        VerticalDivider(thickness = 0.5.dp, color = divider)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint = onAccentColor.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp),
            )
            Slider(
                value = controller.slider,
                onValueChange = { controller.slider = it },
                onValueChangeFinished = onSpeedChangeFinished,
                colors = SliderDefaults.colors(
                    thumbColor = onAccentColor,
                    activeTrackColor = onAccentColor,
                    inactiveTrackColor = onAccentColor.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = speedLabel
                        stateDescription = speedDescription
                    },
            )
            Icon(
                Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = null,
                tint = onAccentColor.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp),
            )
        }
        VerticalDivider(thickness = 0.5.dp, color = divider)
        BottomBarButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.articleReader_handsFree_stop),
            onClick = onStop,
            tint = onAccentColor,
            modifier = Modifier.width(60.dp),
        )
    }
}

@Composable
private fun BottomBarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

/**
 * Äquivalent zum iOS-`.popover` über der Info-Card-Zelle (ArticleReaderView.swift): zeigt
 * Label (klein, gedämpft) + vollen Wert (z.B. den nicht-truncaten Autorennamen, oder
 * "Hinzugefügt am" + Datum) in einer kleinen Karte mit Pfeil, der – wie das native iOS-Popover –
 * auf die angetippte Zelle zeigt. Eine transparente Vollbild-Ebene dahinter schließt das Popover
 * bei jedem Tap außerhalb; die Karte selbst schluckt ihren eigenen Tap, damit ein Tap darauf das
 * Popover nicht versehentlich sofort wieder schließt.
 *
 * **Verankerung an der Zelle:** [popover] trägt die CSS-px-Bounding-Box der angetippten Zelle
 * (`getBoundingClientRect()` in READER_JS). Da die WebView per `width=device-width,
 * initial-scale=1.0`-Viewport-Meta 1 CSS-px ≈ 1 dp rendert und deckungsgleich mit diesem Overlay
 * liegt (beide `Modifier.fillMaxSize()` in derselben Eltern-`Box`), wird das Rect direkt über `.dp`
 * eingelesen und für die gesamte Platzierungsmathematik EINMALIG in echte Px umgerechnet – so
 * läuft alles (Zellenrect, Kartengröße, Container) in einer einzigen Einheit.
 *
 * **Messung der echten Kartengröße:** Die Vorversion klemmte die Karte horizontal mit
 * einer *geschätzten* Breite (220dp). Da die Karte real aber viel schmaler ist ("GESPEICHERT" +
 * Kurzdatum), zog diese Überschätzung die Karte – besonders bei der rechts sitzenden
 * "Erschienen"-Zelle – sichtbar nach links weg von der Zelle. Hier wird die Karte daher per
 * [onSizeChanged] gemessen und erst nach der Messung sichtbar (ein Frame `alpha = 0`), womit sie
 * exakt unter/über der Zellenmitte zentriert und korrekt an die Bildschirmränder geklemmt wird.
 */
@Composable
private fun InfoPopoverOverlay(popover: ReaderJsBridge.InfoPopover, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val cardColor = MaterialTheme.colorScheme.surfaceVariant

    // CSS-px (== dp dank Viewport-Meta) → echte Px, einmalig für die gesamte Mathematik.
    val cellLeftPx = with(density) { popover.x.dp.toPx() }
    val cellTopPx = with(density) { popover.y.dp.toPx() }
    val cellCenterXPx = cellLeftPx + with(density) { popover.width.dp.toPx() } / 2f
    val cellBottomPx = cellTopPx + with(density) { popover.height.dp.toPx() }

    val edgeMarginPx = with(density) { 12.dp.toPx() }
    val gapPx = with(density) { 6.dp.toPx() }
    val arrowHalfPx = with(density) { 8.dp.toPx() }
    val arrowHeightPx = with(density) { 8.dp.toPx() }
    val cornerPx = with(density) { 14.dp.toPx() }

    // Echte Kartengröße ist erst nach dem ersten (unsichtbaren) Layout-Pass bekannt; bei neuem
    // [popover] (andere Zelle) zurücksetzen, damit neu gemessen wird.
    var cardSize by remember(popover) { mutableStateOf<IntSize?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Ein Tap außerhalb der Karte schließt sie – `detectTapGestures` statt `clickable`, damit kein
            // Ripple über dem ganzen Lesebereich aufblitzt.
            .pointerInput(popover) { detectTapGestures { onDismiss() } },
    ) {
        val containerWPx = constraints.maxWidth.toFloat()
        val containerHPx = constraints.maxHeight.toFloat()
        val cardW = cardSize?.width?.toFloat() ?: 0f
        val cardH = cardSize?.height?.toFloat() ?: 0f

        // Horizontal: Karte an der Zellenmitte zentrieren, dann an die Bildschirmränder klemmen.
        val maxLeftPx = (containerWPx - cardW - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val cardLeftPx = (cellCenterXPx - cardW / 2f).coerceIn(edgeMarginPx, maxLeftPx)

        // Vertikal: bevorzugt UNTER der Zelle (mit Platz für den Pfeil); passt das nicht mehr,
        // dann DARÜBER – analog zur automatischen `.popover`-Umklapp-Logik unter iOS.
        val below = cellBottomPx + gapPx + arrowHeightPx + cardH <= containerHPx - edgeMarginPx
        val cardTopPx = if (below) {
            cellBottomPx + gapPx + arrowHeightPx
        } else {
            (cellTopPx - gapPx - arrowHeightPx - cardH).coerceAtLeast(edgeMarginPx)
        }

        // Pfeilspitze zielt auf die Zellenmitte, bleibt aber innerhalb der gerundeten Kartenkante.
        val arrowMinX = cardLeftPx + cornerPx + arrowHalfPx
        val arrowMaxX = cardLeftPx + cardW - cornerPx - arrowHalfPx
        val arrowCenterXPx = if (arrowMinX <= arrowMaxX) {
            cellCenterXPx.coerceIn(arrowMinX, arrowMaxX)
        } else {
            cardLeftPx + cardW / 2f
        }

        // Pfeil erst zeichnen, wenn die Karte gemessen ist (sonst zeigte er ins Leere).
        if (cardSize != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                if (below) {
                    // Spitze nach oben, Basis an der Kartenoberkante.
                    path.moveTo(arrowCenterXPx - arrowHalfPx, cardTopPx)
                    path.lineTo(arrowCenterXPx + arrowHalfPx, cardTopPx)
                    path.lineTo(arrowCenterXPx, cardTopPx - arrowHeightPx)
                } else {
                    // Spitze nach unten, Basis an der Kartenunterkante.
                    val baseY = cardTopPx + cardH
                    path.moveTo(arrowCenterXPx - arrowHalfPx, baseY)
                    path.lineTo(arrowCenterXPx + arrowHalfPx, baseY)
                    path.lineTo(arrowCenterXPx, baseY + arrowHeightPx)
                }
                path.close()
                drawPath(path, cardColor)
            }
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(cardLeftPx.roundToInt(), cardTopPx.roundToInt()) }
                // Vor der Messung unsichtbar, damit die Karte nicht kurz an der noch nicht
                // gemessenen (zentrierten) Stelle aufblitzt.
                .alpha(if (cardSize == null) 0f else 1f)
                .widthIn(max = 260.dp)
                .onSizeChanged { cardSize = it }
                // Taps auf die Karte selbst abfangen, ohne sie zu schließen (und ohne Ripple).
                .pointerInput(Unit) { detectTapGestures { } }
                .background(cardColor, shape = RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                popover.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                popover.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Farbleiste über einer Auswahl bzw. einer angetippten Markierung – Äquivalent zu
 * `HighlightToolbarView` in `ArticleReaderView.swift`: fünf Farben, dahinter „Kommentieren“
 * (nur merlin-nextcloud) und an einer Markierung „Entfernen“.
 */
@Composable
private fun HighlightColorToolbar(
    showColors: Boolean,
    selectedColor: String?,
    onColorSelected: (String) -> Unit,
    onComment: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Abgesetzt vom Text wie auf iOS (`HighlightToolbarBackground`: Schatten unter der Leiste),
    // dazu ein feiner Rand, damit sie auch auf hellem Papier nicht verschwimmt.
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showColors) {
            ReaderHtmlBuilder.HIGHLIGHT_COLORS.forEach { (name, hex) ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(hex.toColorInt()), shape = CircleShape)
                        .then(
                            if (name == selectedColor) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onColorSelected(name) },
                )
            }
        }
        if (showColors && (onComment != null || onDelete != null)) {
            VerticalDivider(modifier = Modifier.height(24.dp))
        }
        if (onComment != null) {
            IconButton(onClick = onComment, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.AddComment, contentDescription = stringResource(R.string.articleReader_comments_comment))
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.articleReader_comments_removeHighlight))
            }
        }
    }
}

/**
 * Suchleiste oben im Reader – Äquivalent zur Suchleiste in `ArticleReaderView.swift`: Suchfeld,
 * „x von y“, vorheriger/nächster Treffer und Fertig.
 */
@Composable
private fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    result: FindResult?,
    onStep: (forward: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.articleReader_find_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onStep(true) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            val label = when {
                query.isBlank() || result == null -> ""
                result.count > 0 -> stringResource(R.string.articleReader_find_position, result.index + 1, result.count)
                else -> stringResource(R.string.articleReader_find_noMatches)
            }
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
            }
            val hasMatches = (result?.count ?: 0) > 0
            IconButton(onClick = { onStep(false) }, enabled = hasMatches) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.articleReader_find_previous))
            }
            IconButton(onClick = { onStep(true) }, enabled = hasMatches) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.articleReader_find_next))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.articleReader_find_close)) }
        }
    }
}

/**
 * Farben für die native Chrome ([ReaderBottomBar]), passend zum
 * gewählten Reader-Theme statt zum System-Theme – Äquivalent zu den iOS-Farben `readerBgColor`/
 * `readerFgColor`/`readerButtonBgColor`/`readerSeparatorColor`/`readerFgMutedColor`
 * (ArticleReaderView.swift). `elevatedBackground` ist die etwas hellere/dunklere Fläche für
 * die Bottom-Bar, damit sie sich (besonders im Dark-Theme mit reinem Schwarz als Seiten-
 * hintergrund) optisch vom Artikelinhalt abhebt.
 */
private data class ReaderChromeColors(
    val background: Color,
    val foreground: Color,
    val mutedForeground: Color,
    val elevatedBackground: Color,
    val separator: Color,
)

/**
 * Bewusst NICHT `MaterialTheme.colorScheme`: das App-weite System-Theme kann vom gewählten
 * Reader-Theme abweichen (z.B. Nutzer wählt "Dunkel" als Reader-Theme bei hellem System-Theme,
 * oder "Hell" bei dunklem System-Theme) – dann müsste die Chrome sonst weiß/hell bleiben,
 * während der Artikeltext (siehe `ReaderHtmlBuilder`) schon dunkel ist, oder umgekehrt.
 */
@Composable
private fun rememberReaderChromeColors(theme: ReaderTheme): ReaderChromeColors {
    val systemDark = isSystemInDarkTheme()
    return remember(theme, systemDark) {
        val isDark = theme == ReaderTheme.DARK || (theme == ReaderTheme.AUTO && systemDark)
        when (theme) {
            ReaderTheme.SEPIA -> ReaderChromeColors(
                background = Color(0xFFF5ECD9),
                foreground = Color(0xFF3B2F1E),
                mutedForeground = Color(0xFF7A6350),
                elevatedBackground = Color(0xFFD1C2A8),
                separator = Color(0xFFAE9678).copy(alpha = 0.45f),
            )
            else -> if (isDark) {
                ReaderChromeColors(
                    background = Color.Black,
                    foreground = Color(0xFFE5E5EA),
                    mutedForeground = Color(0xFF98989D),
                    elevatedBackground = Color(0xFF333333),
                    separator = Color.White.copy(alpha = 0.10f),
                )
            } else {
                ReaderChromeColors(
                    background = Color.White,
                    foreground = Color(0xFF1C1C1E),
                    mutedForeground = Color(0xFF6E6E73),
                    elevatedBackground = Color.White,
                    separator = Color.Black.copy(alpha = 0.10f),
                )
            }
        }
    }
}

/**
 * Färbt Status- und Navigationsleiste ein. `Window.statusBarColor`/`navigationBarColor` sind seit
 * API 35 deprecated, weil Apps mit `targetSdk >= 35` zwingend edge-to-edge laufen und die Setter
 * dort wirkungslos sind. Merlin zielt aktuell auf `targetSdk = 34` ohne `enableEdgeToEdge()`; hier
 * sind die Setter weiterhin der einzige Weg, die Leisten einzufärben (dasselbe Muster wie in
 * `MerlinTheme`, Theme.kt). Die Deprecation wird daher gebündelt an dieser einen Stelle
 * unterdrückt. Beim Umstieg auf `targetSdk 35` entfällt diese Funktion zugunsten von
 * edge-to-edge plus Hintergrund hinter den System-Insets.
 */
@Suppress("DEPRECATION")
private fun Window.setStatusBarColorCompat(argb: Int) {
    statusBarColor = argb
}

@Suppress("DEPRECATION")
private fun Window.setNavigationBarColorCompat(argb: Int) {
    navigationBarColor = argb
}

/** Fenster bis in die Kamera-Aussparung (an den kurzen Kanten) ziehen bzw. zurück zum Standard. */
private fun Window.setCutoutShortEdges(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val mode = if (enabled) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    }
    if (attributes.layoutInDisplayCutoutMode != mode) {
        attributes = attributes.apply { layoutInDisplayCutoutMode = mode }
    }
}

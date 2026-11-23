package dev.merlin.android.ui.tour

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.merlin.android.R
import dev.merlin.android.ui.reader.ACCENT_COLORS
import dev.merlin.android.ui.reader.PosterColors
import dev.merlin.android.ui.reader.ReaderBottomBar

/*
 * App-Tour beim ersten Start, Gegenstück zu `OnboardingTourView.swift` (merlin-ios).
 *
 * Aufbau wie dort: eine Schrittliste ([tourSteps]), eine abgedunkelte Ebene mit
 * Spotlight-Aussparung um das gerade erklärte Element und eine Karte mit Fortschrittspunkten,
 * Zurück/Weiter und „Tour überspringen“. Die Listen-Schritte heben die echten Bedienelemente der
 * Artikelliste hervor (sie melden ihre Position per [tourAnchor]), die Reader-Schritte zeigen einen
 * Demo-Reader mit Beispielartikel, Bottom-Bar und Seitenmenü, weil die Tour über der Liste liegt.
 *
 * Abweichungen von iOS, weil Android anders bedient wird:
 * - Navigations- und Lesemenü öffnen per Button statt per Wisch vom Rand (eigene Texte im
 *   `android.onboarding`-Namespace von merlin-translations).
 * - Statt „Schütteln zum Rückgängigmachen“ erklärt die Tour den Rückgängig-Button der Snackbar.
 * - Die Navigationsmenü-Schritte öffnen das echte Seitenmenü der Liste; Lesemenü und Bottom-Bar
 *   sind dieselben wie im Reader ([ReaderBottomBar], Einträge wie in `ArticleReaderScreen`).
 * - Wischgesten gibt es nur in der Listenansicht, die beiden Wisch-Schritte entfallen in der
 *   Kartenansicht.
 * - Einen Löschfrist-Schritt gibt es (noch) nicht, die Android-App kennt die Server-Löschfrist nicht.
 */

/** Was hinter der Abdunkelung zu sehen ist. */
private enum class TourPhase {
    /** Die echte Artikelliste. */
    LIST,
    /** Die echte Artikelliste mit geöffnetem Seitenmenü. */
    LIST_DRAWER,
    /** Die echte Artikelliste mit Rückgängig-Snackbar. */
    UNDO,
    /** Demo-Reader mit Bottom-Bar. */
    READER,
    /** Demo-Reader mit geöffnetem Seitenmenü. */
    READER_MENU,
    /** Demo-Reader mit markiertem Text. */
    HIGHLIGHTS,
}

/** Elemente, die die Tour hervorheben kann. Die ersten drei liefert die echte Artikelliste. */
enum class TourAnchor {
    PLUS_BUTTON,
    MENU_BUTTON,
    FIRST_ARTICLE,
    LIST_DRAWER,
    VIEW_TOGGLE,
    DEMO_UNDO_SNACKBAR,
    DEMO_READER_MENU_BUTTON,
    DEMO_READER_MENU,
    DEMO_MENU_APPEARANCE,
    DEMO_MENU_TAGS,
    DEMO_MENU_REMINDER,
    DEMO_MENU_REPORT,
    DEMO_HIGHLIGHT,
}

private data class TourStep(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val phase: TourPhase,
    val anchor: TourAnchor?,
    /** Rund statt abgerundet hervorheben (Icon-Buttons der Toolbar). */
    val circularSpotlight: Boolean = false,
    /** Nur in der Listenansicht (Wischgesten gibt es nur dort). */
    val listViewOnly: Boolean = false,
    /** Auswahl der Akzentfarbe in der Karte (letzter Schritt). */
    val showsAccentPicker: Boolean = false,
    /** Die anderen Merlin-Apps (Web, Thunderbird, …) in der Karte. */
    val showsCrossPromo: Boolean = false,
)

private fun tourSteps(isCardView: Boolean): List<TourStep> = listOf(
    TourStep(Icons.Filled.WavingHand, R.string.onboarding_step1_title, R.string.onboarding_step1_body, TourPhase.LIST, null),
    TourStep(Icons.Filled.AddCircleOutline, R.string.onboarding_step2_title, R.string.onboarding_step2_body, TourPhase.LIST, TourAnchor.PLUS_BUTTON, circularSpotlight = true),
    TourStep(Icons.Filled.Menu, R.string.onboarding_step3_title, R.string.android_onboarding_navigationMenu_body, TourPhase.LIST_DRAWER, TourAnchor.LIST_DRAWER),
    TourStep(Icons.Filled.GridView, R.string.onboarding_step4_title, R.string.onboarding_step4_body, TourPhase.LIST_DRAWER, TourAnchor.VIEW_TOGGLE),
    TourStep(Icons.Filled.SwipeLeft, R.string.onboarding_step5_title, R.string.onboarding_step5_body, TourPhase.LIST, TourAnchor.FIRST_ARTICLE, listViewOnly = true),
    TourStep(Icons.Filled.SwipeRight, R.string.onboarding_step6_title, R.string.onboarding_step6_body, TourPhase.LIST, TourAnchor.FIRST_ARTICLE, listViewOnly = true),
    TourStep(Icons.AutoMirrored.Filled.MenuOpen, R.string.onboarding_step7_title, R.string.android_onboarding_readerMenu_body, TourPhase.READER, TourAnchor.DEMO_READER_MENU_BUTTON),
    TourStep(Icons.Filled.FormatSize, R.string.onboarding_step8_title, R.string.onboarding_step8_body, TourPhase.READER_MENU, TourAnchor.DEMO_MENU_APPEARANCE),
    TourStep(Icons.Filled.Tag, R.string.onboarding_step9_title, R.string.onboarding_step9_body, TourPhase.READER_MENU, TourAnchor.DEMO_MENU_TAGS),
    TourStep(Icons.Filled.Notifications, R.string.onboarding_step10_title, R.string.onboarding_step10_body, TourPhase.READER_MENU, TourAnchor.DEMO_MENU_REMINDER),
    TourStep(Icons.Filled.Flag, R.string.onboarding_step11_title, R.string.onboarding_step11_body, TourPhase.READER_MENU, TourAnchor.DEMO_MENU_REPORT),
    TourStep(Icons.Filled.BorderColor, R.string.onboarding_step12_title, R.string.onboarding_step12_body, TourPhase.HIGHLIGHTS, TourAnchor.DEMO_HIGHLIGHT),
    TourStep(Icons.AutoMirrored.Filled.Undo, R.string.android_onboarding_undo_title, R.string.android_onboarding_undo_body, TourPhase.UNDO, TourAnchor.DEMO_UNDO_SNACKBAR),
    TourStep(Icons.Filled.Devices, R.string.onboarding_crossPromo_title, R.string.onboarding_crossPromo_body, TourPhase.LIST, null, showsCrossPromo = true),
    TourStep(Icons.Filled.Palette, R.string.onboarding_accentColor_title, R.string.onboarding_accentColor_body, TourPhase.LIST, null, showsAccentPicker = true),
).filter { !(isCardView && it.listViewOnly) }

/** Positionen der hervorhebbaren Elemente, in Root-Koordinaten. */
class TourAnchors {
    internal val rects: SnapshotStateMap<TourAnchor, Rect> = mutableStateMapOf()
}

@Composable
fun rememberTourAnchors(): TourAnchors = remember { TourAnchors() }

/** Meldet die Position dieses Elements, damit die Tour es hervorheben kann. */
fun Modifier.tourAnchor(anchors: TourAnchors, anchor: TourAnchor): Modifier =
    onGloballyPositioned { anchors.rects[anchor] = it.boundsInRoot() }

private val CardBackground = Color(0xF21C1C1E)

/**
 * Die Tour als Overlay über der Artikelliste. [onFinish] setzt das „Tour erledigt“-Flag und
 * blendet das Overlay aus; [onAccentColorChange] speichert eine in der Tour gewählte Akzentfarbe,
 * [onAccentColorCommit] schickt sie am Ende einmal an den Server.
 */
@Composable
fun OnboardingTourOverlay(
    anchors: TourAnchors,
    isCardView: Boolean,
    accentColorHex: String,
    /** Freihand-Button in der Reader-Bottom-Bar (Einstellung im Erscheinungsbild). */
    showHandsFreeButton: Boolean,
    /** Kommentare im Lesemenü (nur merlin-nextcloud). */
    showComments: Boolean,
    /** Web-Oberfläche des angemeldeten Servers für den Schritt „Zusammenspiel“, null ohne Server. */
    webUrl: String?,
    /** Öffnet bzw. schließt das echte Seitenmenü der Artikelliste. */
    onListDrawerVisibleChange: (Boolean) -> Unit,
    onAccentColorChange: (String) -> Unit,
    onAccentColorCommit: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Beim Öffnen festhalten, damit sich die Schrittzahl nicht verschiebt, wenn jemand während
    // der Tour (theoretisch) die Ansicht wechselt.
    val steps = remember { tourSteps(isCardView) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val index = stepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[index]
    val isLast = index == steps.lastIndex
    var accentChanged by remember { mutableStateOf(false) }
    val accent = remember(accentColorHex) { parseHexColor(accentColorHex) }

    fun finish() {
        if (accentChanged) onAccentColorCommit()
        onFinish()
    }

    // Navigationsmenü-Schritte zeigen das echte Seitenmenü; beim Verlassen (auch Überspringen) schließen.
    val wantsDrawer = step.phase == TourPhase.LIST_DRAWER
    LaunchedEffect(wantsDrawer) { onListDrawerVisibleChange(wantsDrawer) }
    DisposableEffect(Unit) { onDispose { onListDrawerVisibleChange(false) } }

    // System-Zurück blättert zurück, im ersten Schritt beendet es die Tour.
    BackHandler {
        if (index > 0) stepIndex = index - 1 else finish()
    }

    // Eigenen Ursprung merken: die Anker liegen in Root-Koordinaten.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dimColor = Color.Black.copy(alpha = if (dark) 0.6f else 0.72f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            // Alle Berührungen abfangen, die nicht auf der Karte landen.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.toPx() }
        val cardMaxHeight = maxHeight * 0.7f

        // Ebene 1: Demo-Inhalt hinter der Abdunkelung (in der Listen-Phase scheint die echte Liste durch).
        when (step.phase) {
            TourPhase.LIST, TourPhase.LIST_DRAWER -> Unit
            TourPhase.UNDO -> DemoUndoSnackbar(anchors)
            else -> DemoReader(
                anchors = anchors,
                accentColorHex = accentColorHex,
                showHandsFreeButton = showHandsFreeButton,
                showComments = showComments,
                activeAnchor = step.anchor,
                showMenu = step.phase == TourPhase.READER_MENU,
                showHighlight = step.phase == TourPhase.HIGHLIGHTS,
            )
        }

        // Ebene 2: Abdunkelung mit Spotlight-Aussparung.
        val target = step.anchor?.let { anchors.rects[it] }?.translate(-origin)
        val padPx = with(density) { 8.dp.toPx() }
        val spotTarget = target?.let {
            if (step.circularSpotlight) {
                val r = maxOf(it.width, it.height) / 2 + padPx
                Rect(it.center, r)
            } else {
                it.inflate(padPx)
            }
        }
        val animatedSpot by animateRectAsState(spotTarget ?: Rect.Zero, label = "spotlight")
        val showSpot = spotTarget != null
        val cornerPx = if (step.circularSpotlight) animatedSpot.width / 2 else with(density) { 16.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            if (!showSpot) {
                drawRect(dimColor)
            } else {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(animatedSpot, CornerRadius(cornerPx)))
                }
                drawPath(path, dimColor)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = animatedSpot.topLeft,
                    size = animatedSpot.size,
                    cornerRadius = CornerRadius(cornerPx),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Ebene 3: Karte. Unten, außer das Hervorgehobene liegt in der unteren Hälfte
        // (bildschirmhohe Elemente wie das Seitenmenü zählen nicht).
        val cardOnTop = spotTarget != null &&
            spotTarget.height < heightPx * 0.6f &&
            spotTarget.center.y > heightPx / 2
        Box(
            contentAlignment = if (cardOnTop) Alignment.TopCenter else Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            TourCard(
                steps = steps,
                index = index,
                accent = accent,
                accentColorHex = accentColorHex,
                onBack = { stepIndex = index - 1 },
                onNext = { if (isLast) finish() else stepIndex = index + 1 },
                onSkip = { finish() },
                onPickAccent = { hex ->
                    accentChanged = true
                    onAccentColorChange(hex)
                },
                webUrl = webUrl,
                maxCardHeight = cardMaxHeight,
            )
        }
    }
}

@Composable
private fun TourCard(
    steps: List<TourStep>,
    index: Int,
    accent: Color,
    accentColorHex: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onPickAccent: (String) -> Unit,
    webUrl: String?,
    maxCardHeight: Dp,
) {
    val step = steps[index]
    val isLast = index == steps.lastIndex
    val progressLabel = stringResource(R.string.onboarding_progress, index + 1, steps.size)
    Surface(
        color = CardBackground,
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 12.dp,
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 18.dp),
        ) {
            // Fortschrittspunkte
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 16.dp)
                    .semantics { contentDescription = progressLabel },
            ) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (i == index) 22.dp else 6.dp)
                            .background(Color.White.copy(alpha = if (i == index) 1f else 0.28f), CircleShape),
                    )
                }
            }

            // Inhalt scrollt, wenn er nicht passt (Querformat, große Schrift); Buttons bleiben sichtbar.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .heightIn(max = (maxCardHeight - 140.dp).coerceAtLeast(120.dp))
                    .verticalScroll(rememberScrollState()),
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tourStep",
                ) { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            s.icon,
                            contentDescription = null,
                            tint = if (s.showsAccentPicker) accent else Color.White,
                            modifier = Modifier.padding(bottom = 12.dp).size(40.dp),
                        )
                        Text(
                            stringResource(s.title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
                        )
                        Text(
                            stringResource(s.body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 24.dp),
                        )
                    }
                }
                if (step.showsAccentPicker) {
                    AccentColorChooser(accentColorHex, onPickAccent, Modifier.padding(bottom = 24.dp))
                }
                if (step.showsCrossPromo) {
                    CrossPromoList(webUrl, accent, Modifier.padding(bottom = 24.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = index > 0,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                ) {
                    Text(stringResource(R.string.common_back), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = readableOn(accent)),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                ) {
                    Text(
                        stringResource(if (isLast) R.string.onboarding_buttons_getStarted else R.string.common_next),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!isLast) {
                TextButton(
                    onClick = onSkip,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    Text(stringResource(R.string.onboarding_buttons_skip), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// MARK: – Andere Merlin-Apps

/**
 * Store-Seiten der anderen Merlin-Apps. Nur, was es öffentlich gibt: Die Firefox-Erweiterung ist
 * noch nicht gelistet, Chrome unveröffentlicht. iOS wird hier bewusst nicht genannt (Store-Regeln
 * zu anderen Mobilplattformen).
 */
private const val THUNDERBIRD_ADDON_URL = "https://addons.thunderbird.net/thunderbird/addon/merlin/"

/**
 * Die anderen Merlin-Apps, je Zeile Nutzen und Aktion. Die Web-Oberfläche öffnet sich direkt;
 * Erweiterungen installiert man am Rechner, deshalb wird ihr Store-Link geteilt statt geöffnet.
 */
@Composable
private fun CrossPromoList(webUrl: String?, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.onboarding_crossPromo_shareLink)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        if (webUrl != null) {
            CrossPromoRow(Icons.Filled.Public, stringResource(R.string.onboarding_crossPromo_web),
                stringResource(R.string.onboarding_crossPromo_webAction), accent) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        CrossPromoRow(Icons.Filled.Email, stringResource(R.string.onboarding_crossPromo_thunderbird), shareTitle, accent) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, THUNDERBIRD_ADDON_URL)
            runCatching {
                context.startActivity(Intent.createChooser(send, shareTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

@Composable
private fun CrossPromoRow(icon: ImageVector, text: String, action: String, accent: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = readableOn(accent)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.heightIn(min = 32.dp),
        ) {
            Text(action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccentColorChooser(selectedHex: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val selectedLabel = stringResource(R.string.android_common_selected)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        ACCENT_COLORS.forEach { hex ->
            val selected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(parseHexColor(hex), CircleShape)
                    .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
                    .clickable { onPick(hex) }
                    .semantics {
                        this.selected = selected
                        if (selected) contentDescription = selectedLabel
                    },
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = readableOn(parseHexColor(hex)), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// MARK: – Demo-Snackbar

/** Snackbar wie nach dem Archivieren in der Artikelliste (Rückgängig + Schließen). */
@Composable
private fun DemoUndoSnackbar(anchors: TourAnchors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Snackbar(
            action = {
                TextButton(onClick = {}) { Text(stringResource(R.string.articleList_archiveToast_undoButton)) }
            },
            dismissAction = {
                IconButton(onClick = {}) { Icon(Icons.Filled.Close, contentDescription = null) }
            },
            modifier = Modifier
                .padding(12.dp)
                .tourAnchor(anchors, TourAnchor.DEMO_UNDO_SNACKBAR),
        ) {
            Text(stringResource(R.string.articleList_archiveToast_archived))
        }
    }
}

// MARK: – Demo-Reader

/**
 * Beispielartikel mit der echten [ReaderBottomBar], optional mit geöffnetem Lesemenü (von
 * rechts, dieselben Einträge wie in `ArticleReaderScreen` für einen Text-Artikel) oder
 * markiertem Satz. Texte aus `onboarding.demo.*`.
 */
@Composable
private fun DemoReader(
    anchors: TourAnchors,
    accentColorHex: String,
    showHandsFreeButton: Boolean,
    showComments: Boolean,
    activeAnchor: TourAnchor?,
    showMenu: Boolean,
    showHighlight: Boolean,
) {
    val accent = remember(accentColorHex) { parseHexColor(accentColorHex).copy(alpha = 1f) }
    val onAccent = remember(accentColorHex) { parseHexColor(PosterColors.onAccentHex(accentColorHex)) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_demo_sampleArticle_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.onboarding_demo_sampleArticle_byline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.onboarding_demo_sampleArticle_tag),
                style = MaterialTheme.typography.labelMedium,
                color = onAccent,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .background(accent, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Text(
                stringResource(R.string.onboarding_demo_sampleArticle_teaser),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (showHighlight) {
                val text = stringResource(R.string.onboarding_demo_sampleHighlight)
                val phrase = stringResource(R.string.onboarding_demo_sampleHighlightPhrase)
                val start = text.indexOf(phrase)
                val annotated = buildAnnotatedString {
                    if (start < 0) {
                        withStyle(SpanStyle(background = HighlightYellow, color = Color.Black)) { append(text) }
                    } else {
                        append(text.substring(0, start))
                        withStyle(SpanStyle(background = HighlightYellow, color = Color.Black)) { append(phrase) }
                        append(text.substring(start + phrase.length))
                    }
                }
                Text(
                    annotated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .tourAnchor(anchors, TourAnchor.DEMO_HIGHLIGHT),
                )
            } else {
                Text(
                    stringResource(R.string.onboarding_demo_sampleArticle_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        // Die echte Bottom-Bar (Archivieren | Freihand | Menü), hier ohne Funktion.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            ReaderBottomBar(
                onArchiveAndBack = {},
                onStartHandsFree = if (showHandsFreeButton) ({}) else null,
                onOpenMenu = {},
                accentColor = accent,
                onAccentColor = onAccent,
            )
            // Spotlight auf das rechte Drittel bzw. die rechte Hälfte (Menü-Button).
            Row(Modifier.matchParentSize()) {
                Spacer(Modifier.weight(if (showHandsFreeButton) 2f else 1f))
                Box(Modifier.weight(1f).fillMaxHeight().tourAnchor(anchors, TourAnchor.DEMO_READER_MENU_BUTTON))
            }
        }

        if (showMenu) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            DemoReaderMenu(
                anchors = anchors,
                activeAnchor = activeAnchor,
                showComments = showComments,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Lesemenü wie in `ArticleReaderScreen` (Text-Artikel): gleiche Einträge, Reihenfolge, Symbole. */
@Composable
private fun DemoReaderMenu(
    anchors: TourAnchors,
    activeAnchor: TourAnchor?,
    showComments: Boolean,
    modifier: Modifier = Modifier,
) {
    val requesters = remember { TourAnchor.entries.associateWith { BringIntoViewRequester() } }
    // Auf kleinen Bildschirmen passt das Menü nicht ganz: den erklärten Eintrag hineinscrollen.
    LaunchedEffect(activeAnchor) {
        activeAnchor?.let { requesters[it]?.bringIntoView() }
    }
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            @Composable
            fun item(icon: ImageVector, label: String, anchor: TourAnchor? = null) {
                NavigationDrawerItem(
                    label = { Text(label) },
                    icon = { Icon(icon, contentDescription = null) },
                    selected = false,
                    onClick = {},
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .then(
                            if (anchor != null) {
                                Modifier
                                    .bringIntoViewRequester(requesters.getValue(anchor))
                                    .tourAnchor(anchors, anchor)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            item(Icons.Outlined.Star, stringResource(R.string.articleReader_sideMenu_addFavorite))
            item(Icons.Filled.Palette, stringResource(R.string.articleReader_sideMenu_appearance), TourAnchor.DEMO_MENU_APPEARANCE)
            item(Icons.Filled.Share, stringResource(R.string.common_share))
            item(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.articleReader_sideMenu_openInBrowser))
            item(Icons.Filled.History, stringResource(R.string.articleReader_sideMenu_openViaArchive))
            item(Icons.Filled.Link, stringResource(R.string.articleReader_sideMenu_copyLink))
            item(Icons.Filled.Public, stringResource(R.string.android_shareLink_menu))
            if (showComments) {
                item(Icons.AutoMirrored.Outlined.Comment, stringResource(R.string.articleReader_comments_title))
            }
            item(Icons.Filled.Search, stringResource(R.string.articleReader_sideMenu_find))
            item(Icons.Filled.Archive, stringResource(R.string.articleReader_sideMenu_archive))
            item(Icons.Filled.Tag, stringResource(R.string.articleReader_sideMenu_editTags), TourAnchor.DEMO_MENU_TAGS)
            item(Icons.Filled.NotificationAdd, stringResource(R.string.articleReader_sideMenu_setReminder), TourAnchor.DEMO_MENU_REMINDER)
            item(Icons.Filled.Flag, stringResource(R.string.articleReader_sideMenu_reportArticle), TourAnchor.DEMO_MENU_REPORT)
            item(Icons.Filled.Delete, stringResource(R.string.common_delete))
        }
    }
}

private val HighlightYellow = Color(0xFFFFE066)

private fun parseHexColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFFFF3B30))

/** Schwarz oder Weiß, je nachdem, was auf [background] besser lesbar ist. */
private fun readableOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

package dev.merlin.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Eine einzelne Swipe-Aktion: Icon + Label + Akzentfarbe + Callback. */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit,
)

private val ActionWidth = 72.dp
private const val SNAP_DIST_DP = 55f        // Trailing-Öffnungsschwelle aus dem geschlossenen Zustand
private const val SHARE_SNAP_DIST_DP = 100f // Leading-Öffnungsschwelle – höher, gegen versehentliches Teilen
private const val CLOSE_DIST_DP = 20f       // Schließschwelle aus dem offenen Zustand

// response/dampingFraction (SwiftUI) → stiffness/dampingRatio (Compose): stiffness = (2π/response)²,
// dampingRatio = dampingFraction (beide Frameworks nutzen denselben normierten 0–1-Dämpfungsbegriff).
private val SnapSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow) // response 0.3, dampingFraction 0.8
private val BounceCloseSpring = spring<Float>(dampingRatio = 0.42f, stiffness = 247f)              // response 0.4, dampingFraction 0.42

/**
 * Äquivalent zur handgebauten Swipe-Geste in `ArticleCardView.swift` (iOS' Grid-Karte).
 * Die List-Variante `ArticleRowView.swift` nutzt natives SwiftUI-`.swipeActions`, das es
 * unter Compose nicht gibt – Material3s `SwipeToDismissBox` unterstützt zudem nur eine
 * Hintergrund-Aktion pro Richtung, weshalb hier eine eigene Mehrfach-Aktionen-Geste nötig ist.
 *
 * Zieht den Inhalt horizontal per [Modifier.draggable] und legt dabei bis zu 3 Trailing-
 * Aktionen bzw. 1 Leading-Aktion frei. Distanz-Konstanten ([SNAP_DIST_DP]/[SHARE_SNAP_DIST_DP]/
 * [CLOSE_DIST_DP], Aktionsbreite 72dp) und Spring-Parameter (dampingRatio 0.8) sind 1:1 aus
 * dem iOS-Original übernommen (dort in `pt` statt `dp`, sonst identisch).
 *
 * [activeSwipeKey]/[swipeKey]: Äquivalent zu iOS' `@Binding var activeSwipeId: Int?` – wird
 * irgendwo im Screen eine andere Karte geöffnet, schließt sich diese hier automatisch
 * (`LaunchedEffect` unten), sodass nie zwei Karten gleichzeitig offen sind.
 */
@Composable
fun SwipeActionsRow(
    swipeKey: Any,
    activeSwipeKey: MutableState<Any?>,
    leadingAction: SwipeAction?,
    trailingActions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidthPx = with(density) { ActionWidth.toPx() }
    val closeDistPx = with(density) { CLOSE_DIST_DP.dp.toPx() }
    val snapDistPx = with(density) { SNAP_DIST_DP.dp.toPx() }
    val shareSnapDistPx = with(density) { SHARE_SNAP_DIST_DP.dp.toPx() }

    val maxTrailingPx = trailingActions.size * actionWidthPx
    val maxLeadingPx = if (leadingAction != null) actionWidthPx else 0f

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dragBase by remember { mutableStateOf(0f) }

    // Schließt diese Karte automatisch, sobald eine andere zur aktiven Karte wird –
    // Äquivalent zu iOS' `.onChange(of: activeSwipeId)` in ArticleCardView.swift.
    LaunchedEffect(activeSwipeKey.value) {
        if (activeSwipeKey.value != swipeKey && offsetX.value != 0f) {
            offsetX.animateTo(0f, animationSpec = SnapSpring)
        }
    }

    Box(modifier = modifier) {
        if (leadingAction != null) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart),
            ) {
                ActionPill(action = leadingAction, width = ActionWidth)
            }
        }
        if (trailingActions.isNotEmpty()) {
            // Reihenfolge wie im iOS-Original (ArticleRowView.swift): Löschen zuerst
            // deklariert = am content-nächsten (linkes Pill in dieser Gruppe), Favorit
            // zuletzt = am Bildschirmrand (rechtes Pill).
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
            ) {
                trailingActions.forEach { action -> ActionPill(action = action, width = ActionWidth) }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newValue = (offsetX.value + delta).coerceIn(-maxTrailingPx, maxLeadingPx)
                        scope.launch { offsetX.snapTo(newValue) }
                        if (newValue != 0f && activeSwipeKey.value != swipeKey) {
                            activeSwipeKey.value = swipeKey
                        }
                    },
                    onDragStarted = { dragBase = offsetX.value },
                    onDragStopped = {
                        val delta = offsetX.value - dragBase
                        val target = when {
                            dragBase < 0f -> if (delta > closeDistPx) 0f else -maxTrailingPx
                            dragBase > 0f -> if (delta < -closeDistPx) 0f else maxLeadingPx
                            else -> when {
                                delta < -snapDistPx -> -maxTrailingPx
                                delta > shareSnapDistPx -> maxLeadingPx
                                else -> 0f
                            }
                        }
                        scope.launch {
                            offsetX.animateTo(target, animationSpec = SnapSpring)
                            if (target == 0f && activeSwipeKey.value == swipeKey) activeSwipeKey.value = null
                        }
                    },
                ),
        ) {
            content()

            // Äquivalent zu iOS' `cardBody.onTapGesture { if clamped == 0 { onTap() } else { bounceClose() } }`:
            // bei offener Swipe-Reihe fängt diese transparente Overlay-Schicht den Tap ab, statt ihn
            // an [content] (und damit an dessen onClick/Navigation) durchzulassen, und federt die
            // Reihe stattdessen kurz zu – exakt das iOS-"bounceClose"-Gefühl statt einer Navigation.
            if (offsetX.value != 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(swipeKey) {
                            detectTapGestures(
                                onTap = {
                                    scope.launch {
                                        offsetX.animateTo(0f, animationSpec = BounceCloseSpring)
                                        if (activeSwipeKey.value == swipeKey) activeSwipeKey.value = null
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun ActionPill(action: SwipeAction, width: Dp) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(action.color)
            .clickable { action.onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = action.icon, contentDescription = action.label, tint = Color.White)
        Text(text = action.label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

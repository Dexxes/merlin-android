package dev.merlin.android.ui.reader

import android.content.Context
import android.text.format.DateUtils
import android.util.Patterns
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import dev.merlin.android.R
import dev.merlin.android.models.Comment
import dev.merlin.android.models.CommentAnchor
import dev.merlin.android.models.CommentApiException
import dev.merlin.android.viewmodel.CommentStore
import kotlinx.coroutines.launch

/**
 * Wozu das Kommentar-Sheet geöffnet wurde: alle Kommentare (beides null), die Threads einer
 * Markierung ([highlightId], [quote] als Fallback, solange der Kanal sie noch nicht kennt) oder
 * eine frische Auswahl ([anchor]), zu der gerade der erste Kommentar entsteht.
 */
data class CommentFocus(
    val highlightId: Int? = null,
    val quote: String? = null,
    val anchor: CommentAnchor? = null,
)

/** Sortierung von „Alle Kommentare“, wie `CommentSortOrder` auf iOS. */
private enum class CommentSortOrder(@StringRes val label: Int) {
    TEXT(R.string.articleReader_comments_sortText),
    NEWEST(R.string.articleReader_comments_sortNewest),
    OLDEST(R.string.articleReader_comments_sortOldest),
}

private const val SORT_PREFS = "merlin_comments"
private const val SORT_KEY = "sortOrder"

/**
 * Kommentare eines Artikels: Threads mit Antworten, neuer Kommentar, Antwort, Bearbeiten (eigene)
 * und Löschen (alle – der Besitzer moderiert seinen Artikel). Liest live aus dem [CommentStore],
 * neue Kommentare von Gästen erscheinen also ohne Neuladen. Äquivalent zu `CommentsSheet.swift`.
 *
 * Mit [CommentFocus.highlightId] zeigt das Sheet zuerst nur die Threads dieser Markierung und
 * kommentiert neu an ihr; „Alle Kommentare“ schaltet um. Mit [CommentFocus.anchor] zeigt es nur die
 * zitierte Stelle und das Eingabefeld; erst der abgeschickte Kommentar legt die Stelle an, danach
 * zeigt das Sheet deren Thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    store: CommentStore,
    state: CommentStore.State,
    initialFocus: CommentFocus,
    onShowInText: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = initialFocus.anchor != null)

    var focusedHighlightId by remember { mutableStateOf(initialFocus.highlightId) }
    var pendingAnchor by remember { mutableStateOf(initialFocus.anchor) }
    var draft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<Comment?>(null) }
    var editing by remember { mutableStateOf<Comment?>(null) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences(SORT_PREFS, Context.MODE_PRIVATE) }
    var sortOrder by remember {
        mutableStateOf(
            runCatching { CommentSortOrder.valueOf(prefs.getString(SORT_KEY, null) ?: "") }.getOrDefault(CommentSortOrder.TEXT),
        )
    }
    val inputFocus = remember { FocusRequester() }

    val isPassageFocused = focusedHighlightId != null || pendingAnchor != null
    val visibleThreads = when {
        pendingAnchor != null -> emptyList()
        focusedHighlightId != null -> state.threads.filter { it.highlightId == focusedHighlightId }
        else -> sortedThreads(state.threads, sortOrder)
    }
    // Über welchen Threads das Zitat steht: in „Alle Kommentare“ über dem ersten zu jeder Stelle.
    val quotedThreadIds = if (focusedHighlightId != null) {
        emptySet()
    } else {
        val seen = HashSet<Int>()
        visibleThreads.filter { t -> t.highlightId == null || seen.add(t.highlightId) }.mapTo(HashSet()) { it.id }
    }
    val focusedQuote = pendingAnchor?.highlightedText
        ?: focusedHighlightId?.let { id ->
            state.highlight(id)?.highlightedText ?: initialFocus.quote.takeIf { id == initialFocus.highlightId }
        }

    LaunchedEffect(Unit) {
        // Frische Auswahl: wer „Kommentieren“ tippt, will gleich schreiben.
        if (pendingAnchor != null) runCatching { inputFocus.requestFocus() }
    }

    fun send() {
        val body = draft.trim()
        if (body.isEmpty() || isSending) return
        isSending = true
        errorMessage = null
        scope.launch {
            try {
                val target = replyTarget
                val anchor = pendingAnchor
                val edited = editing
                when {
                    edited != null -> {
                        store.update(edited.id, body)
                        editing = null
                    }
                    target != null -> {
                        store.create(body, highlightId = null, parentId = target.id)
                        replyTarget = null
                    }
                    anchor != null -> {
                        val saved = store.create(body, highlightId = null, parentId = null, anchor = anchor)
                        // Ab jetzt gibt es die Stelle: ihren Thread zeigen.
                        pendingAnchor = null
                        focusedHighlightId = saved?.highlightId
                    }
                    else -> store.create(body, highlightId = focusedHighlightId, parentId = null)
                }
                draft = ""
            } catch (e: CommentApiException) {
                errorMessage = context.getString(e.userMessage)
            } catch (e: Exception) {
                errorMessage = context.getString(R.string.articleReader_comments_saveFailed)
            }
            isSending = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxHeight(0.92f).imePadding()) {
            // ── Kopfzeile ─────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close)) }
                // An einer Stelle keine Überschrift: das Zitat darunter sagt genug.
                Text(
                    if (isPassageFocused) "" else stringResource(R.string.articleReader_comments_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isPassageFocused) {
                    TextButton(onClick = {
                        focusedHighlightId = null
                        pendingAnchor = null
                        replyTarget = null
                    }) { Text(stringResource(R.string.articleReader_comments_allComments)) }
                } else if (state.threads.size > 1) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.articleReader_comments_sort))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            CommentSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(order.label)) },
                                    leadingIcon = { RadioButton(selected = order == sortOrder, onClick = null) },
                                    onClick = {
                                        sortOrder = order
                                        prefs.edit().putString(SORT_KEY, order.name).apply()
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ── Threads ───────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                if (!focusedQuote.isNullOrEmpty()) {
                    item(key = "quote") { QuoteView(text = focusedQuote) }
                }
                if (visibleThreads.isEmpty() && pendingAnchor == null) {
                    item(key = "empty") {
                        Text(
                            stringResource(
                                if (focusedHighlightId != null) {
                                    R.string.articleReader_comments_noneOnPassage
                                } else {
                                    R.string.articleReader_comments_none
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
                items(visibleThreads, key = { it.id }) { thread ->
                    ThreadCard(
                        thread = thread,
                        showsQuote = thread.id in quotedThreadIds,
                        onShowInText = { id -> onShowInText(id) },
                        onReply = { comment ->
                            editing = null
                            replyTarget = comment
                            runCatching { inputFocus.requestFocus() }
                        },
                        onEdit = { comment ->
                            replyTarget = null
                            editing = comment
                            draft = comment.body
                            runCatching { inputFocus.requestFocus() }
                        },
                        onDelete = { comment -> commentToDelete = comment },
                    )
                }
            }

            // ── Eingabe ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                val contextLine = replyTarget?.let { stringResource(R.string.articleReader_comments_replyTo, it.authorName) }
                    ?: editing?.let { stringResource(R.string.articleReader_comments_edit) }
                if (contextLine != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            contextLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            if (editing != null) draft = ""
                            replyTarget = null
                            editing = null
                        }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel), modifier = Modifier.size(18.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(5000) },
                        placeholder = { Text(stringResource(if (replyTarget != null) R.string.articleReader_comments_writeReply else R.string.articleReader_comments_writeComment)) },
                        maxLines = 6,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(inputFocus),
                    )
                    Spacer(Modifier.width(6.dp))
                    if (isSending) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { send() }, enabled = draft.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.articleReader_comments_send))
                        }
                    }
                }
            }
        }
    }

    commentToDelete?.let { comment ->
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = { Text(stringResource(R.string.articleReader_comments_confirmDelete)) },
            confirmButton = {
                TextButton(onClick = {
                    commentToDelete = null
                    scope.launch {
                        errorMessage = null
                        try {
                            store.delete(comment.id)
                            if (editing?.id == comment.id) {
                                editing = null
                                draft = ""
                            }
                            if (replyTarget?.id == comment.id) replyTarget = null
                        } catch (e: Exception) {
                            errorMessage = context.getString(R.string.articleReader_comments_deleteFailed)
                        }
                    }
                }) { Text(stringResource(R.string.articleReader_comments_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { commentToDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/**
 * „Alle Kommentare“: Reihenfolge im Text oder nach der neuesten Antwort. Threads zur selben Stelle
 * bleiben beieinander, damit das Zitat nur einmal über ihnen steht.
 */
private fun sortedThreads(threads: List<Comment>, order: CommentSortOrder): List<Comment> {
    val groups = mutableListOf<MutableList<Comment>>()
    val indexByHighlight = HashMap<Int, Int>()
    for (thread in threads) {
        val hid = thread.highlightId
        val index = hid?.let { indexByHighlight[it] }
        if (index != null) {
            groups[index].add(thread)
        } else {
            if (hid != null) indexByHighlight[hid] = groups.size
            groups += mutableListOf(thread)
        }
    }
    if (order == CommentSortOrder.TEXT) return groups.flatten()
    val newest = order == CommentSortOrder.NEWEST
    val threadOrder = compareBy<Comment> { lastActivity(it) }.thenBy { it.id }
    val sortedGroups = groups.map { group -> if (newest) group.sortedWith(threadOrder.reversed()) else group.sortedWith(threadOrder) }
    val groupOrder = compareBy<List<Comment>> { g -> g.maxOf { lastActivity(it) } }.thenBy { it.first().id }
    return (if (newest) sortedGroups.sortedWith(groupOrder.reversed()) else sortedGroups.sortedWith(groupOrder)).flatten()
}

/** Zeitpunkt der neuesten Antwort (ohne Antworten: der Kommentar selbst). */
private fun lastActivity(thread: Comment): Long {
    var latest = thread.createdMillis ?: 0L
    thread.replies.orEmpty().filter { !it.deleted }.forEach { reply ->
        reply.createdMillis?.let { if (it > latest) latest = it }
    }
    return latest
}

@Composable
private fun ThreadCard(
    thread: Comment,
    showsQuote: Boolean,
    onShowInText: (Int) -> Unit,
    onReply: (Comment) -> Unit,
    onEdit: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        val quote = thread.quotedText
        if (showsQuote && !quote.isNullOrEmpty()) {
            val highlightId = thread.highlightId
            QuoteView(
                text = quote,
                removed = highlightId == null,
                modifier = if (highlightId != null) Modifier.clickable { onShowInText(highlightId) } else Modifier,
            )
        }
        CommentRow(comment = thread, replyToName = null, onReply = onReply, onEdit = onEdit, onDelete = onDelete)
        thread.replies.orEmpty().forEach { reply ->
            // Name dessen, dem geantwortet wurde – nur wenn es nicht die Wurzel ist (die ist ohnehin gemeint).
            val replyTo = reply.replyToId
                ?.takeIf { it != thread.id }
                ?.let { target -> thread.replies?.firstOrNull { it.id == target }?.authorName }
            CommentRow(
                comment = reply,
                replyToName = replyTo,
                onReply = onReply,
                onEdit = onEdit,
                onDelete = onDelete,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
    }
}

/** Zitierte Textstelle über einem Thread. */
@Composable
private fun QuoteView(text: String, removed: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color(0xFFF59E0B)))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                maxLines = 4,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (removed) {
            Text(
                stringResource(R.string.articleReader_comments_highlightRemoved),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Ein Kommentar mit Name, Zeit und Aktionen. Gelöschte Wurzeln bleiben als Platzhalter stehen,
 * damit ihre Antworten lesbar bleiben.
 */
@Composable
private fun CommentRow(
    comment: Comment,
    replyToName: String?,
    onReply: (Comment) -> Unit,
    onEdit: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (comment.deleted) {
        Text(
            stringResource(R.string.articleReader_comments_deleted),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    // Verfasser-Farbe vom Server (Besitzer orange, Gäste je eigene).
    val authorColor = comment.authorColor
        ?.let { runCatching { Color(it.toColorInt()) }.getOrNull() }
        ?: MaterialTheme.colorScheme.outline
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(authorColor))
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).background(authorColor, CircleShape))
                Text(comment.authorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (comment.isOwner) {
                    Text(
                        stringResource(R.string.articleReader_comments_you),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                replyToName?.let {
                    Text("→ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                comment.createdMillis?.let { millis ->
                    Text(
                        DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (comment.edited) {
                    Text(stringResource(R.string.articleReader_comments_edited), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(comment) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.articleReader_comments_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            SelectionContainer {
                Text(linkified(comment.body), style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 2.dp)) {
                ActionText(stringResource(R.string.articleReader_comments_reply)) { onReply(comment) }
                if (comment.isOwner) ActionText(stringResource(R.string.articleReader_comments_edit)) { onEdit(comment) }
            }
        }
    }
}

@Composable
private fun ActionText(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

/**
 * Web-Adressen in Kommentaren als antippbare Links (nur http und https; „www.heise.de“ wird zu
 * einem https-Link). Wie `CommentLinks` auf iOS – der Text selbst bleibt reiner Text.
 */
private fun linkified(text: String): AnnotatedString = buildAnnotatedString {
    val matcher = Patterns.WEB_URL.matcher(text)
    var last = 0
    val style = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    while (matcher.find()) {
        val raw = matcher.group() ?: continue
        val start = matcher.start()
        if (start < last) continue
        val scheme = raw.substringBefore("://", "").lowercase()
        val url = when {
            scheme == "http" || scheme == "https" -> raw
            scheme.isEmpty() && raw.startsWith("www.", ignoreCase = true) -> "https://$raw"
            else -> null
        } ?: continue
        append(text.substring(last, start))
        withLink(LinkAnnotation.Url(url, style)) { append(raw) }
        last = matcher.end()
    }
    append(text.substring(last))
}

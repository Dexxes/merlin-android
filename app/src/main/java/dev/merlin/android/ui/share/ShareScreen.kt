package dev.merlin.android.ui.share

import android.app.Activity
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.merlin.android.R
import dev.merlin.android.models.Tag
import dev.merlin.android.models.TagTree
import dev.merlin.android.share.CompressionOption
import dev.merlin.android.share.ImageCompression
import dev.merlin.android.share.SharedFile
import dev.merlin.android.ui.screens.OnboardingScreen
import dev.merlin.android.viewmodel.ShareViewModel
import dev.merlin.android.viewmodel.ShareViewModel.Mode
import dev.merlin.android.viewmodel.SharedContent
import kotlinx.coroutines.delay

/** Breiteste Ausdehnung des Sheets; im Querformat mittig statt über die ganze Breite (wie iOS). */
private val MaxSheetWidth = 540.dp
private val TagRowHeight = 36.dp
/** Sichtbare Tag-Zeilen, bevor die Liste scrollt. */
private const val MAX_VISIBLE_TAG_ROWS = 5
private val CompressionTileHeight = 140.dp
private val ErrorRed = Color(0xFFFF3B30)
private val RateLimitOrange = Color(0xFFFF9500)

/** Fehler bleiben stehen, bis man tippt oder diese Zeit vergeht (wie iOS). */
private const val ERROR_AUTO_CLOSE_MS = 10_000L
private const val RATE_LIMIT_RETRY_MS = 2_500L

/**
 * Nachbau der Sheet-Oberfläche aus `ShareViewController.swift`: Sheet am unteren Rand
 * mit oben abgerundeten Ecken über der abgedunkelten teilenden App, Kopfzeile mit
 * Merlin-Logo, „Merlin“ und Untertitel, darunter je nach Modus Ladekreis/Status,
 * Staging (Adresse bzw. Dateien, Komprimierungs-Kacheln, Tag-Baum, „Neuer Tag“) oder
 * Fehler. Der Speichern-Knopf sitzt unter dem scrollenden Inhalt und bleibt dadurch
 * auch mit Tastatur und im Querformat sichtbar.
 */
@Composable
fun ShareScreen(
    sharedContent: SharedContent,
    onClose: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsState()
    val pendingUrl by viewModel.pendingUrl.collectAsState()
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val selectedTagIds by viewModel.selectedTagIds.collectAsState()
    val hasPhotos by viewModel.hasPhotos.collectAsState()
    val selectedCompression by viewModel.selectedCompression.collectAsState()
    val compressionOptions by viewModel.compressionOptions.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val focusManager = LocalFocusManager.current
    var newTagText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(finished) { if (finished) onClose() }
    // Sobald wir im Extracting-Modus sind (angemeldet oder gerade frisch eingeloggt),
    // einmalig den geteilten Inhalt auswerten.
    LaunchedEffect(mode) {
        when (mode) {
            Mode.EXTRACTING -> viewModel.extractContent(sharedContent)
            Mode.ERROR -> {
                delay(ERROR_AUTO_CLOSE_MS)
                viewModel.finish()
            }
            Mode.RATE_LIMITED -> {
                delay(RATE_LIMIT_RETRY_MS)
                viewModel.backToStaging()
            }
            else -> {}
        }
    }

    // Edge-to-edge (targetSdk 35+, siehe ShareActivity): Die Abdunkelung reicht bis unter die
    // Statusleiste, das Sheet bis unter die Navigationsleiste. Den Inhalt halten Inset-Paddings
    // aus den Leisten und aus der Tastatur heraus (adjustResize verkleinert das Fenster hier nicht
    // mehr). Helle Statusleisten-Icons auf der dunklen Abdunkelung.
    val view = LocalView.current
    val sheetColor = MaterialTheme.colorScheme.surface
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    val isError = mode == Mode.ERROR
    // Antippen: im Fehlerfall schließen (wie iOS, überall), sonst nur die Tastatur ausblenden.
    val onBackgroundTap = { if (isError) viewModel.finish() else focusManager.clearFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBackgroundTap)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = MaxSheetWidth)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBackgroundTap),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = sheetColor,
        ) {
          Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
            if (mode == Mode.ONBOARDING) {
                OnboardingScreen(onLoginSuccess = { viewModel.onLoginSuccess() })
            } else Column {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Header(mode = mode, isFiles = pendingFiles.isNotEmpty())
                    Spacer(Modifier.height(20.dp))
                    when (mode) {
                        Mode.STAGING -> StagingContent(
                            url = pendingUrl,
                            files = pendingFiles,
                            hasPhotos = hasPhotos,
                            selectedCompression = selectedCompression,
                            compressionOptions = compressionOptions,
                            onSelectCompression = viewModel::selectCompression,
                            availableTags = availableTags,
                            selectedTagIds = selectedTagIds,
                            onToggleTag = viewModel::toggleTag,
                            newTagText = newTagText,
                            onNewTagTextChange = { newTagText = it },
                            onDone = { focusManager.clearFocus(); viewModel.confirmSave(newTagText) },
                        )
                        Mode.EXTRACTING, Mode.SAVING -> StatusContent(message = null, showSpinner = true)
                        Mode.UPLOADING -> UploadingContent(uploadProgress)
                        Mode.ERROR -> StatusContent(
                            message = (statusMessage ?: stringResource(R.string.api_error_unknown)) + "\n\n" + stringResource(R.string.share_result_tapToClose),
                            showSpinner = false,
                        )
                        Mode.RATE_LIMITED -> StatusContent(message = statusMessage, showSpinner = false)
                        Mode.ONBOARDING -> {}
                    }
                }
                if (mode == Mode.STAGING) {
                    Button(
                        onClick = { focusManager.clearFocus(); viewModel.confirmSave(newTagText) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)
                            .height(56.dp),
                    ) {
                        Text(stringResource(R.string.share_staging_saveButton), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
          }
        }
    }
}

/** Logo (bzw. Fehler-/Rate-Limit-Symbol), „Merlin“ und der Untertitel des Modus. */
@Composable
private fun Header(mode: Mode, isFiles: Boolean) {
    val subtitle = when (mode) {
        Mode.EXTRACTING -> stringResource(R.string.share_extracting_subtitle)
        Mode.STAGING -> stringResource(if (isFiles) R.string.share_files_subtitle else R.string.share_staging_subtitle)
        Mode.SAVING -> stringResource(R.string.share_saving_subtitle)
        Mode.UPLOADING -> stringResource(R.string.share_files_uploadingSubtitle)
        Mode.ERROR -> stringResource(R.string.share_result_errorTitle)
        Mode.RATE_LIMITED -> stringResource(R.string.share_rateLimited_subtitle)
        Mode.ONBOARDING -> stringResource(R.string.share_settings_subtitle)
    }
    Row(verticalAlignment = Alignment.Top) {
        when (mode) {
            Mode.ERROR -> Icon(Icons.Filled.Cancel, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(40.dp))
            Mode.RATE_LIMITED -> Icon(Icons.Filled.Schedule, contentDescription = null, tint = RateLimitOrange, modifier = Modifier.size(40.dp))
            else -> Image(
                bitmap = ImageBitmap.imageResource(R.drawable.merlin_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.app_name), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Ladekreis und/oder Statustext, mittig unter der Kopfzeile. */
@Composable
private fun StatusContent(message: String?, showSpinner: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 8.dp),
    ) {
        if (showSpinner) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
        }
        if (!message.isNullOrEmpty()) {
            Text(
                message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UploadingContent(progress: ShareViewModel.UploadProgress?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        val text = progress?.let {
            val percent = java.text.NumberFormat.getPercentInstance().format(it.fraction.coerceIn(0.0, 1.0))
            stringResource(R.string.share_files_progress, it.index + 1, it.total, percent)
        } ?: stringResource(R.string.share_files_preparing)
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { (progress?.fraction ?: 0.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StagingContent(
    url: String,
    files: List<SharedFile>,
    hasPhotos: Boolean,
    selectedCompression: ImageCompression,
    compressionOptions: Map<ImageCompression, CompressionOption>,
    onSelectCompression: (ImageCompression) -> Unit,
    availableTags: List<Tag>?,
    selectedTagIds: Set<Int>,
    onToggleTag: (Int) -> Unit,
    newTagText: String,
    onNewTagTextChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Adresse (gekürzt wie iOS) bzw. Name oder Anzahl der Dateien plus Gesamtgröße in
        // der gewählten Stufe (die Originalgröße, bis die Stufe berechnet ist).
        val preview = if (files.isEmpty()) {
            if (url.length > 60) url.take(57) + "…" else url
        } else {
            val shown = if (hasPhotos) compressionOptions[selectedCompression]?.files ?: files else files
            val size = Formatter.formatShortFileSize(context, shown.sumOf { it.size })
            val fileCount = pluralStringResource(R.plurals.share_files_count, shown.size, shown.size)
            stringResource(R.string.share_files_summary, shown.singleOrNull()?.name ?: fileCount, size)
        }
        Text(
            preview,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (hasPhotos) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageCompression.entries.forEach { level ->
                    CompressionTile(
                        level = level,
                        option = compressionOptions[level],
                        selected = level == selectedCompression,
                        onClick = { onSelectCompression(level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        TagTreeList(tags = availableTags, selectedTagIds = selectedTagIds, onToggleTag = onToggleTag)

        OutlinedTextField(
            value = newTagText,
            onValueChange = onNewTagTextChange,
            placeholder = { Text(stringResource(R.string.share_staging_newTagPlaceholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            // Wie iOS: „Fertig“ auf der Tastatur speichert.
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Bestehende Tags als eingerückter Baum (Unter-Tags unter ihrem Eltern-Tag), Zeilen mit
 * Kreis/Häkchen wie in der iOS-Share-Extension. Wächst bis [MAX_VISIBLE_TAG_ROWS] Zeilen
 * und scrollt darüber hinaus.
 */
@Composable
private fun TagTreeList(tags: List<Tag>?, selectedTagIds: Set<Int>, onToggleTag: (Int) -> Unit) {
    when {
        tags == null -> HintRow(stringResource(R.string.share_staging_loadingTags))
        tags.isEmpty() -> HintRow(stringResource(R.string.share_staging_noTags))
        else -> {
            val rows = remember(tags) { TagTree(tags).rows() }
            val visible = rows.size.coerceIn(1, MAX_VISIBLE_TAG_ROWS)
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = TagRowHeight * visible + 2.dp * (visible - 1))
                    .verticalScroll(rememberScrollState()),
            ) {
                rows.forEach { row ->
                    TagRow(
                        name = row.tag.name,
                        depth = row.depth,
                        selected = row.tag.id in selectedTagIds,
                        onClick = { onToggleTag(row.tag.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HintRow(text: String) {
    Box(Modifier.fillMaxWidth().height(TagRowHeight), contentAlignment = Alignment.CenterStart) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TagRow(name: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(TagRowHeight)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 4.dp + 22.dp * depth, end = 4.dp),
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(name, color = tint, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Eine Wahl in der Komprimierungs-Zeile, Äquivalent zu `CompressionTile.swift`:
 * Vorschau-Ausschnitt, Titel und resultierende Größe; Ladekreis, bis die Stufe
 * berechnet ist.
 */
@Composable
private fun CompressionTile(
    level: ImageCompression,
    option: CompressionOption?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    val title = stringResource(level.titleRes)
    val sizeText = option?.let { Formatter.formatShortFileSize(context, it.totalSize) } ?: "…"
    val preview = remember(option) { option?.preview?.asImageBitmap() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(CompressionTileHeight)
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.08f) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = "$title, $sizeText"
            }
            .padding(6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (option == null) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            sizeText,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Ziffern gleicher Breite, damit die Größen beim Berechnen nicht springen.
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
    }
}

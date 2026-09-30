package dev.ironjanowar.fretboard.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.FretboardViewModel
import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.links.ShareConfig
import dev.ironjanowar.fretboard.links.pastedText
import dev.ironjanowar.fretboard.session.SessionView
import dev.ironjanowar.fretboard.session.lastFret
import dev.ironjanowar.fretboard.ui.analyzer.AnalyzerScreen
import dev.ironjanowar.fretboard.ui.common.PillChip
import dev.ironjanowar.fretboard.ui.controls.InstrumentPicker
import dev.ironjanowar.fretboard.ui.keys.KeySheet
import dev.ironjanowar.fretboard.ui.progressions.ProgressionSheet
import dev.ironjanowar.fretboard.ui.tuning.TuningSheet
import dev.ironjanowar.fretboard.ui.visualizer.VisualizerScreen

/** The application's dark theme: the frozen chord palette over a near-black UI. */
private val FretboardTheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF10131A),
    secondary = Color(0xFFFF8A65),
    onSecondary = Color(0xFF10131A),
    background = Color(0xFF11151A),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF1A1F26),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF2A313A),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF546E7A),
    error = Color(0xFFFF8A80),
)

/**
 * The display text of a tuning state: its reference and the exact pitches, in
 * physical string order.
 *
 * The generated binding carries the pitches as a signed `ByteArray` (the Rust
 * `Vec<u8>`), so every byte must be read as unsigned: a pitch of 127 would
 * otherwise show as -1. This is the only transformation the application does,
 * and it is pinned by a unit test.
 */
fun describeTuning(reference: String, pitches: ByteArray): String =
    "$reference ${pitches.map { byte -> byte.toInt() and 0xFF }.joinToString("-")}"

/** The two views of the same session, with the engine's own tab values. */
private val TAB_TITLES: List<Pair<TabDto, String>> = listOf(
    TabDto.VISUALIZER to "Visualizer",
    TabDto.ANALYZER to "Analyzer",
)

/**
 * The whole screen, drawn from the session the [fretboard] holds.
 *
 * The screen takes the session owner as a parameter and keeps no session state
 * of its own: everything it draws is read from the holder, and every control
 * sends that holder an intention. That is what makes a rotation cheap — the
 * composition comes back and finds the session where the last one left it,
 * rather than starting from `null`.
 */
@Composable
fun FretboardApp(fretboard: FretboardViewModel) {
    MaterialTheme(colorScheme = FretboardTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            FretboardScreen(fretboard)
        }
    }
}

@Composable
private fun FretboardScreen(fretboard: FretboardViewModel) {
    // The session is the holder's, never the composition's. Nothing below is a
    // `remember`: a configuration change re-runs this function and reads the
    // same value back.
    val session = fretboard.state
    val view = session.view

    // A19: the explicit paste reads the clipboard *on the tap below* and nowhere else —
    // the application never scrapes it, and nothing is read at startup.
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Idempotent on purpose: this runs again after every rotation, and a session
    // that is already held must come back rather than be asked for again.
    LaunchedEffect(Unit) { fretboard.start() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The window is edge to edge — mandatory from `targetSdk 35` on — so the
            // system bars' insets are applied to the *viewport*, not to the content:
            // the header is never drawn under the clock, the bottom of the screen is
            // never under the navigation bar, and scrolling cannot slide content
            // behind either. Without this the two sheet buttons sat behind the
            // status bar on a real device.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header(view, session.busy)
        session.error?.let { message ->
            ErrorBanner(
                message = message,
                onRetry = { fretboard.reload() },
                enabled = !session.busy,
            )
        }

        val current = view
        if (current == null) {
            LoadingBlock()
            return@Column
        }

        InstrumentPicker(
            instruments = current.instruments,
            selected = current.instrumentId(),
            enabled = !session.busy,
            onSelect = { definition -> fretboard.selectInstrument(definition) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TAB_TITLES.forEach { (candidate, title) ->
                PillChip(
                    text = title,
                    selected = candidate == current.state.tab,
                    enabled = !session.busy,
                    onClick = {
                        fretboard.applyEvent(PageEventDto.SetTab(candidate))
                    },
                    modifier = Modifier.testTag("tab-${candidate.name.lowercase()}"),
                )
            }
            Spacer(Modifier.weight(1f))
            // No ghost tuning control on the keyboard: the engine has no tuning
            // for it and its modal does not exist, so the control is not drawn at
            // all — the note below says why instead.
            if (current.instrumentId() != InstrumentDto.PIANO) {
                TextButton(
                    onClick = { fretboard.openTuning() },
                    enabled = !session.busy,
                    modifier = Modifier.testTag("open-tuning"),
                ) {
                    Text("Tuning")
                }
            }

            // A19: a pasted link is a delivery the user made by hand, so it takes the
            // same path and the same rules as a shared one — there is no second, laxer
            // way into the engine, and a refused paste says why instead of doing nothing.
            TextButton(
                onClick = { fretboard.deliver(pastedText(clipboard.getText()?.text)) },
                enabled = !session.busy,
                modifier = Modifier.testTag("paste-link"),
            ) {
                Text("Pegar enlace")
            }

            // A20: the link this session is, sent through the system chooser. The engine writes
            // the query and `ShareLauncher` joins it to the approved origin; when sharing is off
            // the control is disabled and the note below says why, so there is never a control
            // that quietly does nothing.
            TextButton(
                onClick = {
                    fretboard.shareLink { url ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(send, "Compartir enlace"))
                    }
                },
                enabled = !session.busy && ShareConfig.approvedBase != null,
                modifier = Modifier.testTag("share-link"),
            ) {
                Text("Compartir")
            }
        }

        // A20: sharing is off while no web origin is approved, and it says so where a
        // share control would be — never a control that could not work. The decision and
        // its sentence live in `ShareConfig`, so approving an origin removes this note
        // with the reason, and the tests for both are beside them.
        ShareConfig.unavailableReason?.let { reason ->
            NotApplicable(
                title = "Sharing is not available in this build",
                detail = reason,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // The keyboard has no tuning: said as not applicable, never as a control
        // that could not work.
        if (current.instrumentId() == InstrumentDto.PIANO) {
            NotApplicable(
                title = "Tuning is not applicable to the keyboard",
                detail =
                    "The engine carries no tuning for the piano, so there is nothing to " +
                        "edit. Switch to a fretted instrument for the tuning sheet.",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when (current.state.tab) {
            TabDto.VISUALIZER -> VisualizerScreen(
                view = current,
                root = session.root,
                quality = session.quality,
                onRootChange = { root -> fretboard.setRoot(root) },
                onQualityChange = { quality -> fretboard.setQuality(quality) },
                onAdd = {
                    fretboard.applyEvent(
                        PageEventDto.AddChord(ChordDto(session.root, session.quality)),
                    )
                },
                onRemove = { index ->
                    fretboard.applyEvent(PageEventDto.RemoveChord(index.toULong()))
                },
                onToggleHighlight = { index ->
                    fretboard.applyEvent(PageEventDto.HighlightChord(index.toULong()))
                },
                onClearAll = { fretboard.applyEvent(PageEventDto.ClearAllChords) },
                keyRows = fretboard.evaluationState.keys,
                multiKeyGroups = fretboard.evaluationState.multiKeys,
                keyExpanded = fretboard.evaluationState.expanded,
                onToggleKeyExpansion = { fretboard.toggleKeyExpansion() },
                onApplySuggestion = { suggestion -> fretboard.applySuggestedKey(suggestion) },
                onRetryKeys = { fretboard.retryKeyEvaluation() },
                onOpenKey = { fretboard.openKey() },
                onOpenProgression = { fretboard.openProgression() },
                enabled = !session.busy,
            )

            TabDto.ANALYZER -> AnalyzerScreen(
                view = current,
                onTapPosition = { position: PositionDto ->
                    fretboard.applyEvent(PageEventDto.ToggleNote(position))
                },
                onTapPitch = { pitch: Int ->
                    fretboard.applyEvent(PageEventDto.TogglePianoKey(pitch.toUByte()))
                },
                onClearSelection = { fretboard.applyEvent(PageEventDto.ClearSelection) },
                enabled = !session.busy,
            )
        }
    }

    val open = session.draft
    if (open != null) {
        TuningSheet(
            draft = open,
            stringCount = currentStringCount(view, open.instrument),
            presets = session.presets,
            onSelectPreset = { name -> fretboard.selectPreset(name) },
            onChangeString = { stringIndex, note -> fretboard.changeString(stringIndex, note) },
            onApply = { fretboard.applyDraft() },
            onCancel = { fretboard.cancelDraft() },
            enabled = !session.busy,
        )
    }

    // The A14 key and progression sheets: drafts of their own, each holding the
    // engine's preview; neither touches the committed page until Apply sends the
    // engine its own commit event.
    val keyDraft = session.keyDraft
    if (keyDraft != null) {
        KeySheet(
            draft = keyDraft,
            previewPending = session.keyPreviewPending,
            onTonicChange = { tonic -> fretboard.changeKeyTonic(tonic) },
            onScaleChange = { scale -> fretboard.changeKeyScale(scale) },
            onModeChange = { mode -> fretboard.changeKeyMode(mode) },
            onApply = { fretboard.applyKeyDraft() },
            onCancel = { fretboard.cancelKeyDraft() },
            enabled = !session.busy,
        )
    }

    val progressionDraft = session.progressionDraft
    if (progressionDraft != null) {
        ProgressionSheet(
            draft = progressionDraft,
            catalog = session.progressionCatalog,
            previewPending = session.progressionPreviewPending,
            onTonicChange = { tonic -> fretboard.changeProgressionTonic(tonic) },
            onSelectProgression = { id -> fretboard.selectProgression(id) },
            onApply = { fretboard.applyProgressionDraft() },
            onCancel = { fretboard.cancelProgressionDraft() },
            enabled = !session.busy,
        )
    }
}

/** The string count of the instrument a draft belongs to, from the engine's catalog. */
private fun currentStringCount(view: SessionView?, instrument: InstrumentDto): Int {
    val definition = view?.instruments?.firstOrNull { it.instrument == instrument }
    return definition?.strings?.toInt() ?: 0
}

/** The compact title bar: the app name, the instrument and its tuning. */
@Composable
private fun Header(view: SessionView?, busy: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Fretboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(16.dp)
                        .width(16.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        view?.let { current ->
            val instrument = current.state.instrument
            val name = current.instrumentName()
            val tuning = when (instrument) {
                is InstrumentStateDto.Fretted ->
                    describeTuning(instrument.tuning.reference, instrument.tuning.pitches)
                is InstrumentStateDto.Piano -> "Keyboard"
            }
            Text(
                text = "$name — $tuning",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("session-caption"),
            )
            val lastFret = current.lastFret()
            if (lastFret != null) {
                Text(
                    text = "$lastFret frets, ${current.state.chords.size} active chords",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** A visible failure: an explicit English state, never a plausible fake answer. */
@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, enabled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            // This banner covers a refused call, a failed call and a failed write,
            // so it says what happened to the request rather than borrowing the
            // "not built yet" wording of a pending capability.
            text = "The request could not be completed",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("engine-error"),
        )
        OutlinedButton(onClick = onRetry, enabled = enabled) {
            Text("Try again")
        }
    }
}

/** A centred slot used while the first engine answer is still on its way. */
@Composable
private fun LoadingBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .testTag("engine-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = "Asking the engine…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The instrument id of the current state, as the picker needs it. */
private fun SessionView.instrumentId(): InstrumentDto = when (val instrument = state.instrument) {
    is InstrumentStateDto.Fretted -> instrument.instrument
    is InstrumentStateDto.Piano -> InstrumentDto.PIANO
}

/** The engine's display name for the current instrument, or its wire id. */
private fun SessionView.instrumentName(): String {
    val id = instrumentId()
    return instruments.firstOrNull { it.instrument == id }?.name ?: id.name
}

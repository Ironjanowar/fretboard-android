package dev.ironjanowar.fretboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.session.SessionLoad
import dev.ironjanowar.fretboard.session.SessionView
import dev.ironjanowar.fretboard.session.TuningDraft
import dev.ironjanowar.fretboard.session.applyEvent
import dev.ironjanowar.fretboard.session.applyTuningDraft
import dev.ironjanowar.fretboard.session.changeTuningString
import dev.ironjanowar.fretboard.session.engineFailure
import dev.ironjanowar.fretboard.session.lastFret
import dev.ironjanowar.fretboard.session.openTuningDraft
import dev.ironjanowar.fretboard.session.selectInstrument
import dev.ironjanowar.fretboard.session.startSession
import dev.ironjanowar.fretboard.ui.analyzer.AnalyzerScreen
import dev.ironjanowar.fretboard.ui.common.PillChip
import dev.ironjanowar.fretboard.ui.controls.InstrumentPicker
import dev.ironjanowar.fretboard.ui.tuning.TuningSheet
import dev.ironjanowar.fretboard.ui.visualizer.VisualizerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** The whole screen: the engine's catalogs, the controls and the two tabs. */
@Composable
fun FretboardApp() {
    MaterialTheme(colorScheme = FretboardTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            FretboardScreen()
        }
    }
}

@Composable
private fun FretboardScreen() {
    // The last valid session is kept while an action fails, so a rejected action
    // never wipes what the user already has on screen: the error is shown beside
    // the state, as the design's transition table requires.
    var view by remember { mutableStateOf<SessionView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    // The tuning sheet is open exactly while a draft exists; the draft itself is
    // the engine's value plus the engine's own reading of it.
    var draft by remember { mutableStateOf<TuningDraft?>(null) }
    var root by remember { mutableStateOf("C") }
    var quality by remember { mutableStateOf("major") }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> SessionLoad) {
        busy = true
        scope.launch {
            when (val result = block()) {
                is SessionLoad.Ready -> {
                    view = result.view
                    error = null
                }
                is SessionLoad.Failed -> error = result.reason
            }
            busy = false
        }
    }

    /** One engine call whose answer is not a whole session, on the worker thread. */
    fun <T> withEngine(block: suspend () -> T, onReady: (T) -> Unit) {
        busy = true
        scope.launch {
            try {
                val answer = withContext(Dispatchers.Default) { block() }
                error = null
                onReady(answer)
            } catch (failure: Throwable) {
                error = engineFailure(failure)
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { run { startSession() } }

    // The default quality must exist in the engine's own catalog: if the engine
    // starts grouping differently, the first quality it sends is used instead of
    // a client guess.
    val loaded = view
    LaunchedEffect(loaded?.qualityGroups) {
        val groups = loaded?.qualityGroups ?: return@LaunchedEffect
        val known = groups.flatMap { group -> group.qualities.map { it.quality } }
        if (quality !in known) {
            known.firstOrNull()?.let { quality = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header(view, busy)
        error?.let { message ->
            ErrorBanner(message = message, onRetry = { run { startSession() } }, enabled = !busy)
        }

        val current = view
        if (current == null) {
            LoadingBlock()
            return@Column
        }

        InstrumentPicker(
            instruments = current.instruments,
            selected = current.instrumentId(),
            enabled = !busy,
            onSelect = { definition ->
                // A change of instrument invalidates an open draft: it was opened
                // for the instrument that was current then.
                draft = null
                run { selectInstrument(current, definition) }
            },
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
                    enabled = !busy,
                    onClick = { run { applyEvent(current, PageEventDto.SetTab(candidate)) } },
                    modifier = Modifier.testTag("tab-${candidate.name.lowercase()}"),
                )
            }
            Spacer(Modifier.weight(1f))
            // No ghost tuning control on the keyboard: the engine has no tuning
            // for it and its modal does not exist.
            if (current.instrumentId() != InstrumentDto.PIANO) {
                TextButton(
                    onClick = {
                        withEngine({ openTuningDraft(current) }) { opened -> draft = opened }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag("open-tuning"),
                ) {
                    Text("Tuning")
                }
            }
        }

        when (current.state.tab) {
            TabDto.VISUALIZER -> VisualizerScreen(
                view = current,
                root = root,
                quality = quality,
                onRootChange = { root = it },
                onQualityChange = { quality = it },
                onAdd = {
                    run { applyEvent(current, PageEventDto.AddChord(ChordDto(root, quality))) }
                },
                onRemove = { index ->
                    run { applyEvent(current, PageEventDto.RemoveChord(index.toULong())) }
                },
                onToggleHighlight = { index ->
                    run { applyEvent(current, PageEventDto.HighlightChord(index.toULong())) }
                },
                onClearAll = { run { applyEvent(current, PageEventDto.ClearAllChords) } },
                enabled = !busy,
            )

            TabDto.ANALYZER -> AnalyzerScreen(
                view = current,
                onTapPosition = { position: PositionDto ->
                    run { applyEvent(current, PageEventDto.ToggleNote(position)) }
                },
                onClearSelection = { run { applyEvent(current, PageEventDto.ClearSelection) } },
                enabled = !busy,
            )
        }
    }

    val open = draft
    if (open != null) {
        TuningSheet(
            draft = open,
            stringCount = currentStringCount(view, open.instrument),
            onChangeString = { stringIndex, note ->
                withEngine({ changeTuningString(open, stringIndex, note) }) { edited ->
                    draft = edited
                }
            },
            onApply = {
                val page = view
                if (page != null) {
                    busy = true
                    scope.launch {
                        when (val result = applyTuningDraft(page, open)) {
                            is SessionLoad.Ready -> {
                                view = result.view
                                error = null
                                draft = null
                            }
                            is SessionLoad.Failed -> {
                                error = result.reason
                                // A stale draft cannot be committed: it is dropped
                                // so the sheet cannot be applied twice.
                                draft = null
                            }
                        }
                        busy = false
                    }
                }
            },
            onCancel = { draft = null },
            enabled = !busy,
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
            text = "Not available",
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

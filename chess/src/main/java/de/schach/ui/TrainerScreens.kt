package de.schach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schach.engine.Piece
import de.schach.training.TrainingData

// ==================================================================== Übersicht

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerHomeScreen(vm: TrainerViewModel, onBack: () -> Unit, onOpen: (ExerciseKind, String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Eröffnungen", "Mattaufgaben", "Mattführung")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schachtrainer") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    0 -> {
                        item { SectionHint("Lerne bekannte Eröffnungen: erst Zug für Zug mit Erklärung ansehen, dann selbst üben.") }
                        items(TrainingData.openings) { o ->
                            TrainerItem(
                                title = o.name,
                                subtitle = (if (o.side == Piece.WHITE) "Du spielst Weiß" else "Du spielst Schwarz") + " · ${o.moves.size} Halbzüge",
                                text = o.intro,
                                done = TrainerViewModel.key(ExerciseKind.OPENING, o.id) in s.done,
                            ) { onOpen(ExerciseKind.OPENING, o.id) }
                        }
                    }
                    1 -> {
                        item { SectionHint("Typische Mattbilder mit verschiedenen Figuren. Finde den Zug, der mattsetzt!") }
                        items(TrainingData.puzzles) { p ->
                            TrainerItem(
                                title = p.title,
                                subtitle = "Matt in ${p.mateIn} · ${p.pieces}",
                                text = null,
                                done = TrainerViewModel.key(ExerciseKind.PUZZLE, p.id) in s.done,
                            ) { onOpen(ExerciseKind.PUZZLE, p.id) }
                        }
                    }
                    else -> {
                        item { SectionHint("Lerne, mit wenig Material mattzusetzen. Der Computer verteidigt sich so gut er kann.") }
                        items(TrainingData.mateLessons) { l ->
                            TrainerItem(
                                title = l.title,
                                subtitle = "Schwierigkeit: ${l.difficulty}",
                                text = l.intro,
                                done = TrainerViewModel.key(ExerciseKind.LESSON, l.id) in s.done,
                            ) { onOpen(ExerciseKind.LESSON, l.id) }
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TrainerItem(title: String, subtitle: String, text: String?, done: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (text != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (done) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.CheckCircle, "Geschafft", tint = Color(0xFF2E7D32))
            }
        }
    }
}

// ==================================================================== Übung

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerExerciseScreen(vm: TrainerViewModel, kind: ExerciseKind, id: String, onBack: () -> Unit) {
    LaunchedEffect(kind, id) { if (!vm.isLoaded(kind, id)) vm.start(kind, id) }
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = vm::restart) { Icon(Icons.Filled.Refresh, "Neu starten") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            if (s.kind == ExerciseKind.OPENING) {
                val options = listOf("Ansehen", "Üben")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    options.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = s.practice == (i == 1),
                            onClick = { vm.startOpening(s.id, practice = i == 1) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            icon = {},
                        ) { Text(label) }
                    }
                }
                LinearProgressIndicator(
                    progress = { if (s.total == 0) 0f else s.step.toFloat() / s.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    s.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            ChessBoard(s.board, vm::onSquareTap)

            s.message?.let { MessageBar(it, s.messageType, s.busy) }
            s.note?.let { n ->
                val last = s.sans.lastOrNull()
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Icon(Icons.Filled.School, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            (if (last != null) "${germanSan(last)}: " else "") + n,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // Bedienelemente
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                when {
                    s.kind == ExerciseKind.OPENING && !s.practice -> {
                        ActionButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", enabled = s.step > 0, onClick = vm::openingPrev)
                        ActionButton(Icons.AutoMirrored.Filled.ArrowForward, "Weiter", enabled = s.step < s.total, onClick = vm::openingNext)
                    }
                    else -> {
                        if (s.kind == ExerciseKind.LESSON) {
                            ActionButton(Icons.AutoMirrored.Filled.Undo, "Zurück", enabled = s.canUndo && !s.busy, onClick = vm::undo)
                        }
                        ActionButton(
                            Icons.Filled.Lightbulb, "Tipp",
                            enabled = !s.busy && !s.solved && !s.failed && s.sideToMove == s.userColor,
                            onClick = vm::hint,
                        )
                        if (s.solved || s.failed) ActionButton(Icons.Filled.Refresh, "Nochmal", onClick = vm::restart)
                    }
                }
            }

            if (s.kind == ExerciseKind.LESSON && s.tips.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("So geht's", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        s.tips.forEachIndexed { i, t -> Text("${i + 1}. $t", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (s.kind == ExerciseKind.OPENING) {
                Text(
                    s.intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            MoveList(s.sans)
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    s.promotion?.let {
        PromotionDialog(white = s.userColor == Piece.WHITE, onPick = vm::promote, onDismiss = vm::cancelPromotion)
    }
}

@Composable
private fun MessageBar(text: String, type: MessageType, busy: Boolean) {
    val (bg, fg) = when (type) {
        MessageType.GOOD -> Color(0xFFD7F0D8) to Color(0xFF1B5E20)
        MessageType.BAD -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        MessageType.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(color = bg, contentColor = fg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

package de.schach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schach.engine.GameStatus
import de.schach.engine.Level
import de.schach.engine.Piece

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel, onBack: () -> Unit, onNewComputerGame: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmResign by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (s.mode == GameMode.COMPUTER) "Stufe ${s.level} · ${Level.of(s.level).name}" else "Zu zweit",
                        maxLines = 1,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = vm::flip) { Icon(Icons.Filled.SwapVert, "Brett drehen") }
                    IconButton(onClick = { if (s.isOver || s.sans.isEmpty()) newGame(vm, s, onNewComputerGame) else confirmNew = true }) {
                        Icon(Icons.Filled.Refresh, "Neue Partie")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            ChessBoard(s.board, vm::onSquareTap)
            StatusLine(statusText(s), highlight = s.isOver, busy = s.thinking)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionButton(Icons.AutoMirrored.Filled.Undo, "Zurück", enabled = s.canUndo, onClick = vm::undo)
                ActionButton(
                    Icons.Filled.Lightbulb, if (s.hintLoading) "…" else "Tipp",
                    enabled = !s.isOver && !s.thinking && !s.hintLoading, onClick = vm::hint,
                )
                ActionButton(Icons.Filled.Flag, "Aufgeben", enabled = !s.isOver && s.sans.isNotEmpty()) { confirmResign = true }
            }
            MoveList(s.sans)
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    s.promotion?.let {
        PromotionDialog(white = s.sideToMove == Piece.WHITE, onPick = vm::promote, onDismiss = vm::cancelPromotion)
    }
    if (confirmResign) {
        ConfirmDialog("Aufgeben?", "Die Partie ist dann verloren.", "Aufgeben", onConfirm = {
            confirmResign = false
            vm.resign()
        }, onDismiss = { confirmResign = false })
    }
    if (confirmNew) {
        ConfirmDialog("Neue Partie?", "Die laufende Partie wird beendet.", "Neue Partie", onConfirm = {
            confirmNew = false
            newGame(vm, s, onNewComputerGame)
        }, onDismiss = { confirmNew = false })
    }
}

private fun newGame(vm: GameViewModel, s: GameUiState, onNewComputerGame: () -> Unit) {
    if (s.mode == GameMode.LOCAL) vm.newGame(GameMode.LOCAL, s.level, Piece.WHITE) else onNewComputerGame()
}

private fun colorName(c: Int) = if (c == Piece.WHITE) "Weiß" else "Schwarz"

private fun statusText(s: GameUiState): String {
    val vsComputer = s.mode == GameMode.COMPUTER
    return when {
        s.resigned != 0 -> if (vsComputer) "Du hast aufgegeben." else "${colorName(s.resigned)} gibt auf – ${colorName(-s.resigned)} gewinnt."
        s.status == GameStatus.CHECKMATE -> {
            val winner = -s.sideToMove
            when {
                !vsComputer -> "Schachmatt! ${colorName(winner)} gewinnt."
                winner == s.playerColor -> "Schachmatt – du hast gewonnen! 🎉"
                else -> "Schachmatt – der Computer gewinnt."
            }
        }
        s.status == GameStatus.STALEMATE -> "Patt – unentschieden."
        s.status == GameStatus.DRAW_FIFTY_MOVES -> "Remis (50-Züge-Regel)."
        s.status == GameStatus.DRAW_REPETITION -> "Remis durch dreifache Stellungswiederholung."
        s.status == GameStatus.DRAW_MATERIAL -> "Remis – zu wenig Material zum Mattsetzen."
        s.thinking -> "Der Computer denkt nach …"
        else -> {
            val check = if (s.board.checkSquare >= 0) " – Schach!" else ""
            if (vsComputer) "Du bist am Zug$check" else "${colorName(s.sideToMove)} ist am Zug$check"
        }
    }
}

@Composable
fun StatusLine(text: String, highlight: Boolean = false, busy: Boolean = false) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveList(sans: List<String>) {
    if (sans.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (pair in movePairs(sans)) {
            Text(pair, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

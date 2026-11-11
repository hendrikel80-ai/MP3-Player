package de.schach.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schach.engine.Piece
import de.schach.online.IncomingChallenge
import de.schach.online.LichessClient
import de.schach.online.OnlineGameState
import de.schach.online.OnlineViewModel
import de.schach.online.PlayerInfo
import de.schach.online.TimeControl
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineHomeScreen(vm: OnlineViewModel, onBack: () -> Unit, onOpenGame: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.account) { if (s.account != null) vm.refreshOngoing() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Online spielen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (s.account != null) IconButton(onClick = vm::logout) { Icon(Icons.AutoMirrored.Filled.Logout, "Abmelden") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            s.error?.let { ErrorCard(it, onDismiss = vm::clearError) }
            when {
                s.account != null -> Lobby(vm, s.account!!, onOpenGame)
                s.connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Verbinde mit Lichess …")
                }
                s.hasToken -> {
                    Text("Keine Verbindung zu Lichess. Bist du online?")
                    Button(onClick = vm::retry) { Text("Erneut versuchen") }
                    OutlinedButton(onClick = vm::logout) { Text("Anderen Schlüssel eingeben") }
                }
                else -> Connect(vm)
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Connect(vm: OnlineViewModel) {
    val context = LocalContext.current
    var token by rememberSaveable { mutableStateOf("") }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Text("Mit Lichess verbinden", style = MaterialTheme.typography.titleLarge)
    Text(
        "Online-Partien laufen über lichess.org – die kostenlose, werbefreie Schachplattform mit Millionen Spielern. " +
            "Du brauchst dafür ein (kostenloses) Lichess-Konto und einmalig einen Zugangsschlüssel:",
    )
    Step(1, "Konto anlegen (falls noch keins vorhanden):")
    OutlinedButton(onClick = { open("https://lichess.org/signup") }) { Text("Bei Lichess registrieren") }
    Step(2, "Zugangsschlüssel erstellen – die nötigen Rechte sind schon angehakt. Unten auf der Seite auf „Erstellen“ tippen und den Schlüssel (beginnt mit „lip_“) kopieren:")
    OutlinedButton(onClick = { open(LichessClient.TOKEN_URL) }) { Text("Schlüssel bei Lichess erstellen") }
    Step(3, "Schlüssel hier einfügen:")
    OutlinedTextField(
        value = token,
        onValueChange = { token = it },
        label = { Text("Zugangsschlüssel (lip_…)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { vm.connect(token) }, enabled = token.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Verbinden") }
    Text(
        "Der Schlüssel bleibt nur auf diesem Handy gespeichert und erlaubt der App ausschließlich, Partien zu spielen und " +
            "Herausforderungen zu senden. Du kannst ihn jederzeit auf lichess.org unter Einstellungen → API-Zugangsschlüssel löschen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Step(n: Int, text: String) {
    Row {
        Text("$n.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Text(text)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Lobby(vm: OnlineViewModel, account: LichessClient.Account, onOpenGame: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tcIndex by rememberSaveable { mutableIntStateOf(1) }
    var rated by rememberSaveable { mutableStateOf(false) }
    var friend by rememberSaveable { mutableStateOf("") }
    var colorIndex by rememberSaveable { mutableIntStateOf(1) }
    val tc = TimeControl.OPTIONS[tcIndex]

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Angemeldet als ${account.username}", style = MaterialTheme.typography.titleMedium)
            val r = listOf("rapid" to "Schnellschach", "classical" to "Klassisch", "blitz" to "Blitz")
                .mapNotNull { (k, n) -> account.ratings[k]?.let { "$n $it" } }
            if (r.isNotEmpty()) Text("Wertung: " + r.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }

    // Eingehende Herausforderungen
    for (ch in s.incoming) IncomingCard(ch, vm)

    // Laufende Partien
    if (s.ongoing.isNotEmpty()) {
        Text("Laufende Partien", style = MaterialTheme.typography.titleMedium)
        for (g in s.ongoing) {
            Card(onClick = { onOpenGame(g.gameId) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("gegen ${g.opponent}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            (if (g.color == "white") "Du spielst Weiß" else "Du spielst Schwarz") + " · " +
                                if (g.isMyTurn) "du bist am Zug" else "Gegner ist am Zug",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Öffnen", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    Text("Neue Partie", style = MaterialTheme.typography.titleMedium)
    Text("Bedenkzeit (Minuten + Sekunden pro Zug)", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeControl.OPTIONS.forEachIndexed { i, t ->
            FilterChip(selected = i == tcIndex, onClick = { tcIndex = i }, label = { Text(t.label) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Gewertete Partie")
            Text("verändert deine Lichess-Wertung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = rated, onCheckedChange = { rated = it })
    }

    // Zufälliger Gegner
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Zufälligen Gegner finden", style = MaterialTheme.typography.titleSmall)
            Text(
                "Lichess sucht einen Menschen mit ähnlicher Spielstärke, der die gleiche Bedenkzeit möchte.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (s.seeking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Suche Gegner für ${tc.label} …")
                OutlinedButton(onClick = vm::cancelSeek) { Text("Suche abbrechen") }
            } else {
                Button(onClick = { vm.seek(tc, rated) }, enabled = s.waitingFor == null) { Text("Gegner suchen") }
            }
        }
    }

    // Freund herausfordern
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Freund herausfordern", style = MaterialTheme.typography.titleSmall)
            if (s.waitingFor != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Warte, bis ${s.waitingFor} annimmt …")
                OutlinedButton(onClick = vm::cancelChallenge) { Text("Herausforderung zurückziehen") }
            } else {
                OutlinedTextField(
                    value = friend,
                    onValueChange = { friend = it },
                    label = { Text("Lichess-Benutzername") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                val colors = listOf("white" to "Weiß", "random" to "Zufall", "black" to "Schwarz")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    colors.forEachIndexed { i, (_, label) ->
                        SegmentedButton(
                            selected = colorIndex == i,
                            onClick = { colorIndex = i },
                            shape = SegmentedButtonDefaults.itemShape(i, colors.size),
                        ) { Text(label) }
                    }
                }
                Button(
                    onClick = { vm.challenge(friend, tc, rated, colors[colorIndex].first) },
                    enabled = friend.isNotBlank() && !s.seeking,
                ) { Text("Herausfordern") }
                Text(
                    "Dein Freund bekommt die Herausforderung in seiner Lichess-App, auf lichess.org oder hier in dieser App.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun IncomingCard(ch: IncomingChallenge, vm: OnlineViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Herausforderung von ${ch.from}${ch.rating?.let { " ($it)" } ?: ""}", style = MaterialTheme.typography.titleSmall)
            Text("${ch.timeControl} · ${if (ch.rated) "gewertet" else "ungewertet"}", style = MaterialTheme.typography.bodySmall)
            if (!ch.playable) {
                Text(
                    "Diese Variante/Bedenkzeit kann die App nicht spielen (Bullet und Schachvarianten gehen nur direkt auf lichess.org).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.answerChallenge(ch, true) }, enabled = ch.playable) { Text("Annehmen") }
                OutlinedButton(onClick = { vm.answerChallenge(ch, false) }) { Text("Ablehnen") }
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Schließen") }
        }
    }
}

// ==================================================================== Online-Partie

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineGameScreen(vm: OnlineViewModel, gameId: String, onBack: () -> Unit) {
    LaunchedEffect(gameId) { vm.openGame(gameId) }
    DisposableEffect(gameId) { onDispose { vm.closeGame() } }
    val g by vm.game.collectAsStateWithLifecycle()
    var confirmResign by remember { mutableStateOf(false) }

    // Uhr lokal weiterlaufen lassen
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(g.clockRunning, g.clockAt, g.opponentGone) {
        while (g.clockRunning || g.opponentGone) {
            now = System.currentTimeMillis()
            delay(200)
        }
        now = System.currentTimeMillis()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Online-Partie") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            if (g.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val me = g.myColor
            PlayerBar(if (me == Piece.WHITE) g.black else g.white, clockFor(g, -me, now), g.hasClock, active = !g.isOver && g.sideToMove == -me)
            ChessBoard(g.board, vm::onSquareTap)
            PlayerBar(if (me == Piece.WHITE) g.white else g.black, clockFor(g, me, now), g.hasClock, active = !g.isOver && g.sideToMove == me)
            HorizontalDivider()
            StatusLine(onlineStatus(g), highlight = g.isOver)
            g.error?.let { ErrorCard(it, onDismiss = vm::clearGameError) }

            if (g.opponentGone && !g.isOver) {
                val wait = ((g.claimWinAt - now) / 1000).coerceAtLeast(0)
                Text(
                    if (wait > 0) "Dein Gegner hat die Partie verlassen. In $wait s kannst du den Sieg beanspruchen."
                    else "Dein Gegner ist nicht zurückgekommen.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (wait == 0L) {
                    Button(onClick = vm::claimVictory, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Sieg beanspruchen") }
                }
            }
            if (g.opponentOffersDraw && !g.isOver) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dein Gegner bietet Remis an.", Modifier.weight(1f))
                        TextButton(onClick = vm::offerDraw) { Text("Annehmen") }
                        TextButton(onClick = vm::declineDraw) { Text("Ablehnen") }
                    }
                }
            }
            if (!g.isOver && !g.loading) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (g.canAbort) {
                        ActionButton(Icons.Filled.Close, "Abbrechen", onClick = vm::abort)
                    } else {
                        ActionButton(Icons.Filled.Handshake, if (g.iOfferDraw) "Remis angeboten" else "Remis anbieten",
                            enabled = !g.iOfferDraw && !g.opponentOffersDraw, onClick = vm::offerDraw)
                        ActionButton(Icons.Filled.Flag, "Aufgeben") { confirmResign = true }
                    }
                }
            }
            if (g.isOver) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = onBack) {
                        Icon(Icons.Filled.EmojiEvents, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Zurück zur Lobby")
                    }
                }
            }
            MoveList(g.sans)
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    g.promotion?.let {
        PromotionDialog(white = g.myColor == Piece.WHITE, onPick = vm::promote, onDismiss = vm::cancelPromotion)
    }
    if (confirmResign) {
        ConfirmDialog("Aufgeben?", "Die Partie ist dann verloren.", "Aufgeben", onConfirm = {
            confirmResign = false
            vm.resign()
        }, onDismiss = { confirmResign = false })
    }
}

private fun clockFor(g: OnlineGameState, color: Int, now: Long): Long {
    val base = if (color == Piece.WHITE) g.whiteMs else g.blackMs
    return if (g.clockRunning && g.sideToMove == color) (base - (now - g.clockAt)).coerceAtLeast(0) else base
}

@Composable
private fun PlayerBar(p: PlayerInfo, ms: Long, hasClock: Boolean, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        p.rating?.let {
            Spacer(Modifier.width(6.dp))
            Text("($it)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        if (hasClock) {
            Surface(
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    formatClock(ms),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    if (ms < 10_000) return "0:%02d.%d".format(ms / 1000, (ms % 1000) / 100)
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun onlineStatus(g: OnlineGameState): String {
    if (g.loading) return "Lade Partie …"
    val iWon = g.winner == (if (g.myColor == Piece.WHITE) "white" else "black")
    val result = when {
        g.winner == null -> ""
        iWon -> " – du hast gewonnen! 🎉"
        else -> " – du hast verloren."
    }
    return when (g.status) {
        "created", "started" -> {
            val check = if (g.board.checkSquare >= 0) " – Schach!" else ""
            (if (g.myTurn) "Du bist am Zug" else "Dein Gegner ist am Zug") + check
        }
        "mate" -> "Schachmatt$result"
        "resign" -> "Aufgegeben$result"
        "outoftime", "timeout" -> "Zeit abgelaufen$result"
        "stalemate" -> "Patt – unentschieden."
        "draw" -> "Remis."
        "aborted" -> "Partie abgebrochen."
        "noStart" -> "Partie wurde nicht begonnen$result"
        else -> "Partie beendet$result"
    }
}

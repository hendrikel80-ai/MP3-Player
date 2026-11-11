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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.schach.engine.Level
import de.schach.engine.Piece
import de.schach.online.OnlineViewModel

@Composable
fun ChessApp() {
    val nav = rememberNavController()
    val gameVm: GameViewModel = viewModel()
    val onlineVm: OnlineViewModel = viewModel()
    val online by onlineVm.state.collectAsStateWithLifecycle()

    // Online-Partie hat begonnen → direkt zum Brett
    LaunchedEffect(online.startedGameId) {
        online.startedGameId?.let { id ->
            onlineVm.consumeStartedGame()
            nav.navigate("online/$id") { launchSingleTop = true }
        }
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                gameVm = gameVm,
                onContinue = { nav.navigate("game") },
                onComputer = { nav.navigate("setup") },
                onLocal = {
                    gameVm.newGame(GameMode.LOCAL, 1, Piece.WHITE)
                    nav.navigate("game")
                },
                onOnline = { nav.navigate("online") },
            )
        }
        composable("setup") {
            ComputerSetupScreen(
                initialLevel = gameVm.state.value.level,
                onBack = { nav.popBackStack() },
                onStart = { level, color ->
                    gameVm.newGame(GameMode.COMPUTER, level, color)
                    nav.navigate("game") { popUpTo("home") }
                },
            )
        }
        composable("game") {
            GameScreen(
                vm = gameVm,
                onBack = { nav.popBackStack() },
                onNewComputerGame = { nav.navigate("setup") { popUpTo("home") } },
            )
        }
        composable("online") {
            OnlineHomeScreen(onlineVm, onBack = { nav.popBackStack() }, onOpenGame = { nav.navigate("online/$it") })
        }
        composable("online/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            OnlineGameScreen(onlineVm, id, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun HomeScreen(
    gameVm: GameViewModel,
    onContinue: () -> Unit,
    onComputer: () -> Unit,
    onLocal: () -> Unit,
    onOnline: () -> Unit,
) {
    val game by gameVm.state.collectAsStateWithLifecycle()
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("♞︎ Schach", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Spiele gegen den Computer in 10 Stufen, zu zweit an einem Handy oder online gegen echte Menschen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (game.hasGame && !game.isOver) {
                val what = if (game.mode == GameMode.COMPUTER) {
                    "Gegen Computer · Stufe ${game.level} (${Level.of(game.level).name})"
                } else "Zu zweit an einem Gerät"
                MenuCard(Icons.Filled.PlayArrow, "Partie fortsetzen", "$what · ${game.sans.size / 2 + 1}. Zug", highlight = true, onClick = onContinue)
            }
            MenuCard(Icons.Filled.Computer, "Gegen den Computer", "10 Stufen von Anfänger bis Großmeister", onClick = onComputer)
            MenuCard(Icons.Filled.People, "Zu zweit an einem Gerät", "Abwechselnd ziehen – ideal am Küchentisch", onClick = onLocal)
            MenuCard(Icons.Filled.Public, "Online gegen echte Spieler", "Über Lichess: zufällige Gegner oder Freunde herausfordern", onClick = onOnline)
        }
    }
}

@Composable
private fun MenuCard(icon: ImageVector, title: String, subtitle: String, highlight: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComputerSetupScreen(initialLevel: Int, onBack: () -> Unit, onStart: (level: Int, color: Int) -> Unit) {
    var level by rememberSaveable { mutableIntStateOf(initialLevel) }
    var color by rememberSaveable { mutableIntStateOf(Piece.WHITE) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gegen den Computer") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("Spielstärke", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                for (l in Level.ALL) {
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = level == l.number, role = Role.RadioButton) { level = l.number }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = level == l.number, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Stufe ${l.number}", fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
                        Text(l.name, modifier = Modifier.weight(1f))
                        Text(levelHint(l.number), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Deine Farbe", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                val options = listOf(Piece.WHITE to "Weiß", 0 to "Zufall", Piece.BLACK to "Schwarz")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = color == value,
                            onClick = { color = value },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = { onStart(level, color) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
            ) { Text("Partie starten") }
        }
    }
}

private fun levelHint(n: Int) = when (n) {
    1, 2 -> "macht viele Fehler"
    3, 4 -> "übersieht einiges"
    5, 6 -> "solide"
    7, 8 -> "stark"
    else -> "denkt länger"
}

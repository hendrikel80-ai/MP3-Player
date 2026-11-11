package de.schach.online

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.schach.engine.Board
import de.schach.engine.Move
import de.schach.engine.Piece
import de.schach.ui.BoardView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class TimeControl(val minutes: Int, val increment: Int) {
    val label get() = "$minutes + $increment"

    companion object {
        /** Die Board-API erlaubt Schnellschach (Rapid) und langsamer */
        val OPTIONS = listOf(TimeControl(10, 0), TimeControl(10, 5), TimeControl(15, 10), TimeControl(30, 0), TimeControl(30, 20))
    }
}

data class IncomingChallenge(
    val id: String,
    val from: String,
    val rating: Int?,
    val timeControl: String,
    val rated: Boolean,
    /** Kann die App diese Partie spielen (Standardschach, passendes Tempo)? */
    val playable: Boolean,
)

data class OnlineUiState(
    val hasToken: Boolean = false,
    val account: LichessClient.Account? = null,
    val connecting: Boolean = false,
    val error: String? = null,
    val seeking: Boolean = false,
    val waitingFor: String? = null,
    val incoming: List<IncomingChallenge> = emptyList(),
    val ongoing: List<LichessClient.OngoingGame> = emptyList(),
    /** Neu gestartete Partie – die Oberfläche wechselt dann zum Brett */
    val startedGameId: String? = null,
)

data class PlayerInfo(val name: String, val rating: Int?)

data class OnlineGameState(
    val gameId: String = "",
    val loading: Boolean = true,
    val white: PlayerInfo = PlayerInfo("Weiß", null),
    val black: PlayerInfo = PlayerInfo("Schwarz", null),
    val myColor: Int = Piece.WHITE,
    val board: BoardView = BoardView(),
    val sideToMove: Int = Piece.WHITE,
    val sans: List<String> = emptyList(),
    val status: String = "started",
    val winner: String? = null,
    val whiteMs: Long = 0,
    val blackMs: Long = 0,
    val hasClock: Boolean = true,
    /** Zeitpunkt, zu dem whiteMs/blackMs gemeldet wurden */
    val clockAt: Long = 0,
    val clockRunning: Boolean = false,
    val opponentOffersDraw: Boolean = false,
    val iOfferDraw: Boolean = false,
    val canAbort: Boolean = true,
    val opponentGone: Boolean = false,
    val claimWinAt: Long = 0,
    val promotion: Pair<Int, Int>? = null,
    val error: String? = null,
) {
    val isOver get() = status != "started" && status != "created"
    val myTurn get() = !isOver && sideToMove == myColor
}

class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("lichess", Context.MODE_PRIVATE)
    private var client: LichessClient? = null

    private val _state = MutableStateFlow(OnlineUiState())
    val state: StateFlow<OnlineUiState> = _state

    private val _game = MutableStateFlow(OnlineGameState())
    val game: StateFlow<OnlineGameState> = _game

    private var eventsJob: Job? = null
    private var seekJob: Job? = null
    private var gameJob: Job? = null
    private var outgoingChallengeId: String? = null

    // Partie-Daten
    private val board = Board()
    private var serverMoves = ""
    private var initialFen = Board.START_FEN
    private var selected = -1
    private var legal: List<Int> = emptyList()

    init {
        prefs.getString("token", null)?.let { connect(it) }
    }

    // ---------------------------------------------------------------- Konto

    fun connect(token: String) {
        val t = token.trim()
        if (t.isEmpty()) return
        val c = LichessClient(t)
        _state.update { it.copy(connecting = true, error = null, hasToken = true) }
        viewModelScope.launch {
            try {
                val acc = c.account()
                client = c
                prefs.edit().putString("token", t).apply()
                _state.update { it.copy(account = acc, connecting = false) }
                startEvents()
                refreshOngoing()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val invalid = (e as? LichessException)?.code == 401
                if (invalid) prefs.edit().remove("token").apply()
                _state.update {
                    it.copy(connecting = false, hasToken = !invalid, error = e.message ?: "Keine Verbindung zu Lichess")
                }
            }
        }
    }

    fun retry() {
        prefs.getString("token", null)?.let { connect(it) }
    }

    fun logout() {
        eventsJob?.cancel()
        seekJob?.cancel()
        client = null
        prefs.edit().remove("token").apply()
        _state.value = OnlineUiState()
    }

    fun refreshOngoing() {
        val c = client ?: return
        viewModelScope.launch {
            runCatching { c.ongoingGames() }.onSuccess { list -> _state.update { it.copy(ongoing = list) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------------------------------------------------------------- Ereignisse

    private fun startEvents() {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            while (true) {
                val c = client ?: return@launch
                try {
                    c.events().collect { handleEvent(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Verbindung verloren – gleich neu verbinden
                }
                delay(3000)
            }
        }
    }

    private fun handleEvent(e: JSONObject) {
        val myId = _state.value.account?.id
        when (e.optString("type")) {
            "gameStart" -> {
                val g = e.optJSONObject("game") ?: return
                val id = g.optString("gameId").ifEmpty { g.optString("id") }
                seekJob?.cancel()
                outgoingChallengeId = null
                _state.update { it.copy(seeking = false, waitingFor = null, startedGameId = id) }
                refreshOngoing()
            }
            "gameFinish" -> refreshOngoing()
            "challenge" -> {
                val ch = e.optJSONObject("challenge") ?: return
                val challenger = ch.optJSONObject("challenger")
                if (challenger?.optString("id") == myId) return
                val tc = ch.optJSONObject("timeControl")
                val speed = ch.optString("speed")
                val incoming = IncomingChallenge(
                    id = ch.getString("id"),
                    from = challenger?.optString("name").orEmpty().ifEmpty { "Unbekannt" },
                    rating = challenger?.optInt("rating")?.takeIf { it > 0 },
                    timeControl = when (tc?.optString("type")) {
                        "clock" -> tc.optString("show")
                        "correspondence" -> "${tc.optInt("daysPerTurn")} Tage/Zug"
                        else -> "ohne Zeit"
                    },
                    rated = ch.optBoolean("rated"),
                    playable = ch.optJSONObject("variant")?.optString("key") == "standard" &&
                        speed !in listOf("ultraBullet", "bullet"),
                )
                _state.update { s -> s.copy(incoming = s.incoming.filter { it.id != incoming.id } + incoming) }
            }
            "challengeCanceled", "challengeDeclined" -> {
                val ch = e.optJSONObject("challenge") ?: return
                val id = ch.optString("id")
                _state.update { s -> s.copy(incoming = s.incoming.filter { it.id != id }) }
                if (id == outgoingChallengeId) {
                    outgoingChallengeId = null
                    val declined = e.optString("type") == "challengeDeclined"
                    _state.update {
                        it.copy(waitingFor = null, error = if (declined) "Die Herausforderung wurde abgelehnt." else null)
                    }
                }
            }
        }
    }

    fun consumeStartedGame() = _state.update { it.copy(startedGameId = null) }

    // ---------------------------------------------------------------- Gegner finden

    fun seek(tc: TimeControl, rated: Boolean) {
        val c = client ?: return
        seekJob?.cancel()
        _state.update { it.copy(seeking = true, error = null) }
        seekJob = viewModelScope.launch {
            try {
                c.seek(tc.minutes, tc.increment, rated).collect { }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
            _state.update { it.copy(seeking = false) }
        }
    }

    fun cancelSeek() {
        seekJob?.cancel()
        _state.update { it.copy(seeking = false) }
    }

    fun challenge(username: String, tc: TimeControl, rated: Boolean, color: String) {
        val c = client ?: return
        val name = username.trim()
        if (name.isEmpty()) return
        _state.update { it.copy(waitingFor = name, error = null) }
        viewModelScope.launch {
            try {
                outgoingChallengeId = c.challenge(name, tc.minutes, tc.increment, rated, color)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(waitingFor = null, error = e.message) }
            }
        }
    }

    fun cancelChallenge() {
        val id = outgoingChallengeId
        outgoingChallengeId = null
        _state.update { it.copy(waitingFor = null) }
        val c = client ?: return
        if (id != null) viewModelScope.launch { runCatching { c.cancelChallenge(id) } }
    }

    fun answerChallenge(ch: IncomingChallenge, accept: Boolean) {
        val c = client ?: return
        _state.update { s -> s.copy(incoming = s.incoming.filter { it.id != ch.id }) }
        viewModelScope.launch {
            try {
                if (accept) c.acceptChallenge(ch.id) else c.declineChallenge(ch.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // ---------------------------------------------------------------- Partie

    fun openGame(gameId: String) {
        if (_game.value.gameId == gameId && gameJob?.isActive == true) return
        val c = client ?: return
        gameJob?.cancel()
        serverMoves = ""
        selected = -1
        _game.value = OnlineGameState(gameId = gameId)
        gameJob = viewModelScope.launch {
            while (true) {
                try {
                    c.gameStream(gameId).collect { handleGameEvent(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _game.update { it.copy(error = e.message ?: "Verbindung unterbrochen") }
                }
                if (_game.value.isOver) break
                delay(2000) // Stream beendet/abgebrochen, Partie läuft noch → neu verbinden
            }
        }
    }

    fun closeGame() {
        gameJob?.cancel()
        refreshOngoing()
    }

    private fun handleGameEvent(e: JSONObject) {
        when (e.optString("type")) {
            "gameFull" -> {
                val myId = _state.value.account?.id
                val white = e.optJSONObject("white")
                val black = e.optJSONObject("black")
                val fen = e.optString("initialFen")
                initialFen = if (fen.isEmpty() || fen == "startpos") Board.START_FEN else fen
                val clock = e.optJSONObject("clock")
                _game.update {
                    it.copy(
                        loading = false,
                        error = null,
                        white = player(white, "Weiß"),
                        black = player(black, "Schwarz"),
                        myColor = if (black?.optString("id") == myId) Piece.BLACK else Piece.WHITE,
                        hasClock = clock != null,
                    )
                }
                applyServerState(e.getJSONObject("state"))
            }
            "gameState" -> applyServerState(e)
            "opponentGone" -> {
                val gone = e.optBoolean("gone")
                val secs = e.optInt("claimWinInSeconds", 0)
                _game.update {
                    it.copy(opponentGone = gone, claimWinAt = if (gone) System.currentTimeMillis() + secs * 1000L else 0)
                }
            }
        }
    }

    private fun player(o: JSONObject?, fallback: String): PlayerInfo {
        if (o == null) return PlayerInfo(fallback, null)
        val ai = o.optInt("aiLevel", 0)
        val name = when {
            ai > 0 -> "Lichess-KI Stufe $ai"
            else -> o.optString("name").ifEmpty { fallback }
        }
        return PlayerInfo(name, o.optInt("rating", 0).takeIf { it > 0 })
    }

    private fun applyServerState(s: JSONObject) {
        serverMoves = s.optString("moves")
        rebuildBoard(serverMoves)
        val status = s.optString("status", "started")
        val moveCount = board.historyLength
        _game.update {
            it.copy(
                status = status,
                winner = s.optString("winner").ifEmpty { null },
                whiteMs = s.optLong("wtime"),
                blackMs = s.optLong("btime"),
                clockAt = System.currentTimeMillis(),
                // Die Uhr läuft erst, wenn beide Seiten einmal gezogen haben
                clockRunning = status == "started" && moveCount >= 2,
                opponentOffersDraw = if (it.myColor == Piece.WHITE) s.optBoolean("bdraw") else s.optBoolean("wdraw"),
                iOfferDraw = if (it.myColor == Piece.WHITE) s.optBoolean("wdraw") else s.optBoolean("bdraw"),
                canAbort = moveCount < 2,
            )
        }
        publishBoard()
    }

    private fun rebuildBoard(moves: String) {
        board.loadFen(initialFen)
        val sans = ArrayList<String>()
        for (uci in moves.split(' ').filter { it.isNotBlank() }) {
            val m = board.parseUci(uci) ?: break
            sans.add(board.toSan(m))
            board.makeMove(m)
        }
        legal = board.legalMoves()
        _game.update { it.copy(sans = sans) }
    }

    private fun publishBoard() {
        val last = board.lastMove()
        val g = _game.value
        _game.value = g.copy(
            sideToMove = board.side,
            board = BoardView(
                pieces = board.sq.copyOf(),
                flipped = g.myColor == Piece.BLACK,
                selected = selected,
                targets = if (selected >= 0) legal.filter { Move.from(it) == selected }.map { Move.to(it) }.toSet() else emptySet(),
                lastFrom = if (last != Move.NONE) Move.from(last) else -1,
                lastTo = if (last != Move.NONE) Move.to(last) else -1,
                checkSquare = if (board.inCheck()) board.kingSquare(board.side) else -1,
            ),
        )
    }

    fun onSquareTap(s: Int) {
        val g = _game.value
        if (!g.myTurn || g.loading) return
        if (selected >= 0) {
            val candidates = legal.filter { Move.from(it) == selected && Move.to(it) == s }
            if (candidates.size > 1) {
                _game.update { it.copy(promotion = selected to s) }
                return
            }
            if (candidates.size == 1) {
                sendMove(candidates[0])
                return
            }
        }
        val p = board.sq[s]
        selected = if (p != 0 && (p > 0) == (board.side > 0) && s != selected) s else -1
        publishBoard()
    }

    fun promote(type: Int) {
        val (from, to) = _game.value.promotion ?: return
        _game.update { it.copy(promotion = null) }
        legal.firstOrNull { Move.from(it) == from && Move.to(it) == to && Move.promo(it) == type }?.let { sendMove(it) }
    }

    fun cancelPromotion() {
        selected = -1
        _game.update { it.copy(promotion = null) }
        publishBoard()
    }

    private fun sendMove(m: Int) {
        val c = client ?: return
        val gameId = _game.value.gameId
        val uci = Move.uci(m)
        // Sofort anzeigen, der Server bestätigt gleich
        val san = board.toSan(m)
        board.makeMove(m)
        legal = emptyList()
        selected = -1
        _game.update { it.copy(sans = it.sans + san) }
        publishBoard()
        viewModelScope.launch {
            try {
                c.move(gameId, uci)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rebuildBoard(serverMoves)
                _game.update { it.copy(error = "Zug nicht angenommen: ${e.message}") }
                publishBoard()
            }
        }
    }

    fun resign() = gameAction { c, id -> c.resign(id) }
    fun abort() = gameAction { c, id -> c.abort(id) }
    fun offerDraw() = gameAction { c, id -> c.draw(id, true) }
    fun declineDraw() = gameAction { c, id -> c.draw(id, false) }
    fun claimVictory() = gameAction { c, id -> c.claimVictory(id) }
    fun clearGameError() = _game.update { it.copy(error = null) }

    private fun gameAction(block: suspend (LichessClient, String) -> Unit) {
        val c = client ?: return
        val id = _game.value.gameId
        viewModelScope.launch {
            try {
                block(c, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _game.update { it.copy(error = e.message) }
            }
        }
    }
}

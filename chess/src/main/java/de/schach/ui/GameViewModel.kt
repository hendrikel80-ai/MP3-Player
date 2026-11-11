package de.schach.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.schach.engine.Board
import de.schach.engine.Engine
import de.schach.engine.GameStatus
import de.schach.engine.Level
import de.schach.engine.Move
import de.schach.engine.Piece
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class GameMode { COMPUTER, LOCAL }

data class GameUiState(
    val hasGame: Boolean = false,
    val mode: GameMode = GameMode.COMPUTER,
    val level: Int = 3,
    /** Farbe des Menschen gegen den Computer */
    val playerColor: Int = Piece.WHITE,
    val board: BoardView = BoardView(),
    val sideToMove: Int = Piece.WHITE,
    val sans: List<String> = emptyList(),
    val status: GameStatus = GameStatus.ONGOING,
    /** Farbe, die aufgegeben hat (0 = niemand) */
    val resigned: Int = 0,
    val thinking: Boolean = false,
    val hintLoading: Boolean = false,
    val promotion: Pair<Int, Int>? = null,
    val canUndo: Boolean = false,
) {
    val isOver get() = status != GameStatus.ONGOING || resigned != 0
}

/** Partien gegen den Computer und zu zweit an einem Gerät. Die laufende Partie wird gespeichert. */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("game", Context.MODE_PRIVATE)
    private val board = Board()
    private val sans = ArrayList<String>()
    private var legal: List<Int> = board.legalMoves()

    private var mode = GameMode.COMPUTER
    private var level = 3
    private var playerColor = Piece.WHITE
    private var flipped = false
    private var selected = -1
    private var resigned = 0
    private var hintFrom = -1
    private var hintTo = -1
    private var promotion: Pair<Int, Int>? = null
    private var hasGame = false

    private val engine = Engine()
    private val hintEngine = Engine()
    private val engineLock = Mutex()
    private var engineJob: Job? = null
    private var hintJob: Job? = null
    private var thinking = false
    private var hintLoading = false
    /** Wird bei jeder Brettänderung erhöht, damit veraltete Computerzüge verworfen werden. */
    private var generation = 0

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state

    init {
        restore()
        publish()
        maybeComputerMove()
    }

    // ---------------------------------------------------------------- Aktionen

    fun newGame(mode: GameMode, level: Int, color: Int) {
        cancelEngine()
        this.mode = mode
        this.level = level
        playerColor = if (color == 0) (if (Math.random() < 0.5) Piece.WHITE else Piece.BLACK) else color
        flipped = mode == GameMode.COMPUTER && playerColor == Piece.BLACK
        board.loadFen(Board.START_FEN)
        sans.clear()
        resigned = 0
        hasGame = true
        afterBoardChange()
        maybeComputerMove()
    }

    fun onSquareTap(s: Int) {
        val st = _state.value
        if (st.isOver || thinking) return
        if (mode == GameMode.COMPUTER && board.side != playerColor) return
        if (selected >= 0) {
            val candidates = legal.filter { Move.from(it) == selected && Move.to(it) == s }
            if (candidates.size > 1) {
                promotion = selected to s
                publish()
                return
            }
            if (candidates.size == 1) {
                playerMove(candidates[0])
                return
            }
        }
        val p = board.sq[s]
        selected = if (p != 0 && (p > 0) == (board.side > 0) && s != selected) s else -1
        publish()
    }

    fun promote(type: Int) {
        val (from, to) = promotion ?: return
        promotion = null
        legal.firstOrNull { Move.from(it) == from && Move.to(it) == to && Move.promo(it) == type }?.let { playerMove(it) }
            ?: publish()
    }

    fun cancelPromotion() {
        promotion = null
        selected = -1
        publish()
    }

    fun undo() {
        if (board.historyLength == 0) return
        cancelEngine()
        undoOne()
        if (mode == GameMode.COMPUTER) {
            while (board.historyLength > 0 && board.side != playerColor) undoOne()
        }
        resigned = 0
        afterBoardChange()
        maybeComputerMove()
    }

    fun resign() {
        if (_state.value.isOver) return
        cancelEngine()
        resigned = if (mode == GameMode.COMPUTER) playerColor else board.side
        afterBoardChange()
    }

    fun flip() {
        flipped = !flipped
        publish()
    }

    fun hint() {
        if (_state.value.isOver || thinking || hintLoading) return
        val copy = board.copy()
        val gen = generation
        hintLoading = true
        publish()
        hintJob = viewModelScope.launch {
            val m = withContext(Dispatchers.Default) { hintEngine.search(copy, 64, 1500).move }
            hintLoading = false
            if (gen == generation && m != Move.NONE) {
                hintFrom = Move.from(m)
                hintTo = Move.to(m)
            }
            publish()
        }
    }

    // ---------------------------------------------------------------- intern

    private fun playerMove(m: Int) {
        applyMove(m)
        maybeComputerMove()
    }

    private fun applyMove(m: Int) {
        sans.add(board.toSan(m))
        board.makeMove(m)
        afterBoardChange()
    }

    private fun undoOne() {
        board.unmakeMove()
        if (sans.isNotEmpty()) sans.removeAt(sans.lastIndex)
    }

    private fun afterBoardChange() {
        generation++
        selected = -1
        hintFrom = -1
        hintTo = -1
        promotion = null
        hintLoading = false
        hintJob?.cancel()
        legal = board.legalMoves()
        save()
        publish()
    }

    private fun maybeComputerMove() {
        if (!hasGame || mode != GameMode.COMPUTER || board.side == playerColor || _state.value.isOver) return
        val copy = board.copy()
        val gen = generation
        thinking = true
        publish()
        engineJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            val m = engineLock.withLock {
                withContext(Dispatchers.Default) { engine.chooseMove(copy, Level.of(level)) }
            }
            // Kurze Bedenkzeit, damit der Gegenzug nicht „unsichtbar“ schnell kommt
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < 600) delay(600 - elapsed)
            if (gen != generation) return@launch
            thinking = false
            if (m != Move.NONE) applyMove(m) else publish()
        }
    }

    private fun cancelEngine() {
        generation++
        engine.stop()
        engineJob?.cancel()
        thinking = false
    }

    private fun publish() {
        val inCheck = board.inCheck()
        val last = board.lastMove()
        val status = board.status()
        _state.value = GameUiState(
            hasGame = hasGame,
            mode = mode,
            level = level,
            playerColor = playerColor,
            board = BoardView(
                pieces = board.sq.copyOf(),
                flipped = flipped,
                selected = selected,
                targets = if (selected >= 0) legal.filter { Move.from(it) == selected }.map { Move.to(it) }.toSet() else emptySet(),
                lastFrom = if (last != Move.NONE) Move.from(last) else -1,
                lastTo = if (last != Move.NONE) Move.to(last) else -1,
                checkSquare = if (inCheck) board.kingSquare(board.side) else -1,
                hintFrom = hintFrom,
                hintTo = hintTo,
            ),
            sideToMove = board.side,
            sans = sans.toList(),
            status = status,
            resigned = resigned,
            thinking = thinking,
            hintLoading = hintLoading,
            promotion = promotion,
            canUndo = board.historyLength > 0,
        )
    }

    // ---------------------------------------------------------------- Speichern

    private fun save() {
        prefs.edit()
            .putBoolean("hasGame", hasGame)
            .putString("mode", mode.name)
            .putInt("level", level)
            .putInt("color", playerColor)
            .putBoolean("flipped", flipped)
            .putInt("resigned", resigned)
            .putString("moves", board.moveHistory().joinToString(" ") { Move.uci(it) })
            .apply()
    }

    private fun restore() {
        hasGame = prefs.getBoolean("hasGame", false)
        if (!hasGame) return
        mode = runCatching { GameMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(GameMode.COMPUTER)
        level = prefs.getInt("level", 3)
        playerColor = prefs.getInt("color", Piece.WHITE)
        flipped = prefs.getBoolean("flipped", false)
        resigned = prefs.getInt("resigned", 0)
        for (uci in prefs.getString("moves", "")!!.split(' ').filter { it.isNotBlank() }) {
            val m = board.parseUci(uci) ?: break
            sans.add(board.toSan(m))
            board.makeMove(m)
        }
        legal = board.legalMoves()
    }
}

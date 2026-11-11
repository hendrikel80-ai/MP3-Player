package de.schach.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.schach.engine.Board
import de.schach.engine.Engine
import de.schach.engine.GameStatus
import de.schach.engine.Move
import de.schach.engine.Piece
import de.schach.training.MateLesson
import de.schach.training.Opening
import de.schach.training.Puzzle
import de.schach.training.Trainer
import de.schach.training.TrainingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ExerciseKind { OPENING, PUZZLE, LESSON }

enum class MessageType { INFO, GOOD, BAD }

data class TrainerUiState(
    val kind: ExerciseKind = ExerciseKind.OPENING,
    val id: String = "",
    val title: String = "",
    val intro: String = "",
    val board: BoardView = BoardView(),
    val sideToMove: Int = Piece.WHITE,
    val userColor: Int = Piece.WHITE,
    val sans: List<String> = emptyList(),
    /** Eröffnung: gespielte Züge / Gesamtzahl */
    val step: Int = 0,
    val total: Int = 0,
    val practice: Boolean = false,
    /** Erklärung zum letzten Zug */
    val note: String? = null,
    val message: String? = null,
    val messageType: MessageType = MessageType.INFO,
    val solved: Boolean = false,
    val failed: Boolean = false,
    val busy: Boolean = false,
    val promotion: Pair<Int, Int>? = null,
    val tips: List<String> = emptyList(),
    /** Mattführung: eigene Züge; Aufgabe: verbleibende Züge bis Matt */
    val counter: Int = 0,
    val canUndo: Boolean = false,
    val done: Set<String> = emptySet(),
)

/** Schachtrainer: Eröffnungen, Mattaufgaben und Mattführung gegen den Computer. */
class TrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("trainer", Context.MODE_PRIVATE)
    private val board = Board()
    private val sans = ArrayList<String>()
    private var legal: List<Int> = emptyList()
    private val engine = Engine()
    private val engineLock = Mutex()
    private var job: Job? = null
    private var generation = 0

    private var kind = ExerciseKind.OPENING
    private var opening: Opening? = null
    private var puzzle: Puzzle? = null
    private var lesson: MateLesson? = null
    private var practice = false
    private var step = 0
    private var mistakes = 0
    private var movesLeft = 0
    private var userColor = Piece.WHITE
    private var selected = -1
    private var hintFrom = -1
    private var hintTo = -1
    private var note: String? = null
    private var message: String? = null
    private var messageType = MessageType.INFO
    private var solved = false
    private var failed = false
    private var busy = false
    private var promotion: Pair<Int, Int>? = null
    private var done: Set<String> = prefs.getStringSet("done", emptySet())!!.toSet()

    private val _state = MutableStateFlow(TrainerUiState(done = done))
    val state: StateFlow<TrainerUiState> = _state

    fun isLoaded(kind: ExerciseKind, id: String) = this.kind == kind && currentId() == id

    private fun currentId() = when (kind) {
        ExerciseKind.OPENING -> opening?.id
        ExerciseKind.PUZZLE -> puzzle?.id
        ExerciseKind.LESSON -> lesson?.id
    }

    // ---------------------------------------------------------------- Start

    fun start(kind: ExerciseKind, id: String) {
        when (kind) {
            ExerciseKind.OPENING -> startOpening(id, practice = false)
            ExerciseKind.PUZZLE -> startPuzzle(id)
            ExerciseKind.LESSON -> startLesson(id)
        }
    }

    fun startOpening(id: String, practice: Boolean) {
        val o = TrainingData.openings.firstOrNull { it.id == id } ?: return
        reset(ExerciseKind.OPENING, Board.START_FEN)
        opening = o
        userColor = o.side
        this.practice = practice
        step = 0
        if (practice) {
            info(if (o.side == Piece.WHITE) "Du spielst Weiß – mach den ersten Zug." else "Du spielst Schwarz – der Computer beginnt.")
            maybeOpponentBookMove()
        } else {
            info("Tippe auf ▶, um die Züge Schritt für Schritt anzusehen.")
        }
        publish()
    }

    fun startPuzzle(id: String) {
        val p = TrainingData.puzzles.firstOrNull { it.id == id } ?: return
        reset(ExerciseKind.PUZZLE, p.fen)
        puzzle = p
        userColor = board.side
        movesLeft = p.mateIn
        info("${if (userColor == Piece.WHITE) "Weiß" else "Schwarz"} am Zug: Matt in ${p.mateIn}.")
        publish()
    }

    fun startLesson(id: String) {
        val l = TrainingData.mateLessons.firstOrNull { it.id == id } ?: return
        reset(ExerciseKind.LESSON, l.fen)
        lesson = l
        userColor = board.side
        info("Setze matt! Der Computer verteidigt so gut er kann.")
        publish()
    }

    fun restart() {
        when (kind) {
            ExerciseKind.OPENING -> opening?.let { startOpening(it.id, practice) }
            ExerciseKind.PUZZLE -> puzzle?.let { startPuzzle(it.id) }
            ExerciseKind.LESSON -> lesson?.let { startLesson(it.id) }
        }
    }

    private fun reset(k: ExerciseKind, fen: String) {
        generation++
        engine.stop()
        job?.cancel()
        kind = k
        opening = null
        puzzle = null
        lesson = null
        board.loadFen(fen)
        sans.clear()
        legal = board.legalMoves()
        mistakes = 0
        selected = -1
        clearHint()
        note = null
        solved = false
        failed = false
        busy = false
        promotion = null
    }

    // ---------------------------------------------------------------- Eröffnung ansehen

    fun openingNext() {
        val o = opening ?: return
        if (practice || step >= o.moves.size) return
        val mv = o.moves[step]
        val m = board.parseSan(mv.san) ?: return
        play(m)
        step++
        note = mv.note
        if (step == o.moves.size) {
            good("Das ist die Grundstellung der Eröffnung. Jetzt kannst du sie üben!")
        } else message = null
        publish()
    }

    fun openingPrev() {
        if (practice || step == 0) return
        board.unmakeMove()
        sans.removeAt(sans.lastIndex)
        legal = board.legalMoves()
        step--
        note = opening?.moves?.getOrNull(step - 1)?.note
        message = null
        publish()
    }

    private fun maybeOpponentBookMove() {
        val o = opening ?: return
        if (!practice || step >= o.moves.size || board.side == userColor) return
        busy = true
        val gen = generation
        job = viewModelScope.launch {
            delay(700)
            if (gen != generation) return@launch
            val mv = o.moves[step]
            board.parseSan(mv.san)?.let { play(it) }
            step++
            note = mv.note
            busy = false
            if (step == o.moves.size) finishSolved("Geschafft! Du kennst die ${o.name}.") else publish()
        }
    }

    // ---------------------------------------------------------------- Brett

    fun onSquareTap(s: Int) {
        if (busy || solved || failed) return
        if (kind == ExerciseKind.OPENING && !practice) return
        if (board.side != userColor) return
        if (selected >= 0) {
            val candidates = legal.filter { Move.from(it) == selected && Move.to(it) == s }
            if (candidates.size > 1) {
                promotion = selected to s
                publish()
                return
            }
            if (candidates.size == 1) {
                userMove(candidates[0])
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
        legal.firstOrNull { Move.from(it) == from && Move.to(it) == to && Move.promo(it) == type }?.let { userMove(it) }
            ?: publish()
    }

    fun cancelPromotion() {
        promotion = null
        selected = -1
        publish()
    }

    private fun userMove(m: Int) {
        selected = -1
        when (kind) {
            ExerciseKind.OPENING -> openingUserMove(m)
            ExerciseKind.PUZZLE -> puzzleUserMove(m)
            ExerciseKind.LESSON -> lessonUserMove(m)
        }
    }

    private fun openingUserMove(m: Int) {
        val o = opening ?: return
        val mv = o.moves[step]
        val expected = board.parseSan(mv.san)
        if (m != expected) {
            mistakes++
            if (mistakes >= 2 && expected != null) {
                hintFrom = Move.from(expected)
                hintTo = Move.to(expected)
                bad("Nicht ganz. Der Buchzug ist markiert: ${germanSan(mv.san)}")
            } else {
                bad("Das ist nicht der Zug dieser Eröffnung. Versuch es noch einmal!")
            }
            publish()
            return
        }
        mistakes = 0
        clearHint()
        play(m)
        step++
        note = mv.note
        if (step == o.moves.size) {
            finishSolved("Geschafft! Du kennst die ${o.name}.")
            return
        }
        good("Richtig!")
        publish()
        maybeOpponentBookMove()
    }

    private fun puzzleUserMove(m: Int) {
        val p = puzzle ?: return
        busy = true
        clearHint()
        info("Prüfe …")
        publish()
        val gen = generation
        val copy = board.copy()
        val left = movesLeft
        job = viewModelScope.launch {
            val ok = engineLock.withLock { withContext(Dispatchers.Default) { Trainer.forcesMate(copy, m, left, engine) } }
            if (gen != generation) return@launch
            if (!ok) {
                busy = false
                mistakes++
                bad(if (left == 1) "Das ist kein Matt. Versuch es noch einmal!" else "Damit gelingt kein Matt in $left. Versuch es noch einmal!")
                publish()
                return@launch
            }
            play(m)
            movesLeft--
            if (board.status() == GameStatus.CHECKMATE) {
                busy = false
                finishSolved("Matt! ${p.explanation}")
                return@launch
            }
            good("Stark! Jetzt antwortet der Gegner …")
            publish()
            val reply = engineLock.withLock {
                withContext(Dispatchers.Default) { engine.search(board.copy(), 2 * movesLeft + 1, 3000).move }
            }
            delay(500)
            if (gen != generation) return@launch
            play(reply)
            busy = false
            info("Und jetzt? Matt in $movesLeft.")
            publish()
        }
    }

    private fun lessonUserMove(m: Int) {
        clearHint()
        play(m)
        if (checkLessonEnd()) return
        busy = true
        info("Der Computer verteidigt …")
        publish()
        val gen = generation
        val copy = board.copy()
        job = viewModelScope.launch {
            val reply = engineLock.withLock { withContext(Dispatchers.Default) { engine.search(copy, 64, 700).move } }
            delay(300)
            if (gen != generation) return@launch
            busy = false
            if (reply != Move.NONE) play(reply)
            if (!checkLessonEnd()) {
                info("Du bist am Zug.")
                publish()
            }
        }
    }

    /** Liefert true, wenn die Mattführung beendet ist (gewonnen oder gescheitert). */
    private fun checkLessonEnd(): Boolean {
        val reason = when (board.status()) {
            GameStatus.CHECKMATE -> {
                finishSolved("Matt in ${(board.historyLength + 1) / 2} Zügen – sehr gut!")
                return true
            }
            GameStatus.STALEMATE -> "Patt! Der König hat kein Feld mehr, steht aber nicht im Schach – das ist nur remis."
            GameStatus.DRAW_FIFTY_MOVES -> "50 Züge ohne Matt – remis. Versuch es zielstrebiger!"
            GameStatus.DRAW_REPETITION -> "Dreimal dieselbe Stellung – remis."
            GameStatus.DRAW_MATERIAL -> "Deine Figur wurde geschlagen – mattsetzen ist nicht mehr möglich."
            GameStatus.ONGOING -> return false
        }
        failed = true
        busy = false
        bad(reason)
        publish()
        return true
    }

    fun undo() {
        if (kind != ExerciseKind.LESSON || busy || solved || board.historyLength == 0) return
        board.unmakeMove()
        sans.removeAt(sans.lastIndex)
        while (board.historyLength > 0 && board.side != userColor) {
            board.unmakeMove()
            sans.removeAt(sans.lastIndex)
        }
        legal = board.legalMoves()
        failed = false
        clearHint()
        info("Zug zurückgenommen.")
        publish()
    }

    fun hint() {
        if (busy || solved || failed || board.side != userColor) return
        when (kind) {
            ExerciseKind.OPENING -> {
                val o = opening ?: return
                val expected = o.moves.getOrNull(step)?.let { board.parseSan(it.san) } ?: return
                hintFrom = Move.from(expected)
                hintTo = Move.to(expected)
                info("Der richtige Zug ist markiert.")
                publish()
            }
            ExerciseKind.PUZZLE, ExerciseKind.LESSON -> {
                busy = true
                info(if (kind == ExerciseKind.PUZZLE) "Tipp: ${puzzle?.hint}" else "Suche einen guten Zug …")
                publish()
                val gen = generation
                val copy = board.copy()
                val depth = if (kind == ExerciseKind.PUZZLE) 2 * movesLeft + 1 else 64
                job = viewModelScope.launch {
                    val m = engineLock.withLock { withContext(Dispatchers.Default) { engine.search(copy, depth, 1500).move } }
                    if (gen != generation) return@launch
                    busy = false
                    if (m != Move.NONE) {
                        hintFrom = Move.from(m)
                        hintTo = Move.to(m)
                    }
                    if (kind == ExerciseKind.LESSON) info("Ein guter Zug ist markiert.")
                    publish()
                }
            }
        }
    }

    // ---------------------------------------------------------------- intern

    private fun play(m: Int) {
        sans.add(board.toSan(m))
        board.makeMove(m)
        legal = board.legalMoves()
    }

    private fun clearHint() {
        hintFrom = -1
        hintTo = -1
    }

    private fun info(text: String) { message = text; messageType = MessageType.INFO }
    private fun good(text: String) { message = text; messageType = MessageType.GOOD }
    private fun bad(text: String) { message = text; messageType = MessageType.BAD }

    private fun finishSolved(text: String) {
        solved = true
        busy = false
        good(text)
        val key = "${kind.name}:${currentId()}"
        done = done + key
        prefs.edit().putStringSet("done", done).apply()
        publish()
    }

    private fun publish() {
        val last = board.lastMove()
        val o = opening
        _state.value = TrainerUiState(
            kind = kind,
            id = currentId().orEmpty(),
            title = o?.name ?: puzzle?.title ?: lesson?.title ?: "",
            intro = o?.intro ?: puzzle?.let { "Matt in ${it.mateIn} · ${it.pieces}" } ?: lesson?.intro ?: "",
            board = BoardView(
                pieces = board.sq.copyOf(),
                flipped = userColor == Piece.BLACK,
                selected = selected,
                targets = if (selected >= 0) legal.filter { Move.from(it) == selected }.map { Move.to(it) }.toSet() else emptySet(),
                lastFrom = if (last != Move.NONE) Move.from(last) else -1,
                lastTo = if (last != Move.NONE) Move.to(last) else -1,
                checkSquare = if (board.inCheck()) board.kingSquare(board.side) else -1,
                hintFrom = hintFrom,
                hintTo = hintTo,
            ),
            sideToMove = board.side,
            userColor = userColor,
            sans = sans.toList(),
            step = step,
            total = o?.moves?.size ?: 0,
            practice = practice,
            note = note,
            message = message,
            messageType = messageType,
            solved = solved,
            failed = failed,
            busy = busy,
            promotion = promotion,
            tips = lesson?.tips.orEmpty(),
            counter = if (kind == ExerciseKind.PUZZLE) movesLeft else (board.historyLength + 1) / 2,
            canUndo = kind == ExerciseKind.LESSON && board.historyLength > 0 && !solved,
            done = done,
        )
    }

    companion object {
        fun key(kind: ExerciseKind, id: String) = "${kind.name}:$id"
    }
}

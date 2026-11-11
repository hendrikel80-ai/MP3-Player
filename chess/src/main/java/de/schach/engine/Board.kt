package de.schach.engine

/**
 * Figuren: positive Werte = Weiß, negative = Schwarz.
 * Felder: a1 = 0, b1 = 1 … h8 = 63 (Index = Reihe * 8 + Linie).
 */
object Piece {
    const val EMPTY = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    const val WHITE = 1
    const val BLACK = -1

    fun fenChar(p: Int): Char {
        val c = " pnbrqk"[kotlin.math.abs(p)]
        return if (p > 0) c.uppercaseChar() else c
    }
}

/** Ein Zug als Int: von (6 Bit) | nach (6 Bit) | Umwandlungsfigur (3 Bit) | Kennzeichen (3 Bit). */
object Move {
    const val NONE = 0
    const val FLAG_NONE = 0
    const val FLAG_EN_PASSANT = 1
    const val FLAG_CASTLE = 2
    const val FLAG_DOUBLE_PUSH = 3

    fun of(from: Int, to: Int, promo: Int = 0, flag: Int = 0): Int =
        from or (to shl 6) or (promo shl 12) or (flag shl 15)

    fun from(m: Int) = m and 63
    fun to(m: Int) = (m shr 6) and 63
    fun promo(m: Int) = (m shr 12) and 7
    fun flag(m: Int) = (m shr 15) and 7

    fun squareName(sq: Int): String = "${'a' + (sq and 7)}${sq / 8 + 1}"

    fun parseSquare(s: String): Int {
        if (s.length != 2) return -1
        val f = s[0] - 'a'
        val r = s[1] - '1'
        return if (f in 0..7 && r in 0..7) r * 8 + f else -1
    }

    fun uci(m: Int): String {
        val p = promo(m)
        return squareName(from(m)) + squareName(to(m)) + if (p != 0) " pnbrqk"[p].toString() else ""
    }
}

enum class GameStatus { ONGOING, CHECKMATE, STALEMATE, DRAW_FIFTY_MOVES, DRAW_REPETITION, DRAW_MATERIAL }

class Board {
    val sq = IntArray(64)
    var side = Piece.WHITE
        private set
    /** Rochaderechte: 1 = weiß kurz, 2 = weiß lang, 4 = schwarz kurz, 8 = schwarz lang */
    var castling = 0
        private set
    var epSquare = -1
        private set
    var halfmoveClock = 0
        private set
    var fullmoveNumber = 1
        private set
    var hash = 0L
        private set
    private val kingSq = IntArray(2)

    // Rückgängig-Stapel
    private var ply = 0
    private val uMove = IntArray(MAX_HISTORY)
    private val uCaptured = IntArray(MAX_HISTORY)
    private val uCastling = IntArray(MAX_HISTORY)
    private val uEp = IntArray(MAX_HISTORY)
    private val uHalfmove = IntArray(MAX_HISTORY)
    private val uHash = LongArray(MAX_HISTORY)

    val historyLength get() = ply

    /** Stellung, mit der [loadFen] die Partie begonnen hat. */
    var startFen = START_FEN
        private set

    /** Alle seit [startFen] ausgeführten Züge. */
    fun moveHistory(): IntArray = uMove.copyOf(ply)

    init {
        loadFen(START_FEN)
    }

    /** Kopie inklusive Zugverlauf (für Wiederholungserkennung). */
    fun copy(): Board {
        val b = Board()
        b.loadFen(startFen)
        for (m in moveHistory()) b.makeMove(m)
        return b
    }

    // ---------------------------------------------------------------- FEN

    fun loadFen(fen: String) {
        val parts = fen.trim().split(Regex("\\s+"))
        require(parts.size >= 4) { "Ungültige FEN: $fen" }
        sq.fill(0)
        var rank = 7
        var file = 0
        for (c in parts[0]) {
            when {
                c == '/' -> { rank--; file = 0 }
                c.isDigit() -> file += c - '0'
                else -> {
                    val type = " pnbrqk".indexOf(c.lowercaseChar())
                    require(type > 0 && rank in 0..7 && file in 0..7) { "Ungültige FEN: $fen" }
                    sq[rank * 8 + file] = if (c.isUpperCase()) type else -type
                    file++
                }
            }
        }
        side = if (parts[1] == "b") Piece.BLACK else Piece.WHITE
        castling = 0
        if ('K' in parts[2]) castling = castling or 1
        if ('Q' in parts[2]) castling = castling or 2
        if ('k' in parts[2]) castling = castling or 4
        if ('q' in parts[2]) castling = castling or 8
        epSquare = if (parts[3] == "-") -1 else Move.parseSquare(parts[3])
        halfmoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0
        fullmoveNumber = parts.getOrNull(5)?.toIntOrNull() ?: 1
        for (i in 0 until 64) {
            if (sq[i] == Piece.KING) kingSq[0] = i
            if (sq[i] == -Piece.KING) kingSq[1] = i
        }
        ply = 0
        hash = computeHash()
        startFen = toFen()
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = sq[rank * 8 + file]
                if (p == 0) empty++ else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(Piece.fenChar(p))
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(if (side == Piece.WHITE) " w " else " b ")
        val c = buildString {
            if (castling and 1 != 0) append('K')
            if (castling and 2 != 0) append('Q')
            if (castling and 4 != 0) append('k')
            if (castling and 8 != 0) append('q')
        }
        sb.append(c.ifEmpty { "-" })
        sb.append(' ').append(if (epSquare >= 0) Move.squareName(epSquare) else "-")
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
        return sb.toString()
    }

    private fun computeHash(): Long {
        var h = 0L
        for (i in 0 until 64) if (sq[i] != 0) h = h xor Zobrist.piece(sq[i], i)
        if (side == Piece.BLACK) h = h xor Zobrist.SIDE
        h = h xor Zobrist.CASTLING[castling]
        if (epSquare >= 0) h = h xor Zobrist.EP[epSquare and 7]
        return h
    }

    // ---------------------------------------------------------------- Angriffe

    fun kingSquare(color: Int) = kingSq[if (color == Piece.WHITE) 0 else 1]

    /** Wird Feld [s] von der Farbe [by] angegriffen? */
    fun isAttacked(s: Int, by: Int): Boolean {
        // Bauern
        val f = s and 7
        if (by == Piece.WHITE) {
            if (s >= 16) {
                if (f > 0 && sq[s - 9] == Piece.PAWN) return true
                if (f < 7 && sq[s - 7] == Piece.PAWN) return true
            }
        } else {
            if (s < 48) {
                if (f > 0 && sq[s + 7] == -Piece.PAWN) return true
                if (f < 7 && sq[s + 9] == -Piece.PAWN) return true
            }
        }
        val n = Piece.KNIGHT * by
        for (t in Tables.KNIGHT[s]) if (sq[t] == n) return true
        val k = Piece.KING * by
        for (t in Tables.KING[s]) if (sq[t] == k) return true
        val r = Piece.ROOK * by
        val b = Piece.BISHOP * by
        val q = Piece.QUEEN * by
        for (d in 0 until 4) {
            for (t in Tables.RAYS[d][s]) {
                val p = sq[t]
                if (p != 0) { if (p == r || p == q) return true; break }
            }
        }
        for (d in 4 until 8) {
            for (t in Tables.RAYS[d][s]) {
                val p = sq[t]
                if (p != 0) { if (p == b || p == q) return true; break }
            }
        }
        return false
    }

    fun inCheck(color: Int = side) = isAttacked(kingSquare(color), -color)

    // ---------------------------------------------------------------- Zuggenerierung

    /** Pseudolegale Züge in [out] schreiben, liefert die Anzahl. */
    fun generatePseudo(out: IntArray, capturesOnly: Boolean = false): Int {
        var n = 0
        val us = side
        for (from in 0 until 64) {
            val p = sq[from]
            if (p == 0 || (p > 0) != (us > 0)) continue
            when (kotlin.math.abs(p)) {
                Piece.PAWN -> {
                    val dir = 8 * us
                    val rank = from / 8
                    val startRank = if (us > 0) 1 else 6
                    val promoRank = if (us > 0) 6 else 1
                    val file = from and 7
                    val one = from + dir
                    if (sq[one] == 0) {
                        if (rank == promoRank) {
                            for (pr in intArrayOf(Piece.QUEEN, Piece.KNIGHT, Piece.ROOK, Piece.BISHOP)) out[n++] = Move.of(from, one, pr)
                        } else if (!capturesOnly) {
                            out[n++] = Move.of(from, one)
                            val two = one + dir
                            if (rank == startRank && sq[two] == 0) out[n++] = Move.of(from, two, 0, Move.FLAG_DOUBLE_PUSH)
                        }
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val tf = file + df
                        if (tf !in 0..7) continue
                        val to = one + df
                        val t = sq[to]
                        if (t != 0 && (t > 0) != (us > 0)) {
                            if (rank == promoRank) {
                                for (pr in intArrayOf(Piece.QUEEN, Piece.KNIGHT, Piece.ROOK, Piece.BISHOP)) out[n++] = Move.of(from, to, pr)
                            } else out[n++] = Move.of(from, to)
                        } else if (to == epSquare && t == 0) {
                            out[n++] = Move.of(from, to, 0, Move.FLAG_EN_PASSANT)
                        }
                    }
                }
                Piece.KNIGHT -> for (to in Tables.KNIGHT[from]) {
                    val t = sq[to]
                    if (t == 0) { if (!capturesOnly) out[n++] = Move.of(from, to) }
                    else if ((t > 0) != (us > 0)) out[n++] = Move.of(from, to)
                }
                Piece.KING -> {
                    for (to in Tables.KING[from]) {
                        val t = sq[to]
                        if (t == 0) { if (!capturesOnly) out[n++] = Move.of(from, to) }
                        else if ((t > 0) != (us > 0)) out[n++] = Move.of(from, to)
                    }
                    if (!capturesOnly) n = genCastling(out, n, from)
                }
                else -> {
                    val type = kotlin.math.abs(p)
                    val d0 = if (type == Piece.BISHOP) 4 else 0
                    val d1 = if (type == Piece.ROOK) 4 else 8
                    for (d in d0 until d1) {
                        for (to in Tables.RAYS[d][from]) {
                            val t = sq[to]
                            if (t == 0) { if (!capturesOnly) out[n++] = Move.of(from, to) }
                            else {
                                if ((t > 0) != (us > 0)) out[n++] = Move.of(from, to)
                                break
                            }
                        }
                    }
                }
            }
        }
        return n
    }

    private fun genCastling(out: IntArray, start: Int, from: Int): Int {
        var n = start
        val us = side
        val base = if (us > 0) 0 else 56
        if (from != base + 4) return n
        val kBit = if (us > 0) 1 else 4
        val qBit = if (us > 0) 2 else 8
        val rook = Piece.ROOK * us
        if (castling and kBit != 0 && sq[base + 5] == 0 && sq[base + 6] == 0 && sq[base + 7] == rook &&
            !isAttacked(base + 4, -us) && !isAttacked(base + 5, -us) && !isAttacked(base + 6, -us)
        ) out[n++] = Move.of(from, base + 6, 0, Move.FLAG_CASTLE)
        if (castling and qBit != 0 && sq[base + 3] == 0 && sq[base + 2] == 0 && sq[base + 1] == 0 && sq[base] == rook &&
            !isAttacked(base + 4, -us) && !isAttacked(base + 3, -us) && !isAttacked(base + 2, -us)
        ) out[n++] = Move.of(from, base + 2, 0, Move.FLAG_CASTLE)
        return n
    }

    fun legalMoves(): List<Int> {
        val buf = IntArray(256)
        val n = generatePseudo(buf)
        val result = ArrayList<Int>(n)
        for (i in 0 until n) {
            if (makeMove(buf[i])) { result.add(buf[i]); unmakeMove() }
        }
        return result
    }

    // ---------------------------------------------------------------- Ziehen

    /** Führt einen pseudolegalen Zug aus. Liefert false (und nimmt ihn zurück), wenn der eigene König danach im Schach steht. */
    fun makeMove(m: Int): Boolean {
        val from = Move.from(m)
        val to = Move.to(m)
        val flag = Move.flag(m)
        val us = side
        val p = sq[from]
        var captured = sq[to]

        uMove[ply] = m
        uCastling[ply] = castling
        uEp[ply] = epSquare
        uHalfmove[ply] = halfmoveClock
        uHash[ply] = hash

        var h = hash
        if (epSquare >= 0) h = h xor Zobrist.EP[epSquare and 7]
        h = h xor Zobrist.CASTLING[castling]

        // Figur bewegen
        h = h xor Zobrist.piece(p, from)
        sq[from] = 0
        if (flag == Move.FLAG_EN_PASSANT) {
            val capSq = to - 8 * us
            captured = sq[capSq]
            h = h xor Zobrist.piece(captured, capSq)
            sq[capSq] = 0
        } else if (captured != 0) {
            h = h xor Zobrist.piece(captured, to)
        }
        val promo = Move.promo(m)
        val placed = if (promo != 0) promo * us else p
        sq[to] = placed
        h = h xor Zobrist.piece(placed, to)
        uCaptured[ply] = captured

        if (flag == Move.FLAG_CASTLE) {
            val (rf, rt) = if (to > from) Pair(from + 3, from + 1) else Pair(from - 4, from - 1)
            val rook = sq[rf]
            sq[rf] = 0
            sq[rt] = rook
            h = h xor Zobrist.piece(rook, rf) xor Zobrist.piece(rook, rt)
        }
        if (kotlin.math.abs(p) == Piece.KING) kingSq[if (us > 0) 0 else 1] = to

        castling = castling and Tables.CASTLE_MASK[from] and Tables.CASTLE_MASK[to]
        h = h xor Zobrist.CASTLING[castling]

        epSquare = if (flag == Move.FLAG_DOUBLE_PUSH) from + 8 * us else -1
        if (epSquare >= 0) h = h xor Zobrist.EP[epSquare and 7]

        halfmoveClock = if (kotlin.math.abs(p) == Piece.PAWN || captured != 0) 0 else halfmoveClock + 1
        if (us == Piece.BLACK) fullmoveNumber++
        side = -us
        h = h xor Zobrist.SIDE
        hash = h
        ply++

        if (isAttacked(kingSq[if (us > 0) 0 else 1], -us)) {
            unmakeMove()
            return false
        }
        return true
    }

    fun unmakeMove() {
        ply--
        val m = uMove[ply]
        val from = Move.from(m)
        val to = Move.to(m)
        val flag = Move.flag(m)
        side = -side
        val us = side
        if (us == Piece.BLACK) fullmoveNumber--
        val moved = sq[to]
        val p = if (Move.promo(m) != 0) Piece.PAWN * us else moved
        sq[from] = p
        if (flag == Move.FLAG_EN_PASSANT) {
            sq[to] = 0
            sq[to - 8 * us] = uCaptured[ply]
        } else {
            sq[to] = uCaptured[ply]
        }
        if (flag == Move.FLAG_CASTLE) {
            val (rf, rt) = if (to > from) Pair(from + 3, from + 1) else Pair(from - 4, from - 1)
            sq[rf] = sq[rt]
            sq[rt] = 0
        }
        if (kotlin.math.abs(p) == Piece.KING) kingSq[if (us > 0) 0 else 1] = from
        castling = uCastling[ply]
        epSquare = uEp[ply]
        halfmoveClock = uHalfmove[ply]
        hash = uHash[ply]
    }

    /** „Nullzug“ für die Suche (Seite wechselt, sonst nichts). */
    fun makeNullMove() {
        uMove[ply] = Move.NONE
        uCastling[ply] = castling
        uEp[ply] = epSquare
        uHalfmove[ply] = halfmoveClock
        uHash[ply] = hash
        uCaptured[ply] = 0
        var h = hash
        if (epSquare >= 0) h = h xor Zobrist.EP[epSquare and 7]
        epSquare = -1
        side = -side
        hash = h xor Zobrist.SIDE
        halfmoveClock++
        ply++
    }

    fun unmakeNullMove() {
        ply--
        side = -side
        epSquare = uEp[ply]
        halfmoveClock = uHalfmove[ply]
        hash = uHash[ply]
    }

    /** Ist die aktuelle Stellung eine Wiederholung? ([count] = wie oft sie vorher schon vorkam) */
    fun repetitions(): Int {
        var count = 0
        var i = ply - 2
        val limit = ply - halfmoveClock
        while (i >= 0 && i >= limit) {
            if (uHash[i] == hash) count++
            i -= 2
        }
        return count
    }

    fun lastMove(): Int = if (ply > 0) uMove[ply - 1] else Move.NONE

    // ---------------------------------------------------------------- Spielstatus

    fun insufficientMaterial(): Boolean {
        var minors = 0
        var bishopsLight = 0
        var bishopsDark = 0
        for (i in 0 until 64) {
            when (kotlin.math.abs(sq[i])) {
                Piece.PAWN, Piece.ROOK, Piece.QUEEN -> return false
                Piece.KNIGHT -> minors++
                Piece.BISHOP -> {
                    minors++
                    if (((i / 8) + (i and 7)) % 2 == 0) bishopsDark++ else bishopsLight++
                }
            }
        }
        if (minors <= 1) return true
        // Nur Läufer auf gleichfarbigen Feldern
        return minors == bishopsDark + bishopsLight && (bishopsDark == 0 || bishopsLight == 0)
    }

    fun status(): GameStatus {
        if (legalMoves().isEmpty()) return if (inCheck()) GameStatus.CHECKMATE else GameStatus.STALEMATE
        if (halfmoveClock >= 100) return GameStatus.DRAW_FIFTY_MOVES
        if (repetitions() >= 2) return GameStatus.DRAW_REPETITION
        if (insufficientMaterial()) return GameStatus.DRAW_MATERIAL
        return GameStatus.ONGOING
    }

    // ---------------------------------------------------------------- Notation

    /** Findet einen legalen Zug aus UCI-Notation (z. B. "e2e4", "e7e8q"). */
    fun parseUci(uci: String): Int? {
        if (uci.length < 4) return null
        val from = Move.parseSquare(uci.substring(0, 2))
        val to = Move.parseSquare(uci.substring(2, 4))
        val promo = if (uci.length >= 5) " pnbrqk".indexOf(uci[4].lowercaseChar()).coerceAtLeast(0) else 0
        return legalMoves().firstOrNull { Move.from(it) == from && Move.to(it) == to && Move.promo(it) == promo }
    }

    /** Findet einen legalen Zug aus SAN mit englischen Buchstaben (z. B. "Nf3", "exd5", "O-O"). */
    fun parseSan(san: String): Int? {
        val clean = san.trim().trimEnd('+', '#', '!', '?')
        return legalMoves().firstOrNull { toSan(it).trimEnd('+', '#') == clean }
    }

    /** Standard-Algebraische Notation, z. B. "Sf3" wird als "Nf3" geliefert; die Oberfläche übersetzt die Buchstaben. */
    fun toSan(m: Int): String {
        val from = Move.from(m)
        val to = Move.to(m)
        val p = sq[from]
        val type = kotlin.math.abs(p)
        val sb = StringBuilder()
        if (Move.flag(m) == Move.FLAG_CASTLE) {
            sb.append(if (to > from) "O-O" else "O-O-O")
        } else {
            val capture = sq[to] != 0 || Move.flag(m) == Move.FLAG_EN_PASSANT
            if (type == Piece.PAWN) {
                if (capture) sb.append('a' + (from and 7)).append('x')
                sb.append(Move.squareName(to))
                if (Move.promo(m) != 0) sb.append('=').append(" PNBRQK"[Move.promo(m)])
            } else {
                sb.append(" PNBRQK"[type])
                val others = legalMoves().filter { it != m && Move.to(it) == to && sq[Move.from(it)] == p }
                if (others.isNotEmpty()) {
                    val sameFile = others.any { (Move.from(it) and 7) == (from and 7) }
                    val sameRank = others.any { Move.from(it) / 8 == from / 8 }
                    if (!sameFile) sb.append('a' + (from and 7))
                    else if (!sameRank) sb.append('1' + from / 8)
                    else sb.append(Move.squareName(from))
                }
                if (capture) sb.append('x')
                sb.append(Move.squareName(to))
            }
        }
        if (makeMove(m)) {
            if (inCheck()) sb.append(if (legalMoves().isEmpty()) '#' else '+')
            unmakeMove()
        }
        return sb.toString()
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        private const val MAX_HISTORY = 2048
    }
}

internal object Tables {
    val KNIGHT: Array<IntArray> = Array(64) { s -> jumps(s, listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)) }
    val KING: Array<IntArray> = Array(64) { s -> jumps(s, listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)) }

    /** Richtungen 0–3: Turm (N, S, O, W), 4–7: Läufer (NO, NW, SO, SW) – jeweils (Linie, Reihe) */
    private val DIRS = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0, 1 to 1, -1 to 1, 1 to -1, -1 to -1)
    val RAYS: Array<Array<IntArray>> = Array(8) { d ->
        Array(64) { s ->
            val (df, dr) = DIRS[d]
            val list = ArrayList<Int>()
            var f = (s and 7) + df
            var r = s / 8 + dr
            while (f in 0..7 && r in 0..7) {
                list.add(r * 8 + f); f += df; r += dr
            }
            list.toIntArray()
        }
    }

    val CASTLE_MASK = IntArray(64) { 15 }.also {
        it[0] = 15 and 2.inv()
        it[7] = 15 and 1.inv()
        it[4] = 15 and 3.inv()
        it[56] = 15 and 8.inv()
        it[63] = 15 and 4.inv()
        it[60] = 15 and 12.inv()
    }

    private fun jumps(s: Int, deltas: List<Pair<Int, Int>>): IntArray {
        val f = s and 7
        val r = s / 8
        return deltas.mapNotNull { (df, dr) ->
            val nf = f + df
            val nr = r + dr
            if (nf in 0..7 && nr in 0..7) nr * 8 + nf else null
        }.toIntArray()
    }
}

internal object Zobrist {
    private val rnd = java.util.Random(0x5EED_C4E55L)
    private val PIECES = Array(13) { LongArray(64) { rnd.nextLong() } }
    val SIDE = rnd.nextLong()
    val CASTLING = LongArray(16) { rnd.nextLong() }
    val EP = LongArray(8) { rnd.nextLong() }

    fun piece(p: Int, s: Int) = PIECES[p + 6][s]
}

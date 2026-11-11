package de.schach.engine

import kotlin.math.abs
import kotlin.random.Random

/**
 * Spielstärke: 1 (Anfänger) bis 10 (Meister).
 * Niedrige Stufen rechnen weniger tief und streuen absichtlich ungenaue Züge ein.
 */
data class Level(
    val number: Int,
    val name: String,
    val maxDepth: Int,
    val timeMs: Long,
    /** Zufallsstreuung in Centipawns – je höher, desto „menschlicher“ die Fehler */
    val noise: Int,
    /** Wahrscheinlichkeit für einen bewusst schwachen Zug */
    val blunderChance: Double,
) {
    companion object {
        val ALL = listOf(
            Level(1, "Anfänger", 1, 300, 400, 0.30),
            Level(2, "Einsteiger", 1, 300, 220, 0.18),
            Level(3, "Gelegenheitsspieler", 2, 400, 140, 0.10),
            Level(4, "Hobbyspieler", 2, 500, 80, 0.05),
            Level(5, "Vereinsspieler", 3, 700, 45, 0.02),
            Level(6, "Fortgeschritten", 4, 1000, 25, 0.0),
            Level(7, "Stark", 6, 1500, 12, 0.0),
            Level(8, "Experte", 8, 2500, 0, 0.0),
            Level(9, "Meister", 64, 5000, 0, 0.0),
            Level(10, "Großmeister", 64, 10000, 0, 0.0),
        )

        fun of(n: Int) = ALL[(n - 1).coerceIn(0, ALL.size - 1)]
    }
}

data class SearchResult(val move: Int, val score: Int, val depth: Int, val nodes: Long)

class Engine(private val random: Random = Random.Default) {

    @Volatile
    private var stopFlag = false
    private var deadline = 0L
    private var nodes = 0L

    private val ttKey = LongArray(TT_SIZE)
    private val ttMove = IntArray(TT_SIZE)
    private val ttScore = IntArray(TT_SIZE)
    private val ttDepth = ByteArray(TT_SIZE)
    private val ttFlag = ByteArray(TT_SIZE)

    private val killers = Array(MAX_PLY) { IntArray(2) }
    private val historyTable = Array(2) { IntArray(64 * 64) }
    private val moveBufs = Array(MAX_PLY) { IntArray(256) }
    private val scoreBufs = Array(MAX_PLY) { IntArray(256) }

    fun stop() {
        stopFlag = true
    }

    /** Wählt einen Zug für die gegebene Stufe. Die Stellung [board] bleibt unverändert. */
    fun chooseMove(board: Board, level: Level): Int {
        val legal = board.legalMoves()
        if (legal.isEmpty()) return Move.NONE
        if (legal.size == 1) return legal[0]

        if (level.noise == 0 && board.historyLength >= OPENING_PLIES) {
            return search(board, level.maxDepth, level.timeMs).move
        }
        if (level.noise == 0) {
            // Eröffnung: unter fast gleich guten Zügen zufällig wählen, damit nicht jede Partie gleich verläuft
            val scored = scoreRootMoves(board, legal, 4)
            val best = scored.maxOf { it.second }
            val good = scored.filter { it.second >= best - 20 }
            return good[random.nextInt(good.size)].first
        }

        // Schwächere Stufen: jeden Zug flach bewerten, dann mit Zufall auswählen
        val scored = scoreRootMoves(board, legal, level.maxDepth)
        scored.sortByDescending { it.second }
        // Matt in 1 übersieht auch der Anfänger nur manchmal
        if (scored[0].second > MATE - 100 && random.nextDouble() > level.blunderChance * 2) return scored[0].first

        if (random.nextDouble() < level.blunderChance) {
            // Bewusst schwacher (aber nicht völlig absurder) Zug aus der unteren Hälfte
            val pool = scored.drop(scored.size / 3).filter { it.second > scored[0].second - 900 }
            if (pool.isNotEmpty()) return pool[random.nextInt(pool.size)].first
        }
        return scored.maxBy { it.second + (random.nextDouble() * 2 - 1) * level.noise }.first
    }

    /** Bewertet jeden Zug mit fester (geringer) Tiefe – ohne Zeitlimit, damit alle Bewertungen vergleichbar sind. */
    private fun scoreRootMoves(board: Board, legal: List<Int>, depth: Int): MutableList<Pair<Int, Int>> {
        val b = board.copy()
        resetSearch(60_000)
        val scored = ArrayList<Pair<Int, Int>>()
        for (m in legal) {
            b.makeMove(m)
            val s = -alphaBeta(b, depth - 1, 1, -INF, INF, true)
            b.unmakeMove()
            scored.add(m to s)
        }
        return scored
    }

    /** Iterative Vertiefung bis [maxDepth] oder bis die Zeit [timeMs] abgelaufen ist. */
    fun search(board: Board, maxDepth: Int, timeMs: Long): SearchResult {
        val b = board.copy()
        resetSearch(timeMs)
        val legal = b.legalMoves()
        var best = SearchResult(legal.firstOrNull() ?: Move.NONE, 0, 0, 0)
        val start = System.currentTimeMillis()
        for (depth in 1..maxDepth.coerceAtMost(MAX_PLY - 10)) {
            val score = alphaBeta(b, depth, 0, -INF, INF, true)
            if (stopFlag && depth > 1) break
            val m = probeMove(b.hash)
            if (m != Move.NONE && m in legal) best = SearchResult(m, score, depth, nodes)
            if (abs(score) > MATE - 200) break
            // Wenn schon mehr als die Hälfte der Zeit verbraucht ist, lohnt die nächste Tiefe meist nicht
            if (System.currentTimeMillis() - start > timeMs / 2) break
        }
        return best
    }

    private fun resetSearch(timeMs: Long) {
        stopFlag = false
        nodes = 0
        deadline = System.currentTimeMillis() + timeMs
        for (k in killers) k.fill(0)
        for (h in historyTable) h.fill(0)
    }

    private fun alphaBeta(b: Board, depthIn: Int, ply: Int, alphaIn: Int, beta: Int, allowNull: Boolean): Int {
        var alpha = alphaIn
        var depth = depthIn
        if ((++nodes and 2047L) == 0L && System.currentTimeMillis() > deadline) stopFlag = true
        if (stopFlag) return 0

        if (ply > 0) {
            if (b.halfmoveClock >= 100 || b.repetitions() > 0) return 0
            // Matt-Distanz-Beschneidung
            alpha = maxOf(alpha, -MATE + ply)
            if (alpha >= minOf(beta, MATE - ply - 1)) return alpha
        }
        val inCheck = b.inCheck()
        if (inCheck) depth++
        if (depth <= 0 || ply >= MAX_PLY - 2) return quiesce(b, alpha, beta, ply)

        // Transpositionstabelle
        val idx = (b.hash and TT_MASK).toInt()
        var hashMove = Move.NONE
        if (ttKey[idx] == b.hash) {
            hashMove = ttMove[idx]
            if (ply > 0 && ttDepth[idx] >= depth) {
                val s = fromTt(ttScore[idx], ply)
                when (ttFlag[idx].toInt()) {
                    EXACT -> return s
                    LOWER -> if (s >= beta) return s
                    UPPER -> if (s <= alpha) return s
                }
            }
        }

        val isPv = beta - alpha > 1
        // Nullzug-Beschneidung
        if (allowNull && !inCheck && !isPv && depth >= 3 && hasPieces(b) && evaluate(b) >= beta) {
            b.makeNullMove()
            val s = -alphaBeta(b, depth - 3, ply + 1, -beta, -beta + 1, false)
            b.unmakeNullMove()
            if (stopFlag) return 0
            if (s >= beta) return beta
        }

        val moves = moveBufs[ply]
        val scores = scoreBufs[ply]
        val n = b.generatePseudo(moves)
        scoreMoves(b, moves, scores, n, hashMove, ply)

        var bestScore = -INF
        var bestMove = Move.NONE
        var legalCount = 0
        val origAlpha = alpha
        for (i in 0 until n) {
            pickBest(moves, scores, i, n)
            val m = moves[i]
            val isCapture = b.sq[Move.to(m)] != 0 || Move.flag(m) == Move.FLAG_EN_PASSANT
            if (!b.makeMove(m)) continue
            legalCount++
            var s: Int
            if (legalCount == 1) {
                s = -alphaBeta(b, depth - 1, ply + 1, -beta, -alpha, true)
            } else {
                // Späte, ruhige Züge erst reduziert prüfen
                var reduction = 0
                if (depth >= 3 && legalCount > 3 && !isCapture && Move.promo(m) == 0 && !inCheck && !b.inCheck()) {
                    reduction = if (legalCount > 8) 2 else 1
                }
                s = -alphaBeta(b, depth - 1 - reduction, ply + 1, -alpha - 1, -alpha, true)
                if (s > alpha && reduction > 0) s = -alphaBeta(b, depth - 1, ply + 1, -alpha - 1, -alpha, true)
                if (s > alpha && s < beta) s = -alphaBeta(b, depth - 1, ply + 1, -beta, -alpha, true)
            }
            b.unmakeMove()
            if (stopFlag) return 0
            if (s > bestScore) {
                bestScore = s
                bestMove = m
                if (s > alpha) {
                    alpha = s
                    if (s >= beta) {
                        if (!isCapture) {
                            if (killers[ply][0] != m) {
                                killers[ply][1] = killers[ply][0]
                                killers[ply][0] = m
                            }
                            val h = historyTable[if (b.side > 0) 0 else 1]
                            val k = Move.from(m) * 64 + Move.to(m)
                            h[k] = minOf(h[k] + depth * depth, 1_000_000)
                        }
                        break
                    }
                }
            }
        }
        if (legalCount == 0) return if (inCheck) -MATE + ply else 0

        val flag = when {
            bestScore >= beta -> LOWER
            bestScore > origAlpha -> EXACT
            else -> UPPER
        }
        if (ttKey[idx] != b.hash || depth >= ttDepth[idx] || ply == 0) {
            ttKey[idx] = b.hash
            ttMove[idx] = bestMove
            ttScore[idx] = toTt(bestScore, ply)
            ttDepth[idx] = depth.coerceAtMost(127).toByte()
            ttFlag[idx] = flag.toByte()
        }
        return bestScore
    }

    private fun quiesce(b: Board, alphaIn: Int, beta: Int, ply: Int): Int {
        var alpha = alphaIn
        if ((++nodes and 2047L) == 0L && System.currentTimeMillis() > deadline) stopFlag = true
        if (stopFlag) return 0
        val stand = evaluate(b)
        if (stand >= beta) return stand
        if (stand > alpha) alpha = stand
        if (ply >= MAX_PLY - 2) return stand

        val moves = moveBufs[ply]
        val scores = scoreBufs[ply]
        val n = b.generatePseudo(moves, capturesOnly = true)
        scoreMoves(b, moves, scores, n, Move.NONE, ply)
        var best = stand
        for (i in 0 until n) {
            pickBest(moves, scores, i, n)
            val m = moves[i]
            // Delta-Beschneidung: aussichtslose Schlagzüge überspringen
            val gain = PIECE_VALUE[abs(b.sq[Move.to(m)])] + if (Move.promo(m) != 0) 800 else 0
            if (stand + gain + 200 < alpha && Move.promo(m) == 0) continue
            if (!b.makeMove(m)) continue
            val s = -quiesce(b, -beta, -alpha, ply + 1)
            b.unmakeMove()
            if (stopFlag) return 0
            if (s > best) {
                best = s
                if (s > alpha) {
                    alpha = s
                    if (s >= beta) break
                }
            }
        }
        return best
    }

    private fun scoreMoves(b: Board, moves: IntArray, scores: IntArray, n: Int, hashMove: Int, ply: Int) {
        val hist = historyTable[if (b.side > 0) 0 else 1]
        for (i in 0 until n) {
            val m = moves[i]
            val victim = abs(b.sq[Move.to(m)])
            scores[i] = when {
                m == hashMove -> 10_000_000
                victim != 0 -> 5_000_000 + PIECE_VALUE[victim] * 10 - abs(b.sq[Move.from(m)])
                Move.flag(m) == Move.FLAG_EN_PASSANT -> 5_000_000 + 1000
                Move.promo(m) == Piece.QUEEN -> 4_900_000
                m == killers[ply][0] -> 4_000_000
                m == killers[ply][1] -> 3_900_000
                else -> hist[Move.from(m) * 64 + Move.to(m)]
            }
        }
    }

    private fun pickBest(moves: IntArray, scores: IntArray, start: Int, n: Int) {
        var bi = start
        for (j in start + 1 until n) if (scores[j] > scores[bi]) bi = j
        if (bi != start) {
            val tm = moves[start]; moves[start] = moves[bi]; moves[bi] = tm
            val ts = scores[start]; scores[start] = scores[bi]; scores[bi] = ts
        }
    }

    private fun probeMove(hash: Long): Int {
        val idx = (hash and TT_MASK).toInt()
        return if (ttKey[idx] == hash) ttMove[idx] else Move.NONE
    }

    private fun hasPieces(b: Board): Boolean {
        for (p in b.sq) if (p * b.side > 0 && p * b.side in Piece.KNIGHT..Piece.QUEEN) return true
        return false
    }

    private fun toTt(s: Int, ply: Int) = when {
        s > MATE - 1000 -> s + ply
        s < -MATE + 1000 -> s - ply
        else -> s
    }

    private fun fromTt(s: Int, ply: Int) = when {
        s > MATE - 1000 -> s - ply
        s < -MATE + 1000 -> s + ply
        else -> s
    }

    companion object {
        const val MATE = 100_000
        private const val OPENING_PLIES = 6
        const val INF = 1_000_000
        private const val MAX_PLY = 96
        private const val TT_BITS = 19
        private const val TT_SIZE = 1 shl TT_BITS
        private const val TT_MASK = (TT_SIZE - 1).toLong()
        private const val EXACT = 0
        private const val LOWER = 1
        private const val UPPER = 2

        val PIECE_VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20000)

        /** Bewertung aus Sicht der Seite am Zug (in Centipawns). */
        fun evaluate(b: Board): Int = Eval.evaluate(b) * b.side
    }
}


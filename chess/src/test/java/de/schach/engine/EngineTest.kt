package de.schach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {

    private fun perft(b: Board, depth: Int): Long {
        if (depth == 0) return 1
        val buf = IntArray(256)
        val n = b.generatePseudo(buf)
        var total = 0L
        for (i in 0 until n) {
            if (b.makeMove(buf[i])) {
                total += perft(b, depth - 1)
                b.unmakeMove()
            }
        }
        return total
    }

    private fun board(fen: String) = Board().apply { loadFen(fen) }

    @Test
    fun perftStart() {
        val b = Board()
        assertEquals(20L, perft(b, 1))
        assertEquals(400L, perft(b, 2))
        assertEquals(8902L, perft(b, 3))
        assertEquals(197281L, perft(b, 4))
        assertEquals(Board.START_FEN, b.toFen())
    }

    @Test
    fun perftKiwipete() {
        val b = board("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(b, 1))
        assertEquals(2039L, perft(b, 2))
        assertEquals(97862L, perft(b, 3))
    }

    @Test
    fun perftPosition3() {
        val b = board("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(b, 1))
        assertEquals(191L, perft(b, 2))
        assertEquals(2812L, perft(b, 3))
        assertEquals(43238L, perft(b, 4))
    }

    @Test
    fun perftPosition4() {
        val b = board("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6L, perft(b, 1))
        assertEquals(264L, perft(b, 2))
        assertEquals(9467L, perft(b, 3))
    }

    @Test
    fun perftPosition5() {
        val b = board("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(44L, perft(b, 1))
        assertEquals(1486L, perft(b, 2))
        assertEquals(62379L, perft(b, 3))
    }

    @Test
    fun hashIsIncremental() {
        val b = Board()
        val rnd = Random(1)
        repeat(200) {
            val moves = b.legalMoves()
            if (moves.isEmpty()) return
            b.makeMove(moves[rnd.nextInt(moves.size)])
            val fresh = Board().apply { loadFen(b.toFen()) }
            assertEquals(fresh.hash, b.hash)
        }
    }

    @Test
    fun findsMateInOne() {
        // Weiß: Dh5xf7# (Schäfermatt)
        val b = board("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4")
        val r = Engine().search(b, 4, 2000)
        assertEquals("h5f7", Move.uci(r.move))
        assertTrue(r.score > Engine.MATE - 10)
    }

    @Test
    fun findsBackRankMate() {
        val b = board("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1")
        val r = Engine().search(b, 6, 3000)
        assertEquals("d1d8", Move.uci(r.move))
    }

    @Test
    fun takesHangingQueen() {
        val b = board("rnb1kbnr/pppp1ppp/8/4p1q1/3P4/2N5/PPP1PPPP/R1BQKBNR w KQkq - 0 3")
        val r = Engine().search(b, 5, 2000)
        assertEquals("c1g5", Move.uci(r.move))
    }

    @Test
    fun allLevelsReturnLegalMoves() {
        val b = Board()
        val engine = Engine(Random(7))
        for (level in Level.ALL.take(7)) {
            val m = engine.chooseMove(b, level)
            assertTrue(m in b.legalMoves())
        }
        assertEquals(Board.START_FEN, b.toFen())
    }

    @Test
    fun sanAndStatus() {
        val b = Board()
        for (uci in listOf("f2f3", "e7e5", "g2g4")) b.makeMove(b.parseUci(uci)!!)
        val mate = b.parseUci("d8h4")!!
        assertEquals("Qh4#", b.toSan(mate))
        b.makeMove(mate)
        assertEquals(GameStatus.CHECKMATE, b.status())
        assertEquals("O-O", board("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").let { it.toSan(it.parseUci("e1g1")!!) })
    }

    @Test
    fun repetitionIsDraw() {
        val b = Board()
        repeat(2) {
            for (uci in listOf("g1f3", "g8f6", "f3g1", "f6g8")) b.makeMove(b.parseUci(uci)!!)
        }
        assertEquals(GameStatus.DRAW_REPETITION, b.status())
        assertNotEquals(GameStatus.ONGOING, b.status())
    }
}

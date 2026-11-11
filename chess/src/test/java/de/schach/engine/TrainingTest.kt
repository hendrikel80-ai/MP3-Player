package de.schach.engine

import de.schach.training.Trainer
import de.schach.training.TrainingData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {
    @Test
    fun openingsAreLegal() {
        for (o in TrainingData.openings) {
            val b = Board()
            for ((i, mv) in o.moves.withIndex()) {
                val m = b.parseSan(mv.san)
                assertNotNull("${o.name}: Zug ${i + 1} ${mv.san} ist illegal in ${b.toFen()}", m)
                b.makeMove(m!!)
            }
        }
        assertEquals(TrainingData.openings.size, TrainingData.openings.map { it.id }.toSet().size)
    }

    @Test
    fun puzzlesHaveExactMate() {
        for (p in TrainingData.puzzles) {
            val b = Board().apply { loadFen(p.fen) }
            assertEquals("${p.title}: Seite am Zug darf nicht im Schach stehen", false, b.inCheck(-b.side))
            val d = Trainer.mateDistance(b, p.mateIn, Engine())
            assertEquals("${p.title}: Matt in", p.mateIn, d)
        }
    }

    @Test
    fun puzzleMoveCheck() {
        val p = TrainingData.puzzles.first { it.id == "philidor" }
        val b = Board().apply { loadFen(p.fen) }
        assertTrue(Trainer.forcesMate(b, b.parseUci("d5g8")!!, 2, Engine()))
        assertEquals(false, Trainer.forcesMate(b, b.parseUci("h6f7")!!, 2, Engine()))
    }

    @Test
    fun lessonsAreLegal() {
        for (l in TrainingData.mateLessons) {
            val b = Board().apply { loadFen(l.fen) }
            assertEquals(GameStatus.ONGOING, b.status())
            assertEquals(false, b.inCheck(-b.side))
        }
    }
}

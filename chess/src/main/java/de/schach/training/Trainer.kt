package de.schach.training

import de.schach.engine.Board
import de.schach.engine.Engine
import de.schach.engine.GameStatus

object Trainer {
    /**
     * Prüft, ob [move] in der Stellung [board] ein Matt in höchstens [movesLeft] eigenen Zügen erzwingt.
     * Die Stellung bleibt unverändert.
     */
    fun forcesMate(board: Board, move: Int, movesLeft: Int, engine: Engine): Boolean {
        val b = board.copy()
        if (!b.makeMove(move)) return false
        if (b.status() == GameStatus.CHECKMATE) return true
        if (movesLeft <= 1) return false
        val k = movesLeft - 1
        val score = engine.search(b, 2 * k + 1, 8000).score
        return score <= -Engine.MATE + 2 * k
    }

    /** Anzahl Züge bis Matt, die die Engine für die Seite am Zug findet (oder null). */
    fun mateDistance(board: Board, maxMoves: Int, engine: Engine): Int? {
        val score = engine.search(board, 2 * maxMoves + 1, 10000).score
        if (score < Engine.MATE - 200) return null
        return (Engine.MATE - score + 1) / 2
    }
}

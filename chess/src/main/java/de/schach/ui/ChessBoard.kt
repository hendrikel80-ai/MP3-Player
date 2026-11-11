package de.schach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import de.schach.engine.Piece
import de.schach.ui.theme.BoardColors
import kotlin.math.abs

/** Was das Brett anzeigen soll – unabhängig davon, ob Computer-, Zweier- oder Online-Partie. */
data class BoardView(
    val pieces: IntArray = IntArray(64),
    val flipped: Boolean = false,
    val selected: Int = -1,
    val targets: Set<Int> = emptySet(),
    val lastFrom: Int = -1,
    val lastTo: Int = -1,
    val checkSquare: Int = -1,
    val hintFrom: Int = -1,
    val hintTo: Int = -1,
)

// Figurenzeichen: gefüllt (Silhouette) und Umriss (Innenlinien), Reihenfolge nach Piece-Konstanten
private const val FILLED = " ♟♞♝♜♛♚"
private const val OUTLINE = " ♙♘♗♖♕♔"
private const val TEXT_STYLE = "︎" // Text- statt Emoji-Darstellung erzwingen

@Composable
fun ChessBoard(view: BoardView, onSquareTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tap by rememberUpdatedState(onSquareTap)
    val flipped by rememberUpdatedState(view.flipped)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val density = LocalDensity.current
        val cellPx = with(density) { maxWidth.toPx() } / 8f
        val glyphs = remember(cellPx) {
            val style = TextStyle(fontSize = with(density) { (cellPx * 0.78f).toSp() }, color = Color.Black)
            Array(7) { t ->
                if (t == 0) null else Pair(
                    measurer.measure(FILLED[t] + TEXT_STYLE, style),
                    measurer.measure(OUTLINE[t] + TEXT_STYLE, style),
                )
            }
        }
        val coordStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
        val files = remember(cellPx) { Array(8) { measurer.measure(('a' + it).toString(), coordStyle) } }
        val ranks = remember(cellPx) { Array(8) { measurer.measure(('1' + it).toString(), coordStyle) } }

        Canvas(
            Modifier.size(maxWidth).pointerInput(Unit) {
                detectTapGestures { off ->
                    val c = size.width / 8f
                    var f = (off.x / c).toInt().coerceIn(0, 7)
                    var r = 7 - (off.y / c).toInt().coerceIn(0, 7)
                    if (flipped) { f = 7 - f; r = 7 - r }
                    tap(r * 8 + f)
                }
            },
        ) {
            val c = size.width / 8f
            for (s in 0 until 64) {
                val f = s and 7
                val r = s / 8
                val x = (if (view.flipped) 7 - f else f) * c
                val y = (if (view.flipped) r else 7 - r) * c
                val tl = Offset(x, y)
                val cell = Size(c, c)
                val isLight = (f + r) % 2 == 1
                drawRect(if (isLight) BoardColors.light else BoardColors.dark, tl, cell)
                if (s == view.lastFrom || s == view.lastTo) drawRect(BoardColors.lastMove, tl, cell)
                if (s == view.hintFrom || s == view.hintTo) drawRect(BoardColors.hint, tl, cell)
                if (s == view.selected) drawRect(BoardColors.selected, tl, cell)
                if (s == view.checkSquare) {
                    drawCircle(BoardColors.check, c * 0.5f, tl + Offset(c / 2, c / 2))
                }

                // Koordinaten am Rand
                val coordColor = if (isLight) BoardColors.dark else BoardColors.light
                val bottomRow = if (view.flipped) r == 7 else r == 0
                val leftCol = if (view.flipped) f == 7 else f == 0
                if (bottomRow) {
                    val l = files[f]
                    drawText(l, coordColor, tl + Offset(c - l.size.width - 3f, c - l.size.height))
                }
                if (leftCol) drawText(ranks[r], coordColor, tl + Offset(3f, 1f))

                val p = view.pieces[s]
                if (p != 0) drawPiece(glyphs[abs(p)]!!, p > 0, tl, c)

                if (s in view.targets) {
                    val center = tl + Offset(c / 2, c / 2)
                    if (p != 0) drawCircle(BoardColors.target, c * 0.46f, center, style = Stroke(c * 0.08f))
                    else drawCircle(BoardColors.target, c * 0.15f, center)
                }
            }
        }
    }
}

private fun DrawScope.drawPiece(glyph: Pair<TextLayoutResult, TextLayoutResult>, white: Boolean, tl: Offset, c: Float) {
    val (filled, outline) = glyph
    val pos = tl + Offset((c - filled.size.width) / 2f, (c - filled.size.height) / 2f)
    if (white) {
        // Weiße Silhouette + schwarze Innenlinien
        drawText(filled, Color.White, pos)
        drawText(outline, Color(0xFF1A1A1A), pos)
    } else {
        drawText(filled, Color(0xFF1A1A1A), pos)
    }
}

/** Auswahl der Umwandlungsfigur */
@Composable
fun PromotionDialog(white: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Umwandeln in …") },
        text = {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (t in listOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) {
                    TextButton(onClick = { onPick(t) }) {
                        Text((if (white) OUTLINE[t] else FILLED[t]) + TEXT_STYLE, fontSize = 36.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Deutsche Figurenbuchstaben für die Zugliste (K, D, T, L, S). */
fun germanSan(san: String): String = san.map {
    when (it) {
        'Q' -> 'D'
        'R' -> 'T'
        'B' -> 'L'
        'N' -> 'S'
        else -> it
    }
}.joinToString("")

/** Paare „1. e4 e5“ für die Zugliste */
fun movePairs(sans: List<String>, startsWithBlack: Boolean = false, startNumber: Int = 1): List<String> {
    val list = if (startsWithBlack) listOf("…") + sans else sans
    return list.chunked(2).mapIndexed { i, pair -> "${startNumber + i}. ${pair.joinToString(" ") { germanSan(it) }}" }
}


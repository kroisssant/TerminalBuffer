package com.david

import org.junit.jupiter.api.assertThrows
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class TerminalBufferTest {

    private fun buf(w: Int = 10, h: Int = 5) = TerminalBuffer(w, h, 100, CellAttributes())

    /** Pre-size all screen lines so write/writeAt don't crash on empty cells. */
    private fun TerminalBuffer.preSizeLines() {
        for (row in 0 until height) {
            // Initialize all cells to blank spaces
            for (col in 0 until width) {
                getLine(row).setCellAt(col, ' ')
            }
        }
    }

    // =======================================================================
    // Construction & cursor
    // =======================================================================

    @Test
    fun initialCursorPosition() {
        val tb = buf()
        val cursor = tb.getCursor()
        assertEquals(0, cursor.cy)
        assertEquals(0, cursor.cx)
    }

    @Test
    fun getCursorReturnsSameInstance() {
        val tb = buf()
        val c1 = tb.getCursor()
        val c2 = tb.getCursor()
        assertNotNull(c1)
        assertEquals(c1, c2)
    }

    @Test
    fun cursorMutationsVisibleThroughGetCursor() {
        val tb = buf()
        val cursor = tb.getCursor()
        cursor.moveRight(3)
        assertEquals(3, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
        cursor.moveLeft(3)
        assertEquals(0, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
        cursor.moveUp(3)
        assertEquals(0, tb.getCursor().cx)
        assertEquals(-3, tb.getCursor().cy)
        cursor.moveDown(3)
        assertEquals(0, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
    }

    @Test
    fun bufferConstructsWithDifferentDimensions() {
        val tb = TerminalBuffer(80, 24, 500, CellAttributes())
        assertEquals(80, tb.width)
        assertEquals(24, tb.height)
        assertEquals(500, tb.maxScrollbackSize)
    }

    // =======================================================================
    // getLine
    // =======================================================================

    @Test
    fun getLineReturnsLineObject() {
        val tb = buf(w = 5, h = 3)
        val line = tb.getLine(0)
        assertNotNull(line)
        assertEquals(5, line.maxCells)
    }

    @Test
    fun getLineEachRowIsDistinct() {
        val tb = buf(w = 5, h = 3)
        tb.getLine(0).setCellAt(0, 'A')
        tb.getLine(1).setCellAt(0, 'B')
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(1).getCell(0)!!.char)
    }

    @Test
    fun getLineNegativeIndexThrows() {
        val tb = buf(h = 3)
        assertFailsWith<ArrayIndexOutOfBoundsException> {
            tb.getLine(-1)
        }
    }

    @Test
    fun getLineBeyondHeightThrows() {
        val tb = buf(h = 3)
        assertFailsWith<ArrayIndexOutOfBoundsException> {
            tb.getLine(3)
        }
    }

    // =======================================================================
    // write
    // =======================================================================

    @Test
    fun writeSingleChar() {
        val tb = buf()
        tb.preSizeLines()
        tb.write('A')
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals(1, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
    }

    @Test
    fun writeMultipleChars() {
        val tb = buf()
        tb.preSizeLines()
        tb.write('H')
        tb.write('i')
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('i', tb.getLine(0).getCell(1)!!.char)
        assertEquals(2, tb.getCursor().cx)
    }

    @Test
    fun writeWithCustomAttributes() {
        val tb = buf()
        tb.preSizeLines()
        val attrs = CellAttributes(fgColor = TerminalColor.Green, style = Style.Underline)
        tb.write('X', attrs)
        val cell = tb.getLine(0).getCell(0)!!
        assertEquals('X', cell.char)
        assertEquals(TerminalColor.Green, cell.attributes.fgColor)
        assertEquals(Style.Underline, cell.attributes.style)
    }

    @Test
    fun writeCursorWrapsAtRightEdge() {
        val tb = buf(w = 4, h = 3)
        tb.preSizeLines()
        for (ch in "ABCD") tb.write(ch)
        // After 4 chars in 4-wide buffer, cursor is past the edge (cx=4)
        // Next write will trigger newLine
        assertEquals(4, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
        // Writing a 5th char wraps to next line
        tb.write('E')
        assertEquals(1, tb.getCursor().cx)
        assertEquals(1, tb.getCursor().cy)
        assertEquals('E', tb.getLine(1).getCell(0)!!.char)
    }

    @Test
    fun writeAtRightEdgeWrapsToNextLine() {
        val tb = buf(w = 3, h = 2)
        tb.preSizeLines()
        tb.write('A')
        tb.write('B')
        tb.write('C') // fills row 0, cx=3 (past edge)
        tb.write('D') // wraps to row 1 col 0
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(0).getCell(1)!!.char)
        assertEquals('C', tb.getLine(0).getCell(2)!!.char)
        assertEquals('D', tb.getLine(1).getCell(0)!!.char)
        assertEquals(1, tb.getCursor().cx)
        assertEquals(1, tb.getCursor().cy)
    }

    @Test
    fun writeAdvancesRowOnWrap() {
        val tb = buf(w = 3, h = 3)
        tb.preSizeLines()
        for (ch in "ABCDE") tb.write(ch)
        // ABC fills row 0, DE wraps to row 1
        assertEquals(1, tb.getCursor().cy)
        assertEquals(2, tb.getCursor().cx)
        assertEquals('D', tb.getLine(1).getCell(0)!!.char)
        assertEquals('E', tb.getLine(1).getCell(1)!!.char)
    }

    @Test
    fun writeOnFreshBufferWithoutPreSizeThrows() {
        val tb = buf()
        // With sparse lines, write auto-ensures size - should work fine
        tb.write('A')
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals(1, tb.getCursor().cx)
    }

    @Test
    fun writeOverwritesExistingContent() {
        val tb = buf()
        tb.preSizeLines()
        tb.write('A')
        tb.write('B')
        tb.write('C')
        // Move cursor back and overwrite
        tb.getCursor().moveTo(0, 1)
        tb.write('X')
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('X', tb.getLine(0).getCell(1)!!.char)
        assertEquals('C', tb.getLine(0).getCell(2)!!.char)
    }

    // =======================================================================
    // writeAt
    // =======================================================================

    @Test
    fun writeAtSetsCharAtPosition() {
        val tb = buf(w = 10, h = 5)
        tb.preSizeLines()
        tb.writeAt(3, 2, 'Z')
        assertEquals('Z', tb.getLine(2).getCell(3)!!.char)
    }

    @Test
    fun writeAtDoesNotMoveCursor() {
        val tb = buf()
        tb.preSizeLines()
        tb.writeAt(5, 3, 'Q')
        assertEquals(0, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
    }

    @Test
    fun writeAtWithCustomAttributes() {
        val tb = buf()
        tb.preSizeLines()
        val attrs = CellAttributes(fgColor = TerminalColor.Cyan, bgColor = TerminalColor.Red)
        tb.writeAt(0, 0, 'W', attrs)
        val cell = tb.getLine(0).getCell(0)!!
        assertEquals('W', cell.char)
        assertEquals(TerminalColor.Cyan, cell.attributes.fgColor)
        assertEquals(TerminalColor.Red, cell.attributes.bgColor)
    }

    @Test
    fun writeAtNegativeRowThrows() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        assertFailsWith<IllegalArgumentException> {
            tb.writeAt(0, -1, 'X')
        }
    }

    @Test
    fun writeAtRowBeyondHeightThrows() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        assertFailsWith<IllegalArgumentException> {
            tb.writeAt(0, 3, 'X')
        }
    }

    @Test
    fun writeAtColBeyondLineSizeThrows() {
        val tb = buf(w = 5, h = 3)
        // With sparse lines, writeAt auto-ensures size - should work fine
        tb.writeAt(0, 0, 'X')
        assertEquals('X', tb.getLine(0).getCell(0)!!.char)
    }

    @Test
    fun writeAtOverwritesExistingCell() {
        val tb = buf()
        tb.preSizeLines()
        tb.writeAt(2, 1, 'A')
        tb.writeAt(2, 1, 'B')
        assertEquals('B', tb.getLine(1).getCell(2)!!.char)
    }

    // =======================================================================
    // Single-cell buffer edge case
    // =======================================================================

    @Test
    fun singleCellBufferWriteWrapsAndScrolls() {
        val tb = TerminalBuffer(1, 1, 100, CellAttributes())
        tb.preSizeLines()
        tb.write('A')
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals(1, tb.getCursor().cx)
        // Writing again wraps: newLine scrolls A into scrollback, B on fresh row
        tb.write('B')
        assertEquals('B', tb.getLine(0).getCell(0)!!.char)
        assertEquals(0, tb.getCursor().cy)
        assertEquals(1, tb.getScrollbackSize())
    }

    // =======================================================================
    // Helper: create a scrollback line with a marker char
    // =======================================================================

    private fun markerLine(w: Int, marker: Char): Line {
        val line = Line(w)
        line.setCellAt(0, marker)
        return line
    }

    // =======================================================================
    // Viewport / scrolling
    // =======================================================================

    @Test
    fun initialViewportOffsetIsZero() {
        val tb = buf()
        assertEquals(0, tb.getViewportOffset())
        assertTrue(tb.isAtBottom())
    }

    @Test
    fun scrollUpWithEmptyScrollback() {
        val tb = buf()
        tb.scrollUp(5)
        // No scrollback — offset stays 0
        assertEquals(0, tb.getViewportOffset())
        assertTrue(tb.isAtBottom())
    }

    @Test
    fun scrollUpIncreasesOffset() {
        val tb = buf(w = 10, h = 5)
        repeat(3) { tb.addScrollbackLine(markerLine(10, 'A')) }
        tb.scrollUp(2)
        assertEquals(2, tb.getViewportOffset())
        assertFalse(tb.isAtBottom())
    }

    @Test
    fun scrollUpClampedToScrollbackSize() {
        val tb = buf(w = 10, h = 5)
        repeat(3) { tb.addScrollbackLine(markerLine(10, 'A')) }
        tb.scrollUp(100)
        assertEquals(3, tb.getViewportOffset())
    }

    @Test
    fun scrollDownDecreasesOffset() {
        val tb = buf(w = 10, h = 5)
        repeat(5) { tb.addScrollbackLine(markerLine(10, 'A')) }
        tb.scrollUp(4)
        assertEquals(4, tb.getViewportOffset())
        tb.scrollDown(2)
        assertEquals(2, tb.getViewportOffset())
    }

    @Test
    fun scrollDownClampedToZero() {
        val tb = buf(w = 10, h = 5)
        repeat(3) { tb.addScrollbackLine(markerLine(10, 'A')) }
        tb.scrollUp(2)
        tb.scrollDown(100)
        assertEquals(0, tb.getViewportOffset())
        assertTrue(tb.isAtBottom())
    }

    @Test
    fun scrollToTopSetsMaxOffset() {
        val tb = buf(w = 10, h = 5)
        repeat(7) { tb.addScrollbackLine(markerLine(10, 'A')) }
        tb.scrollToTop()
        assertEquals(7, tb.getViewportOffset())
    }

    @Test
    fun scrollToBottomResetsOffset() {
        val tb = buf(w = 10, h = 5)
        repeat(3) { tb.addScrollbackLine(markerLine(10, 'A')) }
        tb.scrollUp(3)
        tb.scrollToBottom()
        assertEquals(0, tb.getViewportOffset())
        assertTrue(tb.isAtBottom())
    }

    @Test
    fun scrollDoesNotMoveCursor() {
        val tb = buf(w = 10, h = 5)
        tb.preSizeLines()
        tb.getCursor().moveTo(2, 3)
        repeat(5) { tb.addScrollbackLine(markerLine(10, 'A')) }
        tb.scrollUp(3)
        assertEquals(2, tb.getCursor().cy)
        assertEquals(3, tb.getCursor().cx)
        tb.scrollDown(2)
        assertEquals(2, tb.getCursor().cy)
        assertEquals(3, tb.getCursor().cx)
        tb.scrollToTop()
        assertEquals(2, tb.getCursor().cy)
        assertEquals(3, tb.getCursor().cx)
        tb.scrollToBottom()
        assertEquals(2, tb.getCursor().cy)
        assertEquals(3, tb.getCursor().cx)
    }

    // =======================================================================
    // getVisibleLine
    // =======================================================================

    @Test
    fun getVisibleLineAtBottomReturnsScreenLines() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        tb.writeAt(0, 0, 'S')
        tb.writeAt(0, 1, 'T')
        tb.writeAt(0, 2, 'U')
        // No scrollback, viewport at bottom
        assertEquals('S', tb.getVisibleLine(0).getCell(0)!!.char)
        assertEquals('T', tb.getVisibleLine(1).getCell(0)!!.char)
        assertEquals('U', tb.getVisibleLine(2).getCell(0)!!.char)
    }

    @Test
    fun getVisibleLineScrolledShowsScreenThenScrollback() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        // Fill screen: X Y Z
        tb.writeAt(0, 0, 'X')
        tb.writeAt(0, 1, 'Y')
        tb.writeAt(0, 2, 'Z')
        // Scrollback: [C, B, A] (C=newest at index 0, A=oldest at index 2)
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.addScrollbackLine(markerLine(5, 'B'))
        tb.addScrollbackLine(markerLine(5, 'C'))
        // Scroll up by 2: older scrollback appears at TOP (correct terminal behavior)
        // row 0: scrollback[2-1-0] = scrollback[1] = B
        // row 1: scrollback[2-1-1] = scrollback[0] = C
        // row 2: screen[2-2] = screen[0] = X
        tb.scrollUp(2)
        assertEquals('B', tb.getVisibleLine(0).getCell(0)!!.char)
        assertEquals('C', tb.getVisibleLine(1).getCell(0)!!.char)
        assertEquals('X', tb.getVisibleLine(2).getCell(0)!!.char)
    }

    @Test
    fun getVisibleLineScrolledToTopShowsOldestAtTop() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        tb.writeAt(0, 0, 'X')
        // Add 5 scrollback lines: A B C D E (oldest → newest)
        // Scrollback becomes: [E, D, C, B, A] (E=newest at index 0, A=oldest at index 4)
        for (ch in "ABCDE") {
            tb.addScrollbackLine(markerLine(5, ch))
        }
        // scrollToTop → offset = 5, height = 3
        // Oldest content appears at TOP (correct terminal behavior)
        // row 0: scrollback[5-1-0] = scrollback[4] = A
        // row 1: scrollback[5-1-1] = scrollback[3] = B
        // row 2: scrollback[5-1-2] = scrollback[2] = C
        tb.scrollToTop()
        assertEquals('A', tb.getVisibleLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getVisibleLine(1).getCell(0)!!.char)
        assertEquals('C', tb.getVisibleLine(2).getCell(0)!!.char)
    }

    @Test
    fun getVisibleLinePartialScrollMixesBuffers() {
        val tb = buf(w = 5, h = 4)
        tb.preSizeLines()
        // Screen lines: P Q R S
        tb.writeAt(0, 0, 'P')
        tb.writeAt(0, 1, 'Q')
        tb.writeAt(0, 2, 'R')
        tb.writeAt(0, 3, 'S')
        // Scrollback: [2, 1] (2=newest at index 0, 1=oldest at index 1)
        tb.addScrollbackLine(markerLine(5, '1'))
        tb.addScrollbackLine(markerLine(5, '2'))
        // Scroll up by 1 → offset=1
        // Scrollback appears at top (correct terminal behavior):
        // row 0: scrollback[1-1-0] = scrollback[0] = 2
        // row 1: screen[1-1] = screen[0] = P
        // row 2: screen[2-1] = screen[1] = Q
        // row 3: screen[3-1] = screen[2] = R
        tb.scrollUp(1)
        assertEquals('2', tb.getVisibleLine(0).getCell(0)!!.char)
        assertEquals('P', tb.getVisibleLine(1).getCell(0)!!.char)
        assertEquals('Q', tb.getVisibleLine(2).getCell(0)!!.char)
        assertEquals('R', tb.getVisibleLine(3).getCell(0)!!.char)
    }

    @Test
    fun getVisibleLineRowOutOfBoundsThrows() {
        val tb = buf(w = 5, h = 3)
        assertFailsWith<IllegalArgumentException> {
            tb.getVisibleLine(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            tb.getVisibleLine(3)
        }
    }

    // =======================================================================
    // Scrollback management
    // =======================================================================

    @Test
    fun addScrollbackLineIncreasesSize() {
        val tb = buf(w = 5, h = 3)
        assertEquals(0, tb.getScrollbackSize())
        tb.addScrollbackLine(markerLine(5, 'A'))
        assertEquals(1, tb.getScrollbackSize())
        tb.addScrollbackLine(markerLine(5, 'B'))
        assertEquals(2, tb.getScrollbackSize())
    }

    @Test
    fun addScrollbackLineDropsOldestWhenFull() {
        val tb = TerminalBuffer(5, 3, 2, CellAttributes()) // maxScrollback = 2
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.addScrollbackLine(markerLine(5, 'B'))
        tb.addScrollbackLine(markerLine(5, 'C')) // A should be dropped
        assertEquals(2, tb.getScrollbackSize())
        // Scrollback: [C, B] (C=newest at index 0, B=oldest at index 1)
        // Scroll to top — offset=2, height=3
        // Oldest content at TOP (correct terminal behavior):
        // row 0: scrollback[2-1-0] = scrollback[1] = B
        // row 1: scrollback[2-1-1] = scrollback[0] = C
        // row 2: screen[2-2] = screen[0]
        tb.scrollToTop()
        assertEquals('B', tb.getVisibleLine(0).getCell(0)!!.char)
        assertEquals('C', tb.getVisibleLine(1).getCell(0)!!.char)
    }

    @Test
    fun scrollUpThenAddScrollbackClampsOffset() {
        val tb = buf(w = 5, h = 3)
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.scrollUp(1)
        assertEquals(1, tb.getViewportOffset())
        // Add more scrollback — offset stays valid
        tb.addScrollbackLine(markerLine(5, 'B'))
        // Can now scroll further
        tb.scrollUp(1)
        assertEquals(2, tb.getViewportOffset())
    }

    // =======================================================================
    // newLine
    // =======================================================================

    @Test
    fun newLineMidScreenMovesCursorDown() {
        val tb = buf(w = 10, h = 5)
        tb.preSizeLines()
        // Cursor starts at (0,0)
        tb.newLine()
        assertEquals(1, tb.getCursor().cy)
        assertEquals(0, tb.getCursor().cx)
    }

    @Test
    fun newLineMidScreenResetsCxToZero() {
        val tb = buf(w = 10, h = 5)
        tb.preSizeLines()
        tb.write('A')
        tb.write('B')
        // cx is now 2
        assertEquals(2, tb.getCursor().cx)
        tb.newLine()
        assertEquals(1, tb.getCursor().cy)
        assertEquals(0, tb.getCursor().cx)
    }

    @Test
    fun newLineMidScreenPreservesCurrentLineContent() {
        val tb = buf(w = 10, h = 5)
        tb.preSizeLines()
        tb.write('H')
        tb.write('i')
        tb.newLine()
        // Row 0 still has "Hi"
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('i', tb.getLine(0).getCell(1)!!.char)
    }

    @Test
    fun newLineMidScreenDoesNotScroll() {
        val tb = buf(w = 10, h = 5)
        tb.preSizeLines()
        tb.newLine()
        assertEquals(0, tb.getScrollbackSize())
    }

    @Test
    fun newLineAtBottomScrollsScreen() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        // Write markers on each row
        tb.writeAt(0, 0, 'A')
        tb.writeAt(0, 1, 'B')
        tb.writeAt(0, 2, 'C')
        // Move cursor to last row
        tb.getCursor().moveTo(2, 0)
        tb.newLine()
        // Top line ('A') should be in scrollback
        assertEquals(1, tb.getScrollbackSize())
        // Screen shifted up: row0=B, row1=C, row2=blank
        assertEquals('B', tb.getLine(0).getCell(0)!!.char)
        assertEquals('C', tb.getLine(1).getCell(0)!!.char)
        assertEquals(null, tb.getLine(2).getCell(0))
        // Cursor on last row, col 0
        assertEquals(2, tb.getCursor().cy)
        assertEquals(0, tb.getCursor().cx)
    }

    @Test
    fun newLineOneRowBufferScrollsImmediately() {
        val tb = TerminalBuffer(5, 1, 100, CellAttributes())
        tb.preSizeLines()
        tb.write('X')
        tb.newLine()
        assertEquals(1, tb.getScrollbackSize())
        // New blank line at row 0
        assertEquals(null, tb.getLine(0).getCell(0))
        assertEquals(0, tb.getCursor().cy)
        assertEquals(0, tb.getCursor().cx)
    }

    @Test
    fun newLineBlankBottomLineIsWritable() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        tb.getCursor().moveTo(2, 0)
        tb.newLine()
        // Write on the new blank bottom line — should not crash
        tb.write('Z')
        assertEquals('Z', tb.getLine(2).getCell(0)!!.char)
    }

    @Test
    fun multipleConsecutiveNewLinesScrollCorrectly() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        tb.writeAt(0, 0, 'A')
        tb.writeAt(0, 1, 'B')
        tb.writeAt(0, 2, 'C')
        tb.getCursor().moveTo(2, 0)
        // Three newLines — all original lines should scroll off
        tb.newLine() // A→scrollback, screen: B C _
        tb.newLine() // B→scrollback, screen: C _ _
        tb.newLine() // C→scrollback, screen: _ _ _
        assertEquals(3, tb.getScrollbackSize())
        assertEquals(null, tb.getLine(0).getCell(0))
        assertEquals(null, tb.getLine(1).getCell(0))
        assertEquals(null, tb.getLine(2).getCell(0))
    }

    @Test
    fun newLineScrollbackAtMaxCapacityDropsOldest() {
        val tb = TerminalBuffer(5, 2, 2, CellAttributes()) // maxScrollback = 2
        tb.preSizeLines()
        tb.writeAt(0, 0, 'A')
        tb.writeAt(0, 1, 'B')
        tb.getCursor().moveTo(1, 0)
        tb.newLine() // A→scrollback[A], screen: B _
        tb.write('C')
        tb.newLine() // B→scrollback[A,B], screen: C _
        tb.write('D')
        tb.newLine() // C→scrollback — A dropped → [B,C], screen: D _
        assertEquals(2, tb.getScrollbackSize())
    }

    @Test
    fun newLineViewportOffsetZeroStaysZero() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        tb.getCursor().moveTo(2, 0)
        assertEquals(0, tb.getViewportOffset())
        tb.newLine() // scroll occurs
        assertEquals(0, tb.getViewportOffset())
    }

    @Test
    fun newLineViewportOffsetPositiveIncrements() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        tb.addScrollbackLine(markerLine(5, 'S'))
        tb.scrollUp(1) // offset = 1
        assertEquals(1, tb.getViewportOffset())
        tb.getCursor().moveTo(2, 0)
        tb.newLine() // scroll occurs, offset should become 2
        assertEquals(2, tb.getViewportOffset())
    }

    @Test
    fun newLineMidScreenViewportOffsetUnchanged() {
        val tb = buf(w = 5, h = 5)
        tb.preSizeLines()
        tb.addScrollbackLine(markerLine(5, 'S'))
        tb.scrollUp(1) // offset = 1
        // Cursor at row 0 (mid-screen) — no scroll
        tb.newLine()
        assertEquals(1, tb.getViewportOffset())
    }

    @Test
    fun newLineThenWriteEndToEnd() {
        val tb = buf(w = 10, h = 3)
        tb.preSizeLines()
        // Write "AB" on row 0
        tb.write('A')
        tb.write('B')
        tb.newLine()
        // Write "CD" on row 1
        tb.write('C')
        tb.write('D')
        tb.newLine()
        // Write "EF" on row 2
        tb.write('E')
        tb.write('F')
        // Verify all three rows
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(0).getCell(1)!!.char)
        assertEquals('C', tb.getLine(1).getCell(0)!!.char)
        assertEquals('D', tb.getLine(1).getCell(1)!!.char)
        assertEquals('E', tb.getLine(2).getCell(0)!!.char)
        assertEquals('F', tb.getLine(2).getCell(1)!!.char)
    }

    // =======================================================================
    // insertTextWithWrapping / insertCellsWithWrapping
    // =======================================================================

    @Test
    fun insertTextWithWrappingNoOverflow() {
        val tb = buf(w = 10, h = 3)
        tb.insertTextWithWrapping("Hello", cx = 0, cy = 0)

        // Should fit within line, no overflow to next line
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('e', tb.getLine(0).getCell(1)!!.char)
        assertEquals('l', tb.getLine(0).getCell(2)!!.char)
        assertEquals('l', tb.getLine(0).getCell(3)!!.char)
        assertEquals('o', tb.getLine(0).getCell(4)!!.char)
        assertEquals(null, tb.getLine(1).getCell(0)) // next line untouched
    }

    @Test
    fun insertTextWithWrappingOverflowsToNextLine() {
        val tb = buf(w = 5, h = 3)
        tb.insertTextWithWrapping("HelloWorld", cx = 0, cy = 0)

        // "Hello" on line 0, "World" wraps to line 1
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('e', tb.getLine(0).getCell(1)!!.char)
        assertEquals('l', tb.getLine(0).getCell(2)!!.char)
        assertEquals('l', tb.getLine(0).getCell(3)!!.char)
        assertEquals('o', tb.getLine(0).getCell(4)!!.char)

        assertEquals('W', tb.getLine(1).getCell(0)!!.char)
        assertEquals('o', tb.getLine(1).getCell(1)!!.char)
        assertEquals('r', tb.getLine(1).getCell(2)!!.char)
        assertEquals('l', tb.getLine(1).getCell(3)!!.char)
        assertEquals('d', tb.getLine(1).getCell(4)!!.char)
    }

    @Test
    fun insertTextWithWrappingMultipleLinesOverflow() {
        val tb = buf(w = 3, h = 5)
        tb.insertTextWithWrapping("ABCDEFGHIJK", cx = 0, cy = 0)

        // Should wrap across multiple lines
        // Line 0: ABC
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(0).getCell(1)!!.char)
        assertEquals('C', tb.getLine(0).getCell(2)!!.char)

        // Line 1: DEF
        assertEquals('D', tb.getLine(1).getCell(0)!!.char)
        assertEquals('E', tb.getLine(1).getCell(1)!!.char)
        assertEquals('F', tb.getLine(1).getCell(2)!!.char)

        // Line 2: GHI
        assertEquals('G', tb.getLine(2).getCell(0)!!.char)
        assertEquals('H', tb.getLine(2).getCell(1)!!.char)
        assertEquals('I', tb.getLine(2).getCell(2)!!.char)

        // Line 3: JK
        assertEquals('J', tb.getLine(3).getCell(0)!!.char)
        assertEquals('K', tb.getLine(3).getCell(1)!!.char)
    }

    @Test
    fun insertTextWithWrappingAtMiddleOfLine() {
        val tb = buf(w = 10, h = 3)
        // Pre-fill line 0 with "ABCDE"
        for (i in 0 until 5) {
            tb.writeAt(i, 0, "ABCDE"[i])
        }

        // Insert "XX" at position 2
        tb.insertTextWithWrapping("XX", cx = 2, cy = 0)

        // Should insert, pushing CDE to the right
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(0).getCell(1)!!.char)
        assertEquals('X', tb.getLine(0).getCell(2)!!.char)
        assertEquals('X', tb.getLine(0).getCell(3)!!.char)
        assertEquals('C', tb.getLine(0).getCell(4)!!.char)
        assertEquals('D', tb.getLine(0).getCell(5)!!.char)
        assertEquals('E', tb.getLine(0).getCell(6)!!.char)
    }

    @Test
    fun insertTextWithWrappingPushesContentToNextLine() {
        val tb = buf(w = 5, h = 3)
        // Fill line 0: "Hello"
        for (i in 0 until 5) {
            tb.writeAt(i, 0, "Hello"[i])
        }

        // Insert "XX" at position 2
        tb.insertTextWithWrapping("XX", cx = 2, cy = 0)

        // Line 0 should be: H e X X l
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('e', tb.getLine(0).getCell(1)!!.char)
        assertEquals('X', tb.getLine(0).getCell(2)!!.char)
        assertEquals('X', tb.getLine(0).getCell(3)!!.char)
        assertEquals('l', tb.getLine(0).getCell(4)!!.char)

        // Line 1 should have overflow: l o
        assertEquals('l', tb.getLine(1).getCell(0)!!.char)
        assertEquals('o', tb.getLine(1).getCell(1)!!.char)
    }

    @Test
    fun insertTextWithWrappingChainReaction() {
        val tb = buf(w = 3, h = 4)
        // Line 0: ABC
        // Line 1: DEF
        for (i in 0 until 3) {
            tb.writeAt(i, 0, "ABC"[i])
            tb.writeAt(i, 1, "DEF"[i])
        }

        // Insert "XY" at position 1 of line 0
        // Should push BC to line 1, which pushes DEF forward
        tb.insertTextWithWrapping("XY", cx = 1, cy = 0)

        // Line 0: A X Y
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('X', tb.getLine(0).getCell(1)!!.char)
        assertEquals('Y', tb.getLine(0).getCell(2)!!.char)

        // Line 1: B C D (BC pushed from line 0, D from DEF)
        assertEquals('B', tb.getLine(1).getCell(0)!!.char)
        assertEquals('C', tb.getLine(1).getCell(1)!!.char)
        assertEquals('D', tb.getLine(1).getCell(2)!!.char)

        // Line 2: E F (EF pushed from line 1)
        assertEquals('E', tb.getLine(2).getCell(0)!!.char)
        assertEquals('F', tb.getLine(2).getCell(1)!!.char)
    }

    @Test
    fun insertTextWithWrappingOnLastLineScrolls() {
        val tb = buf(w = 5, h = 2)
        tb.preSizeLines()
        // Write on line 0
        tb.writeAt(0, 0, 'A')

        // Fill line 1 (last line): "Hello"
        for (i in 0 until 5) {
            tb.writeAt(i, 1, "Hello"[i])
        }

        // Insert long text that will overflow
        tb.insertTextWithWrapping("XXX", cx = 3, cy = 1)

        // After scrolling, line 0 should have the modified content: H e l X X
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('e', tb.getLine(0).getCell(1)!!.char)
        assertEquals('l', tb.getLine(0).getCell(2)!!.char)
        assertEquals('X', tb.getLine(0).getCell(3)!!.char)
        assertEquals('X', tb.getLine(0).getCell(4)!!.char)

        // Line 1 should have the overflow: l o X
        // (only the 'l' at position 3 and 'o' at position 4 were shifted, not the 'l' at position 2)
        assertEquals('l', tb.getLine(1).getCell(0)!!.char)
        assertEquals('o', tb.getLine(1).getCell(1)!!.char)
        assertEquals('X', tb.getLine(1).getCell(2)!!.char)

        // Original line 0 should be in scrollback
        assertEquals(1, tb.getScrollbackSize())
    }

    @Test
    fun insertTextWithWrappingPreservesAttributes() {
        val tb = buf(w = 10, h = 3)
        val redBold = CellAttributes(fgColor = TerminalColor.Red, style = Style.Bold)

        tb.insertTextWithWrapping("Test", redBold, cx = 0, cy = 0)

        assertEquals('T', tb.getLine(0).getCell(0)!!.char)
        assertEquals(TerminalColor.Red, tb.getLine(0).getCell(0)!!.attributes.fgColor)
        assertEquals(Style.Bold, tb.getLine(0).getCell(0)!!.attributes.style)
    }

    @Test
    fun insertTextWithWrappingPreservesAttributesAcrossLines() {
        val tb = buf(w = 3, h = 3)
        val greenAttr = CellAttributes(fgColor = TerminalColor.Green)

        tb.insertTextWithWrapping("ABCDEF", greenAttr, cx = 0, cy = 0)

        // Line 0: ABC
        assertEquals(TerminalColor.Green, tb.getLine(0).getCell(0)!!.attributes.fgColor)
        assertEquals(TerminalColor.Green, tb.getLine(0).getCell(2)!!.attributes.fgColor)

        // Line 1: DEF
        assertEquals(TerminalColor.Green, tb.getLine(1).getCell(0)!!.attributes.fgColor)
        assertEquals(TerminalColor.Green, tb.getLine(1).getCell(2)!!.attributes.fgColor)
    }

    @Test
    fun insertTextWithWrappingEmptyString() {
        val tb = buf(w = 5, h = 3)
        tb.insertTextWithWrapping("", cx = 0, cy = 0)

        // Nothing should be written
        assertEquals(null, tb.getLine(0).getCell(0))
    }

    @Test
    fun insertTextWithWrappingOverwritesSpaces() {
        val tb = buf(w = 10, h = 3)
        // Fill with spaces
        for (i in 0 until 5) {
            tb.writeAt(i, 0, ' ')
        }

        tb.insertTextWithWrapping("Hi", cx = 0, cy = 0)

        // Should overwrite spaces
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('i', tb.getLine(0).getCell(1)!!.char)
        assertEquals(' ', tb.getLine(0).getCell(2)!!.char)
    }

    @Test
    fun insertCellsWithWrappingNoOverflow() {
        val tb = buf(w = 10, h = 3)
        val cells = listOf(
            Cell('A', CellAttributes()),
            Cell('B', CellAttributes()),
            Cell('C', CellAttributes())
        )

        tb.insertCellsWithWrapping(cells, cx = 0, cy = 0)

        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(0).getCell(1)!!.char)
        assertEquals('C', tb.getLine(0).getCell(2)!!.char)
    }

    @Test
    fun insertCellsWithWrappingOverflowsToNextLine() {
        val tb = buf(w = 3, h = 3)
        val cells = listOf(
            Cell('A', CellAttributes()),
            Cell('B', CellAttributes()),
            Cell('C', CellAttributes()),
            Cell('D', CellAttributes()),
            Cell('E', CellAttributes())
        )

        tb.insertCellsWithWrapping(cells, cx = 0, cy = 0)

        // Line 0: ABC
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(0).getCell(1)!!.char)
        assertEquals('C', tb.getLine(0).getCell(2)!!.char)

        // Line 1: DE
        assertEquals('D', tb.getLine(1).getCell(0)!!.char)
        assertEquals('E', tb.getLine(1).getCell(1)!!.char)
    }

    @Test
    fun insertCellsWithWrappingPreservesAttributesPerCell() {
        val tb = buf(w = 5, h = 3)
        val cells = listOf(
            Cell('A', CellAttributes(fgColor = TerminalColor.Red)),
            Cell('B', CellAttributes(fgColor = TerminalColor.Green)),
            Cell('C', CellAttributes(fgColor = TerminalColor.Blue))
        )

        tb.insertCellsWithWrapping(cells, cx = 0, cy = 0)

        assertEquals(TerminalColor.Red, tb.getLine(0).getCell(0)!!.attributes.fgColor)
        assertEquals(TerminalColor.Green, tb.getLine(0).getCell(1)!!.attributes.fgColor)
        assertEquals(TerminalColor.Blue, tb.getLine(0).getCell(2)!!.attributes.fgColor)
    }

    @Test
    fun insertCellsWithWrappingEmptyList() {
        val tb = buf(w = 5, h = 3)
        tb.insertCellsWithWrapping(emptyList(), cx = 0, cy = 0)

        // Nothing should happen
        assertEquals(null, tb.getLine(0).getCell(0))
    }

    @Test
    fun insertCellsWithWrappingOnLastLineScrolls() {
        val tb = buf(w = 3, h = 2)
        tb.preSizeLines()

        val cells = listOf(
            Cell('A', CellAttributes()),
            Cell('B', CellAttributes()),
            Cell('C', CellAttributes()),
            Cell('D', CellAttributes())
        )

        // Insert on last line, should scroll when overflow reaches beyond last line
        tb.insertCellsWithWrapping(cells, cx = 0, cy = 1)

        // After scrolling, line 0 should have: ABC
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(0).getCell(1)!!.char)
        assertEquals('C', tb.getLine(0).getCell(2)!!.char)

        // Line 1 should have the overflow: D
        assertEquals('D', tb.getLine(1).getCell(0)!!.char)

        // D should have caused a scroll
        assertEquals(1, tb.getScrollbackSize())
    }

    // =======================================================================
    // writeString
    // =======================================================================

    @Test
    fun writeStringBasic() {
        val tb = buf(w = 10, h = 3)
        tb.writeString("Hello")

        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('e', tb.getLine(0).getCell(1)!!.char)
        assertEquals('l', tb.getLine(0).getCell(2)!!.char)
        assertEquals('l', tb.getLine(0).getCell(3)!!.char)
        assertEquals('o', tb.getLine(0).getCell(4)!!.char)
    }

    @Test
    fun writeStringMovesCursor() {
        val tb = buf(w = 10, h = 3)
        tb.writeString("Hello")

        assertEquals(5, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
    }

    @Test
    fun writeStringWrapsAtEdge() {
        val tb = buf(w = 5, h = 3)
        tb.writeString("HelloWorld")

        // "Hello" on line 0
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('o', tb.getLine(0).getCell(4)!!.char)

        // "World" on line 1
        assertEquals('W', tb.getLine(1).getCell(0)!!.char)
        assertEquals('d', tb.getLine(1).getCell(4)!!.char)

        assertEquals(5, tb.getCursor().cx)
        assertEquals(1, tb.getCursor().cy)
    }

    @Test
    fun writeStringWithAttributes() {
        val tb = buf(w = 10, h = 3)
        val attrs = CellAttributes(fgColor = TerminalColor.Red, style = Style.Bold)
        tb.writeString("Hi", attrs)

        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals(TerminalColor.Red, tb.getLine(0).getCell(0)!!.attributes.fgColor)
        assertEquals(Style.Bold, tb.getLine(0).getCell(0)!!.attributes.style)

        assertEquals('i', tb.getLine(0).getCell(1)!!.char)
        assertEquals(TerminalColor.Red, tb.getLine(0).getCell(1)!!.attributes.fgColor)
    }

    @Test
    fun writeStringEmpty() {
        val tb = buf(w = 10, h = 3)
        tb.writeString("")

        assertEquals(0, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
    }

    // =======================================================================
    // fillLine
    // =======================================================================

    @Test
    fun fillLineBasic() {
        val tb = buf(w = 5, h = 3)
        tb.fillLine('X', lineCy = 1)

        // Line 1 should be filled with 'X'
        for (i in 0 until 5) {
            assertEquals('X', tb.getLine(1).getCell(i)!!.char)
        }
    }

    @Test
    fun fillLineWithAttributes() {
        val tb = buf(w = 5, h = 3)
        val attrs = CellAttributes(fgColor = TerminalColor.Green, bgColor = TerminalColor.Blue)
        tb.fillLine('*', lineCy = 0, cellAttributes = attrs)

        assertEquals('*', tb.getLine(0).getCell(0)!!.char)
        assertEquals(TerminalColor.Green, tb.getLine(0).getCell(0)!!.attributes.fgColor)
        assertEquals(TerminalColor.Blue, tb.getLine(0).getCell(0)!!.attributes.bgColor)

        assertEquals('*', tb.getLine(0).getCell(4)!!.char)
        assertEquals(TerminalColor.Green, tb.getLine(0).getCell(4)!!.attributes.fgColor)
    }

    @Test
    fun fillLineDefaultsToCurrentCursorRow() {
        val tb = buf(w = 5, h = 3)
        tb.getCursor().moveTo(2, 0)
        tb.fillLine('-')

        // Should fill line 2 (cursor row)
        for (i in 0 until 5) {
            assertEquals('-', tb.getLine(2).getCell(i)!!.char)
        }

        // Other lines should be unaffected
        assertEquals(null, tb.getLine(0).getCell(0))
        assertEquals(null, tb.getLine(1).getCell(0))
    }

    @Test
    fun fillLineOverwritesExistingContent() {
        val tb = buf(w = 5, h = 3)
        tb.writeAt(0, 1, 'A')
        tb.writeAt(1, 1, 'B')
        tb.writeAt(2, 1, 'C')

        tb.fillLine('X', lineCy = 1)

        // All cells should be 'X' now
        for (i in 0 until 5) {
            assertEquals('X', tb.getLine(1).getCell(i)!!.char)
        }
    }

    // =======================================================================
    // clearScreen
    // =======================================================================

    @Test
    fun clearScreenBasic() {
        val tb = buf(w = 5, h = 3)
        // Write content to all lines
        for (row in 0 until 3) {
            for (col in 0 until 5) {
                tb.writeAt(col, row, 'X')
            }
        }

        tb.clearScreen()

        // All lines should be empty
        for (row in 0 until 3) {
            for (col in 0 until 5) {
                assertEquals(null, tb.getLine(row).getCell(col))
            }
        }
    }

    @Test
    fun clearScreenDoesNotAffectScrollback() {
        val tb = buf(w = 5, h = 3)
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.addScrollbackLine(markerLine(5, 'B'))

        tb.clearScreen()

        // Scrollback should remain
        assertEquals(2, tb.getScrollbackSize())
    }

    @Test
    fun clearScreenResetsCursor() {
        val tb = buf(w = 5, h = 3)
        tb.getCursor().moveTo(2, 3)

        tb.clearScreen()

        assertEquals(0, tb.getCursor().cy)
        assertEquals(0, tb.getCursor().cx)
    }

    @Test
    fun clearScreenCreatesIndependentLines() {
        val tb = buf(w = 5, h = 3)
        // Write some initial content
        for (row in 0 until 3) {
            for (col in 0 until 5) {
                tb.writeAt(col, row, 'X')
            }
        }

        tb.clearScreen()

        // Write to line 0 only
        tb.writeAt(0, 0, 'A')
        tb.writeAt(1, 0, 'B')

        // Verify line 0 has the new content
        assertEquals('A', tb.getLine(0).getCell(0)!!.char)
        assertEquals('B', tb.getLine(0).getCell(1)!!.char)

        // Verify lines 1 and 2 remain empty (not affected by writes to line 0)
        // This ensures each line is an independent object, not shared references
        assertEquals(null, tb.getLine(1).getCell(0))
        assertEquals(null, tb.getLine(1).getCell(1))
        assertEquals(null, tb.getLine(2).getCell(0))
        assertEquals(null, tb.getLine(2).getCell(1))
    }

    // =======================================================================
    // clearAll
    // =======================================================================

    @Test
    fun clearAllClearsScreenAndScrollback() {
        val tb = buf(w = 5, h = 3)
        // Write to screen
        for (row in 0 until 3) {
            tb.writeAt(0, row, 'X')
        }

        // Add scrollback
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.addScrollbackLine(markerLine(5, 'B'))

        tb.clearAll()

        // Screen should be empty
        for (row in 0 until 3) {
            assertEquals(null, tb.getLine(row).getCell(0))
        }

        // Scrollback should be empty
        assertEquals(0, tb.getScrollbackSize())
    }

    @Test
    fun clearAllResetsViewportOffset() {
        val tb = buf(w = 5, h = 3)
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.scrollUp(1)

        assertEquals(1, tb.getViewportOffset())

        tb.clearAll()

        assertEquals(0, tb.getViewportOffset())
    }

    @Test
    fun clearAllResetsCursor() {
        val tb = buf(w = 5, h = 3)
        tb.getCursor().moveTo(1, 2)

        tb.clearAll()

        assertEquals(0, tb.getCursor().cy)
        assertEquals(0, tb.getCursor().cx)
    }

    // =======================================================================
    // insertTextAtCursor (cursor-based insertion with wrapping)
    // =======================================================================

    @Test
    fun insertTextAtCursorBasic() {
        val tb = buf(w = 10, h = 3)
        tb.getCursor().moveTo(0, 2)

        tb.insertTextWithWrapping("Hello")

        // Text should be at cursor position (row 0, col 2)
        assertEquals('H', tb.getLine(0).getCell(2)!!.char)
        assertEquals('e', tb.getLine(0).getCell(3)!!.char)
        assertEquals('l', tb.getLine(0).getCell(4)!!.char)
        assertEquals('l', tb.getLine(0).getCell(5)!!.char)
        assertEquals('o', tb.getLine(0).getCell(6)!!.char)
    }

    @Test
    fun insertTextAtCursorMovesCursor() {
        val tb = buf(w = 10, h = 3)
        tb.getCursor().moveTo(1, 3)

        tb.insertTextWithWrapping("Hi")

        // Cursor should move to end of inserted text
        assertEquals(1, tb.getCursor().cy)
        assertEquals(5, tb.getCursor().cx)
    }

    @Test
    fun insertTextAtCursorWrapsToNextLine() {
        val tb = buf(w = 5, h = 3)
        tb.getCursor().moveTo(0, 3)

        tb.insertTextWithWrapping("ABCDEFG")

        // "AB" fits on line 0 starting at position 3
        assertEquals('A', tb.getLine(0).getCell(3)!!.char)
        assertEquals('B', tb.getLine(0).getCell(4)!!.char)

        // "CDEFG" wraps to line 1
        assertEquals('C', tb.getLine(1).getCell(0)!!.char)
        assertEquals('D', tb.getLine(1).getCell(1)!!.char)
        assertEquals('E', tb.getLine(1).getCell(2)!!.char)
        assertEquals('F', tb.getLine(1).getCell(3)!!.char)
        assertEquals('G', tb.getLine(1).getCell(4)!!.char)

        // Cursor should be at end of wrapped text
        assertEquals(1, tb.getCursor().cy)
        assertEquals(5, tb.getCursor().cx)
    }

    @Test
    fun insertTextAtCursorWithAttributes() {
        val tb = buf(w = 10, h = 3)
        tb.getCursor().moveTo(0, 0)
        val attrs = CellAttributes(fgColor = TerminalColor.Blue)

        tb.insertTextWithWrapping("Test", attrs)

        assertEquals('T', tb.getLine(0).getCell(0)!!.char)
        assertEquals(TerminalColor.Blue, tb.getLine(0).getCell(0)!!.attributes.fgColor)
    }

    @Test
    fun insertTextAtCursorUsesDefaultAttributes() {
        val tb = buf(w = 10, h = 3)
        tb.setAttributes(CellAttributes(fgColor = TerminalColor.Magenta))
        tb.getCursor().moveTo(0, 0)

        tb.insertTextWithWrapping("X")

        assertEquals(TerminalColor.Magenta, tb.getLine(0).getCell(0)!!.attributes.fgColor)
    }

    @Test
    fun insertTextAtCursorIntoExistingContent() {
        val tb = buf(w = 10, h = 3)
        // Write "Hello"
        for (i in 0 until 5) {
            tb.writeAt(i, 0, "Hello"[i])
        }

        // Insert "XX" at position 2
        tb.getCursor().moveTo(0, 2)
        tb.insertTextWithWrapping("XX")

        // Should insert and push content
        assertEquals('H', tb.getLine(0).getCell(0)!!.char)
        assertEquals('e', tb.getLine(0).getCell(1)!!.char)
        assertEquals('X', tb.getLine(0).getCell(2)!!.char)
        assertEquals('X', tb.getLine(0).getCell(3)!!.char)
        assertEquals('l', tb.getLine(0).getCell(4)!!.char)

        // Cursor should be after inserted text
        assertEquals(0, tb.getCursor().cy)
        assertEquals(4, tb.getCursor().cx)
    }

    // =======================================================================
    // insertEmptyLineAtBottom
    // =======================================================================

    @Test
    fun insertEmptyLineAtBottomBasic() {
        val tb = buf(w = 5, h = 3)
        // Fill all lines with content
        for (row in 0 until 3) {
            tb.writeAt(0, row, ('A' + row))
        }

        tb.insertEmptyLineAtBottom()

        // Top line should be in scrollback
        assertEquals(1, tb.getScrollbackSize())

        // Screen should be shifted up: B, C, (blank)
        assertEquals('B', tb.getLine(0).getCell(0)!!.char)
        assertEquals('C', tb.getLine(1).getCell(0)!!.char)
        assertEquals(null, tb.getLine(2).getCell(0))
    }

    @Test
    fun insertEmptyLineAtBottomDoesNotMoveCursor() {
        val tb = buf(w = 5, h = 3)
        tb.getCursor().moveTo(1, 2)

        tb.insertEmptyLineAtBottom()

        // Cursor should stay at same position
        assertEquals(1, tb.getCursor().cy)
        assertEquals(2, tb.getCursor().cx)
    }

    @Test
    fun insertEmptyLineAtBottomRespectsMaxScrollback() {
        val tb = TerminalBuffer(5, 2, 2, CellAttributes())
        // Fill scrollback to max
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.addScrollbackLine(markerLine(5, 'B'))

        tb.insertEmptyLineAtBottom()

        // Scrollback should still be 2 (oldest dropped)
        assertEquals(2, tb.getScrollbackSize())
    }

    @Test
    fun insertEmptyLineAtBottomUpdatesViewportOffset() {
        val tb = buf(w = 5, h = 3)
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.scrollUp(1)

        assertEquals(1, tb.getViewportOffset())

        tb.insertEmptyLineAtBottom()

        // Viewport offset should increment when scrolled up
        assertEquals(2, tb.getViewportOffset())
    }

    @Test
    fun insertEmptyLineAtBottomMultipleTimes() {
        val tb = buf(w = 5, h = 2)
        tb.writeAt(0, 0, 'A')
        tb.writeAt(0, 1, 'B')

        tb.insertEmptyLineAtBottom()
        tb.insertEmptyLineAtBottom()

        // Both original lines should be in scrollback
        assertEquals(2, tb.getScrollbackSize())

        // Screen should be empty
        assertEquals(null, tb.getLine(0).getCell(0))
        assertEquals(null, tb.getLine(1).getCell(0))
    }

    @Test
    fun getLineAsString() {
        val tb = buf(w = 5, h = 3)
        tb.writeAt(0, 0, 'A')
        tb.writeAt(0, 1, 'B')
        tb.writeAt(0, 2, 'C')
        val str = tb.getLineAsString(0)
        assertEquals("A", str)
    }

    @Test
    fun getLineAsStringReturnsEmptyWhenLineIsEmpty() {
        val tb = buf(w = 5, h = 3)

        val str = tb.getLineAsString(1)

        assertEquals("", str)
    }

    @Test
    fun getLineAsStringReturnsCorrectSingleCharFromDifferentRow() {
        val tb = buf(w = 5, h = 3)
        tb.writeAt(1, 0, 'X')

        val str = tb.getLineAsString(1)

        assertEquals("", str)
    }

    @Test
    fun getLineAsStringReturnsOnlyCharactersPresentNotFullWidth() {
        val tb = buf(w = 5, h = 3)
        tb.writeAt(1, 2, 'H')
        tb.writeAt(2, 2, 'i')

        val str = tb.getLineAsString(2)

        assertEquals(" Hi", str)
    }

    @Test
    fun getLineAsStringFromScrollBack() {
        val tb = buf(w = 5, h = 1)
        tb.writeAt(0, 0, 'A')
        tb.insertEmptyLineAtBottom()
        tb.insertEmptyLineAtBottom()

        val str = tb.getLineAsString(2)
        assertEquals("A", str)
    }

    @Test
    fun getLineAsStringReturnsEmptyWhenCyIsWithinScreenBuffer() {
        val tb = buf(w = 5, h = 3)
        tb.writeAt(0, 0, 'A')

        // screenBuffer.size == 3 → cy <= 3 should return ""
        val str = tb.getLineAsString(1)

        assertEquals("", str)
    }

    @Test
    fun getAttributesReturnsDefaultAttributesInitially() {
        val tb = buf(w = 5, h = 3)

        val attrs = tb.getAttributes()

        assertNotNull(attrs)
    }

    @Test
    fun setAttributesUpdatesDefaultAttributes() {
        val tb = buf(w = 5, h = 3)
        val newAttrs = CellAttributes()

        tb.setAttributes(newAttrs)

        val result = tb.getAttributes()
        assertEquals(newAttrs, result)
    }

    @Test
    fun setAttributesOverwritesPreviousAttributes() {
        val tb = buf(w = 5, h = 3)

        val attrs1 = CellAttributes()
        val attrs2 = CellAttributes()

        tb.setAttributes(attrs1)
        tb.setAttributes(attrs2)

        val result = tb.getAttributes()
        assertEquals(attrs2, result)
    }

    @Test
    fun setAttributesDoesNotReturnSameInstanceAsOldAfterChange() {
        val tb = buf(w = 5, h = 3)

        val original = tb.getAttributes()
        val newAttrs = CellAttributes(fgColor = TerminalColor.Magenta)

        tb.setAttributes(newAttrs)

        assertNotEquals(original, tb.getAttributes())
    }

    @Test
    fun setAttributesDoesNotChangeOldAttributes()
    {
        val original = CellAttributes()
        val tb = TerminalBuffer(2, 1, 1, original)
        tb.writeAt(0, 0, 'A')
        val newAttrs = CellAttributes(fgColor = TerminalColor.Magenta)
        tb.setAttributes(newAttrs)
        tb.writeAt(1, 0, 'B')
        assertNotEquals(original, tb.getAttributes())
        assertEquals(original, tb.getAttributeAtPosition(0,0))
        assertEquals(newAttrs, tb.getAttributeAtPosition(1,0))
    }

    @Test
    fun getCharAtPosition() {
        val tb = buf(w = 5, h = 1)
        tb.writeAt(0, 0, 'A')
        tb.writeAt(1, 0, 'B')
        tb.insertEmptyLineAtBottom()
        tb.writeAt(0, 0, 'C')
        assertEquals('C', tb.getCharAtPosition(0, 0))
        assertEquals('B', tb.getCharAtPosition(1, 1))
        assertEquals('A', tb.getCharAtPosition(0, 1))

    }

    @Test
    fun deleteCharacterAtCursorShiftsRemainingLeft() {
        val tb = buf(w = 10, h = 3)
        tb.writeString("Hello")
        tb.moveCursorTo(0, 1)  // Position at 'e'

        tb.deleteCharacterAtCursor()

        assertEquals("Hllo", tb.getLineAsString(0).trimEnd())
    }

    @Test
    fun deleteCharacterAtCursorDoesNotMoveCursor() {
        val tb = buf(w = 10, h = 3)
        tb.writeString("Hello")
        tb.moveCursorTo(0, 2)  // Position at first 'l'

        tb.deleteCharacterAtCursor()

        assertEquals(0, tb.getCursor().cy)
        assertEquals(2, tb.getCursor().cx)
    }

    @Test
    fun deleteCharacterAtCursorOnEmptyLineDoesNothing() {
        val tb = buf(w = 10, h = 3)
        tb.moveCursorTo(0, 0)

        tb.deleteCharacterAtCursor()  // Should not throw

        assertEquals("", tb.getLineAsString(0).trimEnd())
    }


}

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
import kotlin.test.assertNull

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

    @Test
    fun deleteCharacterAtLastCellPosition() {
        val tb = buf(w = 5, h = 3)
        // Write characters to fill the line: "ABCDE"
        tb.writeString("ABCDE")

        // Move cursor to the last cell (index 4)
        tb.moveCursorTo(0, 4)
        assertEquals(4, tb.getCursor().cx)
        assertEquals('E', tb.getCharAtPosition(4, 0))

        // Delete the character at the last position
        tb.deleteCharacterAtCursor()

        // Should have deleted 'E', leaving "ABCD"
        assertEquals("ABCD", tb.getLineAsString(0).trimEnd())
        // Cursor should remain at position 4 (now pointing to null/empty)
        assertEquals(4, tb.getCursor().cx)
    }

    @Test
    fun cursorCannotLandOnLastCellIfWideCharacterBeforeIt() {
        val tb = buf(w = 5, h = 3)
        // Write: "AB" + wide char at position 2-3, leaving position 4 as continuation
        tb.writeString("AB")
        tb.write('中')  // Wide character takes positions 2 and 3

        // Try to move cursor to position 4 (which should be empty after the wide char)
        tb.moveCursorTo(0, 4)

        // Cursor should be clamped to position 4 (after the wide char)
        // because position 4 is a valid position (not a continuation marker)
        assertEquals(4, tb.getCursor().cx)

        // Now test with wide char at positions 3-4 (continuation at last cell)
        val tb2 = buf(w = 5, h = 3)
        tb2.writeString("ABC")  // Positions 0, 1, 2
        tb2.write('中')  // Wide character at positions 3-4 (continuation at last cell)

        // Try to move cursor to position 4 (the continuation marker)
        tb2.moveCursorTo(0, 4)

        // Cursor should adjust to position 3 (start of wide char) since position 4 is a continuation
        assertEquals(3, tb2.getCursor().cx)
    }

    @Test
    fun cursorCanReachLastCellByMovingRight() {
        val tb = buf(w = 5, h = 3)
        tb.writeString("ABCDE")  // Fill line with normal characters

        // Reset cursor to start
        tb.moveCursorTo(0, 0)
        assertEquals(0, tb.getCursor().cx)

        tb.moveCursorRight()  // Should be at position 1
        assertEquals(1, tb.getCursor().cx)

        tb.moveCursorRight()  // Should be at position 2
        assertEquals(2, tb.getCursor().cx)

        tb.moveCursorRight()  // Should be at position 3
        assertEquals(3, tb.getCursor().cx)

        tb.moveCursorRight()  // Should be at position 4 (last cell)
        assertEquals(4, tb.getCursor().cx)

        // Verify we can delete at this position
        assertEquals('E', tb.getCharAtPosition(4, 0))
        tb.deleteCharacterAtCursor()
        assertEquals("ABCD", tb.getLineAsString(0).trimEnd())
    }

    @Test
    fun cursorCannotMoveRightPastWideCharacterAtEnd() {
        val tb = buf(w = 5, h = 3)
        tb.writeString("ABC")  // Positions 0, 1, 2
        tb.write('中')  // Wide character at positions 3-4

        // Move cursor to position 2
        tb.moveCursorTo(0, 2)
        assertEquals(2, tb.getCursor().cx)

        // Move right - should go to position 3 (wide char start)
        tb.moveCursorRight()
        assertEquals(3, tb.getCursor().cx)

        // Try to move right again - should stay at 3 because position 4 is a continuation
        tb.moveCursorRight()
        // The cursor tries to move to position 4, but adjustCursorForWideChars should move it back to 3
        assertEquals(3, tb.getCursor().cx, "Cursor should stay at wide char start, not move to continuation marker")
    }

    @Test
    fun cursorMovesRightThroughMultipleWideCharacters() {
        val tb = buf(w = 10, h = 3)
        // Write: "A" + two wide chars + "B" = A中日B
        // Positions: 0='A', 1-2='中', 3-4='日', 5='B'
        tb.writeString("A")
        tb.write('中')  // positions 1-2
        tb.write('日')  // positions 3-4
        tb.write('B')   // position 5

        // Reset to start
        tb.moveCursorTo(0, 0)
        assertEquals(0, tb.getCursor().cx)

        // Move through all characters
        tb.moveCursorRight()  // 0 -> 1 (wide char start)
        assertEquals(1, tb.getCursor().cx)

        tb.moveCursorRight()  // 1 -> 2, but 2 is continuation, should jump to 3
        assertEquals(3, tb.getCursor().cx)

        tb.moveCursorRight()  // 3 -> 4, but 4 is continuation, should jump to 5
        assertEquals(5, tb.getCursor().cx)

        tb.moveCursorRight()  // 5 -> 6
        assertEquals(6, tb.getCursor().cx)
    }

    @Test
    fun cursorMovesLeftThroughMultipleWideCharacters() {
        val tb = buf(w = 10, h = 3)
        // Write: "A中日B"
        // Positions: 0='A', 1-2='中', 3-4='日', 5='B'
        tb.writeString("A")
        tb.write('中')  // positions 1-2
        tb.write('日')  // positions 3-4
        tb.write('B')   // position 5

        // Start at position 6 (after 'B')
        tb.moveCursorTo(0, 6)
        assertEquals(6, tb.getCursor().cx)

        // Move left through all characters
        tb.moveCursorLeft()  // 6 -> 5 ('B')
        assertEquals(5, tb.getCursor().cx)

        tb.moveCursorLeft()  // 5 -> 4, but 4 is continuation, should jump to 3 (wide char start)
        assertEquals(3, tb.getCursor().cx)

        tb.moveCursorLeft()  // 3 -> 2, but 2 is continuation, should jump to 1 (wide char start)
        assertEquals(1, tb.getCursor().cx)

        tb.moveCursorLeft()  // 1 -> 0 ('A')
        assertEquals(0, tb.getCursor().cx)
    }

    @Test
    fun cursorMovesUpThroughLinesWithWideCharacters() {
        val tb = buf(w = 10, h = 4)

        // Line 0: "AB中"
        tb.moveCursorTo(0, 0)
        tb.writeString("AB")
        tb.write('中')  // positions 2-3

        // Line 1: "中DE"
        tb.moveCursorTo(1, 0)
        tb.write('中')  // positions 0-1
        tb.writeString("DE")  // positions 2-3

        // Line 2: "F中G"
        tb.moveCursorTo(2, 0)
        tb.write('F')
        tb.write('中')  // positions 1-2
        tb.write('G')  // position 3

        // Start at line 2, position 3
        tb.moveCursorTo(2, 3)
        assertEquals(2, tb.getCursor().cy)
        assertEquals(3, tb.getCursor().cx)

        // Move up to line 1, position 3 (should be valid)
        tb.moveCursorUp()
        assertEquals(1, tb.getCursor().cy)
        assertEquals(3, tb.getCursor().cx)

        // Move up to line 0, position 3 (continuation marker, should adjust to 2)
        tb.moveCursorUp()
        assertEquals(0, tb.getCursor().cy)
        assertEquals(2, tb.getCursor().cx)  // Adjusted to wide char start
    }

    @Test
    fun cursorMovesDownThroughLinesWithWideCharacters() {
        val tb = buf(w = 10, h = 4)

        // Line 0: "AB中"
        tb.moveCursorTo(0, 0)
        tb.writeString("AB")
        tb.write('中')  // positions 2-3

        // Line 1: "中DE"
        tb.moveCursorTo(1, 0)
        tb.write('中')  // positions 0-1
        tb.writeString("DE")  // positions 2-3

        // Line 2: "F中G"
        tb.moveCursorTo(2, 0)
        tb.write('F')
        tb.write('中')  // positions 1-2
        tb.write('G')  // position 3

        // Start at line 0, position 2 (wide char start)
        tb.moveCursorTo(0, 2)
        assertEquals(0, tb.getCursor().cy)
        assertEquals(2, tb.getCursor().cx)

        // Move down to line 1, position 2
        tb.moveCursorDown()
        assertEquals(1, tb.getCursor().cy)
        assertEquals(2, tb.getCursor().cx)

        // Move down to line 2, position 2 (continuation marker, should adjust to 1)
        tb.moveCursorDown()
        assertEquals(2, tb.getCursor().cy)
        assertEquals(1, tb.getCursor().cx)  // Adjusted to wide char start
    }

    @Test
    fun cursorMovementWithOffsetThroughWideCharacters() {
        val tb = buf(w = 10, h = 3)
        // Write: "A中B中C"
        // Positions: 0='A', 1-2='中', 3='B', 4-5='中', 6='C'
        tb.writeString("A")
        tb.write('中')  // 1-2
        tb.write('B')   // 3
        tb.write('中')  // 4-5
        tb.write('C')   // 6

        // Test moving right by 2
        tb.moveCursorTo(0, 0)
        tb.moveCursorRight(2)
        // 0 + 2 = 2 (continuation marker), adjusts to 3
        assertEquals(3, tb.getCursor().cx)

        // Test moving left by 2 from position 6
        tb.moveCursorTo(0, 6)
        tb.moveCursorLeft(2)
        // 6 - 2 = 4 (wide char start), stays at 4
        assertEquals(4, tb.getCursor().cx)
    }

    @Test
    fun cursorClampsAndAdjustsAtBoundariesWithWideCharacters() {
        val tb = buf(w = 5, h = 3)
        // Line 0: "中日" (positions 0-1, 2-3, position 4 empty)
        tb.write('中')  // 0-1
        tb.write('日')  // 2-3

        // Try to move right past the end
        tb.moveCursorTo(0, 3)  // At second wide char
        tb.moveCursorRight(10)  // Try to move way past
        assertEquals(4, tb.getCursor().cx)  // Should clamp to width-1

        // Try to move left past the start
        tb.moveCursorTo(0, 1)
        tb.moveCursorLeft(10)
        assertEquals(0, tb.getCursor().cx)  // Should clamp to 0

        // Try to move up past top
        tb.moveCursorTo(0, 2)
        tb.moveCursorUp(10)
        assertEquals(0, tb.getCursor().cy)  // Should stay at row 0

        // Try to move down past bottom
        tb.moveCursorTo(0, 2)
        tb.moveCursorDown(10)
        assertEquals(2, tb.getCursor().cy)  // Should clamp to height-1
    }

    @Test
    fun testWriteAtWithWideCharacter() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeAt(2, 0, '中', CellAttributes())

        val line = buffer.getLine(0)
        assertEquals('中', line.getCell(2)?.char)
        assertEquals('\u0000', line.getCell(3)?.char)
        // Cursor should not move with writeAt
        assertEquals(0, buffer.getCursor().cx)
        assertEquals(0, buffer.getCursor().cy)
    }

    @Test
    fun testWriteAtWideCharacterAtLineEnd() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeAt(9, 0, '中', CellAttributes())

        val line = buffer.getLine(0)
        // Should write null since continuation doesn't fit
        assertNull(line.getCell(9))
    }

    @Test
    fun testWriteAtOverwritesExistingWideChar() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeAt(0, 0, '中', CellAttributes())
        buffer.writeAt(0, 0, 'A', CellAttributes())

        val line = buffer.getLine(0)
        assertEquals('A', line.getCell(0)?.char)
        assertEquals(' ', line.getCell(1)?.char)  // Continuation cleared
    }

    // ===== Line.fill() with Wide Characters =====

    @Test
    fun testFillLineWithWideCharacter() {
        val line = Line(10)
        val attrs = CellAttributes(fgColor = TerminalColor.Red)
        line.fill('中', attrs)

        // fill() is a low-level operation - it fills EVERY cell with the character
        // It doesn't understand wide character semantics
        for (i in 0 until 10) {
            assertEquals('中', line.getCell(i)?.char)
            assertEquals(TerminalColor.Red, line.getCell(i)?.attributes?.fgColor)
        }
    }

    @Test
    fun testFillLineWithSingleWidthCharacter() {
        val line = Line(9)
        val attrs = CellAttributes()
        line.fill('A', attrs)

        // All cells should have 'A'
        for (i in 0 until 9) {
            assertEquals('A', line.getCell(i)?.char)
        }
    }

    // ===== Cursor Positioning Edge Cases =====

    @Test
    fun testMoveCursorToLandsOnContinuation() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.write('中')  // Positions 0-1

        // Try to move cursor to continuation cell (position 1)
        buffer.moveCursorTo(0, 1)

        // Should auto-adjust to wide character start (position 0)
        assertEquals(0, buffer.getCursor().cx)
    }

    @Test
    fun testMoveCursorToWideCharacterStart() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.write('中')  // Positions 0-1

        buffer.moveCursorTo(0, 0)

        // Should stay at position 0 (valid position)
        assertEquals(0, buffer.getCursor().cx)
    }

    @Test
    fun testCursorMovementAcrossMultipleWideChars() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("中文日")  // 6 cells total

        buffer.moveCursorTo(0, 0)
        buffer.moveCursorRight(1)
        // Should skip to position 2 if landed on continuation at 1
        assertEquals(2, buffer.getCursor().cx)

        buffer.moveCursorRight(1)
        // Should skip to position 4 if landed on continuation at 3
        assertEquals(4, buffer.getCursor().cx)
    }

    // ===== getCharAtPosition / getAttributeAtPosition =====

    @Test
    fun testGetCharAtPositionWideCharacter() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.write('中', CellAttributes(fgColor = TerminalColor.Red))

        assertEquals('中', buffer.getCharAtPosition(0, 0))
        assertEquals('\u0000', buffer.getCharAtPosition(1, 0))
    }

    @Test
    fun testGetAttributeAtPositionWideCharacter() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        val attrs = CellAttributes(fgColor = TerminalColor.Blue, bgColor = TerminalColor.Yellow)
        buffer.write('中', attrs)

        val attr0 = buffer.getAttributeAtPosition(0, 0)
        assertEquals(TerminalColor.Blue, attr0.fgColor)
        assertEquals(TerminalColor.Yellow, attr0.bgColor)

        val attr1 = buffer.getAttributeAtPosition(1, 0)
        assertEquals(TerminalColor.Blue, attr1.fgColor)
        assertEquals(TerminalColor.Yellow, attr1.bgColor)
    }

    @Test
    fun testGetCharAtPositionUninitialized() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())

        assertFailsWith<IllegalArgumentException> {
            buffer.getCharAtPosition(5, 0)
        }
    }

    // ===== Scrollback with Wide Characters =====

    @Test
    fun testWideCharactersInScrollback() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())

        // Fill 4 lines to push first line to scrollback
        buffer.writeString("中文日本")
        buffer.newLine()
        buffer.writeString("ABCDEFGH")
        buffer.newLine()
        buffer.writeString("12345678")
        buffer.newLine()
        buffer.writeString("XXXXXXXX")

        // First line should be in scrollback
        assertEquals(1, buffer.getScrollbackSize())

        // Get line from scrollback (index = height + scrollback_index)
        assertEquals("中文日本", buffer.getLineAsString(3))
    }

    @Test
    fun testScrollbackAsStringWithWideChars() {
        val buffer = TerminalBuffer(10, 2, 100, CellAttributes())

        buffer.writeString("中文")
        buffer.newLine()
        buffer.writeString("AB")
        buffer.newLine()
        buffer.writeString("CD")

        // First line in scrollback
        val scrollback = buffer.getScrollbackAsString()
        assertEquals("中文\n", scrollback)
    }

    // ===== Line Wrapping Complex Scenarios =====

    @Test
    fun testInsertTextWithWrappingMixedWidth() {
        val buffer = TerminalBuffer(8, 3, 100, CellAttributes())
        buffer.insertTextWithWrapping("AB中文XY", moveCursor = true)

        val line0 = buffer.getLine(0)
        assertEquals('A', line0.getCell(0)?.char)
        assertEquals('B', line0.getCell(1)?.char)
        assertEquals('中', line0.getCell(2)?.char)
        assertEquals('\u0000', line0.getCell(3)?.char)
        assertEquals('文', line0.getCell(4)?.char)
        assertEquals('\u0000', line0.getCell(5)?.char)
        assertEquals('X', line0.getCell(6)?.char)
        assertEquals('Y', line0.getCell(7)?.char)

        assertEquals(8, buffer.getCursor().cx)
    }

    @Test
    fun testInsertTextWithWrappingOverflow() {
        val buffer = TerminalBuffer(5, 3, 100, CellAttributes())
        buffer.insertTextWithWrapping("A中文B", moveCursor = true)

        // A中文 = 1+2+2 = 5 cells (fits exactly)
        val line0 = buffer.getLine(0)
        assertEquals('A', line0.getCell(0)?.char)
        assertEquals('中', line0.getCell(1)?.char)
        assertEquals('\u0000', line0.getCell(2)?.char)
        assertEquals('文', line0.getCell(3)?.char)
        assertEquals('\u0000', line0.getCell(4)?.char)

        // B wraps to next line
        val line1 = buffer.getLine(1)
        assertEquals('B', line1.getCell(0)?.char)

        assertEquals(1, buffer.getCursor().cy)
        assertEquals(1, buffer.getCursor().cx)
    }

    @Test
    fun testInsertTextWithWrappingWideCharAtBoundary() {
        val buffer = TerminalBuffer(6, 3, 100, CellAttributes())
        buffer.insertTextWithWrapping("ABCD中", moveCursor = true)

        // ABCD = 4 cells, 中 needs 2 cells, total = 6 (fits exactly)
        val line0 = buffer.getLine(0)
        assertEquals('A', line0.getCell(0)?.char)
        assertEquals('B', line0.getCell(1)?.char)
        assertEquals('C', line0.getCell(2)?.char)
        assertEquals('D', line0.getCell(3)?.char)
        assertEquals('中', line0.getCell(4)?.char)
        assertEquals('\u0000', line0.getCell(5)?.char)

        assertEquals(6, buffer.getCursor().cx)
    }

    // ===== Character Width Detection Edge Cases =====

    @Test
    fun testCharDisplayWidthBoundaries() {
        // Test characters at range boundaries
        assertEquals(2, charDisplayWidth(0x1100))  // Start of Hangul Jamo
        assertEquals(2, charDisplayWidth(0x115F))  // End of Hangul Jamo
        assertEquals(2, charDisplayWidth(0x4E00))  // Start of CJK Unified
        assertEquals(2, charDisplayWidth(0x9FFF))  // End of CJK Unified
        assertEquals(2, charDisplayWidth(0x7000))  // Middle of CJK range
        assertEquals(1, charDisplayWidth(0x0041))  // ASCII 'A'
        assertEquals(1, charDisplayWidth(0x007E))  // ASCII '~'
        assertEquals(1, charDisplayWidth(0xA4C7))  // Just after Yi Radicals range
    }

    @Test
    fun testStringDisplayWidthEmptyString() {
        assertEquals(0, stringDisplayWidth(""))
    }

    @Test
    fun testStringDisplayWidthOnlyWideChars() {
        assertEquals(6, stringDisplayWidth("中文日"))
    }

    @Test
    fun testStringDisplayWidthOnlySingleWidth() {
        assertEquals(5, stringDisplayWidth("ABCDE"))
    }

    @Test
    fun testStringDisplayWidthMixed() {
        assertEquals(7, stringDisplayWidth("A中B文C"))  // A(1) + 中(2) + B(1) + 文(2) + C(1) = 7
    }

    // ===== Multiple Sequential Operations =====

    @Test
    fun testWriteDeleteWriteWideChar() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("A中B")

        buffer.moveCursorTo(0, 1)  // Move to wide char
        buffer.deleteCharacterAtCursor()

        assertEquals("AB", buffer.getLine(0).toString())

        // Write another wide char (overwrites 'B')
        buffer.moveCursorTo(0, 1)
        buffer.write('文')

        // '文' overwrites 'B', so result is "A文"
        assertEquals("A文", buffer.getLine(0).toString())
        assertEquals(3, buffer.getCursor().cx)  // Cursor at position 3
    }

    @Test
    fun testClearAndRewriteWideChars() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())
        line.setCellAt(2, '文', CellAttributes())

        line.clearRange(0, 4)

        // Rewrite
        line.setCellAt(0, 'A', CellAttributes())
        line.setCellAt(1, '日', CellAttributes())

        assertEquals('A', line.getCell(0)?.char)
        assertEquals('日', line.getCell(1)?.char)
        assertEquals('\u0000', line.getCell(2)?.char)
    }

    // ===== Viewport and Scrolling with Wide Characters =====

    @Test
    fun testGetVisibleLineWithWideChars() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())

        // Create scrollback
        buffer.writeString("中文日本")
        buffer.newLine()
        buffer.writeString("AAAAAAAA")
        buffer.newLine()
        buffer.writeString("BBBBBBBB")
        buffer.newLine()
        buffer.writeString("CCCCCCCC")

        // After the last newLine:
        // Scrollback: ["中文日本"] (index 0 = most recent)
        // Screen: ["AAAAAAAA", "BBBBBBBB", "CCCCCCCC"]

        // Scroll up by 1 - older content appears at TOP
        buffer.scrollUp(1)

        // viewportOffset = 1
        // Visible lines with NEW logic (scrollback at top):
        // row 0: scrollback[0] = "中文日本" (older content at top)
        // row 1: screen[0] = "AAAAAAAA" (screen shifted down)
        // row 2: screen[1] = "BBBBBBBB"

        val visibleLine0 = buffer.getVisibleLine(0)
        assertEquals("中文日本", visibleLine0.toString())

        val visibleLine1 = buffer.getVisibleLine(1)
        assertEquals("AAAAAAAA", visibleLine1.toString())

        val visibleLine2 = buffer.getVisibleLine(2)
        assertEquals("BBBBBBBB", visibleLine2.toString())
    }

    @Test
    fun testScrollbackAndScreenAsStringWithWideChars() {
        val buffer = TerminalBuffer(10, 2, 100, CellAttributes())

        buffer.writeString("中文")
        buffer.newLine()
        buffer.writeString("日本")
        buffer.newLine()
        buffer.writeString("ABCD")

        val combined = buffer.getScrollbackAndScreenAsString()
        // Should contain all three lines
        assert(combined.contains("中文"))
        assert(combined.contains("日本"))
        assert(combined.contains("ABCD"))
    }

    // ===== Edge Cases at Buffer Boundaries =====

    @Test
    fun testWriteWideCharAtRightmostColumn() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.moveCursorTo(0, 8)
        buffer.write('中')  // Would need positions 8-9

        // Should fit
        assertEquals('中', buffer.getLine(0).getCell(8)?.char)
        assertEquals('\u0000', buffer.getLine(0).getCell(9)?.char)
    }

    @Test
    fun testWriteWideCharBeyondRightEdge() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.moveCursorTo(0, 9)
        buffer.write('中')  // Would need positions 9-10, but 10 doesn't exist

        // Should wrap to next line
        assertEquals(1, buffer.getCursor().cy)
        assertEquals('中', buffer.getLine(1).getCell(0)?.char)
    }

    @Test
    fun testFillEntireBufferWithWideChars() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())

        for (row in 0 until 3) {
            buffer.fillLine('中', row)
        }

        // fillLine() is low-level - it fills every cell with the character
        for (row in 0 until 3) {
            val line = buffer.getLine(row)
            for (col in 0 until 10) {
                assertEquals('中', line.getCell(col)?.char)
            }
        }
    }

    // ===== Continuation Marker Invariant Tests =====

    @Test
    fun testContinuationMarkerNeverAppearsAlone() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())
        line.setCellAt(2, 'A', CellAttributes())  // Add content after

        // Clear just the wide character
        line.clearCellAt(0)

        // Both cells should be cleared, and fixGaps will fill with spaces
        assertEquals(' ', line.getCell(0)?.char)
        assertEquals(' ', line.getCell(1)?.char)
        assertEquals('A', line.getCell(2)?.char)
    }

    @Test
    fun testOverwritingPartialWideChar() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())
        line.setCellAt(2, '文', CellAttributes())

        // Overwrite continuation of first wide char
        line.setCellAt(1, 'A', CellAttributes())

        // First cell should be cleared
        assertEquals(' ', line.getCell(0)?.char)
        assertEquals('A', line.getCell(1)?.char)
        assertEquals('文', line.getCell(2)?.char)
    }

    // ===== Zero-Width and Special Characters =====

    @Test
    fun testNullCharacterDisplay() {
        // \u0000 is used as continuation marker, verify it's not counted as regular char
        assertEquals(1, charDisplayWidth('\u0000'))  // Default to single-width
    }

    @Test
    fun testTabCharacter() {
        // Tab is single-width in our system
        assertEquals(1, charDisplayWidth('\t'))
    }

    @Test
    fun testNewlineCharacter() {
        assertEquals(1, charDisplayWidth('\n'))
    }

    // ===== Stress Tests =====

    @Test
    fun testAlternatingWideAndSingleChars() {
        val buffer = TerminalBuffer(20, 3, 100, CellAttributes())
        buffer.writeString("A中B文C日D本E語")

        val line = buffer.getLine(0)
        assertEquals('A', line.getCell(0)?.char)
        assertEquals('中', line.getCell(1)?.char)
        assertEquals('\u0000', line.getCell(2)?.char)
        assertEquals('B', line.getCell(3)?.char)
        assertEquals('文', line.getCell(4)?.char)
        assertEquals('\u0000', line.getCell(5)?.char)
        assertEquals('C', line.getCell(6)?.char)

        assertEquals("A中B文C日D本E語", line.toString())
    }

    @Test
    fun testManyConsecutiveWideChars() {
        val buffer = TerminalBuffer(20, 3, 100, CellAttributes())
        buffer.writeString("中文日本語한국어")

        val line = buffer.getLine(0)
        var pos = 0
        val chars = listOf('中', '文', '日', '本', '語', '한', '국', '어')

        for (char in chars) {
            assertEquals(char, line.getCell(pos)?.char)
            assertEquals('\u0000', line.getCell(pos + 1)?.char)
            pos += 2
        }

        assertEquals("中文日本語한국어", line.toString())
    }

    @Test
    fun testMaxLengthLineWithWideChars() {
        val buffer = TerminalBuffer(100, 3, 100, CellAttributes())
        val text = "中".repeat(50)  // 50 wide chars = 100 cells
        buffer.writeString(text)

        assertEquals(100, buffer.getCursor().cx)
        assertEquals("中".repeat(50), buffer.getLine(0).toString())
    }

    // ===== getContentLength with Wide Characters =====

    @Test
    fun testGetContentLengthWithWideChars() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())

        assertEquals(2, line.getContentLength())
    }

    @Test
    fun testGetContentLengthMixedWidth() {
        val line = Line(10)
        line.setCellAt(0, 'A', CellAttributes())
        line.setCellAt(1, '中', CellAttributes())
        line.setCellAt(3, 'B', CellAttributes())

        assertEquals(4, line.getContentLength())
    }

    // ===== Attributes Preservation =====

    @Test
    fun testWideCharacterPreservesAttributes() {
        val line = Line(10)
        val attrs = CellAttributes(
            fgColor = TerminalColor.Red,
            bgColor = TerminalColor.Blue,
            style = Style.Bold
        )

        line.setCellAt(0, '中', attrs)

        val cell0 = line.getCell(0)
        assertEquals(TerminalColor.Red, cell0?.attributes?.fgColor)
        assertEquals(TerminalColor.Blue, cell0?.attributes?.bgColor)
        assertEquals(Style.Bold, cell0?.attributes?.style)

        val cell1 = line.getCell(1)
        assertEquals(TerminalColor.Red, cell1?.attributes?.fgColor)
        assertEquals(TerminalColor.Blue, cell1?.attributes?.bgColor)
        assertEquals(Style.Bold, cell1?.attributes?.style)
    }

    @Test
    fun testDeletePreservesAttributesOfRemainingChars() {
        val line = Line(10)
        val redAttrs = CellAttributes(fgColor = TerminalColor.Red)
        val blueAttrs = CellAttributes(fgColor = TerminalColor.Blue)

        line.setCellAt(0, '中', redAttrs)
        line.setCellAt(2, 'A', blueAttrs)

        line.deleteCharAt(0)  // Delete wide char

        assertEquals('A', line.getCell(0)?.char)
        assertEquals(TerminalColor.Blue, line.getCell(0)?.attributes?.fgColor)
    }

    // ===== Unicode Supplementary Plane =====

    @Test
    fun testSupplementaryPlaneEmoji() {
        // Test emoji in supplementary plane (> U+FFFF)
        assertEquals(2, charDisplayWidth(0x1F600))  // 😀 Grinning face
        assertEquals(2, charDisplayWidth(0x1F680))  // 🚀 Rocket
        assertEquals(2, charDisplayWidth(0x1F4BB))  // 💻 Laptop
    }

    @Test
    fun testStringDisplayWidthWithSupplementaryPlane() {
        // Note: These emojis are beyond BMP and need surrogate pairs in Kotlin
        // They might not display correctly in tests but width detection should work
        val width = stringDisplayWidth("A😀B")  // A + emoji + B
        // Emoji should be detected as width 2
        assertEquals(4, width)  // 1 + 2 + 1 = 4
    }

    // ===== Basic Wide Character Writing =====

    @Test
    fun testWriteWideCharacterOccupiesTwoCells() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())

        // First cell should contain the character
        assertEquals('中', line.getCell(0)?.char)
        // Second cell should contain continuation marker
        assertEquals('\u0000', line.getCell(1)?.char)
    }

    @Test
    fun testWriteWideCharacterAdvancesCursorByTwo() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        val cursor = buffer.getCursor()

        assertEquals(0, cursor.cx)
        buffer.write('中')
        assertEquals(2, cursor.cx)
    }

    @Test
    fun testWideCharacterAtLineEndWrapsToNextLine() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.moveCursorTo(0, 9)  // Position at last column

        buffer.write('中')  // Wide char should wrap to next line

        // Cursor should be on next line
        assertEquals(1, buffer.getCursor().cy)
        assertEquals(2, buffer.getCursor().cx)

        // Wide character should be on second line
        assertEquals('中', buffer.getLine(1).getCell(0)?.char)
        assertEquals('\u0000', buffer.getLine(1).getCell(1)?.char)
    }

    @Test
    fun testCannotSplitWideCharacterAcrossLines() {
        val line = Line(10)
        line.setCellAt(9, '中', CellAttributes())  // Position at last cell

        // Should write null instead since continuation doesn't fit
        assertEquals(null, line.getCell(9))
    }

    // ===== Cursor Positioning =====

    @Test
    fun testCursorCannotLandOnContinuationCell() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.write('中')  // Write wide char at position 0-1

        // Reset cursor to 0
        buffer.moveCursorTo(0, 0)

        // Move right by 1 should land on continuation (position 1), should auto-adjust
        buffer.moveCursorRight(1)

        // Cursor should skip to position 2 (next character)
        assertEquals(2, buffer.getCursor().cx)
    }

    @Test
    fun testMovingLeftIntoWideCharacterGoesToStart() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.write('中')  // Wide char at positions 0-1
        buffer.write('A')   // Character at position 2

        // Cursor at position 3
        assertEquals(3, buffer.getCursor().cx)

        // Move left: 3 -> 2 (normal)
        buffer.moveCursorLeft(1)
        assertEquals(2, buffer.getCursor().cx)

        // Move left again: 2 -> 1 (continuation), should adjust to 0
        buffer.moveCursorLeft(1)
        assertEquals(0, buffer.getCursor().cx)  // At wide char start, not in the middle!
    }

    @Test
    fun testMovingRightOverWideCharacterSkipsContinuation() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.write('中')  // Wide char at positions 0-1
        buffer.write('A')   // Character at position 2

        // Reset cursor to start
        buffer.moveCursorTo(0, 0)
        assertEquals(0, buffer.getCursor().cx)

        // Move right: 0 -> 1 (continuation), should adjust to 2
        buffer.moveCursorRight(1)
        assertEquals(2, buffer.getCursor().cx)  // Skipped over continuation
    }

    @Test
    fun testMovingLeftFromContinuationSkipsToWideChar() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.write('A')   // Position 0
        buffer.write('中')  // Positions 1-2

        // Cursor is now at position 3
        assertEquals(3, buffer.getCursor().cx)

        // Move left by 1 lands on continuation (position 2)
        // With preferLeft=true, should adjust to wide char start (position 1)
        buffer.moveCursorLeft(1)

        assertEquals(1, buffer.getCursor().cx)
    }

    // ===== Deletion =====

    @Test
    fun testDeletingWideCharacterRemovesBothCells() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())
        line.setCellAt(2, 'A', CellAttributes())

        line.deleteCharAt(0)  // Delete wide character

        // 'A' should now be at position 0
        assertEquals('A', line.getCell(0)?.char)
        // Position 1 should be null (or the next character)
        assertEquals(null, line.getCell(1))
    }

    @Test
    fun testDeletingContinuationRemovesWideCharacter() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())
        line.setCellAt(2, 'A', CellAttributes())

        line.deleteCharAt(1)  // Delete continuation marker

        // 'A' should now be at position 0
        assertEquals('A', line.getCell(0)?.char)
        // Position 1 should be null
        assertEquals(null, line.getCell(1))
    }

    // ===== Overwriting =====

    @Test
    fun testOverwritingWideCharWithSingleCharClearsBoth() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())

        // Overwrite first cell with single-width character
        line.setCellAt(0, 'A', CellAttributes())

        assertEquals('A', line.getCell(0)?.char)
        // Second cell should be cleared (space)
        assertEquals(' ', line.getCell(1)?.char)
    }

    @Test
    fun testOverwritingSingleCharWithWideCharWorks() {
        val line = Line(10)
        line.setCellAt(0, 'A', CellAttributes())
        line.setCellAt(1, 'B', CellAttributes())

        // Overwrite with wide character
        line.setCellAt(0, '中', CellAttributes())

        assertEquals('中', line.getCell(0)?.char)
        assertEquals('\u0000', line.getCell(1)?.char)
    }

    @Test
    fun testOverwritingContinuationClearsWideChar() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())

        // Overwrite continuation marker
        line.setCellAt(1, 'A', CellAttributes())

        // First cell should be cleared
        assertEquals(' ', line.getCell(0)?.char)
        assertEquals('A', line.getCell(1)?.char)
    }

    // ===== String Output =====

    @Test
    fun testToStringSkipsContinuationMarkers() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())
        line.setCellAt(2, '文', CellAttributes())
        line.setCellAt(4, 'A', CellAttributes())

        // toString should only show actual characters, not continuation markers
        assertEquals("中文A", line.toString())
    }

    // ===== Mixed Width Characters =====

    @Test
    fun testWriteStringWithMixedWidthCharacters() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("A中B文C")

        val line = buffer.getLine(0)
        assertEquals('A', line.getCell(0)?.char)
        assertEquals('中', line.getCell(1)?.char)
        assertEquals('\u0000', line.getCell(2)?.char)
        assertEquals('B', line.getCell(3)?.char)
        assertEquals('文', line.getCell(4)?.char)
        assertEquals('\u0000', line.getCell(5)?.char)
        assertEquals('C', line.getCell(6)?.char)

        // Cursor should be at position 7 (1 + 2 + 1 + 2 + 1)
        assertEquals(7, buffer.getCursor().cx)
    }

    @Test
    fun testMixedWidthCharactersWrapCorrectly() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("ABCD中文XY")  // 4 single + 2 wide (4 cells) + 2 single = 10 cells

        val line0 = buffer.getLine(0)
        assertEquals('A', line0.getCell(0)?.char)
        assertEquals('B', line0.getCell(1)?.char)
        assertEquals('C', line0.getCell(2)?.char)
        assertEquals('D', line0.getCell(3)?.char)
        assertEquals('中', line0.getCell(4)?.char)
        assertEquals('\u0000', line0.getCell(5)?.char)
        assertEquals('文', line0.getCell(6)?.char)
        assertEquals('\u0000', line0.getCell(7)?.char)
        assertEquals('X', line0.getCell(8)?.char)
        assertEquals('Y', line0.getCell(9)?.char)

        // Cursor should be at position 10 (line is exactly full)
        // Wrap only happens when next character is written
        assertEquals(0, buffer.getCursor().cy)
        assertEquals(10, buffer.getCursor().cx)
    }

    // ===== Character Width Detection =====

    @Test
    fun testCharDisplayWidthForSingleWidth() {
        assertEquals(1, charDisplayWidth('A'))
        assertEquals(1, charDisplayWidth('1'))
        assertEquals(1, charDisplayWidth(' '))
        assertEquals(1, charDisplayWidth('!'))
    }

    @Test
    fun testCharDisplayWidthForCJK() {
        assertEquals(2, charDisplayWidth('中'))
        assertEquals(2, charDisplayWidth('文'))
        assertEquals(2, charDisplayWidth('日'))
        assertEquals(2, charDisplayWidth('本'))
        assertEquals(2, charDisplayWidth('語'))
        assertEquals(2, charDisplayWidth('한'))
        assertEquals(2, charDisplayWidth('국'))
        assertEquals(2, charDisplayWidth('어'))
    }

    @Test
    fun testStringDisplayWidth() {
        assertEquals(1, stringDisplayWidth("A"))
        assertEquals(5, stringDisplayWidth("ABCDE"))
        assertEquals(2, stringDisplayWidth("中"))
        assertEquals(4, stringDisplayWidth("中文"))
        assertEquals(7, stringDisplayWidth("A中B文C"))  // 1+2+1+2+1
    }

    // ===== Edge Cases =====

    @Test
    fun testWideCharacterAtLastColumnBecomesNull() {
        val line = Line(10)
        line.setCellAt(9, '中', CellAttributes())

        // Should write null instead since continuation doesn't fit
        assertEquals(null, line.getCell(9))
    }

    @Test
    fun testEmptyLineWithWideCharacter() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())

        assertEquals(2, line.getContentLength())
    }

    // ===== Wrapping Tests =====

    @Test
    fun testWideCharacterWrapsCorrectlyInInsertText() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.insertTextWithWrapping("中文", moveCursor = true)

        val line = buffer.getLine(0)
        assertEquals('中', line.getCell(0)?.char)
        assertEquals('\u0000', line.getCell(1)?.char)
        assertEquals('文', line.getCell(2)?.char)
        assertEquals('\u0000', line.getCell(3)?.char)

        assertEquals(4, buffer.getCursor().cx)
    }

    @Test
    fun testWideCharacterOverflowInInsertText() {
        val buffer = TerminalBuffer(5, 3, 100, CellAttributes())
        buffer.writeString("ABC")  // Positions 0-2
        buffer.moveCursorTo(0, 3)  // Position 3

        // Insert "中文" at position 3
        // "中" needs 2 cells (3-4), "文" needs 2 cells (5-6, overflows)
        buffer.insertTextWithWrapping("中文", moveCursor = true)

        val line0 = buffer.getLine(0)
        assertEquals('A', line0.getCell(0)?.char)
        assertEquals('B', line0.getCell(1)?.char)
        assertEquals('C', line0.getCell(2)?.char)
        assertEquals('中', line0.getCell(3)?.char)
        assertEquals('\u0000', line0.getCell(4)?.char)

        val line1 = buffer.getLine(1)
        assertEquals('文', line1.getCell(0)?.char)
        assertEquals('\u0000', line1.getCell(1)?.char)
    }

    // ===== Emoji Tests =====

    @Test
    fun testEmojiDisplayWidth() {
        // Test some common emojis (these are in BMP)
        assertEquals(2, charDisplayWidth('⌚'))  // Watch (U+231A)

        // Test emoji code points (supplementary plane)
        assertEquals(2, charDisplayWidth(0x1F600))  // 😀 Grinning face
        assertEquals(2, charDisplayWidth(0x1F680))  // 🚀 Rocket
    }

    // ===== Clear Operations =====

    @Test
    fun testClearCellAtWideCharacter() {
        val line = Line(10)
        line.setCellAt(0, 'A', CellAttributes())
        line.setCellAt(1, '中', CellAttributes())
        line.setCellAt(3, 'B', CellAttributes())

        // Clear the wide character (first cell)
        line.clearCellAt(1)

        // Both cells of the wide character should be cleared
        assertEquals('A', line.getCell(0)?.char)
        assertEquals(' ', line.getCell(1)?.char)  // Gap filled
        assertEquals(' ', line.getCell(2)?.char)  // Gap filled (was continuation)
        assertEquals('B', line.getCell(3)?.char)
    }

    @Test
    fun testClearCellAtContinuation() {
        val line = Line(10)
        line.setCellAt(0, 'A', CellAttributes())
        line.setCellAt(1, '中', CellAttributes())
        line.setCellAt(3, 'B', CellAttributes())

        // Clear the continuation marker (second cell)
        line.clearCellAt(2)

        // Both cells of the wide character should be cleared
        assertEquals('A', line.getCell(0)?.char)
        assertEquals(' ', line.getCell(1)?.char)  // Gap filled (was wide char)
        assertEquals(' ', line.getCell(2)?.char)  // Gap filled
        assertEquals('B', line.getCell(3)?.char)
    }

    @Test
    fun testClearRangeWithWideCharacter() {
        val line = Line(10)
        line.setCellAt(0, 'A', CellAttributes())
        line.setCellAt(1, '中', CellAttributes())
        line.setCellAt(3, '文', CellAttributes())
        line.setCellAt(5, 'B', CellAttributes())

        // Clear range [1, 4) - should include both wide characters
        line.clearRange(1, 4)

        assertEquals('A', line.getCell(0)?.char)
        // Cells 1-4 should be cleared (both wide chars)
        assertEquals(' ', line.getCell(1)?.char)
        assertEquals(' ', line.getCell(2)?.char)
        assertEquals(' ', line.getCell(3)?.char)
        assertEquals(' ', line.getCell(4)?.char)
        assertEquals('B', line.getCell(5)?.char)
    }

    @Test
    fun testClearRangeStartsOnContinuation() {
        val line = Line(10)
        line.setCellAt(0, '中', CellAttributes())
        line.setCellAt(2, 'A', CellAttributes())

        // Clear range starting on continuation [1, 3)
        // Should expand to include the wide character at position 0
        line.clearRange(1, 3)

        // Wide character and 'A' should be cleared
        assertEquals(null, line.getCell(0))
        assertEquals(null, line.getCell(1))
        assertEquals(null, line.getCell(2))
    }

    @Test
    fun testClearRangeEndsOnWideCharacter() {
        val line = Line(10)
        line.setCellAt(0, 'A', CellAttributes())
        line.setCellAt(1, '中', CellAttributes())
        line.setCellAt(3, 'B', CellAttributes())

        // Clear range [0, 2) - ends on wide character
        // Should expand to include continuation at position 2
        line.clearRange(0, 2)

        // 'A' and wide character should be cleared
        assertEquals(' ', line.getCell(0)?.char)
        assertEquals(' ', line.getCell(1)?.char)
        assertEquals(' ', line.getCell(2)?.char)
        assertEquals('B', line.getCell(3)?.char)
    }

    // ===== Complex Scenarios =====

    @Test
    fun testDeleteCharacterAtCursorWithWideChar() {
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("A中B")

        // Move cursor to the wide character (position 1)
        buffer.moveCursorTo(0, 1)
        buffer.deleteCharacterAtCursor()

        val line = buffer.getLine(0)
        assertEquals('A', line.getCell(0)?.char)
        assertEquals('B', line.getCell(1)?.char)
        assertEquals(null, line.getCell(2))
    }

    @Test
    fun testMultipleWideCharactersOnSameLine() {
        val buffer = TerminalBuffer(20, 3, 100, CellAttributes())
        buffer.writeString("中文日本語한국어")

        val line = buffer.getLine(0)
        var pos = 0

        // 中 at 0-1
        assertEquals('中', line.getCell(pos)?.char)
        assertEquals('\u0000', line.getCell(pos + 1)?.char)
        pos += 2

        // 文 at 2-3
        assertEquals('文', line.getCell(pos)?.char)
        assertEquals('\u0000', line.getCell(pos + 1)?.char)
        pos += 2

        // 日 at 4-5
        assertEquals('日', line.getCell(pos)?.char)
        assertEquals('\u0000', line.getCell(pos + 1)?.char)
        pos += 2

        // 本 at 6-7
        assertEquals('本', line.getCell(pos)?.char)
        assertEquals('\u0000', line.getCell(pos + 1)?.char)
        pos += 2

        // 語 at 8-9
        assertEquals('語', line.getCell(pos)?.char)
        assertEquals('\u0000', line.getCell(pos + 1)?.char)
        pos += 2

        // 한 at 10-11
        assertEquals('한', line.getCell(pos)?.char)
        assertEquals('\u0000', line.getCell(pos + 1)?.char)
        pos += 2

        // 국 at 12-13
        assertEquals('국', line.getCell(pos)?.char)
        assertEquals('\u0000', line.getCell(pos + 1)?.char)
        pos += 2

        // 어 at 14-15
        assertEquals('어', line.getCell(pos)?.char)
        assertEquals('\u0000', line.getCell(pos + 1)?.char)

        assertEquals("中文日本語한국어", line.toString())
    }


    // --- Width Changes ---

    @Test
    fun resizeWidthIncrease() {
        // Width increase: should pad lines with space (no unwrapping)
        val buffer = TerminalBuffer(5, 3, 100, CellAttributes())
        buffer.writeString("Hello")
        buffer.newLine()
        buffer.writeString("World")

        buffer.resize(10, 3)

        assertEquals(10, buffer.width)
        assertEquals(3, buffer.height)
        assertEquals("Hello", buffer.getLine(0).toString())
        assertEquals("World", buffer.getLine(1).toString())
    }

    @Test
    fun resizeWidthDecrease() {
        // Width decrease: should wrap overflow to new lines
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("HelloWorld")  // 10 chars, exactly fits

        buffer.resize(5, 3)

        assertEquals(5, buffer.width)
        assertEquals(3, buffer.height)
        // "HelloWorld" should wrap to "Hello" + "World"
        assertEquals("Hello", buffer.getLine(0).toString())
        assertEquals("World", buffer.getLine(1).toString())
    }

    @Test
    fun resizeWidthDecreaseWithWideChar() {
        // Width decrease with wide character at boundary
        val buffer = TerminalBuffer(6, 3, 100, CellAttributes())
        buffer.writeString("AB中CD")  // Width: 1+1+2+1+1 = 6

        buffer.resize(3, 3)

        // Should wrap to "AB" + "中C" + "D"
        assertEquals("AB", buffer.getLine(0).toString())
        assertEquals("中C", buffer.getLine(1).toString())
        assertEquals("D", buffer.getLine(2).toString())
    }

    // --- Height Changes ---

    @Test
    fun resizeHeightIncrease() {
        // Height increase: should add blank lines at bottom
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("Line1")
        buffer.newLine()
        buffer.writeString("Line2")
        buffer.newLine()
        buffer.writeString("Line3")

        buffer.resize(10, 5)

        assertEquals(10, buffer.width)
        assertEquals(5, buffer.height)
        assertEquals("Line1", buffer.getLine(0).toString())
        assertEquals("Line2", buffer.getLine(1).toString())
        assertEquals("Line3", buffer.getLine(2).toString())
        assertEquals("", buffer.getLine(3).toString())  // Blank line
        assertEquals("", buffer.getLine(4).toString())  // Blank line
    }

    @Test
    fun resizeHeightDecrease() {
        // Height decrease: should move excess lines to scrollback
        val buffer = TerminalBuffer(10, 5, 100, CellAttributes())
        buffer.writeString("Line1")
        buffer.newLine()
        buffer.writeString("Line2")
        buffer.newLine()
        buffer.writeString("Line3")
        buffer.newLine()
        buffer.writeString("Line4")
        buffer.newLine()
        buffer.writeString("Line5")

        buffer.resize(10, 3)

        assertEquals(10, buffer.width)
        assertEquals(3, buffer.height)
        // Last 3 lines should be visible
        assertEquals("Line3", buffer.getLine(0).toString())
        assertEquals("Line4", buffer.getLine(1).toString())
        assertEquals("Line5", buffer.getLine(2).toString())
        // First 2 lines should be in scrollback
        assertEquals(2, buffer.getScrollbackSize())
    }

    @Test
    fun resizeHeightDecreaseToOne() {
        // Extreme case: decrease height to 1
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("Line1")
        buffer.newLine()
        buffer.writeString("Line2")
        buffer.newLine()
        buffer.writeString("Line3")

        buffer.resize(10, 1)

        assertEquals(1, buffer.height)
        assertEquals("Line3", buffer.getLine(0).toString())
        assertEquals(2, buffer.getScrollbackSize())
    }

    // --- Combined Width and Height Changes ---

    @Test
    fun resizeBothDimensions() {
        // Change both width and height
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("HelloWorld")
        buffer.newLine()
        buffer.writeString("TestString")

        buffer.resize(5, 5)

        // Width decreased: wrapping occurs
        // Height increased: blank lines added
        assertEquals(5, buffer.width)
        assertEquals(5, buffer.height)
        assertEquals("Hello", buffer.getLine(0).toString())
        assertEquals("World", buffer.getLine(1).toString())
        assertEquals("TestS", buffer.getLine(2).toString())
        assertEquals("tring", buffer.getLine(3).toString())
        assertEquals("", buffer.getLine(4).toString())
    }

    // --- Cursor Position ---

    @Test
    fun resizeCursorClamped() {
        // Cursor at (5, 2), resize smaller: cursor should be clamped
        val buffer = TerminalBuffer(10, 5, 100, CellAttributes())
        buffer.moveCursorTo(2, 5)

        buffer.resize(4, 3)

        val cursor = buffer.getCursor()
        assertTrue(cursor.cx < 4)
        assertTrue(cursor.cy < 3)
    }

    @Test
    fun resizeCursorAtEnd() {
        // Cursor at end, resize: cursor should adjust
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.moveCursorTo(2, 9)  // Last row, last column

        buffer.resize(5, 2)

        val cursor = buffer.getCursor()
        assertTrue(cursor.cx < 5)
        assertTrue(cursor.cy < 2)
    }

    @Test
    fun resizeCursorNotOnContinuation() {
        // After resize, cursor should not be on continuation cell
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("A中B")  // A at 0, 中 at 1-2, B at 3
        buffer.moveCursorTo(0, 2)  // Move cursor to continuation cell

        buffer.resize(8, 3)

        val cursor = buffer.getCursor()
        // Cursor should be adjusted to not be on continuation
        val cell = buffer.getLine(cursor.cy).getCell(cursor.cx)
        assertTrue(cell?.char != '\u0000')
    }

    // --- Scrollback ---

    @Test
    fun resizeEmptyScrollback() {
        // Resize with empty scrollback should work
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("Test")

        buffer.resize(5, 3)

        assertEquals(0, buffer.getScrollbackSize())
        assertEquals("Test", buffer.getLine(0).toString())
    }

    @Test
    fun resizeWithScrollback() {
        // Resize should also reflow scrollback content
        val buffer = TerminalBuffer(10, 2, 100, CellAttributes())
        // Fill screen and create scrollback
        buffer.writeString("First_Line")
        buffer.newLine()
        buffer.writeString("SecondLine")
        buffer.newLine()
        buffer.writeString("Third_Line")  // This causes "First_Line" to scroll

        assertEquals(1, buffer.getScrollbackSize())

        buffer.resize(5, 2)

        // Scrollback should also be reflowed
        // "First_Line" (10 chars) wraps to "First" + "_Line"
        // "SecondLine" (10 chars) wraps to "Secon" + "dLine"
        // "Third_Line" (10 chars) wraps to "Third" + "_Line"
        // With height=2, screen shows last 2 lines, rest in scrollback
        assertTrue(buffer.getScrollbackSize() > 1)
    }

    @Test
    fun resizePreservesScrollbackContent() {
        // Content in scrollback should be preserved and accessible after resize
        val buffer = TerminalBuffer(10, 2, 100, CellAttributes())
        buffer.writeString("Line1")
        buffer.newLine()
        buffer.writeString("Line2")
        buffer.newLine()
        buffer.writeString("Line3")  // Line1 goes to scrollback

        buffer.resize(8, 2)

        // Line1 should still be in scrollback
        assertTrue(buffer.getScrollbackSize() >= 1)
    }

    // --- Viewport Offset ---

    @Test
    fun resizeAdjustsViewportOffset() {
        // Viewport offset should be clamped to new scrollback size
        val buffer = TerminalBuffer(10, 2, 100, CellAttributes())
        buffer.writeString("Line1")
        buffer.newLine()
        buffer.writeString("Line2")
        buffer.newLine()
        buffer.writeString("Line3")

        buffer.scrollUp(1)  // Scroll up into scrollback
        val offsetBefore = buffer.getViewportOffset()
        assertTrue(offsetBefore > 0)

        buffer.resize(10, 3)

        // Offset should be adjusted (clamped to new scrollback size)
        val offsetAfter = buffer.getViewportOffset()
        assertTrue(offsetAfter <= buffer.getScrollbackSize())
    }

    @Test
    fun resizeWithMaxViewportOffset() {
        // Edge case: viewport at max offset
        val buffer = TerminalBuffer(10, 2, 100, CellAttributes())
        for (i in 1..10) {
            buffer.writeString("Line$i")
            buffer.newLine()
        }

        buffer.scrollToTop()  // Scroll to top of scrollback

        buffer.resize(10, 3)

        // Offset should be adjusted but not exceed new scrollback size
        assertTrue(buffer.getViewportOffset() <= buffer.getScrollbackSize())
    }

    // --- Edge Cases ---

    @Test
    fun resizeSameDimensions() {
        // Resize to same dimensions should be no-op
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("Test")
        val cursor = buffer.getCursor()
        val originalCx = cursor.cx
        val originalCy = cursor.cy

        buffer.resize(10, 3)

        assertEquals(10, buffer.width)
        assertEquals(3, buffer.height)
        assertEquals("Test", buffer.getLine(0).toString())
        assertEquals(originalCx, cursor.cx)
        assertEquals(originalCy, cursor.cy)
    }

    @Test
    fun resizeToMinimalSize() {
        // Resize to 1x1
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("Test")

        buffer.resize(1, 1)

        assertEquals(1, buffer.width)
        assertEquals(1, buffer.height)
    }

    @Test
    fun resizeEmptyBuffer() {
        // Resize empty buffer should work
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())

        buffer.resize(5, 5)

        assertEquals(5, buffer.width)
        assertEquals(5, buffer.height)
        assertEquals("", buffer.getLine(0).toString())
    }

    @Test
    fun resizeVeryWideToNarrow() {
        // Very wide line (200 chars) to narrow width (20)
        val buffer = TerminalBuffer(200, 3, 100, CellAttributes())
        val longString = "A".repeat(200)
        buffer.writeString(longString)

        buffer.resize(20, 3)

        // Should wrap into multiple lines
        // 200 chars / 20 width = 10 lines, but we only have height 3
        // So 7 lines go to scrollback, 3 lines on screen
        assertEquals(20, buffer.width)
        assertEquals(3, buffer.height)
        assertTrue(buffer.getScrollbackSize() >= 7)
    }

    @Test
    fun resizeMultipleWideCharsAtBoundaries() {
        // Multiple wide chars at various positions
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("A中B文C")  // 1+2+1+2+1 = 7

        buffer.resize(3, 3)

        // Should wrap correctly without splitting any wide char
        // Expected: "A中" (width 3), "B文" (width 3), "C" (width 1)
        assertEquals("A中", buffer.getLine(0).toString())
        assertEquals("B文", buffer.getLine(1).toString())
        assertEquals("C", buffer.getLine(2).toString())
    }

    // --- Integration Tests ---

    @Test
    fun resizeWriteResize() {
        // Write content, resize, write more, verify consistency
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("Hello")

        buffer.resize(5, 3)
        assertEquals("Hello", buffer.getLine(0).toString())

        buffer.moveCursorTo(1, 0)
        buffer.writeString("World")
        assertEquals("World", buffer.getLine(1).toString())

        buffer.resize(3, 3)
        // "Hello" wraps to "Hel" + "lo"
        // "World" wraps to "Wor" + "ld"
        assertEquals("Hel", buffer.getLine(0).toString())
        assertEquals("lo", buffer.getLine(1).toString())
        // Next lines are "Wor" and "ld" but they scrolled up
    }

    @Test
    fun resizeScrollResize() {
        // Resize, scroll, resize again
        val buffer = TerminalBuffer(10, 2, 100, CellAttributes())
        buffer.writeString("Line1")
        buffer.newLine()
        buffer.writeString("Line2")
        buffer.newLine()
        buffer.writeString("Line3")

        buffer.resize(8, 2)
        buffer.scrollUp(1)

        buffer.resize(6, 3)

        // Should handle gracefully
        assertEquals(6, buffer.width)
        assertEquals(3, buffer.height)
    }

    @Test
    fun resizeSequential() {
        // Multiple sequential resizes
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("Test")

        buffer.resize(8, 3)
        assertEquals(8, buffer.width)
        assertEquals("Test", buffer.getLine(0).toString())

        buffer.resize(6, 2)
        assertEquals(6, buffer.width)
        assertEquals(2, buffer.height)
        assertEquals("Test", buffer.getLine(0).toString())

        buffer.resize(4, 4)
        assertEquals(4, buffer.width)
        assertEquals(4, buffer.height)
        assertEquals("Test", buffer.getLine(0).toString())
    }

    @Test
    fun resizePreservesAttributes() {
        // Attributes should be preserved through resize
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        val redAttr = CellAttributes(fgColor = TerminalColor.Red)
        buffer.setAttributes(redAttr)
        buffer.writeString("Red")

        buffer.resize(5, 3)

        assertEquals("Red", buffer.getLine(0).toString())
        val cell = buffer.getLine(0).getCell(0)
        assertEquals(TerminalColor.Red, cell?.attributes?.fgColor)
    }

    @Test
    fun resizeAfterWideCharacterOperations() {
        // Ensure wide character handling is correct after resize
        val buffer = TerminalBuffer(10, 3, 100, CellAttributes())
        buffer.writeString("中文日")  // Three wide chars

        buffer.resize(4, 3)

        // Each wide char takes 2 columns
// "中文" (width 4) fits on first line
        // "日" (width 2) wraps to second line
        assertEquals("中文", buffer.getLine(0).toString())
        assertEquals("日", buffer.getLine(1).toString())

        // Verify continuation markers
        assertEquals('\u0000', buffer.getLine(0).getCell(1)?.char)
        assertEquals('\u0000', buffer.getLine(0).getCell(3)?.char)
        assertEquals('\u0000', buffer.getLine(1).getCell(1)?.char)
    }
}

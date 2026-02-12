package com.david

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TerminalBufferTest {

    private fun buf(w: Int = 10, h: Int = 5) = TerminalBuffer(w, h, 100, CellAttributes())

    /** Pre-size all screen lines so write/writeAt don't crash on empty cells. */
    private fun TerminalBuffer.preSizeLines() {
        for (row in 0 until height) {
            getLine(row).ensureSize(width)
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
        tb.getLine(0).ensureSize(5)
        tb.getLine(0).setCellAt(0, 'A')
        tb.getLine(1).ensureSize(5)
        tb.getLine(1).setCellAt(0, 'B')
        assertEquals('A', tb.getLine(0).getCell(0).char)
        assertEquals('B', tb.getLine(1).getCell(0).char)
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
        assertEquals('A', tb.getLine(0).getCell(0).char)
        assertEquals(1, tb.getCursor().cx)
        assertEquals(0, tb.getCursor().cy)
    }

    @Test
    fun writeMultipleChars() {
        val tb = buf()
        tb.preSizeLines()
        tb.write('H')
        tb.write('i')
        assertEquals('H', tb.getLine(0).getCell(0).char)
        assertEquals('i', tb.getLine(0).getCell(1).char)
        assertEquals(2, tb.getCursor().cx)
    }

    @Test
    fun writeWithCustomAttributes() {
        val tb = buf()
        tb.preSizeLines()
        val attrs = CellAttributes(fgColor = TerminalColor.Green, style = Style.Underline)
        tb.write('X', attrs)
        val cell = tb.getLine(0).getCell(0)
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
        assertEquals('E', tb.getLine(1).getCell(0).char)
    }

    @Test
    fun writeAtRightEdgeWrapsToNextLine() {
        val tb = buf(w = 3, h = 2)
        tb.preSizeLines()
        tb.write('A')
        tb.write('B')
        tb.write('C') // fills row 0, cx=3 (past edge)
        tb.write('D') // wraps to row 1 col 0
        assertEquals('A', tb.getLine(0).getCell(0).char)
        assertEquals('B', tb.getLine(0).getCell(1).char)
        assertEquals('C', tb.getLine(0).getCell(2).char)
        assertEquals('D', tb.getLine(1).getCell(0).char)
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
        assertEquals('D', tb.getLine(1).getCell(0).char)
        assertEquals('E', tb.getLine(1).getCell(1).char)
    }

    @Test
    fun writeOnFreshBufferWithoutPreSizeThrows() {
        val tb = buf()
        assertFailsWith<IndexOutOfBoundsException> {
            tb.write('A')
        }
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
        assertEquals('A', tb.getLine(0).getCell(0).char)
        assertEquals('X', tb.getLine(0).getCell(1).char)
        assertEquals('C', tb.getLine(0).getCell(2).char)
    }

    // =======================================================================
    // writeAt
    // =======================================================================

    @Test
    fun writeAtSetsCharAtPosition() {
        val tb = buf(w = 10, h = 5)
        tb.preSizeLines()
        tb.writeAt(3, 2, 'Z')
        assertEquals('Z', tb.getLine(2).getCell(3).char)
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
        val cell = tb.getLine(0).getCell(0)
        assertEquals('W', cell.char)
        assertEquals(TerminalColor.Cyan, cell.attributes.fgColor)
        assertEquals(TerminalColor.Red, cell.attributes.bgColor)
    }

    @Test
    fun writeAtNegativeRowThrows() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        assertFailsWith<ArrayIndexOutOfBoundsException> {
            tb.writeAt(0, -1, 'X')
        }
    }

    @Test
    fun writeAtRowBeyondHeightThrows() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        assertFailsWith<ArrayIndexOutOfBoundsException> {
            tb.writeAt(0, 3, 'X')
        }
    }

    @Test
    fun writeAtColBeyondLineSizeThrows() {
        val tb = buf(w = 5, h = 3)
        // Line not pre-sized — col 0 is out of bounds
        assertFailsWith<IndexOutOfBoundsException> {
            tb.writeAt(0, 0, 'X')
        }
    }

    @Test
    fun writeAtOverwritesExistingCell() {
        val tb = buf()
        tb.preSizeLines()
        tb.writeAt(2, 1, 'A')
        tb.writeAt(2, 1, 'B')
        assertEquals('B', tb.getLine(1).getCell(2).char)
    }

    // =======================================================================
    // Single-cell buffer edge case
    // =======================================================================

    @Test
    fun singleCellBufferWriteWrapsAndScrolls() {
        val tb = TerminalBuffer(1, 1, 100, CellAttributes())
        tb.preSizeLines()
        tb.write('A')
        assertEquals('A', tb.getLine(0).getCell(0).char)
        assertEquals(1, tb.getCursor().cx)
        // Writing again wraps: newLine scrolls A into scrollback, B on fresh row
        tb.write('B')
        assertEquals('B', tb.getLine(0).getCell(0).char)
        assertEquals(0, tb.getCursor().cy)
        assertEquals(1, tb.getScrollbackSize())
    }

    // =======================================================================
    // Helper: create a scrollback line with a marker char
    // =======================================================================

    private fun markerLine(w: Int, marker: Char): Line {
        val line = Line(w)
        line.ensureSize(w)
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
        assertEquals('S', tb.getVisibleLine(0).getCell(0).char)
        assertEquals('T', tb.getVisibleLine(1).getCell(0).char)
        assertEquals('U', tb.getVisibleLine(2).getCell(0).char)
    }

    @Test
    fun getVisibleLineScrolledShowsScreenThenScrollback() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        // Fill screen: X Y Z
        tb.writeAt(0, 0, 'X')
        tb.writeAt(0, 1, 'Y')
        tb.writeAt(0, 2, 'Z')
        // Scrollback: A B C (oldest → newest)
        tb.addScrollbackLine(markerLine(5, 'A'))
        tb.addScrollbackLine(markerLine(5, 'B'))
        tb.addScrollbackLine(markerLine(5, 'C'))
        // Scroll up by 2: screen lines shift up, scrollback appears at bottom
        // combinedIndex: row0=2 → screen[2]=Z, row1=3 → scrollback newest=C, row2=4 → scrollback=B
        tb.scrollUp(2)
        assertEquals('Z', tb.getVisibleLine(0).getCell(0).char)
        assertEquals('C', tb.getVisibleLine(1).getCell(0).char)
        assertEquals('B', tb.getVisibleLine(2).getCell(0).char)
    }

    @Test
    fun getVisibleLineScrolledToTopShowsOldestAtBottom() {
        val tb = buf(w = 5, h = 3)
        tb.preSizeLines()
        tb.writeAt(0, 0, 'X')
        // Add 5 scrollback lines: A B C D E (oldest → newest)
        for (ch in "ABCDE") {
            tb.addScrollbackLine(markerLine(5, ch))
        }
        // scrollToTop → offset = 5, height = 3
        // row0: combined=5, scrollbackIdx=5-1-(5-3)=2 → C
        // row1: combined=6, scrollbackIdx=5-1-(6-3)=1 → B
        // row2: combined=7, scrollbackIdx=5-1-(7-3)=0 → A
        tb.scrollToTop()
        assertEquals('C', tb.getVisibleLine(0).getCell(0).char)
        assertEquals('B', tb.getVisibleLine(1).getCell(0).char)
        assertEquals('A', tb.getVisibleLine(2).getCell(0).char)
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
        // Scrollback: 1 2 (oldest → newest)
        tb.addScrollbackLine(markerLine(5, '1'))
        tb.addScrollbackLine(markerLine(5, '2'))
        // Scroll up by 1 → offset=1
        // combinedIndex: row0=1→screen[1]=Q, row1=2→screen[2]=R, row2=3→screen[3]=S, row3=4→scrollback newest=2
        tb.scrollUp(1)
        assertEquals('Q', tb.getVisibleLine(0).getCell(0).char)
        assertEquals('R', tb.getVisibleLine(1).getCell(0).char)
        assertEquals('S', tb.getVisibleLine(2).getCell(0).char)
        assertEquals('2', tb.getVisibleLine(3).getCell(0).char)
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
        // Scroll to top — scrollback shown newest first, so C then B
        // offset=2, height=3: row0=2→screen[2], row1=3→scrollback newest=C, row2=4→scrollback oldest=B
        tb.scrollToTop()
        assertEquals('C', tb.getVisibleLine(1).getCell(0).char)
        assertEquals('B', tb.getVisibleLine(2).getCell(0).char)
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
        assertEquals('H', tb.getLine(0).getCell(0).char)
        assertEquals('i', tb.getLine(0).getCell(1).char)
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
        assertEquals('B', tb.getLine(0).getCell(0).char)
        assertEquals('C', tb.getLine(1).getCell(0).char)
        assertEquals(' ', tb.getLine(2).getCell(0).char)
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
        assertEquals(' ', tb.getLine(0).getCell(0).char)
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
        assertEquals('Z', tb.getLine(2).getCell(0).char)
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
        assertEquals(' ', tb.getLine(0).getCell(0).char)
        assertEquals(' ', tb.getLine(1).getCell(0).char)
        assertEquals(' ', tb.getLine(2).getCell(0).char)
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
        assertEquals('A', tb.getLine(0).getCell(0).char)
        assertEquals('B', tb.getLine(0).getCell(1).char)
        assertEquals('C', tb.getLine(1).getCell(0).char)
        assertEquals('D', tb.getLine(1).getCell(1).char)
        assertEquals('E', tb.getLine(2).getCell(0).char)
        assertEquals('F', tb.getLine(2).getCell(1).char)
    }
}

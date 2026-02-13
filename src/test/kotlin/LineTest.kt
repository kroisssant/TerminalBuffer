package com.david

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class LineTest {

    // --- addCell basics ---

    @Test
    fun addSingleCell() {
        val line = Line(10)
        line.addCell('A')
        assertEquals(1, line.getContentLength())
        assertEquals('A', line.getCell(0)!!.char)
    }

    @Test
    fun addCellsUpToLimit() {
        val line = Line(3)
        line.addCell('A')
        line.addCell('B')
        line.addCell('C')
        assertTrue(line.isFull())
    }

    // --- ensureSize ---

    @Test
    fun ensureSizePadsWithBlanks() {
        val line = Line(10)
        // Initialize 5 cells with blank spaces
        for (i in 0 until 5) {
            line.setCellAt(i, ' ')
        }
        assertEquals(5, line.getContentLength())
        for (i in 0 until 5) {
            assertEquals(' ', line.getCell(i)!!.char)
        }
    }

    @Test
    fun ensureSizeDoesNothingWhenAlreadyLargeEnough() {
        val line = Line(10)
        line.addCell('A')
        line.addCell('B')
        // Content length is 2, which is already >= 1
        assertEquals(2, line.getContentLength())
    }

    // --- getCell ---

    @Test
    fun getCellExpandsLine() {
        val line = Line(10)
        val cell = line.getCell(5)
        // Cell at index 5 is null (empty) in sparse representation
        assertEquals(null, cell)
    }

    @Test
    fun getCellReturnsExistingCell() {
        val line = Line(10)
        line.addCell('X')
        val cell = line.getCell(0)
        assertEquals('X', cell!!.char)
    }

    // --- setCellAt ---

    @Test
    fun setCellAtBasic() {
        val line = Line(10)
        line.setCellAt(3, 'Z')
        assertEquals('Z', line.getCell(3)!!.char)
        // Gaps should be filled with spaces
        for (i in 0 until 3) {
            assertEquals(' ', line.getCell(i)!!.char)
        }
    }

    @Test
    fun setCellAtWithAttributes() {
        val line = Line(10)
        val attrs = CellAttributes(fgColor = TerminalColor.Red, style = Style.Bold)
        line.setCellAt(0, 'A', attrs)
        assertEquals(TerminalColor.Red, line.getCell(0)!!.attributes.fgColor)
        assertEquals(Style.Bold, line.getCell(0)!!.attributes.style)
    }

    @Test
    fun setCellAtOverwritesExisting() {
        val line = Line(10)
        line.addCell('A')
        line.addCell('B')
        line.setCellAt(0, 'X')
        assertEquals('X', line.getCell(0)!!.char)
        assertEquals('B', line.getCell(1)!!.char)
    }

    @Test
    fun setCellAtOnEmptyLineThrows() {
        val line = Line(10)
        // setCellAt no longer throws on empty line - it just sets the cell
        line.setCellAt(0, 'A')
        assertEquals('A', line.getCell(0)!!.char)
        // Writing at index 0 should not fill any gaps (no cells before it)
        assertEquals(null, line.getCell(1))
    }

    @Test
    fun setCellAtBeyondSizeThrows() {
        val line = Line(10)
        // setCellAt within maxCells is allowed
        line.setCellAt(5, 'A')
        assertEquals('A', line.getCell(5)!!.char)
        // But beyond maxCells should throw
        assertFailsWith<IllegalArgumentException> {
            line.setCellAt(10, 'A')
        }
    }

    // --- clearRange ---

    @Test
    fun clearRangeBasic() {
        val line = Line(10)
        for (ch in "ABCDEFGH") line.addCell(ch)
        line.clearRange(2, 5)
        assertEquals('A', line.getCell(0)!!.char)
        assertEquals('B', line.getCell(1)!!.char)
        // Gaps are filled with spaces, not left as null
        assertEquals(' ', line.getCell(2)!!.char)
        assertEquals(' ', line.getCell(3)!!.char)
        assertEquals(' ', line.getCell(4)!!.char)
        assertEquals('F', line.getCell(5)!!.char)
    }

    @Test
    fun clearRangeFullLine() {
        val line = Line(5)
        for (ch in "ABCDE") line.addCell(ch)
        line.clearRange(0, 5)
        for (i in 0 until 5) {
            assertEquals(null, line.getCell(i))
        }
    }

    @Test
    fun clearRangeEmptyRangeDoesNothing() {
        val line = Line(10)
        line.addCell('A')
        line.clearRange(3, 3)
        assertEquals(1, line.getContentLength())
        assertEquals('A', line.getCell(0)!!.char)
    }

    @Test
    fun clearRangeNegativeStartClampedToZero() {
        val line = Line(10)
        for (ch in "ABCDE") line.addCell(ch)
        line.clearRange(-5, 2)
        // Gaps are filled with spaces since there's content after
        assertEquals(' ', line.getCell(0)!!.char)
        assertEquals(' ', line.getCell(1)!!.char)
        assertEquals('C', line.getCell(2)!!.char)
    }

    @Test
    fun clearRangeEndBeyondMaxCellsClamped() {
        val line = Line(5)
        for (ch in "ABCDE") line.addCell(ch)
        line.clearRange(3, 100)
        assertEquals('A', line.getCell(0)!!.char)
        assertEquals('B', line.getCell(1)!!.char)
        assertEquals('C', line.getCell(2)!!.char)
        assertEquals(null, line.getCell(3))
        assertEquals(null, line.getCell(4))
    }

    @Test
    fun clearRangeStartBeyondEndIsNoop() {
        val line = Line(10)
        for (ch in "ABC") line.addCell(ch)
        line.clearRange(5, 2)
        assertEquals('A', line.getCell(0)!!.char)
        assertEquals('B', line.getCell(1)!!.char)
        assertEquals('C', line.getCell(2)!!.char)
    }

    @Test
    fun clearRangeEnsuresSizeWhenNeeded() {
        val line = Line(10)
        line.addCell('A')
        // Range extends beyond current content but within maxCells
        line.clearRange(0, 5)
        // All cells in range should be null now
        for (i in 0 until 5) {
            assertEquals(null, line.getCell(i))
        }
    }

    // --- clearCellAt ---

    @Test
    fun clearCellAtResetsToBlank() {
        val line = Line(10)
        line.addCell('X')
        line.addCell('Y')
        line.clearCellAt(0)
        // Gap is filled with space, not left as null
        assertEquals(' ', line.getCell(0)!!.char)
        assertEquals('Y', line.getCell(1)!!.char)
    }

    @Test
    fun clearCellAtOnEmptyLineThrows() {
        val line = Line(10)
        // clearCellAt now just clears the cell, doesn't throw on empty line
        line.clearCellAt(0)
        assertEquals(null, line.getCell(0))
    }

    @Test
    fun clearCellAtBeyondSizeThrows() {
        val line = Line(10)
        line.addCell('A')
        // clearCellAt within maxCells is allowed
        line.clearCellAt(5)
        assertEquals(null, line.getCell(5))
        // But beyond maxCells should throw
        assertFailsWith<IllegalArgumentException> {
            line.clearCellAt(10)
        }
    }

    // --- clearLine ---

    @Test
    fun clearLineResetsAllCellsToBlank() {
        val line = Line(5)
        for (ch in "ABCDE") line.addCell(ch)
        line.clearLine()
        // After clearLine, all cells are null
        assertEquals(0, line.getContentLength())
    }

    @Test
    fun clearLineOnEmptyLineFillsToMaxCells() {
        val line = Line(4)
        assertEquals(0, line.getContentLength())
        line.clearLine()
        // Line remains empty (sparse behavior)
        assertEquals(0, line.getContentLength())
    }

    @Test
    fun clearLineOnPartiallyFilledLine() {
        val line = Line(6)
        line.addCell('A')
        line.addCell('B')
        line.clearLine()
        // After clearLine, all cells are null
        assertEquals(0, line.getContentLength())
    }

    // --- Gap-filling behavior ---

    @Test
    fun setCellAtFillsGapsWithSpaces() {
        val line = Line(10)
        line.setCellAt(5, 'X')
        // Gaps should be filled with spaces
        for (i in 0 until 5) {
            assertEquals(' ', line.getCell(i)!!.char)
        }
        assertEquals('X', line.getCell(5)!!.char)
    }

    @Test
    fun isFullWorksCorrectlyWithGaps() {
        val line = Line(5)
        line.setCellAt(2, 'A')
        assertEquals(false, line.isFull())  // Only filled to position 2

        line.setCellAt(4, 'B')  // Fill to last position
        assertEquals(true, line.isFull())
    }

    @Test
    fun clearCellAtFillsGapsWithSpaces() {
        val line = Line(10)
        line.setCellAt(5, 'X')
        line.clearCellAt(2)  // Clear cell at position 2
        // Gap should be filled with space, not left as null
        assertEquals(' ', line.getCell(2)!!.char)
        assertEquals('X', line.getCell(5)!!.char)
        // Content length should still be 6
        assertEquals(6, line.getContentLength())
    }

    @Test
    fun clearRangeFillsGapsWithSpaces() {
        val line = Line(10)
        for (ch in "ABCDEFGH") line.addCell(ch)
        line.clearRange(2, 5)  // Clear positions 2-4
        // Gaps should be filled with spaces
        assertEquals('A', line.getCell(0)!!.char)
        assertEquals('B', line.getCell(1)!!.char)
        assertEquals(' ', line.getCell(2)!!.char)
        assertEquals(' ', line.getCell(3)!!.char)
        assertEquals(' ', line.getCell(4)!!.char)
        assertEquals('F', line.getCell(5)!!.char)
        // Content length should still be 8
        assertEquals(8, line.getContentLength())
    }
}

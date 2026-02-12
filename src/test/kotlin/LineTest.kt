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
        assertEquals(1, line.cells.size)
        assertEquals('A', line.cells[0].char)
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
        line.ensureSize(5)
        assertEquals(5, line.cells.size)
        for (cell in line.cells) {
            assertEquals(' ', cell.char)
        }
    }

    @Test
    fun ensureSizeDoesNothingWhenAlreadyLargeEnough() {
        val line = Line(10)
        line.addCell('A')
        line.addCell('B')
        line.ensureSize(1)
        assertEquals(2, line.cells.size)
    }

    // --- getCell ---

    @Test
    fun getCellExpandsLine() {
        val line = Line(10)
        val cell = line.getCell(5)
        assertEquals(' ', cell.char)
        assertEquals(6, line.cells.size)
    }

    @Test
    fun getCellReturnsExistingCell() {
        val line = Line(10)
        line.addCell('X')
        val cell = line.getCell(0)
        assertEquals('X', cell.char)
    }

    // --- setCellAt ---

    @Test
    fun setCellAtBasic() {
        val line = Line(10)
        line.ensureSize(4)
        line.setCellAt(3, 'Z')
        assertEquals('Z', line.cells[3].char)
    }

    @Test
    fun setCellAtWithAttributes() {
        val line = Line(10)
        line.ensureSize(1)
        val attrs = CellAttributes(fgColor = TerminalColor.Red, style = Style.Bold)
        line.setCellAt(0, 'A', attrs)
        assertEquals(TerminalColor.Red, line.cells[0].attributes.fgColor)
        assertEquals(Style.Bold, line.cells[0].attributes.style)
    }

    @Test
    fun setCellAtOverwritesExisting() {
        val line = Line(10)
        line.addCell('A')
        line.addCell('B')
        line.setCellAt(0, 'X')
        assertEquals('X', line.cells[0].char)
        assertEquals('B', line.cells[1].char)
    }

    @Test
    fun setCellAtOnEmptyLineThrows() {
        val line = Line(10)
        assertFailsWith<IndexOutOfBoundsException> {
            line.setCellAt(0, 'A')
        }
    }

    @Test
    fun setCellAtBeyondSizeThrows() {
        val line = Line(10)
        line.ensureSize(3)
        assertFailsWith<IndexOutOfBoundsException> {
            line.setCellAt(5, 'A')
        }
    }

    // --- clearRange ---

    @Test
    fun clearRangeBasic() {
        val line = Line(10)
        for (ch in "ABCDEFGH") line.addCell(ch)
        line.clearRange(2, 5)
        assertEquals('A', line.cells[0].char)
        assertEquals('B', line.cells[1].char)
        assertEquals(' ', line.cells[2].char)
        assertEquals(' ', line.cells[3].char)
        assertEquals(' ', line.cells[4].char)
        assertEquals('F', line.cells[5].char)
    }

    @Test
    fun clearRangeFullLine() {
        val line = Line(5)
        for (ch in "ABCDE") line.addCell(ch)
        line.clearRange(0, 5)
        for (cell in line.cells) {
            assertEquals(' ', cell.char)
        }
    }

    @Test
    fun clearRangeEmptyRangeDoesNothing() {
        val line = Line(10)
        line.addCell('A')
        line.clearRange(3, 3)
        assertEquals(1, line.cells.size)
        assertEquals('A', line.cells[0].char)
    }

    @Test
    fun clearRangeNegativeStartClampedToZero() {
        val line = Line(10)
        for (ch in "ABCDE") line.addCell(ch)
        line.clearRange(-5, 2)
        assertEquals(' ', line.cells[0].char)
        assertEquals(' ', line.cells[1].char)
        assertEquals('C', line.cells[2].char)
    }

    @Test
    fun clearRangeEndBeyondMaxCellsClamped() {
        val line = Line(5)
        for (ch in "ABCDE") line.addCell(ch)
        line.clearRange(3, 100)
        assertEquals('A', line.cells[0].char)
        assertEquals('B', line.cells[1].char)
        assertEquals('C', line.cells[2].char)
        assertEquals(' ', line.cells[3].char)
        assertEquals(' ', line.cells[4].char)
    }

    @Test
    fun clearRangeStartBeyondEndIsNoop() {
        val line = Line(10)
        for (ch in "ABC") line.addCell(ch)
        line.clearRange(5, 2)
        assertEquals('A', line.cells[0].char)
        assertEquals('B', line.cells[1].char)
        assertEquals('C', line.cells[2].char)
    }

    @Test
    fun clearRangeEnsuresSizeWhenNeeded() {
        val line = Line(10)
        line.addCell('A')
        // Range extends beyond current cells.size but within maxCells
        line.clearRange(0, 5)
        assertEquals(5, line.cells.size)
        for (cell in line.cells) {
            assertEquals(' ', cell.char)
        }
    }

    // --- clearCellAt ---

    @Test
    fun clearCellAtResetsToBlank() {
        val line = Line(10)
        line.addCell('X')
        line.addCell('Y')
        line.clearCellAt(0)
        assertEquals(' ', line.cells[0].char)
        assertEquals('Y', line.cells[1].char)
    }

    @Test
    fun clearCellAtOnEmptyLineThrows() {
        val line = Line(10)
        assertFailsWith<IndexOutOfBoundsException> {
            line.clearCellAt(0)
        }
    }

    @Test
    fun clearCellAtBeyondSizeThrows() {
        val line = Line(10)
        line.addCell('A')
        assertFailsWith<IndexOutOfBoundsException> {
            line.clearCellAt(5)
        }
    }

    // --- clearLine ---

    @Test
    fun clearLineResetsAllCellsToBlank() {
        val line = Line(5)
        for (ch in "ABCDE") line.addCell(ch)
        line.clearLine()
        assertEquals(5, line.cells.size)
        for (cell in line.cells) {
            assertEquals(' ', cell.char)
        }
    }

    @Test
    fun clearLineOnEmptyLineFillsToMaxCells() {
        val line = Line(4)
        assertEquals(0, line.cells.size)
        line.clearLine()
        assertEquals(4, line.cells.size)
        for (cell in line.cells) {
            assertEquals(' ', cell.char)
        }
    }

    @Test
    fun clearLineOnPartiallyFilledLine() {
        val line = Line(6)
        line.addCell('A')
        line.addCell('B')
        line.clearLine()
        assertEquals(6, line.cells.size)
        for (cell in line.cells) {
            assertEquals(' ', cell.char)
        }
    }
}

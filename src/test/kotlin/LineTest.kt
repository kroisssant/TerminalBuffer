package com.david

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}

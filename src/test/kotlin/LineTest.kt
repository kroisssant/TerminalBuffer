package com.david

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class LineTest {


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


    // --- getCell ---

    @Test
    fun getCellExpandsLine() {
        val line = Line(10)
        val cell = line.getCell(5)
        // Cell at index 5 is null (empty) in sparse representation
        assertEquals(null, cell)
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

    @Test
    fun clearCellAtOnEmptyLineThrows() {
        val line = Line(10)
        // clearCellAt now just clears the cell, doesn't throw on empty line
        line.clearCellAt(0)
        assertEquals(null, line.getCell(0))
    }


    @Test
    fun clearLineOnEmptyLineFillsToMaxCells() {
        val line = Line(4)
        assertEquals(0, line.getContentLength())
        line.clearLine()
        // Line remains empty (sparse behavior)
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

    // --- writeWithWrapping tests ---

    @Test
    fun writeWithWrappingNoOverflow() {
        val line = Line(10)
        val overflow = line.writeWithWrapping(0, "Hello")

        // Text should fit within line, no overflow
        assertEquals(0, overflow.size)
        assertEquals('H', line.getCell(0)!!.char)
        assertEquals('e', line.getCell(1)!!.char)
        assertEquals('l', line.getCell(2)!!.char)
        assertEquals('l', line.getCell(3)!!.char)
        assertEquals('o', line.getCell(4)!!.char)
    }

    @Test
    fun writeWithWrappingExactFit() {
        val line = Line(5)
        val overflow = line.writeWithWrapping(0, "Hello")

        // Text should exactly fit the line
        assertEquals(0, overflow.size)
        assertEquals(5, line.getContentLength())
        assertEquals(true, line.isFull())
    }

    @Test
    fun writeWithWrappingWithOverflow() {
        val line = Line(5)
        val overflow = line.writeWithWrapping(0, "HelloWorld")

        // "Hello" fits, "World" overflows
        assertEquals(5, overflow.size)
        assertEquals('H', line.getCell(0)!!.char)
        assertEquals('e', line.getCell(1)!!.char)
        assertEquals('l', line.getCell(2)!!.char)
        assertEquals('l', line.getCell(3)!!.char)
        assertEquals('o', line.getCell(4)!!.char)

        // Check overflow cells
        assertEquals('W', overflow[0].char)
        assertEquals('o', overflow[1].char)
        assertEquals('r', overflow[2].char)
        assertEquals('l', overflow[3].char)
        assertEquals('d', overflow[4].char)
    }

    @Test
    fun writeWithWrappingAtMiddlePosition() {
        val line = Line(10)
        val overflow = line.writeWithWrapping(5, "12345")

        // Writing at position 5, should fit exactly
        assertEquals(0, overflow.size)
        assertEquals('1', line.getCell(5)!!.char)
        assertEquals('5', line.getCell(9)!!.char)
    }

    @Test
    fun writeWithWrappingAtMiddleWithOverflow() {
        val line = Line(10)
        val overflow = line.writeWithWrapping(7, "ABCDEFG")

        // Position 7-9 fits "ABC", "DEFG" overflows
        assertEquals(4, overflow.size)
        assertEquals('A', line.getCell(7)!!.char)
        assertEquals('B', line.getCell(8)!!.char)
        assertEquals('C', line.getCell(9)!!.char)

        assertEquals('D', overflow[0].char)
        assertEquals('E', overflow[1].char)
        assertEquals('F', overflow[2].char)
        assertEquals('G', overflow[3].char)
    }

    @Test
    fun writeWithWrappingOverwritesNullCells() {
        val line = Line(10)
        // Line is empty (all null cells)
        val overflow = line.writeWithWrapping(0, "Test")

        // Should write directly over null cells without pushing
        assertEquals(0, overflow.size)
        assertEquals('T', line.getCell(0)!!.char)
        assertEquals('e', line.getCell(1)!!.char)
        assertEquals('s', line.getCell(2)!!.char)
        assertEquals('t', line.getCell(3)!!.char)
        // Rest should still be null (no gap filling happened)
        assertEquals(null, line.getCell(4))
    }

    @Test
    fun writeWithWrappingOverwritesSpaceCells() {
        val line = Line(10)
        // Fill with spaces
        for (i in 0 until 5) {
            line.setCellAt(i, ' ')
        }

        val overflow = line.writeWithWrapping(0, "Hi")

        // Should overwrite space cells without pushing
        assertEquals(0, overflow.size)
        assertEquals('H', line.getCell(0)!!.char)
        assertEquals('i', line.getCell(1)!!.char)
        assertEquals(' ', line.getCell(2)!!.char)
        assertEquals(' ', line.getCell(3)!!.char)
        assertEquals(' ', line.getCell(4)!!.char)
    }

    @Test
    fun writeWithWrappingInsertsIntoNonSpaceContent() {
        val line = Line(10)
        // Set up line with "Hello"
        line.setCellAt(0, 'H')
        line.setCellAt(1, 'e')
        line.setCellAt(2, 'l')
        line.setCellAt(3, 'l')
        line.setCellAt(4, 'o')

        // Insert "XX" at position 2 - should push "llo" to the right
        val overflow = line.writeWithWrapping(2, "XX")

        // Should push existing content
        assertEquals(0, overflow.size)  // Nothing overflows in a 10-wide line
        assertEquals('H', line.getCell(0)!!.char)
        assertEquals('e', line.getCell(1)!!.char)
        assertEquals('X', line.getCell(2)!!.char)
        assertEquals('X', line.getCell(3)!!.char)
        assertEquals('l', line.getCell(4)!!.char)
        assertEquals('l', line.getCell(5)!!.char)
        assertEquals('o', line.getCell(6)!!.char)
    }

    @Test
    fun writeWithWrappingInsertsAndOverflows() {
        val line = Line(5)
        // Set up line with "Hello"
        for (i in 0 until 5) {
            line.setCellAt(i, "Hello"[i])
        }

        // Insert "XX" at position 2 - "llo" should be pushed and overflow
        val overflow = line.writeWithWrapping(2, "XX")

        // Line should be: H e X X l
        assertEquals('H', line.getCell(0)!!.char)
        assertEquals('e', line.getCell(1)!!.char)
        assertEquals('X', line.getCell(2)!!.char)
        assertEquals('X', line.getCell(3)!!.char)
        assertEquals('l', line.getCell(4)!!.char)

        // "lo" should overflow
        assertEquals(2, overflow.size)
        assertEquals('l', overflow[0].char)
        assertEquals('o', overflow[1].char)
    }

    @Test
    fun writeWithWrappingMixedNullSpaceAndContent() {
        val line = Line(10)
        // Set up: "AB___CD" (underscores are spaces, rest are null)
        line.setCellAt(0, 'A')
        line.setCellAt(1, 'B')
        line.setCellAt(2, ' ')
        line.setCellAt(3, ' ')
        line.setCellAt(4, ' ')
        line.setCellAt(5, 'C')
        line.setCellAt(6, 'D')

        // Write "XY" at position 3 (over spaces)
        val overflow = line.writeWithWrapping(3, "XY")

        // Spaces should be overwritten, not pushed
        assertEquals(0, overflow.size)
        assertEquals('A', line.getCell(0)!!.char)
        assertEquals('B', line.getCell(1)!!.char)
        assertEquals(' ', line.getCell(2)!!.char)
        assertEquals('X', line.getCell(3)!!.char)
        assertEquals('Y', line.getCell(4)!!.char)
        assertEquals('C', line.getCell(5)!!.char)
        assertEquals('D', line.getCell(6)!!.char)
    }

    @Test
    fun writeWithWrappingPreservesAttributes() {
        val line = Line(10)
        val redBold = CellAttributes(fgColor = TerminalColor.Red, style = Style.Bold)

        val overflow = line.writeWithWrapping(0, "Test", redBold)

        assertEquals(0, overflow.size)
        assertEquals('T', line.getCell(0)!!.char)
        assertEquals(TerminalColor.Red, line.getCell(0)!!.attributes.fgColor)
        assertEquals(Style.Bold, line.getCell(0)!!.attributes.style)
    }

    @Test
    fun writeWithWrappingOverflowPreservesAttributes() {
        val line = Line(3)
        val greenAttr = CellAttributes(fgColor = TerminalColor.Green)

        val overflow = line.writeWithWrapping(0, "Hello", greenAttr)

        // "Hel" fits, "lo" overflows
        assertEquals(2, overflow.size)
        assertEquals('l', overflow[0].char)
        assertEquals(TerminalColor.Green, overflow[0].attributes.fgColor)
        assertEquals('o', overflow[1].char)
        assertEquals(TerminalColor.Green, overflow[1].attributes.fgColor)
    }

    @Test
    fun writeWithWrappingEmptyString() {
        val line = Line(10)
        val overflow = line.writeWithWrapping(0, "")

        assertEquals(0, overflow.size)
        assertEquals(0, line.getContentLength())
    }

    @Test
    fun writeWithWrappingAtEndOfLine() {
        val line = Line(10)
        // Fill line partially
        line.setCellAt(0, 'A')
        line.setCellAt(1, 'B')

        // Write at position 9 (last cell)
        val overflow = line.writeWithWrapping(9, "XY")

        assertEquals('X', line.getCell(9)!!.char)
        assertEquals(1, overflow.size)
        assertEquals('Y', overflow[0].char)
    }

    @Test
    fun writeWithWrappingInsertPushesSpaces() {
        val line = Line(10)
        // Set up: "AB   " (2 chars, then 3 explicit spaces)
        line.setCellAt(0, 'A')
        line.setCellAt(1, 'B')
        line.setCellAt(2, ' ')
        line.setCellAt(3, ' ')
        line.setCellAt(4, ' ')

        // Write "CD" at position 2 (start of spaces)
        // Since we're writing into spaces, should overwrite not insert
        val overflow = line.writeWithWrapping(2, "CD")

        assertEquals(0, overflow.size)
        assertEquals('A', line.getCell(0)!!.char)
        assertEquals('B', line.getCell(1)!!.char)
        assertEquals('C', line.getCell(2)!!.char)
        assertEquals('D', line.getCell(3)!!.char)
        assertEquals(' ', line.getCell(4)!!.char)
    }

    @Test
    fun writeWithWrappingThrowsOnInvalidPosition() {
        val line = Line(10)

        assertFailsWith<IllegalArgumentException> {
            line.writeWithWrapping(-1, "Test")
        }

        assertFailsWith<IllegalArgumentException> {
            line.writeWithWrapping(10, "Test")
        }
    }
}

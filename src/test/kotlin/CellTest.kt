package com.david

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CellTest {

    // --- Basic single-width cell ---

    @Test
    fun defaultAttributes() {
        val cell = Cell('A')
        assertEquals(TerminalColor.White, cell.attributes.fgColor)
        assertEquals(TerminalColor.Black, cell.attributes.bgColor)
        assertEquals(Style.Normal, cell.attributes.style)
    }

    // --- Custom attributes ---

    @Test
    fun customAttributes() {
        val attrs = CellAttributes(
            fgColor = TerminalColor.Red,
            bgColor = TerminalColor.Blue,
            style = Style.Bold
        )
        val cell = Cell('X', attrs)
        assertEquals(TerminalColor.Red, cell.attributes.fgColor)
        assertEquals(TerminalColor.Blue, cell.attributes.bgColor)
        assertEquals(Style.Bold, cell.attributes.style)
    }

    // --- CellAttributes data class equality ---

    @Test
    fun attributesEquality() {
        val a = CellAttributes(TerminalColor.Red, TerminalColor.Black, Style.Bold)
        val b = CellAttributes(TerminalColor.Red, TerminalColor.Black, Style.Bold)
        assertEquals(a, b)
    }
}

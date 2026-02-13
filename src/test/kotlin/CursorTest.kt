package com.david

import kotlin.test.Test
import kotlin.test.assertEquals

class CursorTest {

    // --- Basic movement ---

    @Test
    fun moveRight() {
        val c = Cursor(0, 0)
        c.moveRight()
        assertEquals(1, c.cx)
        assertEquals(0, c.cy)
    }

    @Test
    fun moveLeft() {
        val c = Cursor(0, 5)
        c.moveLeft()
        assertEquals(4, c.cx)
    }

    @Test
    fun moveDown() {
        val c = Cursor(0, 0)
        c.moveDown()
        assertEquals(1, c.cy)
        assertEquals(0, c.cx)
    }

    @Test
    fun moveUp() {
        val c = Cursor(3, 0)
        c.moveUp()
        assertEquals(2, c.cy)
    }

    // --- Custom offsets ---

    @Test
    fun moveRightByOffset() {
        val c = Cursor(0, 0)
        c.moveRight(5)
        assertEquals(5, c.cx)
    }

    @Test
    fun moveDownByOffset() {
        val c = Cursor(0, 0)
        c.moveDown(3)
        assertEquals(3, c.cy)
    }

    // --- moveTo ---

    @Test
    fun moveTo() {
        val c = Cursor(0, 0)
        c.moveTo(4, 7)
        assertEquals(4, c.cy)
        assertEquals(7, c.cx)
    }

    // --- reset ---

    @Test
    fun reset() {
        val c = Cursor(5, 10)
        c.reset()
        assertEquals(0, c.cy)
        assertEquals(0, c.cx)
    }

    // --- Edge cases: cursor does NOT clamp itself (that's TerminalBuffer's job) ---

    @Test
    fun moveLeftBelowZeroGoesNegative() {
        val c = Cursor(0, 0)
        c.moveLeft()
        assertEquals(-1, c.cx)
    }

    @Test
    fun moveUpBelowZeroGoesNegative() {
        val c = Cursor(0, 0)
        c.moveUp()
        assertEquals(-1, c.cy)
    }

    // --- Successive movements ---

    @Test
    fun multipleMovements() {
        val c = Cursor(0, 0)
        c.moveRight(3)
        c.moveDown(2)
        c.moveLeft(1)
        c.moveUp(1)
        assertEquals(2, c.cx)
        assertEquals(1, c.cy)
    }

    // --- clampCursor ---

    @Test
    fun clampCursorClampsNegativeCx() {
        val c = Cursor(0, -5)
        c.clampCursor(0, 0, 79, 23)
        assertEquals(0, c.cx)
        assertEquals(0, c.cy)
    }

    @Test
    fun clampCursorClampsNegativeCy() {
        val c = Cursor(-3, 0)
        c.clampCursor(0, 0, 79, 23)
        assertEquals(0, c.cx)
        assertEquals(0, c.cy)
    }

    @Test
    fun clampCursorClampsCxAboveMax() {
        val c = Cursor(0, 100)
        c.clampCursor(0, 0, 79, 23)
        assertEquals(79, c.cx)
        assertEquals(0, c.cy)
    }

    @Test
    fun clampCursorClampsCyAboveMax() {
        val c = Cursor(50, 0)
        c.clampCursor(0, 0, 79, 23)
        assertEquals(0, c.cx)
        assertEquals(23, c.cy)
    }

    @Test
    fun clampCursorNoOpWhenInBounds() {
        val c = Cursor(5, 10)
        c.clampCursor(0, 0, 79, 23)
        assertEquals(10, c.cx)
        assertEquals(5, c.cy)
    }

    @Test
    fun clampCursorBothAxesOutOfBounds() {
        val c = Cursor(-2, -3)
        c.clampCursor(0, 0, 79, 23)
        assertEquals(0, c.cx)
        assertEquals(0, c.cy)
    }

    @Test
    fun clampCursorAtExactBoundary() {
        val c = Cursor(23, 79)
        c.clampCursor(0, 0, 79, 23)
        assertEquals(79, c.cx)
        assertEquals(23, c.cy)
    }
}

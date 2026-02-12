package com.david

class Cursor(var cy: Int, var cx: Int) {

    /**
     * Move the cursor [offset] cells to the right.
     */
    fun moveRight(offset: Int = 1) {
        this.cx += offset
    }
    /**
     * Move the cursor [offset] cells to the left.
     */
    fun moveLeft(offset: Int = 1) {
        this.cx -= offset
    }

    /**
     * Move the cursor [offset] cells to the top.
     */
    fun moveUp(offset: Int = 1) {
        this.cy -= offset
    }

    /**
     * Move the cursor [offset] cells to the bottom.
     */
    fun moveDown(offset: Int = 1) {
        this.cy += offset
    }

    /**
     * Move cursor to a specific cell with the coordinates [cy], [cx]
     */
    fun moveTo(cy: Int, cx: Int) {
        this.cy = cy
        this.cx = cx
    }

    /**
     * Reset the cursor to the origin
     */
    fun reset() {
        this.cy = 0
        this.cx = 0
    }

    fun clampCursor(min_cx: Int, min_cy: Int, max_cx: Int, max_cy: Int) {
        this.cx.coerceIn(min_cx, max_cx)
        this.cy.coerceIn(min_cy, max_cy)
    }
}
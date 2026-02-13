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

    fun clampCursor(minCx: Int, minCy: Int, maxCx: Int, maxCy: Int) {
        this.cx = this.cx.coerceIn(minCx, maxCx)
        this.cy = this.cy.coerceIn(minCy, maxCy)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Cursor) return false
        return cy == other.cy && cx == other.cx
    }

    override fun hashCode(): Int {
        var result = cy
        result = 31 * result + cx
        return result
    }
}
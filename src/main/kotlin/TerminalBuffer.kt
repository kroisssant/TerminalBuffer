package com.david

/**
 * Terminal buffer class
 */
class TerminalBuffer(val width: Int, val height: Int, val maxScrollbackSize: Int, val cellAttributes: CellAttributes) {
    private val screenBuffer: Array<Line> = Array(height) { Line(width) }
    private val scrollbackBuffer: ArrayDeque<Line> = ArrayDeque()

    private val cursor: Cursor = Cursor(0, 0)

    private val privateAttributes: CellAttributes = CellAttributes()

    /**
     * Write [char] with [attributes] at the cursor position.
     */
    fun write(char: Char, attributes: CellAttributes = privateAttributes) {
        screenBuffer[cursor.cy].setCellAt(cursor.cx, char, attributes)
        cursor.moveRight()

        // If cursor reached the right edge, enter pending wrap state
        if (cursor.cx >= width) {
            cursor.cx = width - 1

        }
    }

    /**
     * Write cell at specific position [cx] [cy].
     */
    fun writeAt(cx: Int, cy: Int, char: Char,  attributes: CellAttributes = privateAttributes) {
        screenBuffer[cy].setCellAt(cx, char, attributes)
    }

    /**
     * Get cursor instance.
     */
    fun getCursor(): Cursor {
        return cursor
    }



}
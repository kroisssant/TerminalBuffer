package com.david

/**
 * Terminal buffer class
 */
class TerminalBuffer(val width: Int, val height: Int, val maxScrollbackSize: Int, val cellAttributes: CellAttributes) {
    private val screenBuffer: Array<Line> = Array(height) { Line(width) }
    private val scrollbackBuffer: ArrayDeque<Line> = ArrayDeque()

    private val cursor: Cursor = Cursor(0, 0)

    private val privateAttributes: CellAttributes = CellAttributes()

    private var viewportOffset: Int = 0

    // Write
    /**
     * Write [char] with [attributes] at the cursor position.
     */
    fun write(char: Char, attributes: CellAttributes = privateAttributes) {
        if (cursor.cx >= width) {
            newLine()
        }
        screenBuffer[cursor.cy].setCellAt(cursor.cx, char, attributes)
        cursor.moveRight()
    }

    /**
     * Write cell at specific position [cx] [cy].
     */
    fun writeAt(cx: Int, cy: Int, char: Char,  attributes: CellAttributes = privateAttributes) {
        screenBuffer[cy].setCellAt(cx, char, attributes)
    }

    // Line
    /**
     * Advance the cursor to the start of the next line.
     * If the cursor is already on the last screen row, scrolls the screen up:
     * the top line is pushed into scrollback, all lines shift up by one,
     * and a fresh blank line appears at the bottom.
     */
    fun newLine() {
        val nextRow = cursor.cy + 1

        if (nextRow < height) {
            cursor.moveTo(nextRow, 0)
        } else {
            addScrollbackLine(screenBuffer[0])

            for (i in 0 until height - 1) {
                screenBuffer[i] = screenBuffer[i + 1]
            }

            val blankLine = Line(width)
            blankLine.ensureSize(width)
            screenBuffer[height - 1] = blankLine

            cursor.moveTo(height - 1, 0)

            if (viewportOffset > 0) {
                viewportOffset++
            }
        }
    }

    fun getLine(cy: Int): Line {
        return screenBuffer[cy]
    }

    /**
     * Get the line visible at viewport row [row] (0 = top of viewport).
     *
     * The viewport is: screen lines first (shifted by offset), then scrollback
     * lines (newest first). When viewportOffset is 0 the viewport shows the
     * screen lines. As offset increases, screen lines scroll off the top and
     * scrollback lines appear at the bottom.
     */
    fun getVisibleLine(row: Int): Line {
        require(row in 0 until height) { "row $row out of viewport bounds [0, $height)" }
        val combinedIndex = viewportOffset + row
        return if (combinedIndex < height) {
            screenBuffer[combinedIndex]
        } else {
            // Scrollback, newest first
            val scrollbackIndex = scrollbackBuffer.size - 1 - (combinedIndex - height)
            scrollbackBuffer[scrollbackIndex]
        }
    }

    // Cursor

    /**
     * Get cursor instance.
     */
    fun getCursor(): Cursor {
        return cursor
    }





    // Scrolling

    /**
     * Get the number of lines currently in the scrollback buffer.
     */
    fun getScrollbackSize(): Int = scrollbackBuffer.size

    /**
     * Push [line] into the scrollback buffer.
     * If the scrollback is full, the oldest line is dropped.
     */
    fun addScrollbackLine(line: Line) {
        if (scrollbackBuffer.size >= maxScrollbackSize) {
            scrollbackBuffer.removeFirst()
        }
        scrollbackBuffer.addLast(line)
    }

    /**
     * Current viewport offset. 0 = live screen, >0 = scrolled into history.
     */
    fun getViewportOffset(): Int = viewportOffset

    /**
     * Whether the viewport is at the bottom (showing the live screen).
     */
    fun isAtBottom(): Boolean = viewportOffset == 0

    /**
     * Scroll the viewport up (towards older history) by [lines] rows.
     * Clamped to the available scrollback size. Does not move the cursor.
     */
    fun scrollUp(lines: Int = 1) {
        viewportOffset = (viewportOffset + lines).coerceAtMost(scrollbackBuffer.size)
    }

    /**
     * Scroll the viewport down (towards the live screen) by [lines] rows.
     * Clamped to 0. Does not move the cursor.
     */
    fun scrollDown(lines: Int = 1) {
        viewportOffset = (viewportOffset - lines).coerceAtLeast(0)
    }

    /**
     * Scroll the viewport to the top of the scrollback history.
     */
    fun scrollToTop() {
        viewportOffset = scrollbackBuffer.size
    }

    /**
     * Scroll the viewport back to the live screen.
     */
    fun scrollToBottom() {
        viewportOffset = 0
    }





}
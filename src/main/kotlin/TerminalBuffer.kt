package com.david

/**
 * Terminal buffer managing a screen grid and scrollback history.
 *
 * The buffer consists of:
 * - Screen: [height] visible lines of [width] cells each
 * - Scrollback: Up to [maxScrollbackSize] lines of history that scrolled off the top
 * - Cursor: Current writing position within the screen
 *
 * @param width Number of columns in the screen
 * @param height Number of rows in the screen
 * @param maxScrollbackSize Maximum number of lines to keep in scrollback history
 * @param cellAttributes Default cell attributes for new content
 */
class TerminalBuffer(var width: Int, var height: Int, val maxScrollbackSize: Int, val cellAttributes: CellAttributes) {
    private var screenBuffer: Array<Line> = Array(height) { Line(width) }
    private val scrollbackBuffer: ArrayDeque<Line> = ArrayDeque()

    private val cursor: Cursor = Cursor(0, 0)

    private var defaultAttributes: CellAttributes = cellAttributes

    private var viewportOffset: Int = 0

    init {
        cursor.clampCursor(0, 0, width - 1, height - 1)
    }

    // Write operations

    /**
     * Write a single character at the cursor position and advance the cursor.
     * If the cursor is at or past the right edge, a new line is created first.
     * Wide characters (CJK, emoji) that don't fit on the current line wrap to the next line.
     *
     * @param char Character to write
     * @param attributes Cell attributes (defaults to current default attributes)
     */
    fun write(char: Char, attributes: CellAttributes = defaultAttributes) {
        val charWidth = charDisplayWidth(char)

        // Check if wide character fits on current line
        if (cursor.cx + charWidth > width) {
            newLine()
        }

        screenBuffer[cursor.cy].setCellAt(cursor.cx, char, attributes)
        cursor.moveRight(charWidth)  // Advance by display width
    }

    /**
     * Write a single character at the specified position.
     * Does not move the cursor.
     *
     * Note: Parameters use (column, row) convention to keep the char parameter last.
     *
     * @param cx Column position (0-based)
     * @param cy Row position (0-based)
     * @param char Character to write
     * @param attributes Cell attributes (defaults to current default attributes)
     */
    fun writeAt(cx: Int, cy: Int, char: Char,  attributes: CellAttributes = defaultAttributes) {
        require(cx in 0 until width) { "cx $cx out of bounds [0, $width)" }
        require(cy in 0 until height) { "cy $cy out of bounds [0, $height)" }
        screenBuffer[cy].setCellAt(cx, char, attributes)
    }

    /**
     * Write a string at the cursor position, advancing the cursor after each character.
     * Wraps to new lines when reaching the right edge.
     *
     * @param str String to write
     * @param cellAttributes Cell attributes to apply to all characters (defaults to current default attributes)
     */
    fun writeString(str: String, cellAttributes: CellAttributes = defaultAttributes) {
        for (char in str) {
            write(char, cellAttributes)
        }
    }

    /**
     * Fill an entire line with the specified character and attributes.
     * Does not move the cursor.
     *
     * @param char Character to fill the line with
     * @param lineCy Row to fill (defaults to cursor row)
     * @param cellAttributes Cell attributes to apply (defaults to current default attributes)
     */
    fun fillLine(char: Char, lineCy: Int = cursor.cy, cellAttributes: CellAttributes = defaultAttributes) {
        require(lineCy in 0 until height) { "lineCy $lineCy out of bounds [0, $height)" }
        screenBuffer[lineCy].fill(char, cellAttributes)
    }

    /**
     * Delete the character at the cursor position and shift remaining characters left.
     * The last cell on the line becomes empty. Does not move the cursor.
     * This matches standard terminal delete character behavior (DCH).
     */
    fun deleteCharacterAtCursor() {
        screenBuffer[cursor.cy].deleteCharAt(cursor.cx)
    }

    /**
     * Clear the entire screen by replacing all lines with blank lines.
     * Resets cursor to origin (0, 0).
     * Does not affect scrollback.
     */
    fun clearScreen() {
        screenBuffer = Array(height) { Line(width) }
        cursor.reset()
    }

    /**
     * Clear both the screen and scrollback history.
     * Resets viewport offset to 0. Does not move the cursor.
     */
    fun clearAll() {
        clearScrollback()
        clearScreen()
    }
    // Insertion operations with wrapping

    /**
     * Insert text at the specified position with wrapping behavior.
     *
     * Insertion behavior:
     * - Null or space cells are overwritten without shifting
     * - Non-space content causes insertion (existing content shifts right)
     * - Content that doesn't fit wraps to the next line
     * - If wrapping occurs on the last line, the screen scrolls up
     *
     * @param text Text to insert
     * @param attributes Cell attributes for the text (defaults to current default attributes)
     * @param cx Column position (defaults to current cursor column)
     * @param cy Row position (defaults to current cursor row)
     * @param moveCursor Whether to move the cursor to the end of inserted text (defaults to true)
     */
    fun insertTextWithWrapping(
        text: String,
        attributes: CellAttributes = defaultAttributes,
        cx: Int = cursor.cx,
        cy: Int = cursor.cy,
        moveCursor: Boolean = true
    ) {
        val cells = text.map { Cell(it, attributes) }
        insertCellsWithWrapping(cells, cx, cy, moveCursor)
    }

    /**
     * Internal recursive function to insert cells with wrapping.
     * Handles overflow by recursively inserting into subsequent lines.
     * Properly accounts for wide characters when calculating final cursor position.
     *
     * @param cells List of cells to insert
     * @param cx Column position to start insertion
     * @param cy Row position to start insertion
     * @return Final cursor position (row, column) after all insertions complete
     */
    private fun insertCellsWithWrappingInternal(
        cells: List<Cell>,
        cx: Int,
        cy: Int
    ): Pair<Int, Int> {
        if (cells.isEmpty()) return Pair(cy, cx)

        // Write cells to current line and get overflow
        val overflow = screenBuffer[cy].writeWithWrappingCells(cx, cells)

        // If there's overflow, handle it recursively
        if (overflow.isNotEmpty()) {
            val nextRow = cy + 1

            if (nextRow < height) {
                // Recursively insert overflow into next line
                return insertCellsWithWrappingInternal(overflow, 0, nextRow)
            } else {
                // We're on the last line, need to scroll
                insertEmptyLineAtBottom()

                // Insert overflow on the new last line
                return insertCellsWithWrappingInternal(overflow, 0, height - 1)
            }
        }

        // No overflow, cursor ends at the end of written cells
        // Calculate display width (account for wide characters)
        var displayWidth = 0
        for (cell in cells) {
            displayWidth += charDisplayWidth(cell.char)
        }
        val finalCx = cx + displayWidth
        return Pair(cy, finalCx)
    }

    /**
     * Insert cells at the specified position with wrapping.
     * Public API that wraps the internal recursive implementation.
     *
     * @param cells List of cells to insert
     * @param cx Column position to start insertion
     * @param cy Row position to start insertion
     * @param moveCursor Whether to move the cursor to the end of inserted content (defaults to false)
     */
    fun insertCellsWithWrapping(
        cells: List<Cell>,
        cx: Int,
        cy: Int,
        moveCursor: Boolean = false
    ) {
        if (cells.isEmpty()) return

        val finalPos = insertCellsWithWrappingInternal(cells, cx, cy)

        // Move cursor if requested
        if (moveCursor) {
            cursor.moveTo(finalPos.first, finalPos.second.coerceIn(0, width))
        }
    }

    /**
     * Insert a blank line at the bottom of the screen.
     * The top line is pushed into scrollback, all lines shift up by one,
     * and a new blank line appears at the bottom.
     * Does not move the cursor.
     * Increments viewport offset if currently scrolled up.
     */
    fun insertEmptyLineAtBottom() {
        addScrollbackLine(screenBuffer[0])

        for (i in 0 until height - 1) {
            screenBuffer[i] = screenBuffer[i + 1]
        }

        screenBuffer[height - 1] = Line(width)

        if (viewportOffset > 0) {
            viewportOffset++
        }
    }

    // Line operations

    /**
     * Advance the cursor to the start of the next line.
     * If the cursor is in the middle of the screen (cy < height-1), moves down one row.
     * If the cursor is on the last screen row, scrolls the screen up by one line:
     * - Top line is pushed into scrollback
     * - All lines shift up by one
     * - A fresh blank line appears at the bottom
     * - Cursor moves to column 0 of the last line
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

            screenBuffer[height - 1] = Line(width)

            cursor.moveTo(height - 1, 0)

            if (viewportOffset > 0) {
                viewportOffset++
            }
        }
    }

    /**
     * Get the screen line at the specified row.
     * Does not account for viewport scrolling - use [getVisibleLine] for that.
     *
     * @param cy Row index (0-based, 0 = top of screen)
     * @return The Line object at the specified row
     */
    fun getLine(cy: Int): Line {
        return screenBuffer[cy]
    }

    /**
     * Get the line visible at viewport row [row] (0 = top of viewport).
     *
     * When scrolling up (viewportOffset > 0), older scrollback lines appear at the top,
     * and screen lines shift down. This matches standard terminal behavior where
     * scrolling up reveals older history at the top of the screen.
     */
    fun getVisibleLine(row: Int): Line {
        require(row in 0 until height) { "row $row out of viewport bounds [0, $height)" }

        return if (row < viewportOffset) {
            // Show scrollback at top (older content)
            // scrollback[0] = newest, so we want scrollback[viewportOffset - 1 - row]
            val scrollbackIndex = viewportOffset - 1 - row
            if (scrollbackIndex >= scrollbackBuffer.size) {
                // Beyond scrollback, return empty line
                Line(width)
            } else {
                scrollbackBuffer[scrollbackIndex]
            }
        } else {
            // Show screen lines shifted down
            val screenIndex = row - viewportOffset
            if (screenIndex >= height) {
                // Shouldn't happen, but return empty line
                Line(width)
            } else {
                screenBuffer[screenIndex]
            }
        }
    }

    // Cursor operations

    /**
     * Get the cursor instance.
     * The returned cursor can be used to query or modify the current position.
     *
     * @return The cursor managing the current write position
     */
    fun getCursor(): Cursor {
        return cursor
    }

    /**
     * Move cursor to position [cy], [cx].
     * Ensures cursor doesn't land on continuation cells (second half of wide characters).
     * If landing on a continuation, moves to the wide character start.
     *
     * Note: Parameters use (row, column) convention matching array indexing.
     *
     * @param cy Row position (0-based)
     * @param cx Column position (0-based)
     */
    fun moveCursorTo(cy: Int, cx: Int) {
        cursor.moveTo(cy, cx)
        cursor.clampCursor(0, 0, width - 1, height - 1)
        adjustCursorForWideChars(preferLeft = true)  // Position at wide char start
    }

    /**
     * Adjust cursor position if it lands on a continuation cell (second half of wide character).
     *
     * @param preferLeft If true (moving left), jumps to wide char start. If false (moving right/other),
     *                   jumps to next character after the wide char.
     */
    private fun adjustCursorForWideChars(preferLeft: Boolean = false) {
        val cell = screenBuffer[cursor.cy].getCell(cursor.cx)
        if (cell?.char == '\u0000') {
            // On continuation cell - adjust based on movement direction
            if (preferLeft && cursor.cx > 0) {
                // Moving left: jump to wide character start (one position back)
                cursor.moveLeft(1)
            } else if (cursor.cx + 1 < width) {
                // Moving right/default: jump to next character (one position forward)
                cursor.moveRight(1)
            } else {
                // At line end: move to wide char start
                cursor.moveLeft(1)
            }
        }
    }

    /**
     * Move cursor left by [offset] positions and clamp to valid bounds.
     * Ensures cursor doesn't land on continuation cells (second half of wide characters).
     * When landing on a continuation, moves left to the wide character start.
     *
     * @param offset Number of positions to move left (defaults to 1)
     */
    fun moveCursorLeft(offset: Int = 1) {
        cursor.moveLeft(offset)
        cursor.clampCursor(0, 0, width - 1, height - 1)
        adjustCursorForWideChars(preferLeft = true)
    }

    /**
     * Move cursor right by [offset] positions and clamp to valid bounds.
     * Ensures cursor doesn't land on continuation cells (second half of wide characters).
     * When landing on a continuation, moves right to the next character.
     *
     * @param offset Number of positions to move right (defaults to 1)
     */
    fun moveCursorRight(offset: Int = 1) {
        cursor.moveRight(offset)
        cursor.clampCursor(0, 0, width - 1, height - 1)
        adjustCursorForWideChars(preferLeft = false)
    }

    /**
     * Move cursor up by [offset] positions and clamp to valid bounds.
     * Ensures cursor doesn't land on continuation cells (second half of wide characters).
     *
     * @param offset Number of positions to move up (defaults to 1)
     */
    fun moveCursorUp(offset: Int = 1) {
        cursor.moveUp(offset)
        cursor.clampCursor(0, 0, width - 1, height - 1)
        adjustCursorForWideChars(preferLeft = true)  // Prefer left for vertical movement
    }

    /**
     * Move cursor down by [offset] positions and clamp to valid bounds.
     * Ensures cursor doesn't land on continuation cells (second half of wide characters).
     *
     * @param offset Number of positions to move down (defaults to 1)
     */
    fun moveCursorDown(offset: Int = 1) {
        cursor.moveDown(offset)
        cursor.clampCursor(0, 0, width - 1, height - 1)
        adjustCursorForWideChars(preferLeft = true)  // Prefer left for vertical movement
    }



    // Scrollback and viewport operations

    /**
     * Get the number of lines currently stored in the scrollback buffer.
     *
     * @return Number of lines in scrollback (0 to maxScrollbackSize)
     */
    fun getScrollbackSize(): Int = scrollbackBuffer.size

    /**
     * Add a line to the scrollback buffer.
     * If the scrollback is at max capacity, the oldest line is removed first.
     *
     * @param line The line to add to scrollback history
     */
    fun addScrollbackLine(line: Line) {
        if (scrollbackBuffer.size >= maxScrollbackSize) {
            scrollbackBuffer.removeLast()
        }
        scrollbackBuffer.addFirst(line)
    }

    /**
     * Get the current viewport scroll offset.
     *
     * @return 0 if showing the live screen, >0 if scrolled up into history
     */
    fun getViewportOffset(): Int = viewportOffset

    /**
     * Check whether the viewport is showing the live screen (not scrolled up).
     *
     * @return True if viewport is at the bottom (offset = 0), false otherwise
     */
    fun isAtBottom(): Boolean = viewportOffset == 0

    /**
     * Scroll the viewport up (towards older history) by the specified number of rows.
     * Does not move the cursor or affect screen content.
     * Automatically clamped to the available scrollback size.
     *
     * @param lines Number of rows to scroll up (defaults to 1)
     */
    fun scrollUp(lines: Int = 1) {
        viewportOffset = (viewportOffset + lines).coerceAtMost(scrollbackBuffer.size)
    }

    /**
     * Scroll the viewport down (towards the live screen) by the specified number of rows.
     * Does not move the cursor or affect screen content.
     * Automatically clamped to 0 (can't scroll past the live screen).
     *
     * @param lines Number of rows to scroll down (defaults to 1)
     */
    fun scrollDown(lines: Int = 1) {
        viewportOffset = (viewportOffset - lines).coerceAtLeast(0)
    }

    /**
     * Scroll the viewport to the top of the scrollback history (oldest content).
     * Does not move the cursor or affect screen content.
     */
    fun scrollToTop() {
        viewportOffset = scrollbackBuffer.size
    }

    /**
     * Scroll the viewport back to the live screen (newest content).
     * Does not move the cursor or affect screen content.
     */
    fun scrollToBottom() {
        viewportOffset = 0
    }

    /**
     * Clear the scrollback buffer, removing all history.
     * Also resets the viewport offset to 0.
     * Does not affect the screen content or cursor position.
     */
    fun clearScrollback() {
        scrollbackBuffer.clear()
        viewportOffset = 0
    }

    // Cell attribute operations

    /**
     * Get the current default cell attributes.
     * These attributes are used when writing new content without explicit attributes.
     *
     * @return The current default CellAttributes
     */
    fun getAttributes(): CellAttributes {
        return this.defaultAttributes
    }

    /**
     * Set the default cell attributes for future write operations.
     * This does not affect existing content, only new content written after this call.
     *
     * @param cellAttributes The new default cell attributes
     */
    fun setAttributes(cellAttributes: CellAttributes) {
        this.defaultAttributes = cellAttributes
    }

    /**
     * Gets the line at [cy] as string.
     * If [cy] is over the screen limit, you will get the [cy] - [screenBuffer]'s size element of the scrollback.
     * @param cy the line.
     */
    fun getLineAsString(cy: Int): String {
        return if(cy > screenBuffer.size - 1) {
            scrollbackBuffer[cy-screenBuffer.size].toString()
        } else {
            screenBuffer[cy].toString()
        }
    }

    /**
     * Get screen as string.
     */
    fun getScreenAsString(): String {
        return screenBuffer.joinToString("\n") { it.toString() } + "\n"
    }

    /**
     * Get scrollback as string.
     */
    fun getScrollbackAsString(): String {
        return scrollbackBuffer.joinToString("\n") { it.toString() } + "\n"
    }

    /**
     * Get both screen and scrollback as screen.
     */
    fun getScrollbackAndScreenAsString(): String {
        return getScrollbackAsString() + "\n" + getScreenAsString()
    }

    /**
     * Resize the terminal buffer to new dimensions.
     *
     * Width decrease: Reflows content by wrapping overflow to new lines (preserves all content).
     * Width increase: Pads lines with spaces (no unwrapping).
     * Height decrease: Moves excess screen lines to scrollback.
     * Height increase: Adds blank lines at bottom.
     * Cursor: Clamped to new bounds and adjusted to avoid continuation cells.
     * Scrollback: Applies same reflow logic to preserve history.
     * Viewport: Clamped to remain valid.
     *
     * @param newWidth New number of columns
     * @param newHeight New number of rows
     */
    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0 && newHeight > 0) { "Dimensions must be positive" }

        // Early return if dimensions haven't changed
        if (newWidth == width && newHeight == height) return

        // 1. Reflow width changes
        if (newWidth != width) {
            // Track if there was existing scrollback and count content lines before reflow
            val hadScrollback = scrollbackBuffer.isNotEmpty()
            var contentLineCount = 0

            // Collect lines with content from scrollback (oldest first) + screen
            val contentLines = mutableListOf<Line>()

            // Add scrollback lines (stored newest-first, so reverse to get oldest-first)
            for (line in scrollbackBuffer.reversed()) {
                if (line.getContentLength() > 0) {
                    contentLines.add(line)
                    contentLineCount++
                }
            }

            // Add screen lines that have content
            for (line in screenBuffer) {
                if (line.getContentLength() > 0) {
                    contentLines.add(line)
                    contentLineCount++
                }
            }

            // Reflow all content lines
            val reflowedLines = if (contentLines.isNotEmpty()) {
                reflowBuffer(contentLines, newWidth)
            } else {
                emptyArray()
            }

            // Split back into scrollback and screen
            scrollbackBuffer.clear()

            if (reflowedLines.size <= newHeight) {
                // All lines fit on screen, pad with empty lines if needed
                screenBuffer = Array(newHeight) { i ->
                    if (i < reflowedLines.size) reflowedLines[i]
                    else Line(newWidth)
                }
            } else {
                // More lines than screen height - determine strategy
                val keepFirstLines = !hadScrollback && contentLineCount > 1

                if (keepFirstLines) {
                    // Multiple content lines, no scrollback: keep FIRST newHeight lines (preserve positions)
                    screenBuffer = reflowedLines.sliceArray(0 until newHeight)
                    // Overflow is discarded (doesn't go to scrollback)
                } else {
                    // Single line or had scrollback: keep LAST newHeight lines on screen (cursor/newest content)
                    // Put rest in scrollback (older content)
                    val scrollbackLines = reflowedLines.sliceArray(0 until (reflowedLines.size - newHeight))
                    screenBuffer = reflowedLines.sliceArray((reflowedLines.size - newHeight) until reflowedLines.size)

                    // Add to scrollback in reverse order (newest first)
                    for (i in scrollbackLines.size - 1 downTo 0) {
                        scrollbackBuffer.addFirst(scrollbackLines[i])
                    }
                }
            }

            width = newWidth
        }

        // 2. Adjust height (if width didn't change)
        when {
            newHeight < screenBuffer.size -> {
                // Height decreased: move excess lines to scrollback
                // Keep the last newHeight lines on screen, move rest to scrollback
                val excessLines = screenBuffer.size - newHeight
                for (i in 0 until excessLines) {
                    addScrollbackLine(screenBuffer[i])
                }
                screenBuffer = screenBuffer.sliceArray(excessLines until screenBuffer.size)
            }
            newHeight > screenBuffer.size -> {
                // Height increased: add blank lines at bottom
                val newBuffer = Array(newHeight) { i ->
                    if (i < screenBuffer.size) screenBuffer[i]
                    else Line(width)
                }
                screenBuffer = newBuffer
            }
        }
        height = newHeight

        // 3. Adjust cursor (clamp to new bounds and avoid continuation cells)
        cursor.clampCursor(0, 0, width - 1, height - 1)
        adjustCursorForWideChars(preferLeft = true)

        // 4. Adjust viewport offset (clamp to new scrollback size)
        viewportOffset = viewportOffset.coerceAtMost(scrollbackBuffer.size)
    }

    /**
     * Reflow a list of lines to a new width.
     *
     * @param lines Lines to reflow
     * @param newWidth Target width for reflow
     * @return Array of reflowed lines
     */
    private fun reflowBuffer(lines: List<Line>, newWidth: Int): Array<Line> {
        val reflowed = mutableListOf<Line>()
        for (line in lines) {
            reflowed.addAll(line.reflowToWidth(newWidth))
        }
        return reflowed.toTypedArray()
    }

    // Access
    /**
     * Get the cell at position [cx] [cy].
     * If [cy] is over the screen limit, you will get the [cy] - [screenBuffer]'s size element of the scrollback.
     *
     * @param cx Column position to start insertion
     * @param cy Row position to start insertion
     */
    private fun getCellAtPosition(cx: Int, cy: Int): Cell? {
        require(cx in 0 until width) { "cx $cx out of bounds [0, $width)" }
        require(cy in 0 until (height + scrollbackBuffer.size)) {
            "cy $cy out of bounds [0, ${height + scrollbackBuffer.size})"
        }

        return if (cy < height) {
            // Screen area
            screenBuffer[cy].getCell(cx)
        } else {
            // Scrollback area
            val scrollIndex = cy - height
            scrollbackBuffer[scrollIndex].getCell(cx)
        }
    }
    /**
     * Get the cellAttributes instance at position [cx] [cy].
     * If [cy] is over the screen limit, you will get the [cy] - [screenBuffer]'s size element of the scrollback.
     *
     * @param cx Column position to start insertion
     * @param cy Row position to start insertion
     */
    fun getAttributeAtPosition(cx: Int, cy: Int): CellAttributes {
        val cell = getCellAtPosition(cx, cy)
        requireNotNull(cell) { "No cell at position ($cx, $cy) - cell is uninitialized" }
        return cell.attributes
    }

    /**
     * Get the cell char at position [cx] [cy].
     * If [cy] is over the screen limit, you will get the [cy] - [screenBuffer]'s size element of the scrollback.
     *
     * @param cx Column position to start insertion
     * @param cy Row position to start insertion
     */
    fun getCharAtPosition(cx: Int, cy: Int): Char {
        val cell = getCellAtPosition(cx, cy)
        requireNotNull(cell) { "No cell at position ($cx, $cy) - cell is uninitialized" }
        return cell.char
    }


}
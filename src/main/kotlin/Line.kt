package com.david

/**
 * A line in the terminal buffer, represented as a fixed-size array of nullable cells.
 *
 * - null cells represent positions that have never been written to
 * - Cell(' ') represents space characters (user-typed or gap-filled by setCellAt)
 * - When writing at a position, gaps before it are automatically filled with spaces
 *   (matching real terminal emulator behavior like xterm.js and VT100)
 *
 * The line can be resized when needed (for future terminal resize support).
 */
class Line(initialCapacity: Int) {
    var maxCells: Int = initialCapacity
        private set

    private var cells: Array<Cell?> = arrayOfNulls(initialCapacity)

    companion object {
        private val DEFAULT_BLANK_ATTRIBUTES = CellAttributes()
    }

    /**
     * Get cell at [index]. Returns null for empty cells.
     * Throws if index is out of bounds.
     */
    fun getCell(index: Int): Cell? {
        require(index in 0 until maxCells) { "index $index out of bounds [0, $maxCells)" }
        return cells[index]
    }

    /**
     * Set cell at [index] to [char] with [attributes].
     * Fills gaps with blank space cells when writing beyond current content,
     * matching real terminal emulator behavior (xterm, VT100, etc.).
     *
     * Wide characters (CJK, emoji) occupy 2 cells: the character itself and a continuation marker (\u0000).
     */
    fun setCellAt(index: Int, char: Char, attributes: CellAttributes = CellAttributes()) {
        require(index in 0 until maxCells) { "index $index out of bounds [0, $maxCells)" }

        // Fill gaps with blank space cells when writing beyond current content
        // This matches real terminal emulator behavior (xterm.js, VT100, etc.)
        for (i in 0 until index) {
            if (cells[i] == null) {
                cells[i] = Cell(' ', DEFAULT_BLANK_ATTRIBUTES)
            }
        }

        val width = charDisplayWidth(char)

        if (width == 2) {
            // Wide character - need 2 cells
            if (index + 1 >= maxCells) {
                // Not enough space for wide character, write space instead
                cells[index] = null
            } else {
                // Clear any existing wide character if we're overwriting continuation
                if (index > 0 && cells[index] != null && cells[index]!!.char == '\u0000') {
                    cells[index - 1] = Cell(' ', DEFAULT_BLANK_ATTRIBUTES)
                }
                // Write wide character and continuation marker
                cells[index] = Cell(char, attributes)
                cells[index + 1] = Cell('\u0000', attributes)  // Continuation marker
            }
        } else {
            // Single-width character
            // If overwriting first cell of wide character, clear continuation too
            if (index + 1 < maxCells && cells[index + 1] != null &&
                cells[index + 1]!!.char == '\u0000') {
                cells[index + 1] = Cell(' ', DEFAULT_BLANK_ATTRIBUTES)
            }
            // If overwriting continuation cell, clear the wide character before it
            if (index > 0 && cells[index] != null && cells[index]!!.char == '\u0000') {
                cells[index - 1] = Cell(' ', DEFAULT_BLANK_ATTRIBUTES)
            }
            cells[index] = Cell(char, attributes)
        }
    }

    /**
     * Clear the cell at [index] (set to null).
     * For wide characters, clears both the character and its continuation marker.
     * Gaps are automatically filled with spaces to maintain terminal emulator behavior.
     */
    fun clearCellAt(index: Int) {
        require(index in 0 until maxCells) { "index $index out of bounds [0, $maxCells)" }

        // Check if we're clearing a wide character (has continuation marker)
        val isWideChar = cells[index] != null &&
                         index + 1 < maxCells &&
                         cells[index + 1]?.char == '\u0000'

        // Check if we're clearing a continuation cell
        val isContinuation = cells[index]?.char == '\u0000'

        if (isContinuation && index > 0) {
            // Clearing continuation - also clear the wide character before it
            cells[index - 1] = null
            cells[index] = null
        } else if (isWideChar) {
            // Clearing wide character - also clear its continuation
            cells[index] = null
            cells[index + 1] = null
        } else {
            // Normal single-width clear
            cells[index] = null
        }

        fixGaps()
    }

    /**
     * Clear a range of cells from [start] to [end) (exclusive).
     * For wide characters, expands the range to include both cells of any partially-covered wide character.
     * Clamps to valid bounds.
     * Gaps are automatically filled with spaces to maintain terminal emulator behavior.
     */
    fun clearRange(start: Int, end: Int) {
        val clampedEnd = minOf(end, maxCells)
        val clampedStart = maxOf(start, 0)
        if (clampedStart >= clampedEnd) return

        // Adjust start: if clearing a continuation cell, include the wide character before it
        var adjustedStart = clampedStart
        if (clampedStart > 0 && cells[clampedStart]?.char == '\u0000') {
            adjustedStart = clampedStart - 1
        }

        // Adjust end: if range ends on a wide character, include its continuation
        var adjustedEnd = clampedEnd
        if (clampedEnd > 0 && clampedEnd - 1 < maxCells) {
            val cellBeforeEnd = cells[clampedEnd - 1]
            if (cellBeforeEnd != null &&
                clampedEnd < maxCells &&
                cells[clampedEnd]?.char == '\u0000') {
                adjustedEnd = clampedEnd + 1
            }
        }

        for (i in adjustedStart until adjustedEnd) {
            cells[i] = null
        }
        fixGaps()
    }

    /**
     * Clear all cells in the line (set to null).
     */
    fun clearLine() {
        cells.fill(null)
    }

    /**
     * Get the actual content length (position of last non-null cell + 1).
     * Returns 0 for empty lines.
     */
    fun getContentLength(): Int {
        for (i in maxCells - 1 downTo 0) {
            if (cells[i] != null) return i + 1
        }
        return 0
    }


    /**
     * Check if line is full (last cell is non-null).
     */
    fun isFull(): Boolean = cells[maxCells - 1] != null

    /**
     * Fix gaps in the line by filling null cells with spaces up to the last non-null cell.
     * This maintains the invariant that a line has no gaps in its content.
     * Called after operations that might create gaps (clearCellAt, clearRange).
     */
    private fun fixGaps() {
        val contentLength = getContentLength()
        if (contentLength == 0) return  // Empty line, nothing to fix

        // Fill all gaps from 0 to the last non-null cell
        for (i in 0 until contentLength) {
            if (cells[i] == null) {
                cells[i] = Cell(' ', DEFAULT_BLANK_ATTRIBUTES)
            }
        }
    }

    fun fill(char: Char, cellAttributes: CellAttributes) {
        cells.fill(Cell(char, cellAttributes))
    }

    /**
     * Delete character at [index] and shift remaining characters left.
     * The last cell becomes null. This matches standard terminal delete behavior (DCH).
     * For wide characters, deletes both cells (the character and its continuation marker).
     *
     * @param index Position to delete at (0-based)
     */
    fun deleteCharAt(index: Int) {
        require(index in 0 until maxCells) { "index $index out of bounds [0, $maxCells)" }

        // Check if we're deleting a wide character
        val isWideChar = cells[index] != null &&
                         index + 1 < maxCells &&
                         cells[index + 1]?.char == '\u0000'

        // Check if we're deleting a continuation cell
        val isContinuation = cells[index]?.char == '\u0000'

        if (isContinuation && index > 0) {
            // Deleting continuation - delete the wide character before it
            for (i in index - 1 until maxCells - 1) {
                cells[i] = cells[i + 1]
            }
            cells[maxCells - 1] = null
            // Shift again to remove continuation
            for (i in index - 1 until maxCells - 1) {
                cells[i] = cells[i + 1]
            }
            cells[maxCells - 1] = null
        } else if (isWideChar) {
            // Deleting wide character - remove both cells
            for (i in index until maxCells - 2) {
                cells[i] = cells[i + 2]
            }
            cells[maxCells - 2] = null
            cells[maxCells - 1] = null
        } else {
            // Normal single-width delete
            for (i in index until maxCells - 1) {
                cells[i] = cells[i + 1]
            }
            cells[maxCells - 1] = null
        }
    }

    /**
     * Write text at [startIndex] with [attributes], returning cells that overflow beyond maxCells.
     *
     * Behavior:
     * - Scans ahead from startIndex to find the first non-space, non-null cell
     * - If found, shifts all content from that point right by text.length positions
     * - Writes the text at startIndex
     * - Returns cells that overflow beyond maxCells
     *
     * @param startIndex Position to start writing at (0-based)
     * @param text Text to write
     * @param attributes Attributes for the written cells
     * @return List of cells that were pushed off the end of the line
     */
    fun writeWithWrapping(
        startIndex: Int,
        text: String,
        attributes: CellAttributes = CellAttributes()
    ): List<Cell> {
        // Convert text to cells and delegate to writeWithWrappingCells
        val cellsToWrite = text.map { Cell(it, attributes) }
        return writeWithWrappingCells(startIndex, cellsToWrite)
    }

    /**
     * Write cells at [startIndex], returning cells that overflow beyond maxCells.
     *
     * Behavior:
     * - Scans from startIndex to end of line to find the first non-space, non-null cell
     * - If found, shifts all content from that point right by the display width of cells
     * - Writes the cells at startIndex
     * - Returns cells that overflow beyond maxCells
     * - Properly handles wide characters (CJK, emoji) that occupy 2 cells
     *
     * @param startIndex Position to start writing at (0-based)
     * @param cellsToWrite Cells to write
     * @return List of cells that were pushed off the end of the line
     */
    fun writeWithWrappingCells(
        startIndex: Int,
        cellsToWrite: List<Cell>
    ): List<Cell> {
        require(startIndex in 0 until maxCells) {
            "startIndex $startIndex out of bounds [0, $maxCells)"
        }

        if (cellsToWrite.isEmpty()) {
            return emptyList()
        }

        val overflow = mutableListOf<Cell>()

        // Find the first non-space, non-null, non-continuation cell from startIndex to end of line
        var firstContentPos = -1
        for (i in startIndex until maxCells) {
            val cell = cells[i]
            if (cell != null && cell.char != ' ' && cell.char != '\u0000') {
                firstContentPos = i
                break
            }
        }

        // Calculate display width of cells to write
        var cellsDisplayWidth = 0
        var cellCount = 0
        for (cell in cellsToWrite) {
            val charWidth = charDisplayWidth(cell.char)
            if (startIndex + cellsDisplayWidth + charWidth > maxCells) {
                // This cell would overflow, stop counting
                break
            }
            cellsDisplayWidth += charWidth
            cellCount++
        }

        // If we found content, we need to insert (shift right)
        if (firstContentPos != -1) {
            // Collect all cells from firstContentPos to end (skipping continuation markers when collecting)
            val cellsToShift = mutableListOf<Cell>()
            var i = firstContentPos
            while (i < maxCells) {
                val cell = cells[i]
                if (cell != null) {
                    cellsToShift.add(cell)
                    // Skip continuation marker if this is a wide character
                    if (i + 1 < maxCells && cells[i + 1]?.char == '\u0000') {
                        cellsToShift.add(cells[i + 1]!!)
                        i += 2
                    } else {
                        i++
                    }
                } else {
                    i++
                }
            }

            // Place shifted cells at their new positions
            var writePos = firstContentPos + cellsDisplayWidth
            for (cell in cellsToShift) {
                if (writePos >= maxCells) {
                    // Only add actual characters to overflow, not continuation markers
                    if (cell.char != '\u0000') {
                        overflow.add(cell)
                    }
                } else {
                    cells[writePos] = cell
                    writePos++
                }
            }
        }

        // Now write the cells at startIndex
        var writePos = startIndex
        for (i in 0 until cellCount) {
            val cell = cellsToWrite[i]
            val charWidth = charDisplayWidth(cell.char)

            if (writePos >= maxCells) {
                // Cell doesn't fit, add to overflow
                overflow.add(cell)
            } else if (charWidth == 2 && writePos + 1 >= maxCells) {
                // Wide character doesn't fit, add to overflow
                overflow.add(cell)
            } else {
                // Write the cell
                cells[writePos] = Cell(cell.char, cell.attributes)
                if (charWidth == 2) {
                    cells[writePos + 1] = Cell('\u0000', cell.attributes)
                    writePos += 2
                } else {
                    writePos++
                }
            }
        }

        // Add remaining cells that didn't fit to overflow
        for (i in cellCount until cellsToWrite.size) {
            overflow.add(cellsToWrite[i])
        }

        return overflow
    }

    override fun toString(): String {
        val builder = StringBuilder()
        for (cell in cells) {
            if (cell == null) {
                return builder.toString()
            }
            // Skip continuation markers in output
            if (cell.char != '\u0000') {
                builder.append(cell.char)
            }
        }
        return builder.toString()
    }
}

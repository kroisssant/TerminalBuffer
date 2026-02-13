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
     */
    fun setCellAt(index: Int, char: Char, attributes: CellAttributes = CellAttributes()) {
        require(index in 0 until maxCells) { "index $index out of bounds [0, $maxCells)" }

        // Fill gaps with blank space cells when writing beyond current content
        // This matches real terminal emulator behavior (xterm.js, VT100, etc.)
        for (i in 0 until index) {
            if (cells[i] == null) {
                cells[i] = Cell(' ', CellAttributes())
            }
        }

        cells[index] = Cell(char, attributes)
    }

    /**
     * Clear the cell at [index] (set to null).
     * Gaps are automatically filled with spaces to maintain terminal emulator behavior.
     */
    fun clearCellAt(index: Int) {
        require(index in 0 until maxCells) { "index $index out of bounds [0, $maxCells)" }
        cells[index] = null
        fixGaps()
    }

    /**
     * Clear a range of cells from [start] to [end) (exclusive).
     * Clamps to valid bounds.
     * Gaps are automatically filled with spaces to maintain terminal emulator behavior.
     */
    fun clearRange(start: Int, end: Int) {
        val clampedEnd = minOf(end, maxCells)
        val clampedStart = maxOf(start, 0)
        if (clampedStart >= clampedEnd) return

        for (i in clampedStart until clampedEnd) {
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
     * Resize the line to [newCapacity].
     * If growing, new cells are initialized to null.
     * If shrinking, cells beyond newCapacity are discarded.
     */
    fun resize(newCapacity: Int) {
        require(newCapacity > 0) { "newCapacity must be positive" }
        if (newCapacity == maxCells) return

        val newCells = arrayOfNulls<Cell>(newCapacity)
        val copyLength = minOf(maxCells, newCapacity)
        cells.copyInto(newCells, 0, 0, copyLength)

        cells = newCells
        maxCells = newCapacity
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
                cells[i] = Cell(' ', CellAttributes())
            }
        }
    }

    fun fill(char: Char, cellAttributes: CellAttributes) {
        cells.fill(Cell(char, cellAttributes))
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
     * - If found, shifts all content from that point right by cells.size positions
     * - Writes the cells at startIndex
     * - Returns cells that overflow beyond maxCells
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

        // Find the first non-space, non-null cell from startIndex to end of line
        var firstContentPos = -1
        for (i in startIndex until maxCells) {
            val cell = cells[i]
            if (cell != null && cell.char != ' ') {
                firstContentPos = i
                break
            }
        }

        // If we found content, we need to insert (shift right)
        if (firstContentPos != -1) {
            // Collect all cells from firstContentPos to end
            val cellsToShift = mutableListOf<Cell>()
            for (i in firstContentPos until maxCells) {
                cells[i]?.let { cellsToShift.add(it) }
            }

            // Calculate how much we're shifting (number of cells that fit)
            val cellsThatFit = minOf(cellsToWrite.size, maxCells - startIndex)
            val shiftAmount = cellsThatFit

            // Place shifted cells at their new positions
            for ((index, cell) in cellsToShift.withIndex()) {
                val newPos = firstContentPos + shiftAmount + index
                if (newPos >= maxCells) {
                    overflow.add(cell)
                } else {
                    cells[newPos] = cell
                }
            }
        }

        // Now write the cells at startIndex
        for ((i, cell) in cellsToWrite.withIndex()) {
            val writePos = startIndex + i
            if (writePos >= maxCells) {
                // Cell doesn't fit, add to overflow
                overflow.add(cell)
            } else {
                cells[writePos] = Cell(cell.char, cell.attributes)
            }
        }

        return overflow
    }

    override fun toString(): String {
        var string = ""
        for(char in this.cells) {
            if(char == null) {
                return string
            }
            string += char.char
        }
        return ""
    }
}

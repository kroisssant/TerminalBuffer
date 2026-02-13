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
     * Legacy method for compatibility with tests.
     * Add a cell at the end of existing content.
     */
    @Deprecated("Use setCellAt with explicit index instead")
    fun addCell(char: Char, attributes: CellAttributes = CellAttributes()) {
        val contentLength = getContentLength()
        require(contentLength < maxCells) { "Line is full" }
        setCellAt(contentLength, char, attributes)
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
}

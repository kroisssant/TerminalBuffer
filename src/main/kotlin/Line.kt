package com.david

/**
 * Calculate character width for terminal display.
 * Returns 2 for wide characters (CJK ideographic), 1 for everything else.
 * Only supports characters in the Basic Multilingual Plane (BMP, U+0000-U+FFFF).
 */

private fun blankCell(attributes: CellAttributes = CellAttributes()): Cell {
    return Cell(' ', attributes)
}

class Line(val maxCells: Int) {
    val cells: MutableList<Cell> = mutableListOf()

    /**
     * Add a cell with the [char] and attributes [attributes].
     */
    fun addCell(char: Char, attributes: CellAttributes = CellAttributes()) {
        cells.add(Cell(char, attributes))
    }

    /**
     * Determine if the line is full
     */
    fun isFull(): Boolean = cells.size >= maxCells

    /**
     * Pad cells with blank spaces up to [size] for random access.
     */
    fun ensureSize(size: Int) {
        while (cells.size < size) {
            cells.add(blankCell())
        }
    }

    /**
     * Get cell at [index], growing the line if needed.
     */
    fun getCell(index: Int): Cell {
        ensureSize(index + 1)
        return cells[index]
    }
}

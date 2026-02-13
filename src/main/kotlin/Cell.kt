package com.david


/**
 * Cell class to hold one cell for the terminal grid.
 */
class Cell(
    var char: Char,
    var attributes: CellAttributes = CellAttributes(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Cell) return false
        return char == other.char && attributes == other.attributes
    }

    override fun hashCode(): Int {
        var result = char.hashCode()
        result = 31 * result + attributes.hashCode()
        return result
    }
}

/**
 * Basic cell attributes.
 */
data class CellAttributes(
    val fgColor: TerminalColor = TerminalColor.White,
    val bgColor: TerminalColor = TerminalColor.Black,
    val style: Style = Style.Normal
)

/**
 * Style used to show text.
 */
enum class Style {
    Normal, Bold, Underline, Blink, Reverse, Concealed
}

/**
 * Terminal basic 16 colors.
 */
enum class TerminalColor {
    Black,
    Red,
    Green,
    Yellow,
    Blue,
    Magenta,
    Cyan,
    White,
    BrightBlack,
    BrightRed,
    BrightGreen,
    BrightYellow,
    BrightBlue,
    BrightMagenta,
    BrightCyan,
    BrightWhite
}
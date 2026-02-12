package com.david


/**
 * Cell class to hold on cell for the terminal gird.
 */
class Cell(
    var char: Char,
    var attributes: CellAttributes = CellAttributes(),
)

/**
 * Basic cell attributes.
 */
data class CellAttributes(
    var fgColor: TerminalColor = TerminalColor.White,
    var bgColor: TerminalColor = TerminalColor.Black,
    var style: Style = Style.Normal
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
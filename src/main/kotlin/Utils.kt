package com.david

/**
 * Determines the display width of a character in a terminal.
 * Returns 2 for wide characters (CJK, emojis, etc.), 1 for normal characters.
 */
fun charDisplayWidth(char: Char): Int {
    return charDisplayWidth(char.code)
}

/**
 * Determines the display width based on Unicode code point.
 * Supports CJK characters and emojis without external libraries.
 */
fun charDisplayWidth(codePoint: Int): Int {
    return when {
        // CJK Unified Ideographs and extensions
        codePoint in 0x1100..0x115F -> 2  // Hangul Jamo
        codePoint in 0x231A..0x231B -> 2  // Watch, Hourglass
        codePoint in 0x2329..0x232A -> 2  // Left/Right angle brackets
        codePoint in 0x23E9..0x23EC -> 2  // Fast-forward, reverse
        codePoint in 0x23F0..0x23F0 -> 2  // Alarm clock
        codePoint in 0x23F3..0x23F3 -> 2  // Hourglass
        codePoint in 0x25FD..0x25FE -> 2  // White/Black medium squares
        codePoint in 0x2614..0x2615 -> 2  // Umbrella, hot beverage
        codePoint in 0x2648..0x2653 -> 2  // Zodiac signs
        codePoint in 0x267F..0x267F -> 2  // Wheelchair symbol
        codePoint in 0x2693..0x2693 -> 2  // Anchor
        codePoint in 0x26A1..0x26A1 -> 2  // High voltage
        codePoint in 0x26AA..0x26AB -> 2  // White/Black circles
        codePoint in 0x26BD..0x26BE -> 2  // Soccer ball, baseball
        codePoint in 0x26C4..0x26C5 -> 2  // Snowman, sun
        codePoint in 0x26CE..0x26CE -> 2  // Ophiuchus
        codePoint in 0x26D4..0x26D4 -> 2  // No entry
        codePoint in 0x26EA..0x26EA -> 2  // Church
        codePoint in 0x26F2..0x26F3 -> 2  // Fountain, flag
        codePoint in 0x26F5..0x26F5 -> 2  // Sailboat
        codePoint in 0x26FA..0x26FA -> 2  // Tent
        codePoint in 0x26FD..0x26FD -> 2  // Fuel pump
        codePoint in 0x2705..0x2705 -> 2  // Check mark
        codePoint in 0x270A..0x270B -> 2  // Fist, hand
        codePoint in 0x2728..0x2728 -> 2  // Sparkles
        codePoint in 0x274C..0x274C -> 2  // Cross mark
        codePoint in 0x274E..0x274E -> 2  // Cross mark button
        codePoint in 0x2753..0x2755 -> 2  // Question/exclamation marks
        codePoint in 0x2757..0x2757 -> 2  // Exclamation mark
        codePoint in 0x2795..0x2797 -> 2  // Plus/minus/division signs
        codePoint in 0x27B0..0x27B0 -> 2  // Curly loop
        codePoint in 0x27BF..0x27BF -> 2  // Double curly loop
        codePoint in 0x2B1B..0x2B1C -> 2  // Black/White large squares
        codePoint in 0x2B50..0x2B50 -> 2  // Star
        codePoint in 0x2B55..0x2B55 -> 2  // Hollow red circle
        codePoint in 0x2E80..0x2EFF -> 2  // CJK Radicals Supplement
        codePoint in 0x2F00..0x2FDF -> 2  // Kangxi Radicals
        codePoint in 0x2FF0..0x2FFF -> 2  // Ideographic Description
        codePoint in 0x3000..0x303E -> 2  // CJK Symbols and Punctuation
        codePoint in 0x3041..0x3096 -> 2  // Hiragana
        codePoint in 0x3099..0x30FF -> 2  // Katakana
        codePoint in 0x3105..0x312F -> 2  // Bopomofo
        codePoint in 0x3131..0x318E -> 2  // Hangul Compatibility Jamo
        codePoint in 0x3190..0x31E3 -> 2  // Ideographic annotation
        codePoint in 0x31F0..0x321E -> 2  // Katakana/Hangul extensions
        codePoint in 0x3220..0x3247 -> 2  // Enclosed CJK
        codePoint in 0x3250..0x4DBF -> 2  // CJK extensions
        codePoint in 0x4DC0..0x4DFF -> 2  // Yijing Hexagram Symbols
        codePoint in 0x4E00..0x9FFF -> 2  // CJK Unified Ideographs
        codePoint in 0xA000..0xA48C -> 2  // Yi Syllables
        codePoint in 0xA490..0xA4C6 -> 2  // Yi Radicals
        codePoint in 0xAC00..0xD7A3 -> 2  // Hangul Syllables
        codePoint in 0xF900..0xFAFF -> 2  // CJK Compatibility Ideographs
        codePoint in 0xFE10..0xFE19 -> 2  // Vertical forms
        codePoint in 0xFE30..0xFE6F -> 2  // CJK Compatibility Forms
        codePoint in 0xFF00..0xFF60 -> 2  // Fullwidth Forms
        codePoint in 0xFFE0..0xFFE6 -> 2  // Fullwidth Forms

        // Emoji ranges (most common ones - Kotlin Char is 16-bit, so using code point ranges)
        // Note: Many emojis are in supplementary planes (> 0xFFFF) which need special handling
        codePoint in 0x1F000..0x1F02B -> 2  // Mahjong Tiles
        codePoint in 0x1F030..0x1F093 -> 2  // Domino Tiles
        codePoint in 0x1F0A0..0x1F0F5 -> 2  // Playing Cards
        codePoint in 0x1F100..0x1F1FF -> 2  // Enclosed Characters
        codePoint in 0x1F200..0x1F251 -> 2  // Enclosed Ideographic Supplement
        codePoint in 0x1F300..0x1F5FF -> 2  // Miscellaneous Symbols and Pictographs
        codePoint in 0x1F600..0x1F64F -> 2  // Emoticons
        codePoint in 0x1F680..0x1F6FF -> 2  // Transport and Map Symbols
        codePoint in 0x1F700..0x1F77F -> 2  // Alchemical Symbols
        codePoint in 0x1F780..0x1F7FF -> 2  // Geometric Shapes Extended
        codePoint in 0x1F800..0x1F8FF -> 2  // Supplemental Arrows-C
        codePoint in 0x1F900..0x1F9FF -> 2  // Supplemental Symbols and Pictographs
        codePoint in 0x1FA00..0x1FA6F -> 2  // Chess Symbols
        codePoint in 0x1FA70..0x1FAFF -> 2  // Symbols and Pictographs Extended-A

        else -> 1  // Default to single-width
    }
}

/**
 * Get display width for a string by summing individual character widths.
 * This is the display width, not the character count.
 */
fun stringDisplayWidth(str: String): Int {
    var width = 0
    var i = 0
    while (i < str.length) {
        val codePoint = str.codePointAt(i)
        width += charDisplayWidth(codePoint)
        i += Character.charCount(codePoint)
    }
    return width
}

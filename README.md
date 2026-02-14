# Terminal Buffer

A terminal buffer in Kotlin. Handles text rendering, wide characters (CJK/emoji), scrollback history, and dynamic resizing.

## Solution

The buffer uses a cell-based grid with four main pieces:

- **Cell** - A character with color and style attributes
- **Line** - A row of cells that handles wide characters
- **Cursor** - Tracks where to write next (row, column)
- **TerminalBuffer** - The screen + scrollback history

The screen is an array of lines you can see. Scrollback is a queue of lines that scrolled off the top. When you scroll up, you see older lines from scrollback.

## Design Decisions & Trade-offs

### Wide Characters Take 2 Cells

When you write a CJK character or emoji, it takes up 2 cells - one for the character, one for a continuation marker (`\u0000`).

**Why:** This is how xterm and VT100 work. Makes rendering straightforward - just skip continuation markers.

**Trade-off:** Cursor movement gets more complex since it needs to avoid landing in the middle of a wide character. But the rendering is simpler.

### Scrollback Uses ArrayDeque

New lines are added to the front (`addFirst`), old lines removed from the back when the limit is hit.

**Why:** Constant time O(1) for both operations.

**Trade-off:** Index 0 is the newest line, which feels backwards.

### Resizing Reflows Content

When width decreases, content wraps to new lines to preserve everything. When width increases, lines just get padded with spaces.

**Why:** You don't want to lose text when shrinking the terminal.

**Trade-off:** When a line wraps, we lose information about whether it wrapped naturally or the user pressed Enter. Without tracking this metadata for every line, we can't know which line breaks to remove when unwrapping. 

### Memory: null vs Space

Cells start as `null` (never written). Writing a space creates `Cell(' ')`.

**Why:** Saves memory for lines that are mostly empty.

**Trade-off:** More complex logic checking for nulls everywhere.

### Text Insertion

`insertTextWithWrapping` looks ahead from the insertion point. If it finds only spaces or empty cells, it just overwrites them. If it finds actual content, it shifts everything right.

**Why:** Terminals have both "insert mode" (shifts content) and "overwrite mode" (just replaces). This gives you both depending on what's already there.
## Build & Test

```bash
./gradlew build
./gradlew test
```

## What's Implemented

- Write text at cursor or specific positions
- Wide characters (CJK, emoji) using Unicode range detection
- Line wrapping when reaching the edge
- Scrollback history with configurable size
- Scroll up/down through history
- Cursor movement that avoids wide character middle positions
- Dynamic resize with content reflow
- Insert with overflow wrapping, delete character, fill line, clear screen
- 16 ANSI colors and 6 text styles (bold, underline, blink, reverse, concealed)

## What Could Be Improved

**Rerender Flags:** Right now if you change one character, you'd need to re-render everything. Adding a `flag: Boolean` per line would let you only re-render changed lines.

**Line Break Tracking:** Add `wrappedFromPrevious: Boolean` to each line. Then on resize we could intelligently unwrap soft breaks but keep hard breaks (where user hit Enter).

**More Colors:** Currently supports 16 ANSI colors. Could add 256-color palette or 24-bit RGB support.

**Multiple Styles per Cell:** Currently you can only have one type of style per cell.
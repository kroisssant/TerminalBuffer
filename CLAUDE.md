# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TerminalV2 is a terminal emulator buffer implementation in Kotlin/JVM. It models the core data structures of a terminal: cells, lines, a cursor, and a screen/scrollback buffer. Supports ANSI-style text attributes (16 colors, bold/underline/blink/reverse/concealed) and wide characters (CJK).

## Build Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.david.CursorTest"

# Run a single test method
./gradlew test --tests "com.david.TerminalBufferTest.testMethodName"
```

## Architecture

The buffer follows a classic terminal emulator cell-grid model with four core types:

- **Cell** (`Cell.kt`) — A single character cell with `CellAttributes` (foreground/background color via `TerminalColor` enum, text `Style` enum).
- **Cursor** (`Cursor.kt`) — Row/column position (`cy`/`cx`) with directional movement, `moveTo`, `reset`, and `clampCursor`.
- **Line** (`Line.kt`) — A row of `Cell` objects with a fixed max width (`maxCells`). Handles wide characters that occupy two columns. Supports `ensureSize` for lazy cell allocation.
- **TerminalBuffer** (`TerminalBuffer.kt`) — Top-level buffer managing a screen grid (`Array<Line>`) and a scrollback history (`ArrayDeque<Line>`). Owns a `Cursor` and default `CellAttributes`. Currently implements `write` (at cursor), `writeAt` (absolute position), and cursor access.

Data flows: `TerminalBuffer` → `Line` → `Cell`. The `Cursor` tracks position within the buffer. All source is in the `com.david` package.

## Tech Stack

- **Language:** Kotlin 2.2.21 targeting JVM
- **Build:** Gradle with Kotlin DSL
- **Testing:** kotlin-test with JUnit Platform
- **Source:** `src/main/kotlin/`, Tests: `src/test/kotlin/`

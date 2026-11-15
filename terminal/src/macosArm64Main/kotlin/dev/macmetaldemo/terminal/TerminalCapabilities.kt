package dev.macmetaldemo.terminal

import kotlinx.cinterop.*
import platform.posix.*

 data class TerminalCapabilities(val tty: Boolean, val term: String?, val columns: Int, val rows: Int, val unicode: Boolean)

internal fun TerminalCapabilities.supportsScreen(): Boolean {
    if (!tty || columns <= 0 || rows <= 0) return false
    val name = term ?: return false
    val families = listOf("xterm", "screen", "tmux", "rxvt", "alacritty", "wezterm", "foot", "kitty")
    return families.any { name == it || name.startsWith("$it-") }
}

internal fun capabilities(tty: Boolean, term: String?, size: Pair<Int, Int>?, unicode: Boolean): TerminalCapabilities =
    TerminalCapabilities(tty, term, size?.first ?: 0, size?.second ?: 0, unicode)

@OptIn(ExperimentalForeignApi::class)
fun terminalCapabilities(): TerminalCapabilities = memScoped {
    val size = alloc<winsize>()
    val tty = isatty(STDIN_FILENO) == 1 && isatty(STDOUT_FILENO) == 1
    val valid = tty && ioctl(STDOUT_FILENO, TIOCGWINSZ.convert(), size.ptr) == 0
    val term = getenv("TERM")?.toKString()
    val locale = getenv("LC_ALL")?.toKString() ?: getenv("LC_CTYPE")?.toKString() ?: getenv("LANG")?.toKString()
    capabilities(tty, term, if (valid) size.ws_col.toInt() to size.ws_row.toInt() else null,
        locale?.contains("UTF-8", ignoreCase = true) == true)
}

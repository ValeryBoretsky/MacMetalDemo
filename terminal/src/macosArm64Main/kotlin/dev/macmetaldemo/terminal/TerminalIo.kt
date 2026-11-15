package dev.macmetaldemo.terminal

interface TerminalIo {
    fun readLine(): String?
    fun write(text: String)
}

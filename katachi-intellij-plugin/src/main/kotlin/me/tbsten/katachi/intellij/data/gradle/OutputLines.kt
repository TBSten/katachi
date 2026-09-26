package me.tbsten.katachi.intellij.data.gradle

// CSI sequences (colors, cursor moves) and the two-character escapes Gradle's rich console might emit.
private val ANSI_ESCAPE = Regex("""\u001B\[[0-?]*[ -/]*[@-~]|\u001B[@-Z\\-_]""")

/** [line] without ANSI escape sequences, which a colored console may mix into the output (S-4). */
internal fun stripAnsi(line: String): String = ANSI_ESCAPE.replace(line, "")

/**
 * Turns output that arrives in chunks into lines. `\r\n`, `\n` and a lone `\r` all end a line.
 * [flush] hands over a last line that never got its line break (E-37).
 */
internal class OutputLineSplitter(private val onLine: (String) -> Unit) {
    private val pending = StringBuilder()
    private var afterCarriageReturn = false

    fun append(chunk: String) {
        for (c in chunk) {
            if (afterCarriageReturn) {
                afterCarriageReturn = false
                if (c == '\n') continue
            }
            when (c) {
                '\n' -> emit()
                '\r' -> {
                    emit()
                    afterCarriageReturn = true
                }
                else -> pending.append(c)
            }
        }
    }

    fun flush() {
        if (pending.isNotEmpty()) emit()
    }

    private fun emit() {
        onLine(pending.toString())
        pending.setLength(0)
    }
}

private val TASK_LINE = Regex("""^> Task (:\S+)""")

/** The task path of Gradle's `> Task :arch:compileTestKotlin UP-TO-DATE` line; `null` for any other line. */
internal fun taskPathOf(line: String): String? = TASK_LINE.find(line)?.groupValues?.get(1)

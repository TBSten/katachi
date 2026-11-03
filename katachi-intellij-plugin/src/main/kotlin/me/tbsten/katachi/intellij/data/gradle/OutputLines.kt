package me.tbsten.katachi.intellij.data.gradle

// CSI sequences (colors, cursor moves) and the two-character escapes Gradle's rich console might emit.
private val ANSI_ESCAPE = Regex("""\u001B\[[0-?]*[ -/]*[@-~]|\u001B[@-Z\\-_]""")

internal fun stripAnsi(line: String): String = ANSI_ESCAPE.replace(line, "")

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

internal fun taskPathOf(line: String): String? = TASK_LINE.find(line)?.groupValues?.get(1)

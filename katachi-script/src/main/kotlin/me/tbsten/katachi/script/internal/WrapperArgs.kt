package me.tbsten.katachi.script.internal

/**
 * The mark `bin/katachi` puts in front of an argument `kotlin` would otherwise take for its own JVM.
 *
 * `kotlin` takes every `-D…` and `-J…`, wherever it sits on the command line, and reads every `@…` as an argfile, so
 * a `-D…` given to katachi would be lost and
 * an argument such as `@com.example` would fail. `bin/katachi` writes
 * this same string (keep the two in sync) in front of those and of any argument already starting with it.
 */
internal const val WRAPPER_ARG_ESCAPE: String = "katachi-escaped:"

/** Undoes what `bin/katachi` did to [args] so the command sees them as the user typed them. */
internal fun unescapeWrapperArgs(args: List<String>): List<String> = args.map { it.removePrefix(WRAPPER_ARG_ESCAPE) }

/**
 * What `bin/katachi.cmd` passes as the only argument, with the command line the user typed in [WINDOWS_COMMAND_LINE_ENV]
 * (keep the three in sync with that file).
 *
 * `bin/katachi` hands `kotlin` the arguments one by one, marked where `kotlin` would take them. In a .cmd file that is not
 * possible: an unquoted `--option=value` already arrives as two arguments, so the file gives `%*`, as typed, and this splits it.
 */
internal const val WINDOWS_COMMAND_LINE_ARG: String = "katachi-windows-command-line"

/** The environment variable `bin/katachi.cmd` puts the command line the user typed (`%*`) in. */
internal const val WINDOWS_COMMAND_LINE_ENV: String = "KATACHI_WINDOWS_COMMAND_LINE"

/**
 * The arguments the command sees. [args] as `kotlin` gave them, with the escape of `bin/katachi` undone; or, when `bin/katachi.cmd`
 * started katachi ([WINDOWS_COMMAND_LINE_ARG] alone), the command line it left in [env].
 */
internal fun argsOfWrapper(args: List<String>, env: Map<String, String>): List<String> =
    if (args == listOf(WINDOWS_COMMAND_LINE_ARG)) {
        splitWindowsCommandLine(env[WINDOWS_COMMAND_LINE_ENV].orEmpty())
    } else {
        unescapeWrapperArgs(args)
    }

/**
 * Splits [line] into arguments as a Windows program reads its own command line (`CommandLineToArgvW`): arguments are
 * separated by spaces and tabs outside double quotes; `"` groups and is dropped; a `\` is literal unless it comes before a `"`,
 * where each pair of them is one `\` and an odd one makes the `"` literal; `""` inside quotes is a literal `"`.
 */
internal fun splitWindowsCommandLine(line: String): List<String> {
    val args = mutableListOf<String>()
    val current = StringBuilder()
    var started = false
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '\\') {
            var backslashes = 0
            while (i < line.length && line[i] == '\\') {
                backslashes++
                i++
            }
            started = true
            if (i < line.length && line[i] == '"') {
                repeat(backslashes / 2) { current.append('\\') }
                if (backslashes % 2 == 1) {
                    current.append('"')
                    i++
                }
            } else {
                repeat(backslashes) { current.append('\\') }
            }
        } else {
            when {
                c == '"' && inQuotes && line.getOrNull(i + 1) == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    started = true
                }
                (c == ' ' || c == '\t') && !inQuotes -> if (started) {
                    args += current.toString()
                    current.clear()
                    started = false
                }
                c == '\r' || c == '\n' -> if (inQuotes) {
                    current.append(c)
                } else if (started) {
                    args += current.toString()
                    current.clear()
                    started = false
                }
                else -> {
                    current.append(c)
                    started = true
                }
            }
            i++
        }
    }
    if (started) args += current.toString()
    return args
}

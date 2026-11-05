package me.tbsten.katachi.script.internal

/** The subcommands this artifact answers itself; everything else goes to the kit script. */
private val KOTLIN_COMMANDS = setOf("data")

internal fun runScript(args: List<String>, context: ScriptContext, runner: ShellRunner = InheritingShellRunner): Int {
    val command = args.firstOrNull() ?: ""
    return try {
        when {
            command == "--version" || command == "version" -> {
                context.out("katachi ${ScriptVersion.current()}")
                0
            }

            command !in KOTLIN_COMMANDS || args.any { it == "-h" || it == "--help" } -> context.delegate(args, runner)

            else -> {
                context.resolveLang(command)
                when (command) {
                    "data" -> context.runData(args.drop(1))
                    else -> context.delegate(args, runner)
                }
            }
        }
    } catch (exit: KatachiScriptExitException) {
        val message = context.pick(exit.ja, exit.en)
        context.err(if (exit.plain) message else "\n[x] $message")
        exit.status
    }
}

/** The kit's `resolve_lang` for a subcommand after `init`: recorded in the working directory > `KATACHI_LANG` > ja. */
private fun ScriptContext.resolveLang(command: String) {
    if (command == "init") return
    val recorded = findWorkdir()?.let(::recordedLang)
    lang = recorded ?: Lang.of(env["KATACHI_LANG"]) ?: Lang.JA
}

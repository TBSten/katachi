package me.tbsten.katachi.script.internal

/** The subcommands that do not read --help themselves: a --help anywhere prints the usage, never "needs a value". */
private val HELP_ANYWHERE = setOf(
    "data", "check", "uncheck", "warn", "lint", "compare-violations", "verify", "summary", "guide", "test", "preferences", "answers",
    "init", "doctor", "scaffold", "docs",
)

internal fun runScript(args: List<String>, context: ScriptContext): Int {
    val command = args.firstOrNull() ?: ""
    val rest = args.drop(1)
    return try {
        context.resolveLang(command, rest)
        when {
            command == "--version" || command == "version" -> {
                context.out("katachi ${ScriptVersion.current()}")
                0
            }

            command == "" || command == "-h" || command == "--help" -> {
                context.out(context.usage())
                0
            }

            command.endsWith(KATACHI_SCRIPT_EXTENSION) -> context.runKatachiScriptFile(command, rest)

            // add reads --help itself: `add role --help` lists the fields of a role.
            command in HELP_ANYWHERE && rest.any { it == "-h" || it == "--help" } -> {
                context.out(context.usage())
                0
            }

            else -> context.dispatch(command, rest)
        }
    } catch (exit: KatachiScriptExitException) {
        val message = context.pick(exit.ja, exit.en)
        if (message.isNotEmpty()) context.err(if (exit.plain) message else "\n[x] $message")
        exit.status
    }
}

private fun ScriptContext.dispatch(command: String, args: List<String>): Int = when (command) {
    "doctor" -> runDoctor(args)
    "init" -> runInit(args)
    "scaffold" -> runScaffold(args)
    "data" -> runData(args)
    "docs" -> runDocs(args)
    "guide" -> runGuide(args)
    "test" -> runTest(args)
    "check" -> runCheck(args)
    "uncheck" -> runUncheck(args)
    "warn" -> runWarn(args)
    "add" -> runAdd(args)
    "preferences" -> runPreferences(args)
    "answers" -> runAnswers(args)
    "lint" -> runLint()
    "compare-violations" -> runCompareViolations(args)
    "verify" -> runVerify()
    "summary" -> runSummary()
    "internal" -> runInternal(args)
    else -> die("知らないサブコマンドです: $command（--help を見てください）", "Unknown subcommand: $command (see --help)")
}

/**
 * The kit's `resolve_lang`: init defaults to en; the others read what init recorded, then
 * `KATACHI_LANG`, then ja. `--lang` on init / doctor / guide wins (they set it while parsing, which
 * is too late for --help, so it is read here first).
 */
private fun ScriptContext.resolveLang(command: String, args: List<String>) {
    lang = if (command == "init") {
        Lang.of(env["KATACHI_LANG"]) ?: Lang.EN
    } else {
        findWorkdir()?.let(::recordedLang) ?: Lang.of(env["KATACHI_LANG"]) ?: Lang.JA
    }
    if (command in setOf("init", "doctor", "guide")) {
        val index = args.indexOf("--lang")
        Lang.of(args.getOrNull(index + 1).takeIf { index >= 0 })?.let { lang = it }
    }
}

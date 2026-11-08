package me.tbsten.katachi.script.internal

/** The subcommands that do not read --help themselves: a --help anywhere prints the usage, never "needs a value". */
private val HELP_ANYWHERE = setOf(
    "data", "check", "uncheck", "warn", "lint", "compare-violations", "verify", "summary", "guide", "test", "preferences", "answers",
    "init", "doctor", "scaffold", "docs",
)

/** `katachi agent <name>`: the steps of the install guide (KATACHI-290). */
private val AGENT_COMMANDS: Set<String> = COMMAND_SPECS.filter { it.agent }.map { it.name }.toSet()

private fun isHelp(arg: String?): Boolean = arg == "-h" || arg == "--help"

internal fun runScript(args: List<String>, context: ScriptContext): Int {
    val command = args.firstOrNull() ?: ""
    val rest = args.drop(1)
    return try {
        if (command == AGENT_GROUP) {
            context.resolveLang(rest.firstOrNull() ?: "", rest.drop(1))
            return context.runAgent(rest)
        }
        context.resolveLang(command, rest)
        when {
            command == "--version" || command == "version" -> {
                context.out("katachi ${ScriptVersion.current()}")
                0
            }

            command == "" || isHelp(command) -> {
                context.out(context.usage())
                0
            }

            command.endsWith(KATACHI_SCRIPT_EXTENSION) -> context.runKatachiScriptFile(command, rest)

            command in AGENT_COMMANDS -> die(
                "$command は '$AGENT_COMMAND $command' です（AI エージェントが手順書に従って使うコマンドは agent の下にあります）",
                "$command is '$AGENT_COMMAND $command' (the commands an AI agent runs for the install guide sit under agent)",
            )

            command in HELP_ANYWHERE && rest.any(::isHelp) -> {
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

/** `katachi agent ...`: only the [AGENT_COMMANDS]; `katachi agent` alone or with --help lists them. */
private fun ScriptContext.runAgent(args: List<String>): Int {
    val command = args.firstOrNull()
    val rest = args.drop(1)
    return when {
        command == null || isHelp(command) -> {
            out(agentUsage())
            0
        }

        command !in AGENT_COMMANDS -> die(
            "$AGENT_COMMAND のサブコマンドではありません: $command（$AGENT_COMMAND --help を見てください）",
            "Not a subcommand of $AGENT_COMMAND: $command (see $AGENT_COMMAND --help)",
        )

        // add reads --help itself: `agent add role --help` lists the fields of a role.
        command in HELP_ANYWHERE && rest.any(::isHelp) -> {
            out(agentUsage())
            0
        }

        else -> dispatch(command, rest)
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

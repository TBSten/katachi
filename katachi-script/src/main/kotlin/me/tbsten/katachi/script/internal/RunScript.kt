package me.tbsten.katachi.script.internal

/** The subcommands that do not read --help themselves: a --help anywhere prints the usage, never "needs a value". */
private val HELP_ANYWHERE = setOf("lint", "compare-violations", "test", "doctor", "scaffold")

/** `katachi agent <name>`: the commands the katachi skill's steps run (KATACHI-290 / 329). */
private val AGENT_COMMANDS: Set<String> = COMMAND_SPECS.filter { it.agent }.map { it.name }.toSet()

/**
 * The agent subcommands the katachi skill replaced (KATACHI-329). Older notes and docs still name them, with or
 * without `agent`, so they get a pointer to the skill instead of "unknown subcommand".
 */
private val REMOVED_FOR_THE_SKILL = setOf(
    "init", "guide", "docs", "data", "check", "uncheck", "warn", "verify", "add", "preferences", "answers", "summary",
)

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

            command in AGENT_COMMANDS -> die(Messages.RunScript.commandsAiAgentRuns(command))

            command in REMOVED_FOR_THE_SKILL -> die(Messages.RunScript.removedForTheSkill("$CLI_COMMAND $command"))

            command in HELP_ANYWHERE && rest.any(::isHelp) -> {
                context.out(context.usage())
                0
            }

            else -> context.dispatch(command, rest)
        }
    } catch (exit: KatachiScriptExitException) {
        val message = context.pick(exit.text)
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

        command in REMOVED_FOR_THE_SKILL -> die(Messages.RunScript.removedForTheSkill("$AGENT_COMMAND $command"))

        command !in AGENT_COMMANDS -> dieUsage(Messages.RunScript.notSubcommandSeeHelp(command))

        command in HELP_ANYWHERE && rest.any(::isHelp) -> {
            out(agentUsage())
            0
        }

        else -> dispatch(command, rest)
    }
}

private fun ScriptContext.dispatch(command: String, args: List<String>): Int = when (command) {
    "doctor" -> runDoctor(args)
    "scaffold" -> runScaffold(args)
    "test" -> runTest(args)
    "lint" -> runLint()
    "compare-violations" -> runCompareViolations(args)
    "internal" -> runInternal(args)
    else -> dieUsage(Messages.RunScript.unknownSubcommandSeeHelp(command))
}

/**
 * The language of the messages: `--lang` on doctor, then the language of the check list the katachi skill
 * placed in the working directory (its `<html lang>`), then `KATACHI_LANG`, then the locale (`LC_ALL` / `LC_MESSAGES` /
 * `LANG`), then ja. `--lang` is read here
 * first because parsing sets it too late for --help.
 */
private fun ScriptContext.resolveLang(command: String, args: List<String>) {
    // Not findWorkdir(): --help and doctor work even with two working directories, which the commands that use one stop at.
    // Two that agree on the language still decide it, so that the message saying which to pick is in that language.
    val recorded = when (val lookup = lookupWorkdir()) {
        is WorkdirLookup.Found -> recordedLang(lookup.path)
        is WorkdirLookup.Ambiguous -> lookup.candidates.map(::recordedLang).distinct().singleOrNull()
        WorkdirLookup.NotFound -> null
    }
    lang = recorded ?: Lang.fromEnv(env) ?: Lang.JA
    if (command == "doctor") {
        val index = args.indexOf("--lang")
        Lang.of(args.getOrNull(index + 1).takeIf { index >= 0 })?.let { lang = it }
    }
}

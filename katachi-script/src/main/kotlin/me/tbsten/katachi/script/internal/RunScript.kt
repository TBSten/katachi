package me.tbsten.katachi.script.internal

/** The subcommands that do not read --help themselves: a --help anywhere prints the usage, never "needs a value". */
private val HELP_ANYWHERE = setOf("lint", "doctor", "scaffold")

/**
 * The commands that checked the definition for an AI agent (`katachi agent`, `katachi agent test`,
 * `katachi agent compare-violations`), removed in KATACHI-429: the katachi skill runs `./gradlew <module>:test` itself.
 * Older notes and docs still name them, with or without `agent`, so they get a pointer instead of "unknown subcommand".
 */
private val REMOVED_CHECK_COMMANDS = setOf("test", "compare-violations")

/**
 * The agent subcommands the katachi skill replaced (KATACHI-329). Older notes and docs still name them, with or
 * without `agent`, so they get a pointer to the skill instead of "unknown subcommand".
 */
private val REMOVED_FOR_THE_SKILL = setOf(
    "init", "guide", "docs", "data", "check", "uncheck", "warn", "verify", "add", "preferences", "answers", "summary",
)

/** `katachi agent`: removed in KATACHI-429, still answered so that old notes and docs find out what to do. */
private const val AGENT_GROUP: String = "agent"
private const val AGENT_COMMAND: String = "$CLI_COMMAND $AGENT_GROUP"

private fun isHelp(arg: String?): Boolean = arg == "-h" || arg == "--help"

internal fun runScript(args: List<String>, context: ScriptContext): Int {
    val command = args.firstOrNull() ?: ""
    val rest = args.drop(1)
    return try {
        if (command == AGENT_GROUP) {
            context.resolveLang(rest.firstOrNull() ?: "", rest.drop(1))
            return context.removedAgent(rest)
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

            command in REMOVED_CHECK_COMMANDS -> die(Messages.RunScript.removedCheckCommand("$CLI_COMMAND $command"))

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

/** `katachi agent ...`: removed as a whole; says what to do instead, whatever follows `agent`. */
private fun ScriptContext.removedAgent(args: List<String>): Nothing {
    val sub = args.firstOrNull()?.takeUnless(::isHelp)
    val typed = if (sub == null) AGENT_COMMAND else "$AGENT_COMMAND $sub"
    die(
        if (sub in REMOVED_FOR_THE_SKILL) Messages.RunScript.removedForTheSkill(typed) else Messages.RunScript.removedCheckCommand(typed),
    )
}

private fun ScriptContext.dispatch(command: String, args: List<String>): Int = when (command) {
    "doctor" -> runDoctor(args)
    "scaffold" -> runScaffold(args)
    "lint" -> runLint(args)
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
        is WorkdirLookup.Misconfigured -> null
    }
    lang = recorded ?: Lang.fromEnv(env) ?: Lang.JA
    if (command == "doctor") {
        val index = args.indexOf("--lang")
        Lang.of(args.getOrNull(index + 1).takeIf { index >= 0 })?.let { lang = it }
    }
}

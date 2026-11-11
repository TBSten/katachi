package me.tbsten.katachi.script.internal

/** Commands git would rather have; without them some checks are skipped. The kit's `REC_ALL`. */
internal fun ScriptContext.warnMissingGit() {
    if (findOnPath("git") != null) return
    err(
        "\n[!] " + pick(Messages.Doctor.theseCommandsNotFound) + "\n" +
            "    - git: " + pick(Messages.Doctor.xcodeSelectInstallBrew) + "\n" +
            "      " + pick(Messages.Doctor.withoutGitCommandCannot),
    )
}

/** `doctor`: what this command needs, Gradle's JDK and the project's Kotlin. The kit's `cmd_doctor`. */
internal fun ScriptContext.runDoctor(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("doctor"), args)
    parsed.positionals.firstOrNull()?.let { dieUsage(Messages.Doctor.doctorUnknownOption(it)) }
    parsed.value("--lang")?.let(::setLangOption)
    warnMissingGit()
    val kotlin = detectKotlinVersion(cwd)
    val kotlinIsTooOld = kotlin != null && kotlinTooOld(kotlin)
    // The first line is the verdict: it must not say "everything is available" above a warning.
    if (kotlinIsTooOld) {
        say(Messages.Doctor.somethingNeedsAttention(ScriptVersion.current()))
    } else {
        say(Messages.Doctor.everythingNeedsAvailableKatachi(ScriptVersion.current()))
    }
    checkGradleJdk(cwd)
    if (kotlin != null) {
        note("Kotlin: $kotlin" + if (kotlinIsTooOld) pick(Messages.Doctor.kotlinTooOldSuffix) else "")
        if (kotlinIsTooOld) warn(kotlinTooOldMessage(cwd, kotlin, forScaffold = false))
    }
    (lookupWorkdir() as? WorkdirLookup.Ambiguous)?.let { warn(ambiguousWorkdirMessage(it)) }
    return 0
}

/** `--lang` of doctor. The kit's `set_lang_option`. */
internal fun ScriptContext.setLangOption(value: String) {
    lang = Lang.of(value) ?: dieUsage(Messages.Doctor.langMustEnJa(value))
}

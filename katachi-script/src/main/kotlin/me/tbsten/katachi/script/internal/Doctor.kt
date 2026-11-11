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
    parsed.positionals.firstOrNull()?.let { die(Messages.Doctor.doctorUnknownOption(it)) }
    parsed.value("--lang")?.let(::setLangOption)
    warnMissingGit()
    say(Messages.Doctor.everythingNeedsAvailableKatachi(ScriptVersion.current()))
    checkGradleJdk(cwd)
    val kotlin = detectKotlinVersion(cwd)
    if (kotlin != null) {
        if (kotlinTooOld(kotlin)) warn(kotlinTooOldMessage(cwd, kotlin, forScaffold = false)) else note("Kotlin: $kotlin")
    }
    (lookupWorkdir() as? WorkdirLookup.Ambiguous)?.let { warn(ambiguousWorkdirMessage(it)) }
    return 0
}

/** `--lang` of doctor. The kit's `set_lang_option`. */
internal fun ScriptContext.setLangOption(value: String) {
    lang = Lang.of(value) ?: die(Messages.Doctor.langMustEnJa(value))
}

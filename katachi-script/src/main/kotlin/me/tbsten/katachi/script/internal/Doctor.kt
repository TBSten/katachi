package me.tbsten.katachi.script.internal

/** Commands git would rather have; without them some checks are skipped. The kit's `REC_ALL`. */
private fun ScriptContext.warnMissingGit() {
    if (findOnPath("git") != null) return
    err(
        "\n[!] " + pick("次のコマンドが見つかりません。無くても進めますが、あったほうが確実です:", "These commands were not found. You can go on without them, but it is safer to have them:") + "\n" +
            "    - git: " + pick(
                "macOS: xcode-select --install か brew install git / Debian・Ubuntu: sudo apt-get install git / Fedora: sudo dnf install git / Alpine: apk add git",
                "macOS: xcode-select --install or brew install git / Debian, Ubuntu: sudo apt-get install git / Fedora: sudo dnf install git / Alpine: apk add git",
            ) + "\n" +
            "      " + pick("git が無いと Git 管理下かを判定できず、未追跡のファイルの確認も飛ばします。", "Without git, the command cannot tell whether the project is under Git, and skips the check for untracked files."),
    )
}

/** `doctor`: what this command needs, Gradle's JDK and the project's Kotlin. The kit's `cmd_doctor`. */
internal fun ScriptContext.runDoctor(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("doctor"), args)
    parsed.positionals.firstOrNull()?.let { die("doctor: 知らないオプションです: $it", "doctor: unknown option: $it") }
    parsed.value("--lang")?.let(::setLangOption)
    warnMissingGit()
    say(
        "必要なものはそろっています（katachi ${ScriptVersion.current()}）。",
        "Everything this needs is available (katachi ${ScriptVersion.current()}).",
    )
    checkGradleJdk(cwd)
    val kotlin = detectKotlinVersion(cwd)
    if (kotlin != null) {
        if (kotlinTooOld(kotlin)) warn(kotlinTooOldMessage(cwd, kotlin, forScaffold = false)) else note("Kotlin: $kotlin")
    }
    return 0
}

/** `--lang` of doctor. The kit's `set_lang_option`. */
internal fun ScriptContext.setLangOption(value: String) {
    lang = Lang.of(value) ?: die("--lang は en か ja です（指定されたのは '$value'）。", "--lang must be en or ja (got '$value').")
}

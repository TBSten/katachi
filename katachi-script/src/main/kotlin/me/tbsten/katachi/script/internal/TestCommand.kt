package me.tbsten.katachi.script.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

// `test` and `compare-violations`: run the test module and summarize its log, and compare the check
// results before and after a refactoring. The kit's `cmd_test` / `cmd_compare_violations`.

/**
 * The line `Katachi check failed:`, with or without the exception class Gradle prints in front of it
 * (`me.tbsten.katachi.check.KatachiArchitectureAssertionError: Katachi check failed: ...`).
 */
private val CHECK_FAILED_LINE = Regex("^\\s*([A-Za-z0-9_.$]*(Error|Exception): )?Katachi check failed:")

/**
 * The violation kinds katachi prints (the `label` of each Violation under katachi/.../check). When
 * katachi adds a kind, add it here (check-install-kit-smoke.sh matches them). Gradle prints lines
 * such as `[Incubating] Problems report ...` too, so only these count as headings.
 */
private const val KATACHI_VIOLATION_LABELS =
    "UnexpectedFile|UnexpectedDirectory|CaptureMismatch|MissingFile|UncheckedFile|UncheckedDirectory|UncheckedCheck|UnsatisfiedFileConstraint|UncheckedFileConstraint|AmbiguousLayout|MissingDescription|StaleBaselineEntry"

private val VIOLATION_HEADING_LINE = Regex("^\\s*\\[($KATACHI_VIOLATION_LABELS)] ")

private val RESULT_PREFIX = Regex("^\\s*([A-Za-z0-9_.$]*(Error|Exception): )?")

private val MISSING_DESCRIPTION = Regex("^\\s*\\[MissingDescription] ")

/** test always passes this, so that the log lists every violation and the diffs see past the tenth. */
private const val TEST_MAX_VIOLATIONS_ARG = "-Dkatachi.maxViolations=all"

private const val PRINT_CHECK_DIFF_MAX = 50

/**
 * The check result lines of a log, sorted: the summary line and the violation headings, the exception
 * prefix and `, N warnings` removed, [MissingDescription] left out (writing descriptions should
 * make it go down). The kit's `extract_check_result`.
 */
private fun extractCheckResult(log: Path): List<String> = log.readLinesLenientLog()
    .filter { CHECK_FAILED_LINE.containsMatchIn(it) || VIOLATION_HEADING_LINE.containsMatchIn(it) }
    .map { it.replaceFirst(RESULT_PREFIX, "").replace(Regex(", [0-9][0-9]* warnings*$"), "") }
    .filter { !it.startsWith("[MissingDescription] ") }
    .sorted()

private fun Path.readLinesLenientLog(): List<String> = if (isRegularFile()) String(Files.readAllBytes(this), Charsets.UTF_8).lines().let { if (it.lastOrNull() == "") it.dropLast(1) else it } else emptyList()

/** Whether the log got as far as the check (a compile error must not read as "0 violations"). */
private fun hasCheckResult(log: Path): Boolean = log.readLinesLenientLog().any { it.contains("Katachi check failed:") || it.contains("BUILD SUCCESSFUL") }

private fun countMissingDescription(log: Path): Int = log.readLinesLenientLog().count { MISSING_DESCRIPTION.containsMatchIn(it) }

/** The `Showing first N (M more...)` line when katachi printed only the first violations. */
private fun truncatedLineOf(log: Path): String? = log.readLinesLenientLog().firstOrNull { Regex("^\\s*Showing first ").containsMatchIn(it) }?.trimStart()

/**
 * `- only before` / `+ only after` of two sorted lists, grouped by change as `diff` prints them:
 * within a change, the removed lines first.
 */
private fun sortedDiff(before: List<String>, after: List<String>): List<String> {
    val out = mutableListOf<String>()
    val removed = mutableListOf<String>()
    val added = mutableListOf<String>()
    fun flush() {
        out += removed.map { "  - $it" }
        out += added.map { "  + $it" }
        removed.clear()
        added.clear()
    }
    var i = 0
    var j = 0
    while (i < before.size || j < after.size) {
        when {
            i < before.size && j < after.size && before[i] == after[j] -> {
                flush()
                i++
                j++
            }
            j >= after.size || (i < before.size && before[i] < after[j]) -> removed += before[i++]
            else -> added += after[j++]
        }
    }
    flush()
    return out
}

/** Prints the difference of two extracted results; returns false when they differ. The kit's `print_check_diff`. */
private fun ScriptContext.printCheckDiff(before: List<String>, after: List<String>, prefix: String = ""): Boolean {
    if (before == after) return true
    val diff = sortedDiff(before, after)
    val added = diff.count { it.startsWith("  + ") }
    val removed = diff.count { it.startsWith("  - ") }
    out(prefix + pick("増えた $added 件 / 減った $removed 件", "Added $added / removed $removed"))
    diff.take(PRINT_CHECK_DIFF_MAX).forEach(out)
    if (diff.size > PRINT_CHECK_DIFF_MAX) {
        say("  ほか ${diff.size - PRINT_CHECK_DIFF_MAX} 件（ログを見る）", "  and ${diff.size - PRINT_CHECK_DIFF_MAX} more (see the log)")
    }
    return false
}

/**
 * Where `test` keeps its logs: `tmp/` of the working directory when there is one, otherwise `build/katachi/`
 * of the project root (ignored by git in a Gradle project, so the logs never become an `[UnexpectedFile]`).
 */
internal fun ScriptContext.testLogDir(): Path = findWorkdir()?.resolve("tmp") ?: projectRoot().resolve("build/katachi")

/** `compare-violations [<before> [<after>]]`: 0 unchanged, 1 changed, 2 not comparable. */
internal fun ScriptContext.runCompareViolations(args: List<String>): Int {
    val module = resolveModuleDir()
    requireDefinitionDir(module)
    val before = args.getOrNull(0)?.let(::resolve) ?: testLogDir().resolve("test-before-refactor.log")
    val after = args.getOrNull(1)?.let(::resolve) ?: testLogDir().resolve("test-after-refactor.log")
    for (log in listOf(before, after)) {
        if (!log.isRegularFile()) {
            die(
                "${fileUri(log)} がありません。$AGENT_COMMAND test --save <名前> で取るか、./gradlew ${module.gradlePath}:test の出力をこのパスに保存してください（手順 3-2 / 3-3）。",
                "${fileUri(log)} not found. Capture it with $AGENT_COMMAND test --save <name>, or save the output of ./gradlew ${module.gradlePath}:test to this path (steps 3-2 / 3-3).",
            )
        }
        if (!hasCheckResult(log)) {
            die(
                "${fileUri(log)} に検査の結果がありません。コンパイルエラーなどで検査まで届いていません。直してから取り直してください。",
                "${fileUri(log)} has no check result. The build did not get as far as the check (a compile error, for example). Fix it and capture the log again.",
            )
        }
    }
    // A log cut to the first N violations cannot be compared: swaps past N go unseen, and a full log shows a false difference.
    val truncated = listOf(before, after).mapNotNull { log -> truncatedLineOf(log)?.let { "    ${fileUri(log)} : $it\n" } }.joinToString("")
    if (truncated.isNotEmpty()) {
        warn(
            "違反を先頭の数件だけ出したログがあるので、比べません。ログに出ていない違反が入れ替わっても気づけず、全件のログと比べると偽の差分が出るためです。\n" +
                "$truncated    打ち切られたほうを、全件で取り直してください。$AGENT_COMMAND test --save <名前> はいつも全件を出します（-Dkatachi.maxViolations=all を Gradle に渡す）。\n" +
                "    前のログ（before-refactor）を取り直すときは、定義をリファクタリング前に戻してから取ります。\n" +
                "    取り直しても打ち切られるなら、テストのコードの assert() に maxViolations が書いてあります（-D より優先される）。外してから取り直してください。",
            "Not comparing: a log shows only the first few violations. Violations missing from the log could swap without being noticed, and comparing with a full log shows a false difference.\n" +
                "$truncated    Capture the cut-off one again with every violation. $AGENT_COMMAND test --save <name> always prints them all (it passes -Dkatachi.maxViolations=all to Gradle).\n" +
                "    To capture the before log (before-refactor) again, put the definition back to how it was before the refactoring first.\n" +
                "    If it is still cut off, the test code passes maxViolations to assert() (that wins over -D). Remove it and capture again.",
        )
        exitWith(2)
    }
    val a = extractCheckResult(before)
    val b = extractCheckResult(after)
    say("前: ${fileUri(before)}", "Before: ${fileUri(before)}")
    say("後: ${fileUri(after)}", "After:  ${fileUri(after)}")
    say("違反の見出し: 前 ${a.count { it.startsWith("[") }} 件 / 後 ${b.count { it.startsWith("[") }} 件", "Violation headings: before ${a.count { it.startsWith("[") }} / after ${b.count { it.startsWith("[") }}")
    say(
        "[MissingDescription]（比べない）: 前 ${countMissingDescription(before)} 件 / 後 ${countMissingDescription(after)} 件",
        "[MissingDescription] (not compared): before ${countMissingDescription(before)} / after ${countMissingDescription(after)}",
    )
    if (a == b) {
        say("")
        say("検査結果は変わっていません。", "The check result has not changed.")
        return 0
    }
    say("")
    say("検査結果が変わっています（- が前だけ、+ が後だけにある行）。", "The check result has changed (- lines only in before, + lines only in after).")
    printCheckDiff(a, b)
    say("")
    say(
        "リファクタリングで振る舞いが変わっています。前のログを取り直すのではなく、定義のほうを直してください。\n" +
            "分けた定義ファイルが [UnexpectedFile] になっているなら、${module.dir}/ を受け持つ Role の layout { } が\n" +
            "定義のファイルの最大の深さ（<group>/<sub>/<Role>Role.kt など）まで受け入れていません。",
        "The refactoring changed the behavior. Fix the definition instead of capturing the before log again.\n" +
            "If the split definition files show up as [UnexpectedFile], the layout { } of the Role that owns\n" +
            "${module.dir}/ does not accept files as deep as the deepest definition file (e.g. <group>/<sub>/<Role>Role.kt).",
    )
    return 1
}

/**
 * `test [--save <name>] [-- <gradle args>]`: runs the test module, keeps the whole log and prints a
 * summary. 0 passed, 1 the check failed, 2 the check was not reached (the reason goes to stderr). The
 * environment, JAVA_HOME included, is passed on as it is.
 */
internal fun ScriptContext.runTest(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("test"), args)
    parsed.positionals.firstOrNull()?.let {
        die("test: 知らないオプションです: $it（Gradle に渡す引数は -- の後ろに）", "test: unknown option: $it (put arguments for Gradle after --)")
    }
    val save = parsed.value("--save")
    val gradleArgs = parsed.passthrough.toMutableList()
    if (gradleArgs.none { it.startsWith("-Dkatachi.maxViolations=") }) gradleArgs += TEST_MAX_VIOLATIONS_ARG

    val module = resolveModuleDir()
    val root = projectRoot()
    if (!root.isDirectory()) {
        warn(
            "プロジェクトルートがありません: ${fileUri(root)}（チェックリストの meta.projectRoot）。直すか、空にしてプロジェクトのルートで実行してください。",
            "The project root does not exist: ${fileUri(root)} (meta.projectRoot in the check list). Fix it, or empty it and run this in the root of the project.",
        )
        return 2
    }
    val gradlew = root.resolve("gradlew")
    if (!gradlew.isRegularFile()) {
        warn(
            "gradlew がありません: ${fileUri(gradlew)} 。Gradle wrapper の無いプロジェクトでは、./gradlew ${module.gradlePath}:test を自分で実行してログを ${fileUri(testLogDir())}/test-<名前>.log に保存してください。",
            "gradlew not found: ${fileUri(gradlew)} . Without the Gradle wrapper, run ./gradlew ${module.gradlePath}:test yourself and save the log as ${fileUri(testLogDir())}/test-<name>.log.",
        )
        return 2
    }
    val definition = definitionDir(module)
    if (!definition.isDirectory()) {
        val (ja, en) = definitionMissingMessage(module, definition)
        warn(ja, en)
        return 2
    }

    val dir = testLogDir().resolve("test")
    dir.createDirectories()
    val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").format(clock.instant().atOffset(ZoneOffset.UTC))
    var log = dir.resolve("$stamp.log")
    if (log.exists()) log = dir.resolve("$stamp.${ProcessHandle.current().pid()}.log")
    val latest = dir.resolve("latest.log")
    val previous = dir.resolve("previous.log")
    if (latest.isRegularFile()) Files.move(latest, previous, StandardCopyOption.REPLACE_EXISTING)

    val jdk = detectGradleJdk(root)
    when {
        jdk.major != null -> say("JDK: ${jdk.major}（${jdk.fromJa}）", "JDK: ${jdk.major} (${jdk.fromEn})")
        jdk.fromJa != null -> say("JDK: 不明（${jdk.fromJa}。版を読めませんでした）", "JDK: unknown (${jdk.fromEn}; could not read the version)")
        else -> say("JDK: 不明（JAVA_HOME も PATH の java もありません。Gradle 自身の設定に任せます）", "JDK: unknown (no JAVA_HOME and no java on PATH; leaving it to Gradle)")
    }
    say("ログ: ${fileUri(log)}", "Log: ${fileUri(log)}")

    val command = (if (gradlew.isExecutable()) listOf("./gradlew") else listOf("sh", "./gradlew")) + listOf("${module.gradlePath}:test", "--console=plain") + gradleArgs
    val status = runToLog(command, root, log)
    Files.copy(log, latest, StandardCopyOption.REPLACE_EXISTING)
    if (save != null) {
        val copy = testLogDir().resolve("test-$save.log")
        Files.copy(log, copy, StandardCopyOption.REPLACE_EXISTING)
        say("写し: ${fileUri(copy)}", "Copy: ${fileUri(copy)}")
    }

    val lines = log.readLinesLenientLog()
    if (!hasCheckResult(log)) {
        // No violation, but other tests of the module (a spec the user added) failed: 1, worded apart from a compile error.
        if (lines.any { it.contains("There were failing tests") }) {
            say(
                "結果: 検査の違反は出ていませんが、検査用モジュールのほかのテストが失敗しています（Gradle の終了コード $status）。ログの FAILED の行:",
                "Result: no violations, but other tests in the verification module failed (Gradle exit code $status). FAILED lines in the log:",
            )
            lines.filter { it.endsWith("FAILED") }.take(30).forEach(out)
            return 1
        }
        say(
            "結果: 検査まで届いていません（コンパイルエラーなど。Gradle の終了コード $status）。ログの末尾:",
            "Result: the check was not reached (a compile error, for example; Gradle exit code $status). Tail of the log:",
        )
        lines.takeLast(30).forEach(out)
        return 2
    }

    val result = lines.firstOrNull { CHECK_FAILED_LINE.containsMatchIn(it) }?.replaceFirst(RESULT_PREFIX, "")
    val rc = if (result != null) {
        say("結果: $result", "Result: $result")
        1
    } else {
        say("結果: 検査は通りました（違反 0 件）", "Result: the check passed (0 violations)")
        0
    }
    val missing = countMissingDescription(log)
    if (missing > 0) say("[MissingDescription]: $missing 件（説明を書けば減る）", "[MissingDescription]: $missing (goes down as descriptions are written)")
    // Warnings ([AmbiguousLayout] and the like) never fail the check and only reach the log, so they are counted here.
    if (rc == 0) {
        val warnings = lines.firstNotNullOfOrNull { Regex("^\\s*Katachi check found ([0-9]+) warnings?\\..*").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (warnings != null && warnings > 0) {
            say(
                "警告: $warnings 件（検査は失敗させない。内容を見て、直すか残すかを決める）",
                "Warnings: $warnings (they never fail the check; read them and decide whether to fix or keep each)",
            )
            lines.filter { VIOLATION_HEADING_LINE.containsMatchIn(it) && !MISSING_DESCRIPTION.containsMatchIn(it) }
                .take(PRINT_CHECK_DIFF_MAX).forEach { out("  " + it.trimStart()) }
        }
    }

    val omitted = truncatedLineOf(log)
    val previousOmitted = if (previous.isRegularFile()) truncatedLineOf(previous) else null
    if (previous.isRegularFile() && hasCheckResult(previous)) {
        if (omitted != null || previousOmitted != null) {
            say(
                "前回との差分（previous.log 比）: 比べません（違反を先頭の数件だけ出したログがあるため）",
                "Difference from the previous run (vs previous.log): not compared (a log shows only the first few violations)",
            )
        } else {
            // Headings only: the summary line changes whenever the count does, which would add one to the difference.
            val a = extractCheckResult(previous).filter { it.startsWith("[") }
            val b = extractCheckResult(log).filter { it.startsWith("[") }
            if (a == b) {
                say("前回との差分（previous.log 比）: 無し", "Difference from the previous run (vs previous.log): none")
            } else {
                printCheckDiff(a, b, pick("前回との差分（previous.log 比）: ", "Difference from the previous run (vs previous.log): "))
            }
        }
    }
    if (omitted != null) {
        say(
            "省略: $omitted。-Dkatachi.maxViolations に数を渡したか、テストの assert() に maxViolations が書いてあります（全件を出すには、どちらも外す）",
            "Omitted: $omitted. Either -Dkatachi.maxViolations was given a number, or the test passes maxViolations to assert() (remove both to see everything)",
        )
    }
    return rc
}

/** Runs [command] in [dir] with stdout and stderr in [log]; the exit code, 127 when it cannot start. */
private fun ScriptContext.runToLog(command: List<String>, dir: Path, log: Path): Int = try {
    val builder = ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile())
    builder.environment().clear()
    builder.environment().putAll(env)
    builder.start().waitFor()
} catch (e: java.io.IOException) {
    Files.writeString(log, e.message ?: e.toString())
    127
}

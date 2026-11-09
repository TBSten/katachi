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
    out(prefix + pick(Messages.Test.addedRemoved(added, removed)))
    diff.take(PRINT_CHECK_DIFF_MAX).forEach(out)
    if (diff.size > PRINT_CHECK_DIFF_MAX) {
        say(Messages.Test.moreSeeLog(diff.size - PRINT_CHECK_DIFF_MAX))
    }
    return false
}

/** `compare-violations [<before> [<after>]]`: 0 unchanged, 1 changed, 2 not comparable. */
internal fun ScriptContext.runCompareViolations(args: List<String>): Int {
    val workdir = requireWorkdir()
    val module = resolveModuleDir()
    requireDefinitionDir(module)
    val before = args.getOrNull(0)?.let(::resolve) ?: workdir.resolve("tmp/test-before-refactor.log")
    val after = args.getOrNull(1)?.let(::resolve) ?: workdir.resolve("tmp/test-after-refactor.log")
    for (log in listOf(before, after)) {
        if (!log.isRegularFile()) {
            die(Messages.Test.notFoundSaveOutput(fileUri(log), module.gradlePath))
        }
        if (!hasCheckResult(log)) {
            die(Messages.Test.hasNoCheckResult(fileUri(log)))
        }
    }
    // A log cut to the first N violations cannot be compared: swaps past N go unseen, and a full log shows a false difference.
    val truncated = listOf(before, after).mapNotNull { log -> truncatedLineOf(log)?.let { "    ${fileUri(log)} : $it\n" } }.joinToString("")
    if (truncated.isNotEmpty()) {
        warn(Messages.Test.notComparingLogShows(truncated))
        exitWith(2)
    }
    val a = extractCheckResult(before)
    val b = extractCheckResult(after)
    say(Messages.Test.before(fileUri(before)))
    say(Messages.Test.after(fileUri(after)))
    say(Messages.Test.violationHeadingsBeforeAfter(a.count { it.startsWith("[") }, b.count { it.startsWith("[") }))
    say(Messages.Test.missingdescriptionNotComparedBefore(countMissingDescription(before), countMissingDescription(after)))
    if (a == b) {
        say("")
        say(Messages.Test.checkResultHasNot)
        return 0
    }
    say("")
    say(Messages.Test.checkResultHasChanged)
    printCheckDiff(a, b)
    say("")
    say(Messages.Test.refactoringChangedBehaviorFix(module.dir))
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
        die(Messages.Test.unknownOptionPutArguments(it))
    }
    val save = parsed.value("--save")
    val gradleArgs = parsed.passthrough.toMutableList()
    if (gradleArgs.none { it.startsWith("-Dkatachi.maxViolations=") }) gradleArgs += TEST_MAX_VIOLATIONS_ARG

    val workdir = requireWorkdir()
    val module = resolveModuleDir()
    val recordedRoot = checklistMeta(workdir.resolve(CHECK_LIST_FILE), "projectRoot")
    if (recordedRoot.isEmpty() || !resolve(recordedRoot).isDirectory()) {
        warn(Messages.Test.cannotFindProjectRoot(recordedRoot))
        return 2
    }
    val root = resolve(recordedRoot)
    val gradlew = root.resolve("gradlew")
    if (!gradlew.isRegularFile()) {
        warn(Messages.Test.withoutGradleWrapperRun(fileUri(gradlew), module.gradlePath))
        return 2
    }
    val definition = definitionDir(module)
    if (!definition.isDirectory()) {
        warn(definitionMissingMessage(module, definition))
        return 2
    }

    val dir = workdir.resolve("tmp/test")
    dir.createDirectories()
    val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").format(clock.instant().atOffset(ZoneOffset.UTC))
    var log = dir.resolve("$stamp.log")
    if (log.exists()) log = dir.resolve("$stamp.${ProcessHandle.current().pid()}.log")
    val latest = dir.resolve("latest.log")
    val previous = dir.resolve("previous.log")
    if (latest.isRegularFile()) Files.move(latest, previous, StandardCopyOption.REPLACE_EXISTING)

    val jdk = detectGradleJdk(root)
    when {
        jdk.major != null && jdk.source != null -> say(Messages.Test.jdk(jdk.major, jdk.source))
        jdk.source != null -> say(Messages.Test.jdkUnknownCouldNot(jdk.source))
        else -> say(Messages.Test.jdkUnknownNoJava)
    }
    say(Messages.Test.log(fileUri(log)))

    val command = (if (gradlew.isExecutable()) listOf("./gradlew") else listOf("sh", "./gradlew")) + listOf("${module.gradlePath}:test", "--console=plain") + gradleArgs
    val status = runToLog(command, root, log)
    Files.copy(log, latest, StandardCopyOption.REPLACE_EXISTING)
    if (save != null) {
        val copy = workdir.resolve("tmp/test-$save.log")
        Files.copy(log, copy, StandardCopyOption.REPLACE_EXISTING)
        say(Messages.Test.copy(fileUri(copy)))
    }

    val lines = log.readLinesLenientLog()
    if (!hasCheckResult(log)) {
        // No violation, but other tests of the module (a spec the user added) failed: 1, worded apart from a compile error.
        if (lines.any { it.contains("There were failing tests") }) {
            say(Messages.Test.resultNoViolationsBut(status))
            lines.filter { it.endsWith("FAILED") }.take(30).forEach(out)
            return 1
        }
        say(Messages.Test.resultCheckNotReached(status))
        lines.takeLast(30).forEach(out)
        return 2
    }

    val result = lines.firstOrNull { CHECK_FAILED_LINE.containsMatchIn(it) }?.replaceFirst(RESULT_PREFIX, "")
    val rc = if (result != null) {
        say(Messages.Test.result(result))
        1
    } else {
        say(Messages.Test.resultCheckPassedViolations)
        0
    }
    val missing = countMissingDescription(log)
    if (missing > 0) say(Messages.Test.missingdescriptionGoesDownDescriptions(missing))
    // Warnings ([AmbiguousLayout] and the like) never fail the check and only reach the log, so they are counted here.
    if (rc == 0) {
        val warnings = lines.firstNotNullOfOrNull { Regex("^\\s*Katachi check found ([0-9]+) warnings?\\..*").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (warnings != null && warnings > 0) {
            say(Messages.Test.warningsTheyNeverFail(warnings))
            lines.filter { VIOLATION_HEADING_LINE.containsMatchIn(it) && !MISSING_DESCRIPTION.containsMatchIn(it) }
                .take(PRINT_CHECK_DIFF_MAX).forEach { out("  " + it.trimStart()) }
        }
    }

    val omitted = truncatedLineOf(log)
    val previousOmitted = if (previous.isRegularFile()) truncatedLineOf(previous) else null
    if (previous.isRegularFile() && hasCheckResult(previous)) {
        if (omitted != null || previousOmitted != null) {
            say(Messages.Test.differencePreviousRunVs)
        } else {
            // Headings only: the summary line changes whenever the count does, which would add one to the difference.
            val a = extractCheckResult(previous).filter { it.startsWith("[") }
            val b = extractCheckResult(log).filter { it.startsWith("[") }
            if (a == b) {
                say(Messages.Test.diffFromPreviousNone)
            } else {
                printCheckDiff(a, b, pick(Messages.Test.diffFromPreviousPrefix))
            }
        }
    }
    if (omitted != null) {
        say(Messages.Test.omittedEitherDkatachiMaxviolations(omitted))
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

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * `fileName` and `captureValue(...)` per file (plan P1, property 7), through `FileConstraintCheck()`:
 * one block over files of several names and features follows each file's own values, and a file
 * moved to another name or feature is judged by the rules the block makes for where it is now.
 *
 * Each file's report is also compared with the same block judged directly for that file's name and
 * captured value, so that the run through the role and the direct run cannot drift apart.
 */
class KtFileContentPerFilePropertySpec : FreeSpec({
    "ファイルごとの fileName と captureValue で評価した規則どおりに判定される。別の名前・feature に移したファイルは、移した先の規則で判定される" {
        checkTrials(
            property = "per file",
            trials = pbtTrials(DEFAULT_TRIALS),
            seed = PBT_SEED + 20,
            generate = { random -> random.perFileCase() },
        ) { case ->
            val conforming = case.files.map { Placed(at = it, writtenFor = it) }
            withClue("the files that follow their rules:\n${describe(conforming, case.rules)}") {
                runPerFile(conforming, case.rules).shouldBeEmpty()
            }
            val from = case.files[case.movedIndex]
            val moved = conforming.mapIndexed { index, placed ->
                if (index == case.movedIndex) Placed(at = case.movedTo, writtenFor = from) else placed
            }
            withClue("${from.path} moved to ${case.movedTo.path}:\n${describe(moved, case.rules)}") {
                val report = runPerFile(moved, case.rules).filter { it.startsWith(case.movedTo.path + ": ") }
                report.map { keyOf(it.substringAfter(": ")) }.sorted() shouldBe case.expectedKeysOfMoved().sorted()
            }
        }
    }
})

/** Trials unless `-Pkatachi.pbt.scale` says otherwise: each runs two checks over a few files. */
private const val DEFAULT_TRIALS: Int = 40

private val NAMES: List<String> = listOf("Home", "Settings", "Profile", "Search").flatMap { listOf("${it}ViewModel", "${it}Screen") }
private val FEATURES: List<String> = listOf("home", "settings", "profile", "search")

/** A file: the name `fileName` reads, the feature its path captures, and whether its name carries a platform (`.android`). */
private data class PerFileFile(val name: String, val feature: String, val platform: Boolean) {
    val path: String get() = "feature/$feature/$name" + (if (platform) ".android" else "") + ".kt"
}

/** A file put [at] a path, holding what the block asks of the file [writtenFor]. */
private data class Placed(val at: PerFileFile, val writtenFor: PerFileFile)

/** Which lines the block writes. */
private data class PerFileRules(val packageLine: Boolean, val imports: Boolean, val prohibitOthers: Boolean, val body: Boolean, val keys: Boolean)

/** Files that each follow the block for their own name and feature, and where one of them is moved. */
private data class PerFileCase(val rules: PerFileRules, val files: List<PerFileFile>, val movedIndex: Int, val movedTo: PerFileFile) {
    /** The keys of the report for the moved file, from what changed: its feature, its name, or both. */
    fun expectedKeysOfMoved(): List<String> {
        val from = files[movedIndex]
        return buildList {
            if (from.feature != movedTo.feature) {
                if (rules.packageLine) add("package com.example.feature.${from.feature}")
                if (rules.imports) add("import com.example.feature.${from.feature}.model.Item")
            }
            if (from.name != movedTo.name) {
                addAll(listOf(from.name, movedTo.name))
                if (rules.keys) addAll(listOf("${from.name}Keys", "${movedTo.name}Keys"))
            }
        }
    }
}

private fun Random.perFileCase(): PerFileCase {
    val rules = PerFileRules(packageLine = chance(60), imports = chance(60), prohibitOthers = chance(50), body = chance(50), keys = chance(40))
    val pairs = NAMES.flatMap { name -> FEATURES.map { name to it } }.shuffled(this)
    val count = nextInt(2, 5)
    val files = pairs.take(count).map { (name, feature) -> PerFileFile(name, feature, platform = chance(25)) }
    val target = pairs[count].let { (name, feature) -> PerFileFile(name, feature, platform = chance(25)) }
    val movedIndex = nextInt(count)
    val from = files[movedIndex]
    // Now and then only the name, or only the feature, changes.
    val movedTo = when (nextInt(3)) {
        0 -> target.copy(feature = from.feature)
        1 -> target.copy(name = from.name)
        else -> target
    }.takeIf { candidate -> files.none { it.name == candidate.name && it.feature == candidate.feature } } ?: target
    return PerFileCase(rules, files, movedIndex, movedTo)
}

/**
 * What the block asks of a file of [file]'s name and feature. Deny by default: a kind the block
 * writes no line for is left out, and a class line without `{ }` has no member.
 */
private fun contentOf(file: PerFileFile, rules: PerFileRules): String = buildString {
    if (rules.packageLine) {
        appendLine("package com.example.feature.${file.feature}")
        appendLine()
    }
    if (rules.imports) {
        appendLine("import kotlinx.coroutines.flow.StateFlow")
        appendLine("import com.example.feature.${file.feature}.model.Item")
        appendLine()
    }
    when {
        file.name.endsWith("Screen") -> appendLine("fun ${file.name}() {}")
        rules.body -> {
            appendLine("class ${file.name} {")
            appendLine("    val state: StateFlow<${file.name}State> = TODO()")
            appendLine("    private val cache = 1")
            appendLine("}")
        }
        else -> appendLine("class ${file.name}")
    }
    if (rules.keys) appendLine("object ${file.name}Keys")
}

private fun describe(files: List<Placed>, rules: PerFileRules): String =
    "$rules\n" + files.joinToString("\n") { "${it.at.path}:\n${contentOf(it.writtenFor, rules)}" }

/** The block, branching on the file it runs for. */
private fun KtFileContentScope.perFileRules(rules: PerFileRules) {
    val feature = captureValue("feature")
    if (rules.packageLine) `package`("com.example.feature.$feature")
    if (rules.imports) {
        `import`("kotlinx.coroutines.flow.*")
        `import`("com.example.feature.$feature.**")
        if (rules.prohibitOthers) not.`import`("com.example.feature.**").except("com.example.feature.$feature.**")
    }
    when {
        fileName.endsWith("Screen") -> required(`fun`, fileName)
        rules.body -> required(`class`, fileName) {
            required(`val`, "state").type("StateFlow<${fileName}State>")
            allow(private, anythingElse)
        }
        else -> required(`class`, fileName)
    }
    if (rules.keys) required(`object`, "${fileName}Keys")
}

/**
 * [files] checked through a role whose layout captures the feature, the report returned as
 * `<path>: <declaration> ...`. Each file's lines are first compared with the block judged directly
 * for that file's name and feature.
 */
private fun runPerFile(files: List<Placed>, rules: PerFileRules): List<String> {
    val sources = files.map { it.at to contentOf(it.writtenFor, rules) }
    val violations = ktFileContentRun(
        *sources.map { (file, content) -> file.path to content }.toTypedArray(),
        paths = { "feature" / capture("feature") / "*".ktFile() },
    ) {
        ktFileContent { perFileRules(rules) }
    }
    withClue("only [UnsatisfiedFileConstraint] is expected") { violations.filterNot { it is UnsatisfiedFileConstraint }.shouldBeEmpty() }
    val lines = violations.unsatisfiedLines()
    for ((file, content) in sources) {
        withClue("${file.path} judged directly for its fileName and captureValue(\"feature\") = ${file.feature}") {
            lines.filter { it.startsWith(file.path + ": ") }.map { it.substringAfter(": ") } shouldBe
                judged(content, path = file.path, captures = mapOf("feature" to file.feature)) { perFileRules(rules) }
        }
    }
    return lines
}

package me.tbsten.katachi.intellij.uitest.pbt.definition

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.testing.constraintModel
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel

/** A generated definition and the paths to ask it about. */
internal data class MatcherScenario(val model: DefinitionModel, val paths: List<String>) {
    override fun toString(): String = buildString {
        for (role in model.roles) {
            appendLine("${role.qualifiedName}:")
            for (entry in role.layout) appendLine("  [${entry.index}] ${entry.kind.jsonName} ${entry.pattern}")
            for (constraint in role.constraints) appendLine("  constraint files=${constraint.coverage.files} anyFile=${constraint.coverage.anyFileDirectories}")
        }
        append("paths=$paths")
    }
}

// `**` (zero levels or more) and the escaped `\*` (a level named `*`) are what real definitions use most, e.g. `src/main/kotlin/**/useCase/*UseCase.kt`.
private val dirLevels = listOf("a", "b", "c", "x", "*", "**", "\\*")
private val fileLevels = listOf("*.kt", "Foo.kt", "*Impl.kt", "README.md", "*")

/** Levels of a directory pattern: never two `**` in a row (katachi does not read `a/**/**` as matching `a`), and never `**` alone (it would ignore or declare everything). */
private fun levelsArb(range: IntRange): Arb<List<String>> = arbitrary {
    val levels = mutableListOf<String>()
    for (level in Arb.list(Arb.element(dirLevels), range).bind()) levels += if (level == "**" && levels.lastOrNull() == "**") "x" else level
    if (levels == listOf("**")) listOf("x") else levels
}

/** One generated entry: files are the most common, then directories and `anyFile()`, and now and then an `ignore()`. */
private fun entryArb(): Arb<LayoutEntryModel> = arbitrary {
    val kindRoll = Arb.int(0..9).bind()
    val directory = levelsArb(1..3).bind().joinToString("/")
    when (kindRoll) {
        in 0..4 -> {
            val above = if (Arb.boolean().bind()) levelsArb(1..3).bind() else emptyList()
            val file = Arb.element(fileLevels).bind()
            layoutEntry((above + file).joinToString("/"), LayoutEntryKind.File, synthetic = Arb.int(0..4).bind() == 0)
        }
        5, 6 -> layoutEntry(directory, LayoutEntryKind.AnyFile)
        7, 8 -> layoutEntry(directory, LayoutEntryKind.Directory, place = Arb.boolean().bind())
        else -> layoutEntry(directory, LayoutEntryKind.Ignore, synthetic = Arb.int(0..4).bind() == 0)
    }
}

private val coveragePool = listOf("a/*.kt", "*.kt", "a/b/*Impl.kt", "x/**", "**/README.md", "b/Foo.kt")
private val anyDirPool = listOf("a", "a/b", "*", "x/*", "c")

private fun roleArb(number: Int): Arb<RoleModel> = arbitrary {
    val entries = (0 until Arb.int(1..5).bind()).map { entryArb().bind() }
    val constraints = (0 until Arb.int(0..2).bind()).map { index ->
        constraintModel(
            name = "c$number.$index",
            files = if (Arb.boolean().bind()) listOf(Arb.element(coveragePool).bind()) else emptyList(),
            anyFileDirectories = if (Arb.boolean().bind()) listOf(Arb.element(anyDirPool).bind()) else emptyList(),
        )
    }
    roleModel("g.R$number", *entries.toTypedArray(), constraints = constraints)
}

// The tool directories katachi never goes into (`.idea`, `.gradle`) and a level literally named `*` (what `\\*` declares) now and then.
private val pathDirs = List(3) { listOf("a", "b", "c", "x", "y") }.flatten() + listOf(".idea", ".gradle", "*")
private val pathFiles = listOf("Foo.kt", "BarImpl.kt", "README.md", "Zed.txt")

internal val matcherPathArb: Arb<String> = arbitrary {
    val dirs = Arb.list(Arb.element(pathDirs), 0..4).bind()
    (dirs + Arb.element(pathFiles).bind()).joinToString("/")
}

internal val matcherScenarioArb: Arb<MatcherScenario> = arbitrary {
    val roles = (0 until Arb.int(1..4).bind()).map { roleArb(it).bind() }
    MatcherScenario(definitionModel(*roles.toTypedArray()), (0 until 12).map { matcherPathArb.bind() })
}

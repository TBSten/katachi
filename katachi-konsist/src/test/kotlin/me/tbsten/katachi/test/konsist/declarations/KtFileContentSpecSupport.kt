@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.AnythingElseRule
import me.tbsten.katachi.konsist.declarations.internal.DeclarationListRules
import me.tbsten.katachi.konsist.declarations.internal.DeclarationRule
import me.tbsten.katachi.konsist.declarations.internal.EvaluationInput
import me.tbsten.katachi.konsist.declarations.internal.FileRules
import me.tbsten.katachi.konsist.declarations.internal.ImportRule
import me.tbsten.katachi.konsist.declarations.internal.ModifierSet
import me.tbsten.katachi.konsist.declarations.internal.PackageRule
import me.tbsten.katachi.konsist.declarations.internal.PackageTarget
import me.tbsten.katachi.konsist.declarations.internal.PatternCache
import me.tbsten.katachi.konsist.declarations.internal.Shape
import me.tbsten.katachi.konsist.declarations.internal.evaluateRules
import me.tbsten.katachi.test.konsist.fixtureProject

/*
 * What the specs of `ktFileContent { }` share: running a block into its model the way one file's
 * check does, a plain-text dump of that model to compare against, and an end-to-end run over a
 * throwaway project.
 *
 * NOTE: このパッケージを `me.tbsten.katachi.konsist.…` にしてはいけない。宣言元の行
 * （DeclarationSite.capture()）は `me.tbsten.katachi.test.` のフレームしか利用者のものと見なさない。
 */

/** Where the specs say `ktFileContent { }` was called. The lines inside are captured for real. */
internal val SPEC_SITE: DeclarationSite = DeclarationSite("KtFileContentSpec.kt", 1)

/**
 * [block] run once into its model, as the check runs it for a file named [fileName] (up to its
 * first `.`) whose layout path captured [captures].
 */
internal fun rulesOf(
    fileName: String = "Sample",
    captures: Map<String, String> = emptyMap(),
    block: KtFileContentScope.() -> Unit,
): FileRules = evaluateRules(
    block = block,
    input = EvaluationInput(fileName, { name -> captures.getValue(name) }, PatternCache()),
    declaredAt = SPEC_SITE,
)

/** [block]'s model for [fileName], dumped. */
internal fun dumpOf(
    fileName: String = "Sample",
    captures: Map<String, String> = emptyMap(),
    block: KtFileContentScope.() -> Unit,
): String = rulesOf(fileName, captures, block).dump()

/**
 * The model as plain text, one line per rule and the members of a class indented under it, so
 * that a spec can state the whole model at once. Every field the matching reads is in it.
 *
 * ```text
 * package com.example.feature.**
 * import kotlin.** optional
 * required class{@HiltViewModel public} HomeViewModel supers=[ViewModel]
 *   members:
 *     required val{public} uiState type=StateFlow<HomeUiState>
 *     anythingElse {private}
 * ```
 */
internal fun FileRules.dump(): String = buildList {
    packages.forEach { add(it.dump()) }
    imports.forEach { add(it.dump()) }
    declarations?.let { addAll(it.dumpEntries(indent = "")) }
}.joinToString("\n")

private fun PackageRule.dump(): String {
    val target = when (val target = target) {
        is PackageTarget.Pattern -> target.rule.written
        PackageTarget.MatchingDirectory -> "(matching directory)"
    }
    return "${if (prohibited) "not " else ""}package $target${exceptOf(except.map { it.written })}"
}

private fun ImportRule.dump(): String {
    val status = if (prohibited) "prohibited" else "optional"
    return "import ${path.written} $status${exceptOf(except.map { it.written })}"
}

private fun DeclarationListRules.dumpEntries(indent: String): List<String> = entries.flatMap { entry ->
    when (entry) {
        is DeclarationRule -> entry.dump(indent)
        is AnythingElseRule -> listOf("${indent}anythingElse ${entry.alternatives.joinToString("|") { it.dump() }}")
    }
}

private fun DeclarationRule.dump(indent: String): List<String> {
    val status = when {
        prohibited -> "prohibited"
        required -> "required"
        else -> "optional"
    }
    val head = buildString {
        append(indent).append(status).append(' ')
        append(shapes.joinToString("|") { it.dump() }).append(' ').append(name.written)
        parameters?.let { types -> append(" params=(").append(types.joinToString(", ") { it.written }).append(')') }
        returnType?.let { append(" returns=").append(it.written) }
        propertyType?.let { append(" type=").append(it.written) }
        if (superclasses.isNotEmpty()) append(" supers=").append(superclasses.map { it.written })
        if (except.isNotEmpty()) append(" except=").append(except.map { it.written })
    }
    val members = members ?: return listOf(head)
    val memberLines = members.dumpEntries("$indent    ")
    return listOf(head, if (memberLines.isEmpty()) "$indent  members: (none)" else "$indent  members:") + memberLines
}

private fun Shape.dump(): String = "${kind.keyword}${modifiers.dump()}"

private fun ModifierSet.dump(): String =
    (annotations.map { "@${it.written}" } + modifiers.sortedBy { it.ordinal }.map { it.keyword })
        .joinToString(" ", "{", "}")

private fun exceptOf(except: List<String>): String = if (except.isEmpty()) "" else " except $except"

/** The first declaration line written directly in the file. */
internal val FileRules.firstDeclaration: DeclarationRule
    get() = declarations?.lines?.firstOrNull() ?: throw AssertionError("no declaration line was written: ${dump()}")

/**
 * One end-to-end run: a throwaway project holding [sources], a role named `Screen` whose layout
 * declares [paths] and whose [rules] write `ktFileContent { }` (more than one, or none, if they
 * like), checked with `FileConstraintCheck()`.
 */
internal fun ktFileContentRun(
    vararg sources: Pair<String, String>,
    paths: LayoutScope.() -> Unit,
    rules: RoleScope.() -> Unit,
): List<Violation> = fixtureProject(*sources) { root ->
    val projectArchitecture = architecture {
        files = wholeTree()
        "Screen".role {
            layout {
                "gradlew".file()
                paths()
            }
            rules()
        }
    }
    projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
}

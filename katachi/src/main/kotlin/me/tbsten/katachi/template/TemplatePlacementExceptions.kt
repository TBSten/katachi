package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * A template produced a file no pattern of its role's `layout { }` accepts.
 *
 * Writing it anyway would put a file where the role says none may live, so the very next
 * `assert()` would report it as `[UnexpectedFile]` -- a generator whose output fails the check
 * that generated it. The layout is the only thing that knows where a role's files go, so a name
 * it does not accept has nowhere to go.
 *
 * ## Example 1: catch a generated name the layout does not accept
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiNoTemplatePlacementException
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout { "useCase" / "*UseCase.kt".file() }
 *             template { file("GetUser.kt") { "// not a use case" } }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiNoTemplatePlacementException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCase")).getOrThrow()
 * }
 * thrown.fileName shouldBe "GetUser.kt"
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiNoTemplatePlacementException internal constructor(
    /** The role whose template produced it, qualified. */
    public val role: String,
    /** The generated file name, after the template's parameters were filled in. */
    public val fileName: String,
    /** Every file pattern that role's `layout { }` declares, sorted. */
    public val declaredPatterns: List<String>,
    /** Where the `file(...)` that produced the name was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template file "$fileName" declared at $declaredAt has no place in role "$role".""",
        )
        appendLine(
            "A template names files and that role's layout { } names directories, so a name no " +
                "pattern of the layout accepts would be written somewhere the role says nothing " +
                "may live -- and the next check would report it as an unexpected file.",
        )
        if (declaredPatterns.isEmpty()) {
            append(
                """That role declares no file pattern at all. Add one to its layout { }, such """ +
                    """as `"useCase" / "*UseCase.kt".file()`.""",
            )
        } else {
            appendLine("That role declares these file patterns:")
            for (pattern in declaredPatterns) appendLine("  $pattern")
            append(
                "Rename what file(...) produces so that one of them matches it, or declare the " +
                    "pattern the template needs.",
            )
        }
    },
)

/**
 * The only patterns accepting a generated file name sit below a wildcard directory.
 *
 * `":feature:*".module { }` declares where a *kind* of module keeps this role. Which modules exist
 * is a question only a walk of the project answers, so the flattened entry keeps the `*` standing
 * where a module name would be -- and katachi will not pick one of them to generate into.
 *
 * Naming the wildcard is what lets a run pick: `capture("feature")` for a directory level, or
 * `":feature:*".module(capture = "feature") { }` for a module, and `--arg feature=home` then says
 * which one. Only a wildcard left without a name ends here -- or a `**`, which no name can fill
 * because how many levels it stands for is not fixed. For that one, the directory the template
 * writes into is declared as a path of its own, without the `**`.
 *
 * ## Example 1: catch a role whose only place is a wildcard module
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.dsl.gradle.div
 * import me.tbsten.katachi.dsl.gradle.module
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiWildcardTemplatePlacementException
 *
 * val arch = architecture {
 *     "feature".group {
 *         "Screen" {
 *             layout { ":feature:*".module { "*Screen.kt".file() } }
 *             template { file("HomeScreen.kt") { "// a screen" } }
 *         }
 *     }
 * }
 * shouldThrow<KatachiWildcardTemplatePlacementException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "Screen")).getOrThrow()
 * }
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiWildcardTemplatePlacementException internal constructor(
    /** The role whose template produced it, qualified. */
    public val role: String,
    /** The generated file name, after the template's parameters were filled in. */
    public val fileName: String,
    /**
     * The patterns that accept the name but name no single directory, sorted, with the values the
     * run gave its captures already put in: what is left of a wildcard is what could not be filled.
     */
    public val patterns: List<String>,
    /** Where the `file(...)` that produced the name was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template file "$fileName" declared at $declaredAt has no single directory in """ +
                """role "$role".""",
        )
        appendLine("Every pattern of that role accepting this name still holds a wildcard in its directory:")
        for (pattern in patterns) appendLine("  $pattern")
        val directories = patterns.map { it.substringBeforeLast('/', missingDelimiterValue = "").split('/') }
        val singleStar = directories.firstNotNullOfOrNull { segments ->
            segments.indexOfFirst { '*' in it && it != "**" }.takeIf { it >= 0 }?.let { segments to it }
        }
        if (singleStar != null) {
            val (segments, index) = singleStar
            val suggested = segments.getOrNull(index - 1)
                ?.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_-]*")) }
                ?: "dir"
            appendLine(
                "A * there stands for the directories or modules the project happens to have, which is " +
                    "not something a declaration says -- so katachi cannot choose one of them to write " +
                    "into without inventing the answer.",
            )
            appendLine(
                "Name every * left in the directory: capture(\"$suggested\") for a directory level, or " +
                    ".module(capture = \"$suggested\") for a module key -- a name no parameter of the " +
                    "template uses -- and pass --arg $suggested=<$suggested> to choose it.",
            )
        }
        if (directories.any { "**" in it }) {
            appendLine(
                "A ** stands for any number of levels, so no one value can fill it and it cannot be " +
                    "named with capture().",
            )
            appendLine(
                "Declare the directory the template writes into as a path of its own, without the **, " +
                    "next to the one that has it: the check accepts both, and generation uses the one " +
                    "without.",
            )
        }
    }.trimEnd(),
)

/**
 * A generated file name fits two different directories of one role.
 *
 * A role may live in several places, and both of them accepting the name is a real thing to
 * declare. What it is not is an instruction: picking the first would make where a file lands
 * depend on the order two lines happen to be in.
 *
 * When the places differ at one level -- `scenario/a/` and `scenario/b/` -- that level is usually
 * a directory each run should choose, and the message says so: declared once as `capture("...")`,
 * it becomes one place that `--arg` fills in.
 *
 * ## Example 1: catch a name two places of one role accept
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiAmbiguousTemplatePlacementException
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout { "api" / "*UseCase.kt".file() }
 *             layout { "impl" / "*UseCase.kt".file() }
 *             template { file("GetUserUseCase.kt") { "// a use case" } }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiAmbiguousTemplatePlacementException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCase")).getOrThrow()
 * }
 * thrown.candidates.size shouldBe 2
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiAmbiguousTemplatePlacementException internal constructor(
    /** The role whose template produced it, qualified. */
    public val role: String,
    /** The generated file name, after the template's parameters were filled in. */
    public val fileName: String,
    /** Every path the file could have been written to, sorted. */
    public val candidates: List<String>,
    /** Where the `file(...)` that produced the name was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template file "$fileName" declared at $declaredAt fits """ +
                """${candidates.size} places of role "$role".""",
        )
        for (candidate in candidates) appendLine("  $candidate")
        appendLine(
            "Choosing one of them would make where this file lands depend on the order the " +
                "layout { } blocks happen to be written in, which is not something the " +
                "definition says.",
        )
        val level = differingLevelOf(candidates)
        if (level != null) {
            val (index, names) = level
            val above = candidates.first().split('/').take(index).joinToString("/")
            appendLine(
                "They differ only at level ${index + 1}${if (above.isEmpty()) "" else " (below $above/)"}: " +
                    "${names.joinToString(", ")}.",
            )
            appendLine(
                "If that level is a directory each run should choose, declare it once as " +
                    "capture(\"...\") instead of one path per directory, and pass the directory with --arg. " +
                    "A place reached through a capture the run gave a value is chosen over the others.",
            )
            append("Otherwise give the places file patterns that tell them apart, or split the role into one role per place.")
        } else {
            append(
                "Give the places file patterns that tell them apart, or split the role into one " +
                    "role per place.",
            )
        }
    },
)

/**
 * The one level at which every path of [paths] differs, with the names found there, or `null`
 * when they differ in depth or at more than one level.
 */
private fun differingLevelOf(paths: List<String>): Pair<Int, List<String>>? {
    val split = paths.map { it.split('/') }
    val depth = split.first().size
    if (split.any { it.size != depth }) return null
    val differing = (0 until depth).filter { index -> split.map { it[index] }.distinct().size > 1 }
    val index = differing.singleOrNull() ?: return null
    return index to split.map { it[index] }.distinct()
}

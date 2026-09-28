package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutModule
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.internal.captureDeclarationSite

/**
 * Declares a Gradle module, by its module path.
 *
 * This is sugar and nothing else. The two blocks below produce exactly the same
 * declarations, and a violation cannot tell which one was written:
 *
 * ```kt
 * ":core:domain:common".module {
 *   mainSourceSet / kotlin / "model" / "*".ktFile()
 * }
 *
 * "core/domain/common" {
 *   "build".ignore()
 *   "build.gradle".ktsFile()
 *   mainSourceSet / kotlin / "model" / "*".ktFile()
 * }
 * ```
 *
 * Only those two lines are added. `src`, `proguard-rules.pro` and the module's own
 * `.gitignore` are not: where sources live is what the role's own layout says, and the
 * other two are neither universal nor required.
 *
 * The module path may hold wildcards, and then the block is evaluated once per module
 * that matches, with [wildcards] holding what that match captured:
 *
 * ```kt
 * ":feature:*".module {
 *   mainSourceSet / kotlin / "${wildcards[0].pascalCase}Screen".ktFile()
 * }
 * ```
 *
 * A key with a wildcard stands for the modules that exist, so a key that matches none
 * declares nothing at all and is never reported as missing. A key without one stands for
 * the module it names whether or not it is there, so a module that was deleted shows up
 * as its missing build file rather than silently disappearing from the check.
 *
 * Where a module path lands is [me.tbsten.katachi.dsl.ModuleResolver]'s answer, which
 * by default replaces `:` with `/`.
 *
 * ## Example 1: Declare a module by its module path
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * ":app".module {
 *     mainSourceSet / kotlin / "com/example/sample" {
 *         "MainActivity".ktFile()
 *         "MainApplication".ktFile()
 *     }
 * }
 * ```
 *
 * ## Example 2: Expand over every module a wildcard matches
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 * import me.tbsten.katachi.dsl.pascalCase
 *
 * ":feature:*".module {
 *     "${wildcards[0].pascalCase}Screen".ktFile()
 * }
 * ```
 *
 * @throws me.tbsten.katachi.dsl.KatachiGlobSyntaxException when the module path cannot be
 *   read, `":core::data"` or a `**` written anywhere but last.
 * @throws me.tbsten.katachi.dsl.KatachiModuleOutsideLayoutRootException when written
 *   anywhere but directly inside `layout { }`.
 */
context(layoutScope: LayoutScope)
public fun String.module(block: LayoutDirectoryScope.() -> Unit): LayoutModule =
    layoutScope.expandModulePath(this, block)

/**
 * Declares a Gradle module whose `*`s have names, one per `*` in order.
 *
 * The check reads it exactly as the same key without names. The names are what a template fills
 * in: generating a file for the role takes the module from `--arg feature=...`. Inside the block,
 * [wildcard] reads a value by its name, and [wildcards] still reads them all by position.
 *
 * A `**` is not counted and cannot be named, because how many levels it stands for is not fixed:
 * `":feature:*:**".module("feature") { }` names the `*` and leaves the `**` as it is.
 *
 * This overload takes the one name of a key with a single `*`, so that it can be written as
 * `capture = "..."`; a key with more `*`s passes its names positionally to the other one.
 *
 * ## Example 1: Name the feature a module key stands for
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 * import me.tbsten.katachi.dsl.pascalCase
 *
 * ":feature:*".module(capture = "feature") {
 *     mainSourceSet / kotlin / "${wildcard("feature").pascalCase}Screen".ktFile()
 * }
 * ```
 *
 * @param capture the name of the module path's one `*`, following the same rule as a role name.
 * @throws me.tbsten.katachi.dsl.KatachiInvalidIdentifierException when the name is not an identifier.
 * @throws me.tbsten.katachi.dsl.KatachiCaptureCountMismatchException when the module path does not
 *   hold exactly one `*`.
 */
context(layoutScope: LayoutScope)
public fun String.module(capture: String, block: LayoutDirectoryScope.() -> Unit): LayoutModule =
    layoutScope.expandModulePath(this, listOf(capture), block)

/**
 * Declares a Gradle module whose `*`s have names: [captures] names them one by one, in order.
 *
 * The same as the single-name overload of `module` for a key with several `*`s. A `**` is not
 * counted and cannot be named.
 *
 * ## Example 1: Name two levels of a module key
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * ":feature:*:*".module("feature", "layer") {
 *     "${wildcard("layer")}.kt".ktFile()
 * }
 * ```
 *
 * @param captures one name per `*` of the module path, following the same rule as a role name.
 * @throws me.tbsten.katachi.dsl.KatachiInvalidIdentifierException when a name is not an identifier.
 * @throws me.tbsten.katachi.dsl.KatachiCaptureCountMismatchException when there is not exactly
 *   one name per `*`.
 * @throws me.tbsten.katachi.dsl.KatachiDuplicateCaptureException when a name is given twice.
 */
context(layoutScope: LayoutScope)
public fun String.module(vararg captures: String, block: LayoutDirectoryScope.() -> Unit): LayoutModule =
    layoutScope.expandModulePath(this, captures.toList(), block)

/**
 * `wildcards` was read, or `wildcard(name)` called, outside a `module { }` block.
 *
 * Both read what a module key's `*`s captured for the module being evaluated. Directly under
 * `layout { }`, or inside a plain directory block, there is no module key to have captured
 * anything -- a `capture("...")` level names a directory, and its value is read by the template
 * with `captureValue(...)`, not by the layout.
 *
 * ## Example 1: catch a `wildcards` read that has no module to read from
 * ```kt
 * shouldThrow<KatachiWildcardsOutsideModuleException> {
 *     layout { wildcards }
 * }
 * ```
 *
 * @property name the name `wildcard(name)` was called with, or `null` for a read of `wildcards`.
 * @property declaredAt where it was read.
 */
public class KatachiWildcardsOutsideModuleException internal constructor(
    public val name: String? = null,
    public val declaredAt: DeclarationSite? = null,
) : KatachiDeclarationException(
    message = buildString {
        val read = if (name == null) "`wildcards`" else "`wildcard(\"$name\")`"
        append(read)
        declaredAt?.let { append(" at ").append(it) }
        appendLine(" is read outside a `module { }` block.")
        appendLine(
            "It reads what a module key's `*` captured for the module being evaluated, and directly " +
                "under `layout { }`, or inside a plain directory block, there is no module key to have " +
                "captured anything.",
        )
        if (name == null) {
            append("Read it inside the block of a module key with a wildcard, such as `\":feature:*\".module { }`.")
        } else {
            append(
                "Call it inside `\":feature:*\".module(capture = \"$name\") { }`, the module key whose `*` " +
                    "is named \"$name\". For a directory named with capture(\"$name\"), the template reads " +
                    "the value with captureValue(\"$name\").",
            )
        }
    },
)

/**
 * What the module path's wildcards captured, for the module being evaluated.
 *
 * One element per `*`, and one per level for a trailing `**`, so `":feature:**"` against
 * `:feature:hoge:fuga` reads as `["hoge", "fuga"]` and against `:feature` itself as an
 * empty list — take the innermost name with `lastOrNull()`, not `last()`.
 *
 * ## Example 1: Build a file name from what a wildcard captured
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 * import me.tbsten.katachi.dsl.pascalCase
 *
 * ":feature:*".module {
 *     "${wildcards[0].pascalCase}Screen".ktFile()
 * }
 * ```
 *
 * @throws KatachiWildcardsOutsideModuleException when read outside a `module { }` block,
 *   where there is no module path to have captured anything.
 */
context(layoutScope: LayoutScope)
public val wildcards: List<String>
    get() = layoutScope.currentWildcards
        ?: throw KatachiWildcardsOutsideModuleException(declaredAt = captureDeclarationSite())

/**
 * What the module path's `*` named [name] captured, for the module being evaluated.
 *
 * The same value as the matching element of [wildcards]; the name only saves counting positions.
 * Names are given with `"...".module(capture = ...)`.
 *
 * ## Example 1: Build a file name from a named wildcard
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 * import me.tbsten.katachi.dsl.pascalCase
 *
 * ":feature:*".module(capture = "feature") {
 *     "${wildcard("feature").pascalCase}Screen".ktFile()
 * }
 * ```
 *
 * @throws KatachiWildcardsOutsideModuleException when called outside a `module { }` block.
 * @throws KatachiUnknownCaptureException when the module key gave no wildcard that name.
 */
context(layoutScope: LayoutScope)
public fun wildcard(name: String): String {
    if (layoutScope.currentWildcards == null) {
        throw KatachiWildcardsOutsideModuleException(name = name, declaredAt = captureDeclarationSite())
    }
    val captures = layoutScope.currentCaptures.orEmpty()
    return captures[name] ?: throw KatachiUnknownCaptureException(
        name = name,
        modulePath = layoutScope.currentModulePath.orEmpty(),
        knownNames = captures.keys.toList(),
        declaredAt = captureDeclarationSite(),
    )
}

/**
 * `wildcard(name)` asked for a name the module key did not give: the key named nothing, or named
 * its wildcards differently.
 *
 * ## Example 1: catch a named read inside a module key that named nothing
 * ```kt
 * shouldThrow<KatachiUnknownCaptureException> {
 *     architecture {
 *         "ui".group {
 *             "Screen" { layout { ":feature:*".module { wildcard("feature") } } }
 *         }
 *     }.flattenLayout()
 * }.knownNames shouldBe emptyList()
 * ```
 *
 * @property name the name that was asked for.
 * @property modulePath the module the block was being evaluated for.
 * @property knownNames the names the module key did give; empty when it named nothing.
 * @property declaredAt where `wildcard(name)` was called.
 */
public class KatachiUnknownCaptureException internal constructor(
    public val name: String,
    public val modulePath: String,
    public val knownNames: List<String>,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("`wildcard(\"$name\")` at $declaredAt has no wildcard of that name to read, in `$modulePath`.")
        if (knownNames.isEmpty()) {
            append(
                "The module key gave its wildcards no names. Name them with " +
                    "`.module(capture = \"$name\") { }`, one name per `*`, or read them by position " +
                    "with `wildcards[i]`.",
            )
        } else {
            append(
                "The names this module key gave are ${knownNames.joinToString { "\"$it\"" }}. " +
                    "Use one of them, or rename the capture in `.module(capture = ...)`.",
            )
        }
    },
)

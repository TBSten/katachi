@file:Suppress("DEPRECATION")

package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutModule
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.internal.MODULE_SUGAR_DEPRECATION
import me.tbsten.katachi.dsl.internal.MODULE_WILDCARDS_DEPRECATION
import me.tbsten.katachi.dsl.internal.captureDeclarationSite

/**
 * Declares a Gradle module, by its module path.
 *
 * **Deprecated** since 0.3, removed in 0.5. Write the module's directory instead —
 * `"core/data" { }` for `":core:data"` — name a level that varies with `capture("...")`, and
 * let [gradle] declare the build scripts this sugar added on its own.
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
 * ## Example 1: Write the module's directory instead
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.kotlin
 * import me.tbsten.katachi.dsl.gradle.mainSourceSet
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 * import me.tbsten.katachi.dsl.pascalCase
 *
 * // Before (0.2): ":feature:*".module { mainSourceSet / kotlin / "${wildcards[0].pascalCase}Screen".ktFile() }
 * "feature" / capture("feature") / mainSourceSet / kotlin /
 *     "${capture("feature").pascalCase}Screen".ktFile()
 * // The build scripts and build/ of every module: gradle(), next to the roles.
 * ```
 *
 * @throws me.tbsten.katachi.dsl.KatachiGlobSyntaxException when the module path cannot be
 *   read, `":core::data"` or a `**` written anywhere but last.
 * @throws me.tbsten.katachi.dsl.KatachiModuleOutsideLayoutRootException when written
 *   anywhere but directly inside `layout { }`.
 */
@Deprecated(message = MODULE_SUGAR_DEPRECATION, level = DeprecationLevel.WARNING)
context(layoutScope: LayoutScope)
public fun String.module(block: LayoutDirectoryScope.() -> Unit): LayoutModule =
    layoutScope.expandModulePath(this, block)

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
            append(
                "Name the level in the path with capture(\"...\") instead, and write the same capture " +
                    "where the value is used, such as \"\${capture(\"feature\").pascalCase}Screen\".ktFile(): " +
                    "one name in one path is one value.",
            )
        } else {
            append(
                "Name the level in the path with capture(\"$name\") instead, and write capture(\"$name\") " +
                    "or capture(\"$name\").pascalCase where the value is used: one name in one path is one " +
                    "value. The template reads it with captureValue(\"$name\").",
            )
        }
    },
)

/**
 * What the module path's wildcards captured, for the module being evaluated.
 *
 * **Deprecated** since 0.3, removed in 0.5, with [module]. Name the level with
 * `capture("...")` in the path and write the same capture, or a form derived from it such as
 * `capture("...").pascalCase`, where the value is used: one name in one path is one value.
 *
 * One element per `*`, and one per level for a trailing `**`, so `":feature:**"` against
 * `:feature:hoge:fuga` reads as `["hoge", "fuga"]` and against `:feature` itself as an
 * empty list — take the innermost name with `lastOrNull()`, not `last()`.
 *
 * ## Example 1: Build a file name from the directory's capture instead
 * ```kt
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 * import me.tbsten.katachi.dsl.pascalCase
 *
 * // Before (0.2): ":feature:*".module { "${wildcards[0].pascalCase}Screen".ktFile() }
 * "feature" / capture("feature") / "${capture("feature").pascalCase}Screen".ktFile()
 * ```
 *
 * @throws KatachiWildcardsOutsideModuleException when read outside a `module { }` block,
 *   where there is no module path to have captured anything.
 */
@Deprecated(message = MODULE_WILDCARDS_DEPRECATION, level = DeprecationLevel.WARNING)
context(layoutScope: LayoutScope)
public val wildcards: List<String>
    get() = layoutScope.currentWildcards
        ?: throw KatachiWildcardsOutsideModuleException(declaredAt = captureDeclarationSite())

/**
 * What the module path's `*` named [name] captured, for the module being evaluated.
 *
 * **Deprecated** since 0.3, removed in 0.5, with [module]. Name the level with
 * `capture("...")` in the path and write the same capture, or a form derived from it such as
 * `capture("...").pascalCase`, where the value is used: one name in one path is one value.
 *
 * The same value as the matching element of [wildcards]; the name only saves counting positions.
 * Names are given by embedding `capture(...)` tokens in the module key itself.
 *
 * ## Example 1: Build a file name from the directory's capture instead
 * ```kt
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 * import me.tbsten.katachi.dsl.pascalCase
 *
 * // Before (0.2): ":feature:${capture("feature")}".module { "${wildcard("feature").pascalCase}Screen".ktFile() }
 * "feature" / capture("feature") / "${capture("feature").pascalCase}Screen".ktFile()
 * ```
 *
 * @throws KatachiWildcardsOutsideModuleException when called outside a `module { }` block.
 * @throws KatachiUnknownCaptureException when the module key gave no wildcard that name.
 */
@Deprecated(message = MODULE_WILDCARDS_DEPRECATION, level = DeprecationLevel.WARNING)
@ExperimentalKatachiApi
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
 *             "Screen".role { layout { ":feature:*".module { wildcard("feature") } } }
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
@ExperimentalKatachiApi
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
                "The module key gave its wildcards no names. Name them by embedding " +
                    "`\${capture(\"$name\")}` in the module key itself, or read them by position " +
                    "with `wildcards[i]`.",
            )
        } else {
            append(
                "The names this module key gave are ${knownNames.joinToString { "\"$it\"" }}. " +
                    "Use one of them, or rename the `\${capture(\"...\")}` embedded in the module key.",
            )
        }
    },
)

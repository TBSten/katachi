// The Gradle vocabulary is the one utility layer that cannot be written through the public
// vocabulary alone: expanding a module path needs the project's module index.
@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiDeclarationException
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutModule
import me.tbsten.katachi.dsl.LayoutScope

/**
 * Declares a Gradle module, by its module path.
 *
 * This is sugar and nothing else. The two blocks below produce exactly the same
 * declarations, and a violation cannot tell which one was written:
 *
 * ```kotlin
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
 * ```kotlin
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
 * Where a module path lands is [me.tbsten.katachi.check.ModuleResolver]'s answer, which
 * by default replaces `:` with `/`.
 *
 * @throws me.tbsten.katachi.check.GlobSyntaxException when the module path cannot be
 *   read, `":core::data"` or a `**` written anywhere but last.
 * @throws KatachiDeclarationException when written anywhere but directly inside
 *   `layout { }`.
 */
context(layoutScope: LayoutScope)
public fun String.module(block: LayoutDirectoryScope.() -> Unit): LayoutModule =
    layoutScope.expandModulePath(this, block)

/**
 * What the module path's wildcards captured, for the module being evaluated.
 *
 * One element per `*`, and one per level for a trailing `**`, so `":feature:**"` against
 * `:feature:hoge:fuga` reads as `["hoge", "fuga"]` and against `:feature` itself as an
 * empty list — take the innermost name with `lastOrNull()`, not `last()`.
 *
 * @throws KatachiDeclarationException when read outside a `module { }` block, where
 *   there is no module path to have captured anything.
 */
context(layoutScope: LayoutScope)
public val wildcards: List<String>
    get() = layoutScope.currentWildcards ?: throw KatachiDeclarationException(
        "`wildcards` can only be read inside a `module { }` block. It holds what the " +
            "module path's `*` and `**` captured for the module being evaluated, and " +
            "directly under `layout { }`, or inside a plain directory block, there is no " +
            "module path to have captured anything.",
    )

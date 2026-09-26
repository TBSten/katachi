package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiInternalException

/**
 * A module pattern with no wildcard in it named no module.
 *
 * ## Example 1: report it instead of treating it as a bad definition
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiUnresolvableModulePatternException) {
 *     println("katachi bug, pattern was `${cause.pattern}`. Please report it.")
 * }
 * ```
 *
 * @property pattern the pattern that was being expanded.
 */
public class KatachiUnresolvableModulePatternException internal constructor(
    public val pattern: String,
) : KatachiInternalException(
    message = """
        The module pattern `$pattern` holds no wildcard, yet it names no single module.
        A pattern without a wildcard always names exactly one, so a layout cannot cause
        this. Please report it at https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)

/**
 * Turns a Gradle module path into the directory that holds it.
 *
 * This is the only thing that is replaceable, and it does one thing: module path in,
 * directory out. Nothing else about a module goes through here — a source set is the plain
 * directory `src/<name>`, and a package directory is derived by whatever strategy the user
 * assigned to their own `modulePackage`. Keeping those out is what lets v0.1 resolve modules
 * without reading anything from Gradle.
 *
 * The default, [Conventional], replaces `:` with `/`. That is right for every project that
 * has not customised `projectDir`, which in practice means every new project. A build that
 * has customised it replaces the resolver. A resolver replaced this way is asked about the
 * modules the layout names. It is not asked which modules exist: expanding `":feature:*"`
 * walks the tree looking for a `build.gradle.kts` or `build.gradle`, and that stays convention
 * based until a Gradle plugin can hand katachi the real list.
 *
 * ## Example 1: place a module at a non-conventional directory
 * ```kt
 * val projectArchitecture = architecture {
 *   moduleResolver = ModuleResolver { module ->
 *     if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
 *   }
 * }
 * ```
 */
public fun interface ModuleResolver {
    /**
     * Where [module] lives, written relative to the project root and `/` separated.
     *
     * The empty string means the project root itself, which is what the root project
     * resolves to.
     *
     * ## Example 1: check the default conversion
     * ```kt
     * ModuleResolver.Conventional.directoryOf(ModulePath.of(":core:data")) shouldBe "core/data"
     * ```
     */
    public fun directoryOf(module: ModulePath): String

    /**
     * Where the [ModuleResolver]s katachi ships with live.
     *
     * ## Example 1: name the default resolver explicitly
     * ```kt
     * val projectArchitecture = architecture {
     *     moduleResolver = ModuleResolver.Conventional
     *     "domain".group { "UseCase" { } }
     * }
     * ```
     */
    public companion object {
        /**
         * The default: `:` becomes `/`, so `:core:data` is `core/data` and `":"` is the
         * project root.
         *
         * ## Example 1: resolve the root project
         * ```kt
         * ModuleResolver.Conventional.directoryOf(ModulePath.ROOT) shouldBe ""
         * ```
         */
        public val Conventional: ModuleResolver = ConventionalModuleResolver
    }
}

private object ConventionalModuleResolver : ModuleResolver {
    override fun directoryOf(module: ModulePath): String = module.segments.joinToString("/")

    override fun toString(): String = "ModuleResolver.Conventional"
}

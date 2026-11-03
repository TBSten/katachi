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
 * The default, [Conventional], replaces `:` with `/`, which is right for every project that
 * has not customised `projectDir`. A build that has customised it replaces the resolver.
 * The resolver is not asked which modules exist: katachi finds modules by looking for a
 * `build.gradle.kts` or `build.gradle` in the tree, never by reading Gradle.
 *
 * Leaves the public API together with `.module { }` in katachi 0.5.
 *
 * ## Example 1: place a module at a non-conventional directory
 * ```kt
 * val projectArchitecture = architecture {
 *   moduleResolver = ModuleResolver { module ->
 *     if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
 *   }
 * }
 *
 * ModuleResolver.Conventional.directoryOf(ModulePath.of(":core:data")) shouldBe "core/data"
 * ModuleResolver.Conventional.directoryOf(ModulePath.ROOT) shouldBe "" // the project root
 * ```
 */
public fun interface ModuleResolver {
    /**
     * Where [module] lives, written relative to the project root and `/` separated.
     *
     * The empty string means the project root itself, which is what the root project
     * resolves to.
     */
    public fun directoryOf(module: ModulePath): String

    /**
     * Where the [ModuleResolver]s katachi ships with live.
     *
     * ## Example 1: name the default resolver explicitly
     * ```kt
     * val projectArchitecture = architecture {
     *     moduleResolver = ModuleResolver.Conventional
     *     "domain".group { "UseCase".role { } }
     * }
     * ```
     */
    public companion object {
        /**
         * The default: `:` becomes `/`, so `:core:data` is `core/data` and `":"` is the
         * project root.
         */
        public val Conventional: ModuleResolver = ConventionalModuleResolver
    }
}

private object ConventionalModuleResolver : ModuleResolver {
    override fun directoryOf(module: ModulePath): String = module.segments.joinToString("/")

    override fun toString(): String = "ModuleResolver.Conventional"
}

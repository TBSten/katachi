package me.tbsten.katachi.dsl.gradle.internal

/**
 * Why a [me.tbsten.katachi.dsl.gradle.ModulePackage] could not be turned into a directory.
 *
 * Internal bookkeeping: one subtype per thing that can go wrong, each holding what its own
 * sentence needs.
 *
 * ## Example 1: tell which problem was raised
 * ```kt
 * shouldThrow<KatachiModulePackageException> { capitalizedModuleNamePackage().resolveFor(null) }
 *     .problem shouldBe ModulePackageProblem.OutsideModule
 * ```
 */
internal sealed interface ModulePackageProblem {
    /** The sentence this problem contributes. */
    fun explain(): String

    /** Written where there is no module to derive a package from. */
    object OutsideModule : ModulePackageProblem {
        override fun explain(): String =
            "A module package can only be used inside a module block. It is derived from the " +
                "module being evaluated, and directly under `layout { }`, or inside a plain " +
                "directory block, there is no module to derive it from. Write the package " +
                "directory out in the path instead, naming a level that varies with a capture, " +
                "such as \"com/example/feature\" / capture(\"feature\").camelCase."
    }

    /** The strategy answered with something that is not a directory path. */
    class NotADirectory(
        val modulePath: String,
        val directory: String,
    ) : ModulePackageProblem {
        override fun explain(): String =
            "The module package of `$modulePath` came out as `$directory`, which is not a " +
                "directory path: it is empty, or one of its levels is. A module path that " +
                "names no level, such as `:` for the root project, only has a package when a " +
                "base package was given, as in `capitalizedModuleNamePackage(\"com.example\")`."
    }
}

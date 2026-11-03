package me.tbsten.katachi.dsl.gradle.internal

internal sealed interface ModulePackageProblem {
    fun explain(): String

    object OutsideModule : ModulePackageProblem {
        override fun explain(): String =
            "A module package can only be used inside a module block. It is derived from the " +
                "module being evaluated, and directly under `layout { }`, or inside a plain " +
                "directory block, there is no module to derive it from. Write the package " +
                "directory out in the path instead, naming a level that varies with a capture, " +
                "such as \"com/example/feature\" / capture(\"feature\").camelCase."
    }

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

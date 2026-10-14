package me.tbsten.katachi.dsl.internal

// The messages of the @Deprecated annotations on `.module { }` and the API that only works inside it.
// TODO(v0.4): raise every @Deprecated that reads one of these to DeprecationLevel.ERROR.
// TODO(v0.5): remove those declarations, then this file.

/** `@Deprecated` message of `String.module { }`. */
internal const val MODULE_SUGAR_DEPRECATION: String =
    ".module { } is removed in katachi 0.5: it adds a build.gradle.kts and an ignored build/ " +
        "that the layout does not show. Write the module's directory instead, \"core/data\" { } " +
        "for \":core:data\" and \"feature/\${capture(\"feature\")}\" { } for \":feature:*\", " +
        "and declare build scripts with gradle()."

/** `@Deprecated` message of `wildcards` and `wildcard(name)`. */
internal const val MODULE_WILDCARDS_DEPRECATION: String =
    "wildcards and wildcard(name) are removed with .module { } in katachi 0.5: they only read " +
        "what a module key captured. Name the level with capture(\"feature\") in the path instead " +
        "and write the same capture where the value is used, such as " +
        "\"\${capture(\"feature\").pascalCase}Screen\".ktFile(): one name in one path is one value."

/** `@Deprecated` message of `ModulePackage` and everything built on it. */
internal const val MODULE_PACKAGE_DEPRECATION: String =
    "ModulePackage is removed with .module { } in katachi 0.5: it only resolves inside a module " +
        "block. Write the package directory in the path instead, naming the module's level with a " +
        "capture: \"feature/\${capture(\"feature\")}\" / mainSourceSet / kotlin / " +
        "\"com/example/feature\" / capture(\"feature\").camelCase."

/** `@Deprecated` message of the scope's module accessors, `expandModulePath` and the like. */
internal const val MODULE_SCOPE_DEPRECATION: String =
    "Removed with .module { } in katachi 0.5: it exists so that a project can write a " +
        ".module { } of its own. Write the module's directory in the layout instead, " +
        "\"core/data\" { }, naming a level that varies with capture(\"...\")."

/** `@Deprecated` message of `ArchitectureScope.moduleResolver`. */
internal const val MODULE_RESOLVER_DEPRECATION: String =
    "moduleResolver is removed with .module { } in katachi 0.5: only module keys were resolved " +
        "through it. Write the directory the module lives in, such as \"apps/android\" { }, in " +
        "the layout instead."

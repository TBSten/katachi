package me.tbsten.katachi.dsl

/**
 * The directory a module's package lives in, as a strategy rather than as a path.
 *
 * `modulePackage` is not a built-in symbol: the user declares it once, next to their
 * `architecture { }`, and picks how it is derived.
 *
 * ```kotlin
 * val modulePackage = capitalizedModuleNamePackage("com.example")
 * ```
 *
 * The value cannot be a string, because one `val` stands for a different directory in every
 * module it is written in: in `":core:domain".module { }` it means `com/example/core/domain`,
 * and in `":feature:home".module { }` it means `com/example/feature/home`. So it holds the
 * derivation itself, and nothing is derived until a layout block is evaluated for one
 * concrete module.
 *
 * A project whose packages do not follow the module path writes its own strategy, which is
 * why this is a `fun interface`:
 *
 * ```kotlin
 * // :core:data and :feature:home both live directly under com.example
 * val modulePackage = ModulePackage { modulePath ->
 *     "com/example/" + modulePath.substringAfterLast(':')
 * }
 * ```
 *
 * @see capitalizedModuleNamePackage
 * @see moduleNamePackage
 */
public fun interface ModulePackage {
    /**
     * Returns the package directory of [modulePath], relative to that module's own directory.
     *
     * @param modulePath the Gradle module path of the module being evaluated, such as
     *   `":core:data"`. The leading `:` may be absent, and `":"` is the root project.
     * @return a `/` separated directory path, such as `com/example/core/data`. It may not be
     *   empty and may not hold an empty level.
     */
    public fun packageDirectoryOf(modulePath: String): String
}

/**
 * How a hyphen inside one segment of a module path is folded away.
 *
 * A package directory may hold a hyphen as far as the file system is concerned, but a Kotlin
 * package name may not, so `:core:data:remote-api` has to become something else.
 *
 * Only `-` is treated as a word separator. `_`, upper-case letters and digits are carried
 * over as written, because they are legal in a package name and the module author meant them.
 */
public enum class HyphenFolding {
    /**
     * `fuga-piyo` becomes `fugaPiyo`: the first word as written, every later word with its
     * first character upper-cased.
     */
    Capitalize,

    /**
     * `fuga-piyo` becomes `fugapiyo`: the words joined with nothing between them.
     *
     * No character changes case, so `Fuga-Piyo` becomes `FugaPiyo`. Module names are lower
     * case by convention, which is where this option's usual description comes from.
     */
    Concatenate,
}

/**
 * Derives the package directory from the module path, level for level.
 *
 * `:hoge:fuga-piyo` becomes `hoge/fugaPiyo`, and with [basePackage] `"com.example"` it
 * becomes `com/example/hoge/fugaPiyo`. Each `:` of the module path is one directory level, in
 * the order it was written; only a hyphen inside a level is rewritten, according to [hyphens].
 *
 * Nothing is checked against the Kotlin grammar. A level named `in`, `object` or `2fa` is
 * carried over as it is: what this returns is matched against directory names, and a
 * directory may be named anything.
 *
 * @param basePackage the package every module sits under, written either as `com.example` or
 *   as `com/example`. Empty means the module path is the whole package.
 * @param hyphens how a hyphen inside one level is folded away.
 * @see capitalizedModuleNamePackage
 */
public fun moduleNamePackage(
    basePackage: String = "",
    hyphens: HyphenFolding = HyphenFolding.Capitalize,
): ModulePackage {
    val base = splitBasePackage(basePackage)
    return ModulePackage { modulePath ->
        val levels = modulePath.split(':')
            .filter { it.isNotEmpty() }
            .map { it.foldHyphens(hyphens) }
        (base + levels).joinToString("/")
    }
}

/**
 * [moduleNamePackage] with its default folding: `:hoge:fuga-piyo` becomes `hoge/fugaPiyo`.
 *
 * This is the preset to reach for first.
 *
 * ```kotlin
 * val modulePackage = capitalizedModuleNamePackage("com.example")
 * // :core:domain        -> com/example/core/domain
 * // :feature:debug-menu -> com/example/feature/debugMenu
 * ```
 *
 * @param basePackage the package every module sits under, written either as `com.example` or
 *   as `com/example`.
 */
public fun capitalizedModuleNamePackage(basePackage: String = ""): ModulePackage =
    moduleNamePackage(basePackage = basePackage, hyphens = HyphenFolding.Capitalize)

/**
 * A [ModulePackage] could not be turned into a directory where it was written.
 *
 * Raised while a `layout { }` block is evaluated, which is later than the rest of
 * [KatachiDeclarationException] — layout blocks are deferred — but it says the same thing:
 * the definition is wrong, not the tree it describes.
 */
public class ModulePackageException internal constructor(
    message: String,
) : KatachiDeclarationException(message)

/**
 * Derives the package directory for the module a layout block is being evaluated for.
 *
 * @param modulePath the module being evaluated, or `null` when there is none, which is the
 *   case directly under `layout { }` and inside a plain directory block.
 * @throws ModulePackageException when [modulePath] is `null`, or when the strategy returned
 *   something that is not a directory path.
 */
@InternalKatachiApi
public fun ModulePackage.resolveFor(modulePath: String?): String {
    if (modulePath == null) {
        throw ModulePackageException(
            "A module package can only be used inside a module block. It is derived from the " +
                "module being evaluated, and directly under `layout { }`, or inside a plain " +
                "directory block, there is no module to derive it from. Either wrap the path " +
                "in `\":core:data\".module { }`, or write the package directory out as a " +
                "string.",
        )
    }
    val directory = packageDirectoryOf(modulePath)
    if (directory.isEmpty() || directory.split('/').any { it.isBlank() }) {
        throw ModulePackageException(
            "The module package of `$modulePath` came out as `$directory`, which is not a " +
                "directory path: it is empty, or one of its levels is. A module path that " +
                "names no level, such as `:` for the root project, only has a package when a " +
                "base package was given, as in `capitalizedModuleNamePackage(\"com.example\")`.",
        )
    }
    return directory
}

/** `com.example`, `com/example` and `com.example.` all split into `[com, example]`. */
private fun splitBasePackage(basePackage: String): List<String> =
    basePackage.split('.', '/').filter { it.isNotBlank() }

private fun String.foldHyphens(hyphens: HyphenFolding): String {
    val words = split('-').filter { it.isNotEmpty() }
    if (words.size <= 1) return words.firstOrNull() ?: ""
    return when (hyphens) {
        HyphenFolding.Capitalize ->
            words.first() +
                words.drop(1).joinToString("") { word ->
                    word.replaceFirstChar { it.uppercaseChar() }
                }

        HyphenFolding.Concatenate -> words.joinToString("")
    }
}

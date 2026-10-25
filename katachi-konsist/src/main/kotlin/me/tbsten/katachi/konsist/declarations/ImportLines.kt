package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.ImportLineBuilder
import me.tbsten.katachi.konsist.declarations.internal.PackageLineBuilder

/**
 * An import line just written, which the calls chained after it change.
 *
 * ## Example 1: what a view model may import
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *
 *     `import`("kotlin.**")
 *     `import`("kotlinx.coroutines.**")
 *     `import`("com.example.Logger")       // may be imported; an import line never asks for one
 *     not.`import`("android.**").except("android.os.Parcelable")
 *
 *     allow(anythingElse)
 * }
 * ```
 */
@ExperimentalKatachiApi
public class ImportLine internal constructor(
    internal val line: ImportLineBuilder,
) {
    /**
     * Lets the imports matching [patterns] through this prohibited line.
     *
     * ## Example 1: no other feature but this one
     * ```kt
     * not.`import`("com.example.feature.**").except("com.example.feature.${captureValue("feature")}.**")
     * ```
     *
     * @throws KatachiKtFileContentLineException on a line that does not start with `not.`, or when
     *   no pattern is given.
     * @throws KatachiKtFileContentPatternException when a pattern is not a path katachi reads.
     */
    public fun except(vararg patterns: String): ImportLine {
        line.addExcept(patterns.toList())
        return this
    }

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * A package line just written, which the calls chained after it change. A file has one package,
 * so a package line is neither required nor optional: the package has to match one allowed line.
 *
 * ## Example 1: the packages a feature may use
 * ```kt
 * ktFileContent {
 *     `package`("com.example.feature.${captureValue("feature")}.**")
 *     not.`package`("com.example.feature.*.internal.**").except("com.example.feature.*.internal.api")
 *
 *     `import`("**")
 *     allow(anythingElse)
 * }
 * ```
 */
@ExperimentalKatachiApi
public class PackageLine internal constructor(
    internal val line: PackageLineBuilder,
) {
    /**
     * Lets the packages matching [patterns] through this prohibited line.
     *
     * ## Example 1: no legacy package but one
     * ```kt
     * not.`package`("com.example.legacy.**").except("com.example.legacy.compat")
     * ```
     *
     * @throws KatachiKtFileContentLineException on a line that does not start with `not.`, or when
     *   no pattern is given.
     * @throws KatachiKtFileContentPatternException when a pattern is not a path katachi reads.
     */
    public fun except(vararg patterns: String): PackageLine {
        line.addExcept(patterns.toList())
        return this
    }

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * What `matchingDirectory` is: "the file's own directory", written where a package line's
 * pattern goes. The package then has to be the directories below the source set's `kotlin/` (or
 * `java/`), joined with `.`.
 *
 * ## Example 1: every file's package matches where it is
 * ```kt
 * ktFileContent {
 *     `package`(matchingDirectory)
 *     `import`("**")
 *     allow(anythingElse)
 * }
 * ```
 */
@ExperimentalKatachiApi
public class MatchingDirectory internal constructor() {
    /** `matchingDirectory`. */
    override fun toString(): String = "matchingDirectory"
}

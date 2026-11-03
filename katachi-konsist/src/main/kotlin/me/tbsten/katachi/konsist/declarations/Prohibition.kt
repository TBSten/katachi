package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.AllowWriter
import me.tbsten.katachi.konsist.declarations.internal.FileBuilder

/**
 * What `not.` starts: a prohibited line, `not.allow(...)`. A declaration it matches is a violation
 * even when an allowed line matches it too -- a prohibition is stronger than a permission -- and
 * `.except(...)` lets part of it through. Unlike an allowed line, it asks only for the modifiers
 * it writes: `` not.allow(`class`, "*Manager") `` prohibits an `internal class XManager` too, and a
 * `.optional()` modifier on it is refused as meaningless.
 *
 * It holds `allow(...)` only, written as on an allowed line, and in the file `` `import` `` and
 * `` `package` ``. `not.allow(..., anythingElse)` is refused: "anything else" names nothing in
 * particular to prohibit. There is no `not.required(...)`: a prohibited line asks for nothing.
 *
 * ## Example 1: any class but the `*Impl` ones, `LegacyImpl` aside
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *
 *     allow(`interface`, fileName) { allow(`fun`, "*") }
 *     allow(`class`, "*") { allow(anythingElse) }  // a not. line only takes from what a line allows
 *     not.allow(`class`, "*Impl").except("LegacyImpl")
 *     not.allow(private, `fun`, "*")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface Prohibition : DeclarationAllow

/**
 * `not.` directly in `ktFileContent { }`, where imports and packages can be prohibited too.
 *
 * ## Example 1: any import but the Android framework's
 * ```kt
 * ktFileContent {
 *     `package`("**")
 *     not.`package`("com.example.legacy.**")
 *     `import`("**")
 *     not.`import`("android.**").except("android.os.Parcelable")
 *     allow(anythingElse)
 * }
 * ```
 */
@ExperimentalKatachiApi
public class FileProhibition internal constructor(
    private val file: FileBuilder,
) : Prohibition {
    internal val writer: AllowWriter = AllowWriter(file.declarations, prohibited = true)

    /**
     * Prohibits the imports whose path matches [pattern], a `.` separated path where `*` is one level and `**` any number.
     *
     * `**` matches zero levels too: `"com.example.legacy.**"` prohibits `com.example.legacy`
     * itself as well, so a class import `com.example.Legacy` is prohibited by `"com.example.Legacy.**"`.
     *
     * @throws KatachiKtFileContentPatternException when [pattern] is not a path katachi reads.
     */
    public fun `import`(pattern: String): ImportLine = ImportLine(file.declareImport(pattern, prohibited = true))

    /**
     * Prohibits the packages matching [pattern], `.` separated. `**` is any number of levels, zero included: `"com.example.legacy.**"`
     * prohibits `com.example.legacy` itself as well.
     *
     * @throws KatachiKtFileContentPatternException when [pattern] is not a path katachi reads.
     */
    public fun `package`(pattern: String): PackageLine = PackageLine(file.declarePackage(pattern, prohibited = true))

    /** `not`. */
    override fun toString(): String = "not"
}

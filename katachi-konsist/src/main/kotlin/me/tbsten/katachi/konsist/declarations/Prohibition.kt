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
 * ## Example 1: everything but implementations named after the interface
 * ```kt
 * ktFileContent {
 *     allow(`interface`, fileName)
 *     allow(`class`, "Default$fileName")
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
 *     `import`("**")
 *     not.`import`("android.**").except("android.os.Parcelable")
 * }
 * ```
 */
@ExperimentalKatachiApi
public class FileProhibition internal constructor(
    private val file: FileBuilder,
) : Prohibition {
    internal val writer: AllowWriter = AllowWriter(file.declarations, prohibited = true)

    /**
     * Prohibits the imports whose path matches [pattern], `.` separated: `*` is one level, `**`
     * any number of them, zero included: `"com.example.legacy.**"` prohibits `com.example.legacy`
     * itself as well, so a class import `com.example.Legacy` is prohibited by `"com.example.Legacy.**"`.
     *
     * ## Example 1: no other feature
     * ```kt
     * not.`import`("com.example.feature.**").except("com.example.feature.${captureValue("feature")}.**")
     * ```
     *
     * @throws KatachiKtFileContentPatternException when [pattern] is not a path katachi reads.
     */
    public fun `import`(pattern: String): ImportLine = ImportLine(file.declareImport(pattern, prohibited = true))

    /**
     * Prohibits the packages matching [pattern], `.` separated. `**` is any number of levels, zero included: `"com.example.legacy.**"`
     * prohibits `com.example.legacy` itself as well.
     *
     * ## Example 1: nothing left in the legacy package
     * ```kt
     * not.`package`("com.example.legacy.**")
     * ```
     *
     * @throws KatachiKtFileContentPatternException when [pattern] is not a path katachi reads.
     */
    public fun `package`(pattern: String): PackageLine = PackageLine(file.declarePackage(pattern, prohibited = true))

    /** `not`. */
    override fun toString(): String = "not"
}

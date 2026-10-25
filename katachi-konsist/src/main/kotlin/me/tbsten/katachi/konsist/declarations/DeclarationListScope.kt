package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.KatachiDsl

/**
 * Where declaration lines are written: directly in `ktFileContent { }` ([KtFileContentScope]) and
 * inside the `{ }` of a class line ([ClassBodyScope]). The vocabulary is the same in both, so an
 * extension of this type works in either.
 *
 * Every line written here with `allow(...)` or `required(...)` is an allow list entry; a
 * `required(...)` line also has to be matched by a declaration. A declaration matched by no line
 * is a violation, so with no line written, every declaration is one (deny by default). Lines are
 * judged in this order: a prohibited line (`not.allow(...)`) wins, then an allowed or required
 * line, then `allow(..., anythingElse)`, and anything left is a violation.
 *
 * ## Example 1: a name of your own for a combination of kinds
 * ```kt
 * val DeclarationListScope.classOrInterface get() = `class` or `interface`
 *
 * ktFileContent {
 *     `package`("com.example.**")
 *     allow(classOrInterface, fileName) {
 *         allow(classOrInterface, "*")
 *     }
 * }
 * ```
 */
@KatachiDsl
@ExperimentalKatachiApi
public sealed interface DeclarationListScope : DeclarationVocabulary, DeclarationAllow, DeclarationRequired {
    /**
     * The name of the file being checked, up to its first `.`: `Platform.android.kt` is
     * `Platform`. A plain `String`: the block runs once per file, so `replace`, `removePrefix` and
     * the rest work, and an `if` can tell files apart.
     *
     * ## Example 1: the state type named after the view model's file
     * ```kt
     * allow(public, `val`, "uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
     * ```
     */
    public val fileName: String

    /**
     * What `capture(name)` in the role's layout took from the path of the file being checked.
     *
     * ## Example 1: the package named after the feature directory
     * ```kt
     * `package`("com.example.feature.${captureValue("feature")}")
     * ```
     *
     * @throws KatachiKtFileContentUnknownCaptureException when the role's layout captures no
     *   [name], found when the check starts.
     * @throws KatachiKtFileContentCaptureUnavailableException when the file's own layout path does
     *   not give [name] a value.
     */
    public fun captureValue(name: String): String

    /**
     * Starts a prohibited line, `not.allow(...)`: what it matches is a violation, even when an
     * allowed line matches it too.
     *
     * ## Example 1: no implementation class but the default one
     * ```kt
     * not.allow(`class`, "*Impl").except("DefaultImpl")
     * ```
     */
    public val not: Prohibition

    /**
     * "Any parameters", for a function line that reads better with it than without; the same as
     * writing no parameters.
     *
     * ## Example 1: functions starting with `on`, whatever their parameters
     * ```kt
     * allow(`fun`, "on*")(anyArgs())
     * ```
     */
    public fun anyArgs(): AnyArgs
}

/**
 * Receiver of `ktFileContent { }`: the file's package, its imports and the declarations directly
 * in it, written in the order a Kotlin file lists them.
 *
 * The package, the imports and the declarations are each an allow list of the lines written for
 * them, and a kind no line is written for allows nothing (deny by default). The `{ }` of a class
 * line is the allow list of its members; a class line without `{ }` allows no member.
 *
 * ## Example 1: the shape of a view model file
 * ```kt
 * ktFileContent {
 *     `package`("com.example.feature.**")
 *
 *     `import`("kotlin.**")
 *     `import`("androidx.**")
 *     `import`("dagger.hilt.**")
 *     `import`("javax.inject.**")
 *     not.`import`("android.**")
 *
 *     required(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *         required(public, `val`, "uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
 *         allow(public, `fun`, "on*")().returns("Unit")
 *         allow(private, anythingElse)
 *     }
 * }
 * ```
 */
@KatachiDsl
@ExperimentalKatachiApi
public sealed interface KtFileContentScope : DeclarationListScope {
    /**
     * Starts a prohibited line, which here can also be an import or a package.
     *
     * ## Example 1: every import but the Android framework's
     * ```kt
     * `import`("**")
     * not.`import`("android.**")
     * ```
     */
    override val not: FileProhibition

    /**
     * Allows the packages matching [pattern], `.` separated: `*` is one level, `**` any number of
     * levels, zero included: `"com.example.**"` matches `com.example` itself as well as `com.example.feature`.
     * A package no package line matches is a violation, and with no package line written, every
     * declared package is one. A file that declares no package is matched only by a pattern of `**`
     * levels alone (`"**"`), so it passes `` `package`("**") `` and no other pattern.
     *
     * ## Example 1: the packages of the feature the file is in
     * ```kt
     * `package`("com.example.feature.${captureValue("feature")}.**")
     * ```
     *
     * @throws KatachiKtFileContentPatternException when [pattern] is not a path katachi reads.
     */
    public fun `package`(pattern: String): PackageLine

    /**
     * Allows the package that matches the file's own directory: [matchingDirectory].
     *
     * ## Example 1: every package matches where its file is
     * ```kt
     * `package`(matchingDirectory)
     * ```
     */
    public fun `package`(directory: MatchingDirectory): PackageLine

    /**
     * The file's own directory below its source set's `kotlin/`, as a package.
     *
     * ## Example 1: every package matches where its file is
     * ```kt
     * `package`(matchingDirectory)
     * ```
     */
    public val matchingDirectory: MatchingDirectory

    /**
     * Allows the imports whose path matches [pattern], `.` separated: `*` is one level, `**` any
     * number of levels, zero included: `"com.example.App.**"` matches the class import
     * `com.example.App` itself as well as `com.example.App.Companion`. An import line only allows:
     * a file that does not import it is fine. A star import `import a.b.*` is matched as the path
     * `a.b.*`; an alias is not looked at.
     *
     * ## Example 1: imports of Kotlin and of the domain layer only
     * ```kt
     * `import`("kotlin.**")
     * `import`("com.example.domain.**")
     * ```
     *
     * @throws KatachiKtFileContentPatternException when [pattern] is not a path katachi reads.
     */
    public fun `import`(pattern: String): ImportLine
}

/**
 * Receiver of the `{ }` of a class, interface or object line: the members of that declaration,
 * with the same vocabulary as directly in the file. Members are functions, properties (those of a
 * primary constructor included) and nested classes, interfaces and objects (companion objects
 * included) -- not constructors, `init` blocks, enum entries or what is inherited.
 *
 * ## Example 1: what a use case interface and its implementation hold
 * ```kt
 * ktFileContent {
 *     `package`("com.example.domain")
 *     required(`interface`, fileName) {
 *         required(operator, `fun`, "invoke")
 *     }
 *     required(`class`, "Default$fileName").superclass(fileName) {
 *         required(override, `fun`, "invoke")
 *         allow(private, anythingElse)
 *     }
 * }
 * ```
 */
@KatachiDsl
@ExperimentalKatachiApi
public sealed interface ClassBodyScope : DeclarationListScope

package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Every `allow(...)` a declaration line is written with: modifiers and annotations, then kind, then name.
 *
 * That is the order Kotlin writes the declaration in. It is the same in the file,
 * inside the `{ }` of a class line, and after `not.`, where the line prohibits what it matches.
 *
 * - **The modifiers are the declaration's, as its Kotlin source writes them**, in any order: each
 *   one written has to be there, and one not written may not be, except `public` and `final`.
 *   One that may be there or not is written `suspend.optional()`. Annotations written have to be
 *   there; others are not asked about. Zero to ten of them, `annotated(...)` and `.optional()`
 *   ones included, each one a single word or several joined with `or`.
 * - **After `not.`, and with `anythingElse`, only what is written is asked for**: `` not.allow(`class`, "*Manager") ``
 *   also prohibits an `internal class XManager`.
 * - **The kind comes after the modifiers**, and only one kind: `` allow(`class`, public, "*") ``,
 *   two kinds, or no kind at all does not compile.
 * - **A combination no declaration can have is refused while the definition is read**
 *   (`` allow(data, `interface`, "*") ``, `` allow(public, internal, `class`, "*") ``), with
 *   [KatachiKtFileContentModifierException].
 *
 * ## Example 1: the lines of a view model file
 * ```kt
 * ktFileContent {
 *     `package`("com.example.feature.**")
 *     `import`("**")
 *
 *     allow(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *         allow(public, `val`, "uiState").type("StateFlow<*>")
 *         allow(public, `fun`, "on*")().returns("Unit")
 *         allow(private, anythingElse)
 *     }
 *     allow(const, `val`, "*")
 *     allow(`fun`, `interface`, "*Listener") { allow(`fun`, "on*") }
 *     not.allow(public, `class`, "*Impl").except("LegacyImpl")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface DeclarationAllow :
    TypeAllow,
    FunInterfaceAllow,
    FunctionAllow,
    PropertyAllow,
    TypeAliasAllow,
    AnythingElseAllow

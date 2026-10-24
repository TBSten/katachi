package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Every `allow(...)` a declaration line is written with: the modifiers and annotations, then the
 * kind, then the name -- the order Kotlin writes the declaration in. It is the same in the file,
 * inside the `{ }` of a class line, and after `not.`, where the line prohibits what it matches.
 *
 * - **A modifier written is a modifier required; one not written is not asked about**, in any
 *   order. Zero to ten of them, `annotated(...)` included, each one a single word or several joined
 *   with `or`.
 * - **The kind comes after the modifiers**, and only one kind: `` allow(`class`, public, "*") ``,
 *   two kinds, or no kind at all does not compile.
 * - **A combination no declaration can have is refused while the definition is read**
 *   (`` allow(data, `interface`, "*") ``, `` allow(public, internal, `class`, "*") ``), with
 *   [KatachiKtFileContentModifierException].
 *
 * ## Example 1: the lines of a view model file
 * ```kt
 * ktFileContent {
 *     allow(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *         allow(public, `val`, "uiState", "StateFlow<*>")
 *         allow(public, `fun`, "on*")().returns("Unit")
 *         allow(private, anythingElse)
 *     }
 *     allow(const, `val`, "*")
 *     allow(`fun`, `interface`, "*Listener")
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

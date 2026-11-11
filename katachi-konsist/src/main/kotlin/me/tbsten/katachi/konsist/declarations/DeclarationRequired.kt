package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Every `required(...)` a declaration line is written with, for a declaration that has to be there.
 *
 * It has the same overloads as `allow(...)`: modifiers and annotations, then the kind, then the
 * name. It is written directly in the file and inside the `{ }` of a class line; `not.` has none.
 *
 * - **`allow(...)` lets a declaration be; `required(...)` also asks for it.** A required line is an
 *   allow list entry too: what it matches need not be written again with `allow(...)`.
 * - **A name that is not a pattern asks for that name; a pattern (`*`, `{A,B}` and the like) asks
 *   for at least one declaration it matches**: `required(sealed, type, "*Action")` is at least
 *   one sealed `...Action`.
 * - **The lines written are the allow list, as with `allow(...)`**: a declaration no line matches
 *   is a violation, and with no line written in a list, every declaration of it is one.
 *
 * ## Example 1: a view model file with what it has to hold
 * ```kt
 * ktFileContent {
 *     `package`("com.example.feature.**")
 *     `import`("**")
 *
 *     required(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *         required(public, `val`, "uiState").type("StateFlow<*>")
 *         allow(public, `fun`, "on*")().returns("Unit")
 *         allow(private, anythingElse)
 *     }
 *     required(sealed, type, "*Action") { allow(data, `object`, "*") }
 *     allow(`object`, "*Preview")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface DeclarationRequired :
    TypeRequired,
    FunInterfaceRequired,
    FunctionRequired,
    PropertyRequired,
    TypeAliasRequired

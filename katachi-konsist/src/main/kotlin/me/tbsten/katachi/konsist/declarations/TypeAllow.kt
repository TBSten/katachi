package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.allowWriter

/**
 * The `allow(...)` lines for a class, an interface or an object: the modifiers and annotations
 * (none to ten of them), then the kind, then the name, as Kotlin writes the declaration. With a
 * `{ }`, the declaration's members are an allow list too.
 *
 * The kind is `` `class` ``, `` `interface` ``, `` `object` ``, `type`, or any of them joined with
 * `or`. Modifiers in front of a kind that cannot carry them leave that kind out: `allow(sealed, type, "*")`
 * is a sealed class or a sealed interface. A line only allows, with or without `*` in its name:
 * [TypeRequired]'s `required(...)` is the line for a declaration that has to be there.
 *
 * ## Example 1: a view model, sealed actions and a data class
 * ```kt
 * ktFileContent {
 *     allow(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *         allow(public, `val`, "uiState", "StateFlow<*>")
 *         allow(private, anythingElse)
 *     }
 *     allow(sealed, type, "*Action")
 *     allow(internal or private, data, `object`, "*")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface TypeAllow {
    /**
     * Writes the line for a class, interface or object named [name], asking for no modifier.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(emptyList(), kind, name, body)

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(listOf(modifier1), kind, name, body)

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(listOf(modifier1, modifier2), kind, name, body)

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(listOf(modifier1, modifier2, modifier3), kind, name, body)

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(listOf(modifier1, modifier2, modifier3, modifier4), kind, name, body)

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind, name, body)

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name, body,
    )

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name, body,
    )

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        modifier8: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name, body,
    )

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        modifier8: Modifiers,
        modifier9: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name, body,
    )

    /**
     * Writes the line for a class, interface or object named [name], with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        modifier8: Modifiers,
        modifier9: Modifiers,
        modifier10: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name, body,
    )
}

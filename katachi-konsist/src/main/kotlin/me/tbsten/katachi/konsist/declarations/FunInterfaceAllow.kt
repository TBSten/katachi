package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.allowWriter

/**
 * The `allow(...)` lines for a fun interface: `` `fun` `` and `` `interface` `` written one after
 * the other, as Kotlin writes `fun interface`, with the modifiers and annotations (none to ten of
 * them) in front.
 *
 * A [TypeKind] other than `` `interface` `` keeps only its interface: `` allow(`fun`, type, "*") ``
 * is a fun interface, and `` allow(`fun`, `class`, "*") `` is refused.
 *
 * ## Example 1: listeners declared as fun interfaces
 * ```kt
 * ktFileContent {
 *     allow(`fun`, `interface`, "*Listener")
 *     allow(public, `fun`, `interface`, fileName) { allow(`fun`, "on*") }
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface FunInterfaceAllow {
    /**
     * Writes the line for a fun interface named [name], carrying no modifier but `fun` (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(emptyList(), fn, kind, name, body)

    /**
     * Writes the line for a fun interface named [name], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(listOf(modifier1), fn, kind, name, body)

    /**
     * Writes the line for a fun interface named [name], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(listOf(modifier1, modifier2), fn, kind, name, body)

    /**
     * Writes the line for a fun interface named [name], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(listOf(modifier1, modifier2, modifier3), fn, kind, name, body)

    /**
     * Writes the line for a fun interface named [name], with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or for a [body] on a line
     *   after `not.`.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(listOf(modifier1, modifier2, modifier3, modifier4), fn, kind, name, body)

    /**
     * Writes the line for a fun interface named [name], with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
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
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5),
        fn, kind, name, body,
    )

    /**
     * Writes the line for a fun interface named [name], with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
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
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        fn, kind, name, body,
    )

    /**
     * Writes the line for a fun interface named [name], with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
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
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        fn, kind, name, body,
    )

    /**
     * Writes the line for a fun interface named [name], with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
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
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        fn, kind, name, body,
    )

    /**
     * Writes the line for a fun interface named [name], with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
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
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        fn, kind, name, body,
    )

    /**
     * Writes the line for a fun interface named [name], with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
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
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = allowWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        fn, kind, name, body,
    )
}

package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.requiredWriter

/**
 * The `required(...)` lines for a fun interface: `` `fun` `` and `` `interface` `` one after the
 * other, as on an `allow(...)` line, and the fun interface has to be there.
 *
 * ## Example 1: a listener named after the file
 * ```kt
 * ktFileContent {
 *     required(public, `fun`, `interface`, fileName) { required(`fun`, "on*") }
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface FunInterfaceRequired {
    /**
     * Writes the required line for a fun interface named [name], carrying no modifier but `fun` (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.funInterface(emptyList(), fn, kind, name, body)

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.funInterface(listOf(modifier1), fn, kind, name, body)

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.funInterface(listOf(modifier1, modifier2), fn, kind, name, body)

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.funInterface(listOf(modifier1, modifier2, modifier3), fn, kind, name, body)

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.funInterface(listOf(modifier1, modifier2, modifier3, modifier4), fn, kind, name, body)

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5),
        fn, kind, name, body,
    )

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
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
    ): TypeLine = requiredWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        fn, kind, name, body,
    )

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
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
    ): TypeLine = requiredWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        fn, kind, name, body,
    )

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
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
    ): TypeLine = requiredWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        fn, kind, name, body,
    )

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
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
    ): TypeLine = requiredWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        fn, kind, name, body,
    )

    /**
     * Writes the required line for a fun interface named [name], with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when [kind] holds no interface, or no declaration can have
     *   the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
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
    ): TypeLine = requiredWriter.funInterface(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        fn, kind, name, body,
    )
}

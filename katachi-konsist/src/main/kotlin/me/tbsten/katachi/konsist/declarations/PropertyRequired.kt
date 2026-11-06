package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.requiredWriter

/**
 * The `required(...)` lines for a property that has to be there, written as `allow(...)` is.
 *
 * A name that is not a pattern asks for that property; a pattern asks for at least one matching
 * property. For an extension property, the receiver's type may be written between the kind and
 * the name, and the type is then written too (`"*"` for any).
 *
 * ## Example 1: the state a view model exposes
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     `import`("kotlinx.coroutines.flow.**")
 *
 *     allow(`class`, fileName) {
 *         required(public, `val`, "uiState", "StateFlow<*>")
 *         allow(public, `val`, "is*", "Boolean")
 *     }
 * }
 * ```
 *
 * ## Example 2: an extension property of the type the file is named after
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *
 *     required(public, `val`, fileName, "*", "*")   // public val Xxx.yyy: ...
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface PropertyRequired {
    /**
     * Writes the required line for a property named [name], of [type] when given, carrying no modifier (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(emptyList(), kind, name, type)

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(listOf(modifier1), kind, name, type)

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(listOf(modifier1, modifier2), kind, name, type)

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(listOf(modifier1, modifier2, modifier3), kind, name, type)

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(listOf(modifier1, modifier2, modifier3, modifier4), kind, name, type)

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5),
        kind, name, type,
    )

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name, type,
    )

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name, type,
    )

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name, type,
    )

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name, type,
    )

    /**
     * Writes the required line for a property named [name], of [type] when given, with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name, type,
    )

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], carrying no modifier (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(emptyList(), kind, name, type, receiver)

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(listOf(modifier1), kind, name, type, receiver)

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(listOf(modifier1, modifier2), kind, name, type, receiver)

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(listOf(modifier1, modifier2, modifier3), kind, name, type, receiver)

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(listOf(modifier1, modifier2, modifier3, modifier4), kind, name, type, receiver)

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5),
        kind, name, type, receiver,
    )

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name, type, receiver,
    )

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
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
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name, type, receiver,
    )

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
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
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name, type, receiver,
    )

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
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
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name, type, receiver,
    )

    /**
     * Writes the required line for an extension property named [name] of [type] on a receiver matching [receiver], with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver], [name] or [type] is not a pattern katachi reads.
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
        kind: PropertyKind,
        receiver: String,
        name: String,
        type: String,
    ): PropertyLine = requiredWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name, type, receiver,
    )
}

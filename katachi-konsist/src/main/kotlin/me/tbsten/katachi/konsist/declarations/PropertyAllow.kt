package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.allowWriter

/**
 * The `allow(...)` lines for a property: modifiers and annotations, `` `val` `` or `` `var` ``, then the name.
 *
 * Up to ten modifiers and annotations can be written, and `` `val` or `var` `` allows both. For an
 * extension property, the name is the part after the receiver, and the receiver's type may be
 * written between the kind and the name, as for a function; a line without it asks nothing about
 * the receiver. The type is chained after it with [PropertyLine.type]; a line without it asks
 * nothing about the type.
 *
 * ## Example 1: a state holder typed after the file, and constants
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     `import`("kotlinx.coroutines.flow.**")
 *
 *     allow(`class`, fileName) {
 *         allow(public, `val`, "uiState").type("StateFlow<${fileName.replace("ViewModel", "UiState")}>")
 *     }
 *     allow(const, `val` or `var`, "*")
 * }
 * ```
 *
 * ## Example 2: extension properties, by their receiver
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *
 *     allow(public, `val`, "User", "is*").type("Boolean")   // public val User.isAdmin: Boolean
 *     allow(`val`, "Context", "*")                          // any extension property of Context
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface PropertyAllow {
    /**
     * Writes the line for a property named [name], carrying no modifier (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(emptyList(), kind, name)

    /**
     * Writes the line for a property named [name], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(listOf(modifier1), kind, name)

    /**
     * Writes the line for a property named [name], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2), kind, name)

    /**
     * Writes the line for a property named [name], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2, modifier3), kind, name)

    /**
     * Writes the line for a property named [name], with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2, modifier3, modifier4), kind, name)

    /**
     * Writes the line for a property named [name], with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5),
        kind, name,
    )

    /**
     * Writes the line for a property named [name], with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name,
    )

    /**
     * Writes the line for a property named [name], with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name,
    )

    /**
     * Writes the line for a property named [name], with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
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
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name,
    )

    /**
     * Writes the line for a property named [name], with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
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
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name,
    )

    /**
     * Writes the line for a property named [name], with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
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
        kind: PropertyKind,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name,
    )

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], carrying no modifier (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(emptyList(), kind, name, receiver)

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(listOf(modifier1), kind, name, receiver)

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2), kind, name, receiver)

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2, modifier3), kind, name, receiver)

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2, modifier3, modifier4), kind, name, receiver)

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5),
        kind, name, receiver,
    )

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name, receiver,
    )

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
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
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name, receiver,
    )

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
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
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name, receiver,
    )

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
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
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name, receiver,
    )

    /**
     * Writes the line for an extension property named [name] on a receiver matching [receiver], with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [receiver] or [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
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
        kind: PropertyKind,
        receiver: String,
        name: String,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name, receiver,
    )
}

package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.allowWriter

/**
 * The `allow(...)` lines for a property: the modifiers and annotations (none to ten of them), then
 * `` `val` ``, `` `var` `` or both joined with `or`, then the name and, optionally, the type
 * written in the source (package names and spaces aside; an inferred type matches no type).
 *
 * ## Example 1: a state holder typed after the file, and constants
 * ```kt
 * ktFileContent {
 *     allow(`class`, fileName) {
 *         allow(public, `val`, "uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
 *     }
 *     allow(const, `val` or `var`, "*")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface PropertyAllow {
    /**
     * Writes the line for a property named [name], of [type] when given, carrying no modifier (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = allowWriter.property(emptyList(), kind, name, type)

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = allowWriter.property(listOf(modifier1), kind, name, type)

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2), kind, name, type)

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2, modifier3), kind, name, type)

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: PropertyKind,
        name: String,
        type: String? = null,
    ): PropertyLine = allowWriter.property(listOf(modifier1, modifier2, modifier3, modifier4), kind, name, type)

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        type: String? = null,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5),
        kind, name, type,
    )

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        type: String? = null,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name, type,
    )

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        type: String? = null,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name, type,
    )

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        type: String? = null,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name, type,
    )

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        type: String? = null,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name, type,
    )

    /**
     * Writes the line for a property named [name], of [type] when given, with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
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
        type: String? = null,
    ): PropertyLine = allowWriter.property(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name, type,
    )
}

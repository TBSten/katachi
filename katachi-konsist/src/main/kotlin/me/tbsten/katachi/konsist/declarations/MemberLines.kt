package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.DeclarationLineBuilder
import me.tbsten.katachi.konsist.declarations.internal.VocabularyWord
import me.tbsten.katachi.konsist.declarations.internal.allowWriter
import me.tbsten.katachi.konsist.declarations.internal.requiredWriter

/**
 * `entry`: the entries of an enum class, written in its `{ }`.
 *
 * ## Example 1: any entry
 * ```kt
 * allow(enum, `class`, "Color") { allow(entry, "*") }
 * ```
 */
@ExperimentalKatachiApi
public class EntryKind internal constructor(
    internal val word: VocabularyWord,
) {
    /** `entry`. */
    override fun toString(): String = DeclarationKindName.EnumEntry.keyword
}

/**
 * `constructor`: the secondary constructors of a class, written in its `{ }`.
 *
 * ## Example 1: a private constructor taking a token
 * ```kt
 * allow(`class`, "Session") { allow(private, constructor)("token: String") }
 * ```
 */
@ExperimentalKatachiApi
public class ConstructorKind internal constructor(
    internal val word: VocabularyWord,
) {
    /** `constructor`. */
    override fun toString(): String = DeclarationKindName.Constructor.keyword
}

/**
 * `init`: the `init { }` blocks of a class, written in its `{ }`.
 *
 * ## Example 1: allow init blocks
 * ```kt
 * allow(`class`, "Session") { allow(init) }
 * ```
 */
@ExperimentalKatachiApi
public class InitKind internal constructor(
    internal val word: VocabularyWord,
) {
    /** `init`. */
    override fun toString(): String = DeclarationKindName.Init.keyword
}

/**
 * The words of what only a class body holds: enum entries, secondary constructors and `init` blocks. Like any other
 * member, one no line allows is not allowed, and `allow(anythingElse)` allows them too.
 *
 * ## Example 1: an enum class with its entries, and a class with a private secondary constructor
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     allow(enum, `class`, "*Color") {
 *         allow(entry, "*")
 *     }
 *     allow(`class`, "Session") {
 *         allow(private, constructor)("token: String")
 *         allow(init)
 *     }
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface MemberVocabulary {
    /** An enum entry: `allow(entry, "RED")`. */
    public val entry: EntryKind

    /** A secondary constructor: `allow(private, constructor)("token: String")`. The primary one is asked with `.primaryConstructor(...)`. */
    public val constructor: ConstructorKind

    /** An `init { }` block: `allow(init)`. */
    public val init: InitKind
}

/**
 * The `allow` lines of enum entries, secondary constructors and `init` blocks, in a class body.
 *
 * ## Example 1: the entries listed by name
 * ```kt
 * allow(enum, `class`, "Color") { allow(entry, "{RED,GREEN,BLUE}") }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface MemberAllow {
    /**
     * Allows the enum entries whose names match [name].
     *
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(kind: EntryKind, name: String): EntryLine =
        EntryLine(allowWriter.member(emptyList(), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(
        modifier1: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] and [modifier2] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] to [modifier3] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2, modifier3), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] to [modifier4] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] to [modifier5] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] to [modifier6] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] to [modifier7] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] to [modifier8] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] to [modifier9] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Allows the enum entries whose names match [name], with [modifier1] to [modifier10] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10), kind.word, DeclarationKindName.EnumEntry, name))

    /** Allows secondary constructors; the parameters are written after it as with a function: `allow(constructor)("id: Int")`. */
    public fun allow(kind: ConstructorKind): ConstructorLine =
        ConstructorLine(allowWriter.member(emptyList(), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun allow(
        modifier1: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] and [modifier2] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] to [modifier3] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2, modifier3), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] to [modifier4] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] to [modifier5] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] to [modifier6] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] to [modifier7] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] to [modifier8] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
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
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] to [modifier9] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
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
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Allows secondary constructors, with [modifier1] to [modifier10] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
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
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(allowWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10), kind.word, DeclarationKindName.Constructor, "*"))

    /** Allows `init { }` blocks. */
    public fun allow(kind: InitKind): InitLine = InitLine(allowWriter.member(emptyList(), kind.word, DeclarationKindName.Init, "*"))
}

/**
 * The `required` lines of enum entries, secondary constructors and `init` blocks, in a class body: a declaration the
 * line matches has to be there, and is allowed.
 *
 * ## Example 1: an enum class that has to have an UNKNOWN entry
 * ```kt
 * allow(enum, `class`, "*Type") {
 *     required(entry, "UNKNOWN")
 *     allow(entry, "*")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface MemberRequired {
    /**
     * Requires an enum entry whose name matches [name].
     *
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(kind: EntryKind, name: String): EntryLine =
        EntryLine(requiredWriter.member(emptyList(), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(
        modifier1: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] and [modifier2] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] to [modifier3] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] to [modifier4] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] to [modifier5] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] to [modifier6] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] to [modifier7] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] to [modifier8] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] to [modifier9] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9), kind.word, DeclarationKindName.EnumEntry, name))

    /**
     * Requires an enum entry whose name matches [name], with [modifier1] to [modifier10] in front. An enum entry carries only annotations, such as `annotated("Deprecated")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is given, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: EntryKind,
        name: String,
    ): EntryLine = EntryLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10), kind.word, DeclarationKindName.EnumEntry, name))

    /** Requires a secondary constructor; the parameters are written after it as with a function: `required(constructor)("id: Int")`. */
    public fun required(kind: ConstructorKind): ConstructorLine =
        ConstructorLine(requiredWriter.member(emptyList(), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun required(
        modifier1: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] and [modifier2] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] to [modifier3] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] to [modifier4] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] to [modifier5] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] to [modifier6] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] to [modifier7] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] to [modifier8] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
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
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] to [modifier9] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
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
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9), kind.word, DeclarationKindName.Constructor, "*"))

    /**
     * Requires a secondary constructor, with [modifier1] to [modifier10] in front, such as `private` or `annotated("Inject")`.
     *
     * @throws KatachiKtFileContentModifierException when a modifier is one no constructor carries.
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
        kind: ConstructorKind,
    ): ConstructorLine = ConstructorLine(requiredWriter.member(listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10), kind.word, DeclarationKindName.Constructor, "*"))

    /** Requires an `init { }` block. */
    public fun required(kind: InitKind): InitLine = InitLine(requiredWriter.member(emptyList(), kind.word, DeclarationKindName.Init, "*"))
}

/**
 * An enum entry line just written.
 *
 * ## Example 1: documented entries
 * ```kt
 * allow(enum, `class`, "Color") { allow(entry, "*").kdoc() }
 * ```
 */
@ExperimentalKatachiApi
public class EntryLine internal constructor(
    internal val line: DeclarationLineBuilder,
) {
    /**
     * Requires a KDoc on the entry.
     *
     * @throws KatachiKtFileContentLineException when written twice.
     */
    public fun kdoc(): EntryLine {
        line.writeKDoc()
        return this
    }

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * A secondary constructor line just written, whose parameters are written after it as with a function.
 *
 * ## Example 1: a private constructor taking a token
 * ```kt
 * allow(`class`, "Session") { allow(private, constructor)("token: String") }
 * ```
 */
@ExperimentalKatachiApi
public class ConstructorLine internal constructor(
    internal val line: DeclarationLineBuilder,
) {
    /**
     * Requires exactly these parameters, in order, each written `"name: Type"`; `()` is no parameter.
     *
     * @throws KatachiKtFileContentLineException when the parameters were already written, or when a parameter is not
     *   `"name: Type"`.
     * @throws KatachiKtFileContentPatternException when a name or a type is not a pattern katachi reads.
     */
    public operator fun invoke(vararg parameters: String): ConstructorLine {
        line.writeParameters(parameters.toList())
        return this
    }

    /**
     * Asks nothing about the parameters, the same as writing none.
     *
     * @throws KatachiKtFileContentLineException when the parameters were already written.
     */
    public operator fun invoke(args: AnyArgs): ConstructorLine {
        line.writeParameters(null)
        return this
    }

    /**
     * Requires a KDoc on the constructor.
     *
     * @throws KatachiKtFileContentLineException when written twice.
     */
    public fun kdoc(): ConstructorLine {
        line.writeKDoc()
        return this
    }

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * An `init { }` block line just written.
 *
 * ## Example 1: allow init blocks
 * ```kt
 * allow(`class`, "Session") { allow(init) }
 * ```
 */
@ExperimentalKatachiApi
public class InitLine internal constructor(
    internal val line: DeclarationLineBuilder,
) {
    /** The line as written so far. */
    override fun toString(): String = line.text
}

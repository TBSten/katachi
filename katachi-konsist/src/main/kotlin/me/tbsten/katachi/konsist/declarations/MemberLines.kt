package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.DeclarationLineBuilder
import me.tbsten.katachi.konsist.declarations.internal.VocabularyWord

/**
 * `entry`: the entries of an enum class, written in its `{ }`.
 *
 * ## Example 1
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
 * ## Example 1
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
 * ## Example 1
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

    /** A secondary constructor: `allow(private, constructor)("token: String")`. */
    public val constructor: ConstructorKind

    /** An `init { }` block: `allow(init)`. */
    public val init: InitKind
}

/**
 * The `allow` lines of enum entries, secondary constructors and `init` blocks, in a class body.
 *
 * ## Example 1: entries named in upper case
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
    public fun allow(kind: EntryKind, name: String): EntryLine

    /**
     * Allows the enum entries whose names match [name] and that carry the annotations of [m1].
     *
     * @throws KatachiKtFileContentModifierException when [m1] holds a modifier, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun allow(m1: Modifiers, kind: EntryKind, name: String): EntryLine

    /** Allows secondary constructors; the parameters are written after it as with a function: `allow(constructor)("id: Int")`. */
    public fun allow(kind: ConstructorKind): ConstructorLine

    /**
     * Allows secondary constructors that carry [m1], such as `private`.
     *
     * @throws KatachiKtFileContentModifierException when [m1] holds a modifier no constructor carries.
     */
    public fun allow(m1: Modifiers, kind: ConstructorKind): ConstructorLine

    /** Allows `init { }` blocks. */
    public fun allow(kind: InitKind): InitLine
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
    public fun required(kind: EntryKind, name: String): EntryLine

    /**
     * Requires an enum entry whose name matches [name] and that carries the annotations of [m1].
     *
     * @throws KatachiKtFileContentModifierException when [m1] holds a modifier, which an enum entry never carries.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public fun required(m1: Modifiers, kind: EntryKind, name: String): EntryLine

    /** Requires a secondary constructor. */
    public fun required(kind: ConstructorKind): ConstructorLine

    /**
     * Requires a secondary constructor that carries [m1].
     *
     * @throws KatachiKtFileContentModifierException when [m1] holds a modifier no constructor carries.
     */
    public fun required(m1: Modifiers, kind: ConstructorKind): ConstructorLine

    /** Requires an `init { }` block. */
    public fun required(kind: InitKind): InitLine
}

/**
 * An enum entry line just written.
 *
 * ## Example 1
 * ```kt
 * allow(enum, `class`, "Color") { allow(entry, "*") }
 * ```
 */
@ExperimentalKatachiApi
public class EntryLine internal constructor(
    internal val line: DeclarationLineBuilder,
) {
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

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * An `init { }` block line just written.
 *
 * ## Example 1
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

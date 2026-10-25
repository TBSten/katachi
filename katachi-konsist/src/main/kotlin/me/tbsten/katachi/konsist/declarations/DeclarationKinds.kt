package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.VocabularyWord
import me.tbsten.katachi.konsist.declarations.internal.renderKinds

/*
 * The kinds of declaration, and `anythingElse`: values passed to `allow(...)` after the modifiers.
 * A kind on its own writes nothing, and is refused when the block ends, as a modifier is.
 */

/**
 * A class, an interface, an object, or any mix of them made with `or`, passed to `allow(...)`
 * where the kind goes.
 *
 * ## Example 1: kinds combined with `or`, and a class given members
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *
 *     allow(`class` or `interface`, fileName) { allow(anythingElse) }
 *     allow(sealed, type, "*Action") { allow(data, `object`, "*") }
 *     allow(`class`, "Default$fileName").superclass(fileName) {
 *         allow(override, `fun`, "invoke")
 *     }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class TypeKind internal constructor(
    internal val kinds: List<DeclarationKindName>,
    internal val word: VocabularyWord,
) {
    /**
     * Either this kind or [other].
     *
     * @throws KatachiKtFileContentLineException when [other] belongs to another block.
     */
    public infix fun or(other: TypeKind): TypeKind {
        val joined = (kinds + other.kinds).distinct()
        return TypeKind(joined, word.or(other.word, "$this or $other") { renderKinds(joined) })
    }

    /** `class`, `(class or interface)`. */
    override fun toString(): String = renderKinds(kinds)
}

/**
 * A function, passed to `allow(...)` where the kind goes -- or, followed by `` `interface` ``, the
 * `fun` of a fun interface.
 *
 * ## Example 1: functions, and a fun interface
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     `import`("androidx.compose.**")
 *
 *     allow(annotated("Composable"), public, `fun`, fileName)
 *     allow(`fun`, `interface`, "*Listener") { allow(`fun`, "on*") }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class FunctionKind internal constructor(
    internal val word: VocabularyWord,
) {
    /** `fun`. */
    override fun toString(): String = DeclarationKindName.Function.keyword
}

/**
 * A `val`, a `var`, or either of them made with `or`, passed to `allow(...)` where the kind goes.
 *
 * ## Example 1: a property with its type, and constants
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     `import`("kotlinx.coroutines.flow.**")
 *
 *     allow(`class`, fileName) { allow(public, `val`, "uiState", "StateFlow<*>") }
 *     allow(const, `val` or `var`, "*")
 * }
 * ```
 */
@ExperimentalKatachiApi
public class PropertyKind internal constructor(
    internal val kinds: List<DeclarationKindName>,
    internal val word: VocabularyWord,
) {
    /**
     * Either this kind or [other].
     *
     * @throws KatachiKtFileContentLineException when [other] belongs to another block.
     */
    public infix fun or(other: PropertyKind): PropertyKind {
        val joined = (kinds + other.kinds).distinct()
        return PropertyKind(joined, word.or(other.word, "$this or $other") { renderKinds(joined) })
    }

    /** `val`, `(val or var)`. */
    override fun toString(): String = renderKinds(kinds)
}

/**
 * A type alias, passed to `allow(...)` where the kind goes.
 *
 * ## Example 1: a type alias named after the file
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     allow(`typealias`, fileName)
 * }
 * ```
 */
@ExperimentalKatachiApi
public class TypeAliasKind internal constructor(
    internal val word: VocabularyWord,
) {
    /** `typealias`. */
    override fun toString(): String = DeclarationKindName.TypeAlias.keyword
}

/**
 * What `anythingElse` is: "any declaration no other line matches", passed to `allow(...)` where
 * the kind goes. The modifiers in front of it are what such a declaration has to carry.
 *
 * ## Example 1: any private helper next to the class named after the file
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *
 *     allow(`class`, fileName) { allow(anythingElse) }
 *     allow(private, anythingElse)
 * }
 * ```
 */
@ExperimentalKatachiApi
public class AnythingElse internal constructor(
    internal val word: VocabularyWord,
) {
    /** `anythingElse`. */
    override fun toString(): String = "anythingElse"
}

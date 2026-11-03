package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * The values a line of `ktFileContent { }` is written with: modifiers, `annotated(...)`, the kinds
 * of declaration and [anythingElse]. They are passed to `allow(...)` in the order Kotlin writes
 * the declaration -- annotations, then modifiers, then the kind -- followed by the name.
 *
 * - **A line's modifiers are the declaration's, as its Kotlin source writes them.** Each one
 *   written has to be there, and one not written may not be -- except `public` and `final`, which
 *   Kotlin reads a declaration without them as having. A modifier that may be there or not is
 *   written [Modifiers.optional]: `suspend.optional()`. An annotation not written is not asked
 *   about, and `allow(..., anythingElse)` and `not.allow(...)` ask only for what they write.
 * - **Modifiers and kinds are values**, so `or` makes alternatives of them --
 *   `` allow(internal or private, data, `object`, "*") `` -- and a name of your own is an extension:
 *   `` val DeclarationListScope.classOrInterface get() = `class` or `interface` ``.
 * - **A value on a line of its own writes nothing**, and is refused when the block ends
 *   ([KtFileContentLineProblem.DanglingModifiers]): pass it to `allow(...)`.
 *
 * ## Example 1: modifiers and annotations in front, as in Kotlin
 * ```kt
 * ktFileContent {
 *     `package`("com.example.feature.**")
 *     `import`("**")
 *
 *     allow(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") { allow(anythingElse) }
 *     allow(annotated("Preview") or annotated("PreviewLightDark"), private, `fun`, "*Preview")
 *     allow(const, `val`, "*")
 *     allow(sealed, type, "*Action") { allow(data, `object`, "*") }
 *     allow(suspend.optional(), `fun`, "execute")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface DeclarationVocabulary {
    /**
     * Requires `public`, or no visibility written at all, which Kotlin reads as public: a line
     * with it and one without match the same declarations.
     */
    public val public: Modifiers

    /** Requires `protected`. Only a member can be protected. */
    public val protected: Modifiers

    /** Requires `private`. */
    public val private: Modifiers

    /** Requires `internal`. */
    public val internal: Modifiers

    /** Requires `expect`. */
    public val expect: Modifiers

    /** Requires `actual`. */
    public val actual: Modifiers

    /** Requires `abstract`. On a fun, val or var, only inside a class. */
    public val abstract: Modifiers

    /** Requires `open`. On a fun, val or var, only inside a class. */
    public val open: Modifiers

    /** Requires `sealed`: a sealed class or a sealed interface. */
    public val sealed: Modifiers

    /** Requires `const`, which only a `val` carries. */
    public val const: Modifiers

    /** Requires `external`, which only a function carries. */
    public val external: Modifiers

    /** Requires `override`, on a member function or property. */
    public val override: Modifiers

    /** Requires `lateinit`, which only a `var` carries. */
    public val lateinit: Modifiers

    /** Requires `tailrec`, which only a function carries. */
    public val tailrec: Modifiers

    /** Requires `suspend`, which only a function carries. */
    public val suspend: Modifiers

    /** Requires `inner`, on a nested class. */
    public val inner: Modifiers

    /** Requires `enum`: an enum class. */
    public val enum: Modifiers

    /** Requires `annotation`: an annotation class. */
    public val annotation: Modifiers

    /** Requires `companion`: a companion object inside a class. */
    public val companion: Modifiers

    /** Requires `inline`, which only a function carries. */
    public val inline: Modifiers

    /** Requires `value`: a value class. */
    public val value: Modifiers

    /** Requires `infix`, which only a function carries. */
    public val infix: Modifiers

    /** Requires `operator`, which only a function carries. */
    public val operator: Modifiers

    /** Requires `data`: a data class or a data object. */
    public val data: Modifiers

    /**
     * Requires every one of the annotations [names]; arguments are not looked at.
     *
     * Each is compared by simple name, or by full name when it holds a `.`
     * (`androidx.compose.runtime.Composable`).
     *
     * A simple name is compared with the name as written in the file, and an alias is not resolved to
     * what it stands for: after `import a.Inject as Wired`, `@Wired` is matched by `annotated("Wired")`
     * and not by `annotated("Inject")`. To match through the alias, write the full name:
     * `annotated("a.Inject")` matches that `@Wired` too.
     *
     * `allow(annotated("A"), annotated("B"), ...)` is the same as `allow(annotated("A", "B"), ...)`.
     *
     * @throws KatachiKtFileContentLineException when no name is given.
     */
    public fun annotated(vararg names: String): Modifiers

    /** A class -- data, enum, sealed, value and annotation classes included. */
    public val `class`: TypeKind

    /** An interface -- `fun interface` included. */
    public val `interface`: TypeKind

    /** An object -- data objects and companion objects included. */
    public val `object`: TypeKind

    /** A class, an interface or an object: `` `class` or `interface` or `object` ``. */
    public val type: TypeKind

    /**
     * A function, extensions included (its name is the part after the receiver). Followed by
     * `` `interface` ``, a fun interface: `` allow(`fun`, `interface`, "*Listener") ``.
     */
    public val `fun`: FunctionKind

    /** A read-only property, a `val` of a primary constructor included. */
    public val `val`: PropertyKind

    /** A mutable property, a `var` of a primary constructor included. */
    public val `var`: PropertyKind

    /** A type alias. Only directly in a file: Kotlin has none inside a class. */
    public val `typealias`: TypeAliasKind

    /**
     * Any declaration no other line matches, whatever its kind and name, written where the kind goes.
     *
     * `allow(private, anythingElse)` lets any private declaration through.
     */
    public val anythingElse: AnythingElse
}

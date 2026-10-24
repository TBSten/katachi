package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * The values a line of `ktFileContent { }` is written with: modifiers, `annotated(...)`, the kinds
 * of declaration and [anythingElse]. They are passed to `allow(...)` in the order Kotlin writes
 * the declaration -- annotations, then modifiers, then the kind -- followed by the name.
 *
 * - **A modifier written is a modifier required; one not written is not asked about.** `public`
 *   is also met by a declaration that writes no visibility, as Kotlin reads it.
 * - **Modifiers and kinds are values**, so `or` makes alternatives of them --
 *   `` allow(internal or private, data, `object`, "*") `` -- and a name of your own is an extension:
 *   `` val DeclarationListScope.classOrInterface get() = `class` or `interface` ``.
 * - **A value on a line of its own writes nothing**, and is refused when the block ends
 *   ([KtFileContentLineProblem.DanglingModifiers]): pass it to `allow(...)`.
 *
 * ## Example 1: modifiers and annotations in front, as in Kotlin
 * ```kt
 * ktFileContent {
 *     allow(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel")
 *     allow(annotated("Preview") or annotated("PreviewLightDark"), private, `fun`, "*Preview")
 *     allow(const, `val`, "*")
 *     allow(sealed, type, "*Action")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface DeclarationVocabulary {
    /**
     * Requires `public`, or no visibility written at all, which Kotlin reads as public.
     *
     * ## Example 1: a public class
     * ```kt
     * allow(public, `class`, fileName)
     * ```
     */
    public val public: Modifiers

    /**
     * Requires `protected`. Only a member can be protected.
     *
     * ## Example 1: protected members of a class
     * ```kt
     * allow(`class`, fileName) { allow(protected, anythingElse) }
     * ```
     */
    public val protected: Modifiers

    /**
     * Requires `private`.
     *
     * ## Example 1: allow any private helper
     * ```kt
     * allow(private, anythingElse)
     * ```
     */
    public val private: Modifiers

    /**
     * Requires `internal`.
     *
     * ## Example 1: internal objects
     * ```kt
     * allow(internal, `object`, "*")
     * ```
     */
    public val internal: Modifiers

    /**
     * Requires `expect`.
     *
     * ## Example 1: an expect function named after the file
     * ```kt
     * allow(expect, `fun`, fileName)
     * ```
     */
    public val expect: Modifiers

    /**
     * Requires `actual`.
     *
     * ## Example 1: an actual function named after the file
     * ```kt
     * allow(actual, `fun`, fileName)
     * ```
     */
    public val actual: Modifiers

    /**
     * Requires `abstract`. On a fun, val or var, only inside a class.
     *
     * ## Example 1: an abstract class with abstract functions
     * ```kt
     * allow(abstract, `class`, fileName) { allow(abstract, `fun`, "*") }
     * ```
     */
    public val abstract: Modifiers

    /**
     * Requires `open`. On a fun, val or var, only inside a class.
     *
     * ## Example 1: an open class
     * ```kt
     * allow(open, `class`, fileName)
     * ```
     */
    public val open: Modifiers

    /**
     * Requires `sealed`: a sealed class or a sealed interface.
     *
     * ## Example 1: a sealed class or interface named after the file
     * ```kt
     * allow(sealed, type, fileName)
     * ```
     */
    public val sealed: Modifiers

    /**
     * Requires `const`, which only a `val` carries.
     *
     * ## Example 1: constants
     * ```kt
     * allow(const, `val`, "*")
     * ```
     */
    public val const: Modifiers

    /**
     * Requires `external`, which only a function carries.
     *
     * ## Example 1: external functions
     * ```kt
     * allow(external, `fun`, "*")
     * ```
     */
    public val external: Modifiers

    /**
     * Requires `override`, on a member function or property.
     *
     * ## Example 1: the implementation of a use case
     * ```kt
     * allow(`class`, "Default$fileName").superclass(fileName) { allow(override, `fun`, "invoke") }
     * ```
     */
    public val override: Modifiers

    /**
     * Requires `lateinit`, which only a `var` carries.
     *
     * ## Example 1: late-initialised properties
     * ```kt
     * allow(`class`, fileName) { allow(lateinit, `var`, "*") }
     * ```
     */
    public val lateinit: Modifiers

    /**
     * Requires `tailrec`, which only a function carries.
     *
     * ## Example 1: tail-recursive functions
     * ```kt
     * allow(tailrec, `fun`, "*")
     * ```
     */
    public val tailrec: Modifiers

    /**
     * Requires `suspend`, which only a function carries.
     *
     * ## Example 1: suspending functions
     * ```kt
     * allow(suspend, `fun`, "*")
     * ```
     */
    public val suspend: Modifiers

    /**
     * Requires `inner`, on a nested class.
     *
     * ## Example 1: inner classes inside a class
     * ```kt
     * allow(`class`, fileName) { allow(inner, `class`, "*") }
     * ```
     */
    public val inner: Modifiers

    /**
     * Requires `enum`: an enum class.
     *
     * ## Example 1: an enum class named after the file
     * ```kt
     * allow(enum, `class`, fileName)
     * ```
     */
    public val enum: Modifiers

    /**
     * Requires `annotation`: an annotation class.
     *
     * ## Example 1: an annotation class named after the file
     * ```kt
     * allow(annotation, `class`, fileName)
     * ```
     */
    public val annotation: Modifiers

    /**
     * Requires `companion`: a companion object inside a class.
     *
     * ## Example 1: a class with a companion object
     * ```kt
     * allow(`class`, fileName) { allow(companion, `object`, "*") }
     * ```
     */
    public val companion: Modifiers

    /**
     * Requires `inline`, which only a function carries.
     *
     * ## Example 1: inline functions
     * ```kt
     * allow(inline, `fun`, "*")
     * ```
     */
    public val inline: Modifiers

    /**
     * Requires `value`: a value class.
     *
     * ## Example 1: a value class named after the file
     * ```kt
     * allow(annotated("JvmInline"), value, `class`, fileName)
     * ```
     */
    public val value: Modifiers

    /**
     * Requires `infix`, which only a function carries.
     *
     * ## Example 1: infix functions
     * ```kt
     * allow(infix, `fun`, "*")
     * ```
     */
    public val infix: Modifiers

    /**
     * Requires `operator`, which only a function carries.
     *
     * ## Example 1: a use case interface with an operator invoke
     * ```kt
     * allow(`interface`, fileName) { allow(operator, `fun`, "invoke") }
     * ```
     */
    public val operator: Modifiers

    /**
     * Requires `data`: a data class or a data object.
     *
     * ## Example 1: a data class named after the file
     * ```kt
     * allow(data, `class`, fileName)
     * ```
     */
    public val data: Modifiers

    /**
     * Requires every one of the annotations [names], compared by simple name -- or by full name
     * when a name holds a `.` (`androidx.compose.runtime.Composable`). Arguments are not looked at.
     *
     * A simple name is compared with the name as written in the file, and an alias is not resolved to
     * what it stands for: after `import a.Inject as Wired`, `@Wired` is matched by `annotated("Wired")`
     * and not by `annotated("Inject")`. To match through the alias, write the full name:
     * `annotated("a.Inject")` matches that `@Wired` too.
     *
     * `allow(annotated("A"), annotated("B"), ...)` is the same as `allow(annotated("A", "B"), ...)`.
     *
     * ## Example 1: a composable function named after the file
     * ```kt
     * allow(annotated("Composable"), public, `fun`, fileName)
     * ```
     *
     * @throws KatachiKtFileContentLineException when no name is given.
     */
    public fun annotated(vararg names: String): Modifiers

    /**
     * A class -- data, enum, sealed, value and annotation classes included.
     *
     * ## Example 1: a class named after the file, with its members
     * ```kt
     * allow(`class`, fileName) { allow(`val`, "*") }
     * ```
     */
    public val `class`: TypeKind

    /**
     * An interface -- `fun interface` included.
     *
     * ## Example 1: an interface named after the file
     * ```kt
     * allow(`interface`, fileName)
     * ```
     */
    public val `interface`: TypeKind

    /**
     * An object -- data objects and companion objects included.
     *
     * ## Example 1: a data object named after the file
     * ```kt
     * allow(data, `object`, fileName)
     * ```
     */
    public val `object`: TypeKind

    /**
     * A class, an interface or an object: `` `class` or `interface` or `object` ``.
     *
     * ## Example 1: any type named after the file
     * ```kt
     * allow(type, fileName)
     * ```
     */
    public val type: TypeKind

    /**
     * A function, extensions included (its name is the part after the receiver). Followed by
     * `` `interface` ``, a fun interface: `` allow(`fun`, `interface`, "*Listener") ``.
     *
     * ## Example 1: functions with no parameter that return Unit
     * ```kt
     * allow(public, `fun`, "on*")().returns("Unit")
     * ```
     */
    public val `fun`: FunctionKind

    /**
     * A read-only property, a `val` of a primary constructor included.
     *
     * ## Example 1: a property with its type
     * ```kt
     * allow(public, `val`, "uiState", "StateFlow<*>")
     * ```
     */
    public val `val`: PropertyKind

    /**
     * A mutable property, a `var` of a primary constructor included.
     *
     * ## Example 1: private mutable properties
     * ```kt
     * allow(private, `var`, "*")
     * ```
     */
    public val `var`: PropertyKind

    /**
     * A type alias. Only directly in a file: Kotlin has none inside a class.
     *
     * ## Example 1: a type alias named after the file
     * ```kt
     * allow(`typealias`, fileName)
     * ```
     */
    public val `typealias`: TypeAliasKind

    /**
     * Any declaration no other line matches, whatever its kind and name, written where the kind goes:
     * `allow(private, anythingElse)` lets any private declaration through.
     *
     * ## Example 1: one class named after the file, plus anything else
     * ```kt
     * allow(`class`, fileName)
     * allow(anythingElse)
     * ```
     */
    public val anythingElse: AnythingElse
}

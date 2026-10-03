package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * The words a line of `ktFileContent { }` starts with: modifiers, `annotated(...)`, and the kinds
 * of declaration. Shared by the scopes and by what modifiers return, so that a line reads the way
 * Kotlin writes the declaration -- annotations, then modifiers, then the kind, then the name.
 *
 * - **A modifier written is a modifier required; one not written is not asked about.** `public`
 *   is also met by a declaration that writes no visibility, as Kotlin reads it.
 * - **Modifiers chain with `.`, in any order**, and `or` makes alternatives:
 *   `` (internal or private).data.`object`("*") ``.
 * - **A kind is a value**, so kinds combine with `or` too, and a name of your own is an extension:
 *   `` val DeclarationListScope.classOrInterface get() = `class` or `interface` ``.
 * - **A combination no declaration can have is refused while the definition is read**
 *   (`` data.`interface` ``, `` const.`fun` ``, `public.internal`), with
 *   [KatachiKtFileContentModifierException]. Modifiers in front of [type] or of kinds combined with
 *   `or` keep only the kinds they can be on: `sealed.type` is a sealed class or a sealed interface.
 *
 * ## Example 1: modifiers and annotations in front, as in Kotlin
 * ```kt
 * ktFileContent {
 *     annotated("HiltViewModel")
 *         .public.`class`(fileName).superclass("ViewModel")
 *     (annotated("Preview") or annotated("PreviewLightDark")).private.`fun`("*Preview")
 *     const.`val`("*")
 *     sealed.type("*Action")
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
     * public.`class`(fileName)
     * ```
     */
    public val public: Modifiers

    /**
     * Requires `protected`. Only a member can be protected.
     *
     * ## Example 1: protected members of a class
     * ```kt
     * `class`(fileName) { protected.anythingElse() }
     * ```
     */
    public val protected: Modifiers

    /**
     * Requires `private`.
     *
     * ## Example 1: allow any private helper
     * ```kt
     * private.anythingElse()
     * ```
     */
    public val private: Modifiers

    /**
     * Requires `internal`.
     *
     * ## Example 1: internal objects
     * ```kt
     * internal.`object`("*")
     * ```
     */
    public val internal: Modifiers

    /**
     * Requires `expect`.
     *
     * ## Example 1: an expect function named after the file
     * ```kt
     * expect.`fun`(fileName)
     * ```
     */
    public val expect: Modifiers

    /**
     * Requires `actual`.
     *
     * ## Example 1: an actual function named after the file
     * ```kt
     * actual.`fun`(fileName)
     * ```
     */
    public val actual: Modifiers

    /**
     * Requires `abstract`. On a fun, val or var, only inside a class.
     *
     * ## Example 1: an abstract class with abstract functions
     * ```kt
     * abstract.`class`(fileName) { abstract.`fun`("*") }
     * ```
     */
    public val abstract: Modifiers

    /**
     * Requires `open`. On a fun, val or var, only inside a class.
     *
     * ## Example 1: an open class
     * ```kt
     * open.`class`(fileName)
     * ```
     */
    public val open: Modifiers

    /**
     * Requires `sealed`: a sealed class or a sealed interface.
     *
     * ## Example 1: a sealed class or interface named after the file
     * ```kt
     * sealed.type(fileName)
     * ```
     */
    public val sealed: Modifiers

    /**
     * Requires `const`, which only a `val` carries.
     *
     * ## Example 1: constants
     * ```kt
     * const.`val`("*")
     * ```
     */
    public val const: Modifiers

    /**
     * Requires `external`, which only a function carries.
     *
     * ## Example 1: external functions
     * ```kt
     * external.`fun`("*")
     * ```
     */
    public val external: Modifiers

    /**
     * Requires `override`, on a member function or property.
     *
     * ## Example 1: the implementation of a use case
     * ```kt
     * `class`("Default$fileName").superclass(fileName) { override.`fun`("invoke") }
     * ```
     */
    public val override: Modifiers

    /**
     * Requires `lateinit`, which only a `var` carries.
     *
     * ## Example 1: late-initialised properties
     * ```kt
     * `class`(fileName) { lateinit.`var`("*") }
     * ```
     */
    public val lateinit: Modifiers

    /**
     * Requires `tailrec`, which only a function carries.
     *
     * ## Example 1: tail-recursive functions
     * ```kt
     * tailrec.`fun`("*")
     * ```
     */
    public val tailrec: Modifiers

    /**
     * Requires `suspend`, which only a function carries.
     *
     * ## Example 1: suspending functions
     * ```kt
     * suspend.`fun`("*")
     * ```
     */
    public val suspend: Modifiers

    /**
     * Requires `inner`, on a nested class.
     *
     * ## Example 1: inner classes inside a class
     * ```kt
     * `class`(fileName) { inner.`class`("*") }
     * ```
     */
    public val inner: Modifiers

    /**
     * Requires `enum`: an enum class.
     *
     * ## Example 1: an enum class named after the file
     * ```kt
     * enum.`class`(fileName)
     * ```
     */
    public val enum: Modifiers

    /**
     * Requires `annotation`: an annotation class.
     *
     * ## Example 1: an annotation class named after the file
     * ```kt
     * annotation.`class`(fileName)
     * ```
     */
    public val annotation: Modifiers

    /**
     * Requires `companion`: a companion object inside a class.
     *
     * ## Example 1: a class with a companion object
     * ```kt
     * `class`(fileName) { companion.`object`("*") }
     * ```
     */
    public val companion: Modifiers

    /**
     * Requires `inline`, which only a function carries.
     *
     * ## Example 1: inline functions
     * ```kt
     * inline.`fun`("*")
     * ```
     */
    public val inline: Modifiers

    /**
     * Requires `value`: a value class.
     *
     * ## Example 1: a value class named after the file
     * ```kt
     * annotated("JvmInline").value.`class`(fileName)
     * ```
     */
    public val value: Modifiers

    /**
     * Requires `infix`, which only a function carries.
     *
     * ## Example 1: infix functions
     * ```kt
     * infix.`fun`("*")
     * ```
     */
    public val infix: Modifiers

    /**
     * Requires `operator`, which only a function carries.
     *
     * ## Example 1: a use case interface with an operator invoke
     * ```kt
     * `interface`(fileName) { operator.`fun`("invoke") }
     * ```
     */
    public val operator: Modifiers

    /**
     * Requires `data`: a data class or a data object.
     *
     * ## Example 1: a data class named after the file
     * ```kt
     * data.`class`(fileName)
     * ```
     */
    public val data: Modifiers

    /**
     * Requires every one of the annotations [names], compared by simple name -- or by full name
     * when a name holds a `.` (`androidx.compose.runtime.Composable`). Arguments are not looked at.
     *
     * `annotated("A").annotated("B")` is the same as `annotated("A", "B")`.
     *
     * ## Example 1: a composable function named after the file
     * ```kt
     * annotated("Composable").public.`fun`(fileName)
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
     * `class`(fileName) { `val`("*") }
     * ```
     */
    public val `class`: TypeKind

    /**
     * An interface -- `fun interface` included.
     *
     * ## Example 1: an interface named after the file
     * ```kt
     * `interface`(fileName)
     * ```
     */
    public val `interface`: TypeKind

    /**
     * An object -- data objects and companion objects included.
     *
     * ## Example 1: a data object named after the file
     * ```kt
     * data.`object`(fileName)
     * ```
     */
    public val `object`: TypeKind

    /**
     * A class, an interface or an object: `` `class` or `interface` or `object` ``.
     *
     * ## Example 1: any type named after the file
     * ```kt
     * type(fileName)
     * ```
     */
    public val type: TypeKind

    /**
     * A function, extensions included (its name is the part after the receiver). `` `fun`.`interface` ``
     * is a fun interface.
     *
     * ## Example 1: functions with no parameter that return Unit
     * ```kt
     * public.`fun`("on*")().returns("Unit")
     * ```
     */
    public val `fun`: FunctionKind

    /**
     * A read-only property, a `val` of a primary constructor included.
     *
     * ## Example 1: a property with its type
     * ```kt
     * public.`val`("uiState", "StateFlow<*>")
     * ```
     */
    public val `val`: PropertyKind

    /**
     * A mutable property, a `var` of a primary constructor included.
     *
     * ## Example 1: private mutable properties
     * ```kt
     * private.`var`("*")
     * ```
     */
    public val `var`: PropertyKind

    /**
     * A type alias. Only directly in a file: Kotlin has none inside a class.
     *
     * ## Example 1: a type alias named after the file
     * ```kt
     * `typealias`(fileName)
     * ```
     */
    public val `typealias`: TypeAliasKind
}

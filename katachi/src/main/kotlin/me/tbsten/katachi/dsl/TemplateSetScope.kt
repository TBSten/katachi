package me.tbsten.katachi.dsl

import kotlin.enums.EnumEntries
import me.tbsten.katachi.ExperimentalKatachiApi
import org.intellij.lang.annotations.Language

/**
 * Receiver of `"name".templateSet { }`: gathers templates so one `--arg template=<name>` generates them all.
 *
 * The block runs once when `architecture { }` is evaluated. That fixes the set's own parameters
 * (the `...Parameter()` functions, written through `by` like a template's) and which templates
 * are its members. The block of each [template] call is not run then: it runs when files are
 * generated or previewed, and is where a parameter's value may be read and passed on with
 * [TemplateSetMemberScope.arg].
 *
 * ## Example 1: generate a screen and its view model together
 * ```kt
 * architecture {
 *     "feature".group {
 *         "FeatureScreen".role {
 *             layout { "feature" / capture("name") / "${capture("name")}Screen".ktFile()
 *                 .template { "fun ${captureValue("name")}Screen() {}" } }
 *         }
 *         "FeatureViewModel".role {
 *             layout { "feature" / capture("name") / "${capture("name")}ViewModel".ktFile()
 *                 .template { "class ${captureValue("name")}ViewModel" } }
 *         }
 *         "NewFeature".templateSet {
 *             title = "New feature"
 *             val name by stringParameter()
 *             template("feature.FeatureScreen") { arg("name", name) }
 *             template("feature.FeatureViewModel") { arg("name", name) }
 *         }
 *     }
 * }
 * ```
 *
 * @see TemplateSetMemberScope
 * @see TemplateParameter
 */
@ExperimentalKatachiApi
@KatachiDsl
public sealed interface TemplateSetScope {
    /** Display name of the template set. Defaults to its name. */
    @get:Language("markdown")
    @set:Language("markdown")
    public var title: String?

    /** One paragraph describing what generating this set produces. */
    @get:Language("markdown")
    @set:Language("markdown")
    public var summary: String?

    /** Declares a `String` parameter of the set. Write it with `by`, as a template's is. */
    public fun stringParameter(default: String? = null): TemplateParameter<String>

    /** Declares a `Boolean` parameter of the set, read from `true` / `false`. */
    public fun booleanParameter(default: Boolean? = null): TemplateParameter<Boolean>

    /** Declares an `Int` parameter of the set. */
    public fun intParameter(default: Int? = null): TemplateParameter<Int>

    /** Declares an enum parameter of the set that has no default. */
    public fun <E : Enum<E>> enumParameter(entries: EnumEntries<E>): TemplateParameter<E>

    /** Declares an enum parameter of the set whose default is [default]. */
    public fun <E : Enum<E>> enumParameter(default: E): TemplateParameter<E>

    /**
     * Adds the template that [specifier] names to the set, and passes it values in [block].
     *
     * [specifier] reads as `--arg template=` does, never relative to the set. The templates are
     * generated in the order they are written here. The block may be left out for a template
     * whose parameters all have defaults.
     */
    public fun template(specifier: String, block: TemplateSetMemberScope.() -> Unit = {})
}

/**
 * Receiver of the block of one [TemplateSetScope.template]: the values passed to that member.
 *
 * Only what is written with [arg] reaches the member. A parameter of the set having the same
 * name as one of the member's is not passed on by itself.
 *
 * ## Example 1: pass different values to two members
 * ```kt
 * "NewFeature".templateSet {
 *     val name by stringParameter()
 *     template("feature.FeatureScreen") { arg("name", name) }
 *     template("feature.FeatureViewModel") {
 *         arg("name", "${name}Main")
 *         arg("withState", false)
 *     }
 * }
 * ```
 *
 * @see TemplateSetScope
 */
@ExperimentalKatachiApi
@KatachiDsl
public sealed interface TemplateSetMemberScope {
    /** Passes [value] as the member's `--arg [name]`. */
    public fun arg(name: String, value: String)

    /** Passes [value] as `true` or `false`. */
    public fun arg(name: String, value: Boolean)

    /** Passes [value] in decimal. */
    public fun arg(name: String, value: Int)

    /** Passes the entry name of [value]. */
    public fun arg(name: String, value: Enum<*>)

    /** Whether the values come from a preview (`katachiTemplates`) rather than from a run. */
    public val isPreview: Boolean
}

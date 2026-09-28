package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * The same capture name was used twice along one path of a `layout { }`: two `capture("x")`
 * levels, a `capture("x")` below a module key that named a wildcard `x` too, or one
 * `"...".module(capture = ...)` giving the same name twice.
 *
 * A template takes the value of a name from one `--arg`, so two levels sharing a name could never
 * hold two different directories. The same name on two *different* paths of a role is fine:
 * those paths share the one parameter.
 *
 * ## Example 1: catch a path that names two levels alike
 * ```kt
 * shouldThrow<KatachiDuplicateCaptureException> {
 *     architecture {
 *         "ui".group {
 *             "Screen" { layout { capture("x") / capture("x") / "*Screen".ktFile() } }
 *         }
 *     }.flattenLayout()
 * }.name shouldBe "x"
 * ```
 *
 * @property name the name used twice.
 * @property role the role's qualified name, or `null` when the duplicate was within the names of
 *   one module key, which is noticed before the role's entries exist.
 * @property path the path the two uses meet on, with the named levels written as `*`, or the
 *   module key for a duplicate within one module key's names.
 * @property firstDeclaredAt where the name was used the first time.
 * @property declaredAt where it was used again.
 */
public class KatachiDuplicateCaptureException internal constructor(
    public val name: String,
    public val role: String?,
    public val path: String,
    public val firstDeclaredAt: DeclarationSite,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        val where = if (role == null) "`$path`" else "`$path` of role \"$role\""
        appendLine("Capture \"$name\" is used twice on $where, at $declaredAt (first at $firstDeclaredAt).")
        appendLine(
            "A template takes one value for it from `--arg $name=...`, so the two levels could " +
                "never hold different directories.",
        )
        append(
            "Give one of them another name. Using the same name on a different path of the same " +
                "role is fine: those paths share the one parameter.",
        )
    },
)

/**
 * `"...".module(capture = ...)` was given a different number of names than its module path has
 * `*`s.
 *
 * The names are matched to the `*`s one for one, in the order they are written. A `**` is not
 * counted and cannot be named: how many levels it stands for is not fixed, so no one value could
 * fill it in.
 *
 * ## Example 1: catch a name given to a `**`
 * ```kt
 * shouldThrow<KatachiCaptureCountMismatchException> {
 *     architecture {
 *         "ui".group {
 *             "Screen" { layout { ":feature:**".module(capture = "feature") { } } }
 *         }
 *     }.flattenLayout()
 * }.wildcardCount shouldBe 0
 * ```
 *
 * @property modulePath the module path, as katachi prints it (`":feature:*"`).
 * @property captures the names that were given.
 * @property wildcardCount how many `*`s the module path holds, `**` not counted.
 * @property declaredAt where the module key was written.
 */
public class KatachiCaptureCountMismatchException internal constructor(
    public val modulePath: String,
    public val captures: List<String>,
    public val wildcardCount: Int,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "`$modulePath`.module(capture = ${captures.joinToString { "\"$it\"" }}) at $declaredAt " +
                "gives ${captures.size} name(s), but the module path has $wildcardCount `*`.",
        )
        appendLine("The names are matched to the `*`s one for one, in order, so the two counts have to agree.")
        append(
            "A `**` cannot be named: how many levels it stands for is not fixed. Read what it " +
                "matched through `wildcards` instead.",
        )
    },
)

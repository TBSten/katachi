package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * The same capture name was used twice along one path of a `layout { }`: two `capture("x")`
 * levels, a `capture("x")` below a module key that named a wildcard `x` too, or one
 * `":...:${capture("x")}".module { }` key giving the same name twice.
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
@ExperimentalKatachiApi
public class KatachiDuplicateCaptureException internal constructor(
    public val name: String,
    public val role: String?,
    public val path: String,
    public val firstDeclaredAt: DeclarationSite,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        val where = if (role == null) "`$path`" else "`$path` of role \"$role\""
        val at = if (firstDeclaredAt == declaredAt) "at $declaredAt" else "at $declaredAt (first at $firstDeclaredAt)"
        appendLine("Capture \"$name\" is used twice on $where, $at.")
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
 * Two `capture(...)` tokens sit next to each other with no literal between them, or one sits
 * next to a plain `*`.
 *
 * A capture is checked as a plain `*`, so two of them touching would substitute to `**` --
 * which the glob compiler reads as the unrelated "match any depth" wildcard rather than as two
 * single levels next to each other. There would also be no way to tell, from a matched path,
 * where one capture's value ends and the next begins.
 *
 * ## Example 1: catch two captures written back to back
 * ```kt
 * shouldThrow<KatachiAdjacentCaptureException> {
 *     architecture {
 *         "ui".group {
 *             "Screen" {
 *                 layout { "${capture("a")}${capture("b")}Screen".ktFile() }
 *             }
 *         }
 *     }.flattenLayout()
 * }.names shouldBe listOf("a", "b")
 * ```
 *
 * @property key the segment the tokens were found in, with each shown as `${capture("name")}`.
 * @property names the capture names involved, in the order they appear: both, for two captures
 *   touching, or the one, for a capture touching a plain `*`.
 * @property declaredAt where the segment was written.
 */
@ExperimentalKatachiApi
public class KatachiAdjacentCaptureException internal constructor(
    public val key: String,
    public val names: List<String>,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Adjacent captures in "$key" at $declaredAt: ${names.joinToString(", ")}.""")
        appendLine(
            "A capture is checked as a plain *, so two of them touching would read as ** -- the " +
                "unrelated \"any depth\" wildcard -- and there would be no way to tell where one " +
                "capture's value ends and the next begins.",
        )
        append("Put a literal character between them, such as a `-` or a `/`.")
    },
)

/**
 * A `capture(...)` token was found somewhere other than a layout key: in a `description`, a
 * `fileConstraint` name, or the string a `.template { }` block returned.
 *
 * `capture(...)` hands back a token meant to be read by the layout DSL, which strips it back out
 * before the key reaches the glob compiler. Nothing else in katachi knows to do that, so the
 * token would otherwise leak into text a person reads as the private-use characters it is made
 * of.
 *
 * ## Example 1: catch a capture read outside a layout key
 * ```kt
 * shouldThrow<KatachiStrayCaptureTokenException> {
 *     architecture {
 *         "ui".group {
 *             "Screen" {
 *                 layout {
 *                     "feature" / capture("feature") / "*Screen".ktFile()
 *                     description = "for ${capture("feature")}"
 *                 }
 *             }
 *         }
 *     }.flattenLayout()
 * }.name shouldBe "feature"
 * ```
 *
 * @property where what the token was found in: `"description"`, `"fileConstraint name"`, or
 *   `"template"`.
 * @property name the capture name the stray token carried.
 * @property declaredAt where the text holding it was written.
 */
@ExperimentalKatachiApi
public class KatachiStrayCaptureTokenException internal constructor(
    public val where: String,
    public val name: String,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""capture("$name") was read into $where at $declaredAt, not into a layout key.""")
        appendLine(
            "capture(...) returns a token meant for a layout key -- a directory, a file name or " +
                "a module path -- which reads it back out before the check ever sees it. Nothing " +
                "else does that, so it would otherwise show up as itself.",
        )
        append("Read the value instead, with captureValue(\"$name\") inside a .template { } block.")
    },
)

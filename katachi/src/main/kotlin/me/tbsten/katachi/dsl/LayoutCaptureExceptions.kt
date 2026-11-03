package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * A derived capture such as `capture("m").camelCase` sits on a path with no plain `capture("m")` to derive it from.
 *
 * A derived level is checked against the converted value of its plain capture, so without one
 * nothing would be checked. The plain capture may sit above or below the derived one on the
 * same path.
 *
 * ## Example 1: catch a derived capture whose plain capture is on another path
 * ```kt
 * shouldThrow<KatachiDerivedCaptureWithoutSourceException> {
 *     architecture {
 *         "ui".group {
 *             "Screen".role {
 *                 layout {
 *                     "feature" / capture("m") / "a.kt".file()
 *                     "docs" / "${capture("m").camelCase}.md".file()
 *                 }
 *             }
 *         }
 *     }.flattenLayout()
 * }.name shouldBe "m"
 * ```
 *
 * @property name the plain capture's name the derived one asks for.
 * @property transform the conversion that was written, such as `"camelCase"`.
 * @property role the role's qualified name.
 * @property path the path the derived capture sits on, with every capture written as `*`.
 * @property declaredAt where the derived capture was written.
 */
@ExperimentalKatachiApi
public class KatachiDerivedCaptureWithoutSourceException internal constructor(
    public val name: String,
    public val transform: String,
    public val role: String,
    public val path: String,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "capture(\"$name\").$transform at $declaredAt has no plain capture(\"$name\") on `$path` of role \"$role\".",
        )
        appendLine(
            "A derived capture is checked against the value of its plain capture, so without one nothing " +
                "would be checked.",
        )
        append("Write capture(\"$name\") on a level of the same path, or drop .$transform.")
    },
)

/**
 * Two naming conversions were applied to one capture token: `capture("m").kebabCase.pascalCase`.
 *
 * Every conversion reads the same words out of a value, so one of them already says everything
 * the two would; and `flatCase` runs the words together, after which a second conversion has no
 * word boundaries left to read, so a chain has no one defined meaning.
 *
 * ## Example 1: catch a chained conversion
 * ```kt
 * shouldThrow<KatachiChainedCaptureTransformException> {
 *     architecture {
 *         "ui".group {
 *             "Screen".role {
 *                 layout { capture("m") / "${capture("m").kebabCase.pascalCase}.kt".file() }
 *             }
 *         }
 *     }.flattenLayout()
 * }.second shouldBe "pascalCase"
 * ```
 *
 * @property name the capture's name.
 * @property first the conversion the token already carried, such as `"kebabCase"`.
 * @property second the conversion applied on top of it.
 * @property declaredAt where the second conversion was written.
 */
@ExperimentalKatachiApi
public class KatachiChainedCaptureTransformException internal constructor(
    public val name: String,
    public val first: String,
    public val second: String,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("capture(\"$name\").$first.$second at $declaredAt applies two conversions.")
        appendLine(
            "Each conversion reads the same words, so one of them is enough; flatCase loses the word " +
                "boundaries, so a second conversion after it has no defined meaning.",
        )
        append("Keep one of .$first and .$second.")
    },
)

/**
 * A naming conversion was applied to a string holding a capture beside other text, as in `"${capture("x")}Screen".pascalCase`.
 *
 * A conversion on a token makes a derived capture of that one token's value. On a string with
 * text around it, whether the literal text is meant to change as well is a guess katachi does
 * not make; the conversion goes on the capture alone, and the text stays as written.
 *
 * ## Example 1: catch a conversion applied around a capture
 * ```kt
 * shouldThrow<KatachiCaptureTransformOnMixedTextException> {
 *     architecture {
 *         "ui".group {
 *             "Screen".role {
 *                 layout { capture("x") / "${capture("x")}Screen".pascalCase.ktFile() }
 *             }
 *         }
 *     }.flattenLayout()
 * }.names shouldBe listOf("x")
 * ```
 *
 * @property text the string the conversion was applied to, each token shown as `${capture("name")}`.
 * @property names the names of the tokens in [text], in order.
 * @property transform the conversion that was applied, such as `"pascalCase"`.
 * @property declaredAt where the conversion was written.
 */
@ExperimentalKatachiApi
public class KatachiCaptureTransformOnMixedTextException internal constructor(
    public val text: String,
    public val names: List<String>,
    public val transform: String,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(".$transform at $declaredAt was applied to \"$text\", which holds text beside the capture.")
        appendLine(
            "A conversion applies to one capture's value; whether the literal text should change too " +
                "is not something katachi guesses.",
        )
        val fixed = names.fold(text) { acc, name -> acc.replace("\${capture(\"$name\")}", "\${capture(\"$name\").$transform}") }
        append("Apply it to the capture alone: \"$fixed\".")
    },
)

/**
 * A derived capture was written inside a module key: `":feature:${capture("f").camelCase}".module { }`.
 *
 * A module key names modules that exist, and a module's name is read from the project rather
 * than derived from another value. The key's `*` may still be named -- `capture("f")` -- and a
 * level of the path below it may then be derived from that name.
 *
 * ## Example 1: catch a conversion inside a module key
 * ```kt
 * shouldThrow<KatachiCaptureTransformInModuleKeyException> {
 *     architecture {
 *         "ui".group {
 *             "Screen".role {
 *                 layout { ":feature:${capture("f").camelCase}".module { "a.kt".file() } }
 *             }
 *         }
 *     }.flattenLayout()
 * }.name shouldBe "f"
 * ```
 *
 * @property name the capture's name.
 * @property transform the conversion that was written, such as `"camelCase"`.
 * @property moduleKey the key as it was written, each token shown as `${capture("name")}`.
 * @property declaredAt where the key was written.
 */
@ExperimentalKatachiApi
public class KatachiCaptureTransformInModuleKeyException internal constructor(
    public val name: String,
    public val transform: String,
    public val moduleKey: String,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("\"$moduleKey\" at $declaredAt converts a module wildcard.")
        appendLine("A module key names an existing module, and a module's name is not derived from another value.")
        append("Write capture(\"$name\") in the key, and put .$transform on a level of the path below it.")
    },
)

/**
 * Two `capture(...)` tokens sit next to each other with no literal between them, or one sits
 * next to a plain `*`.
 *
 * A capture matches like a plain `*`, so two of them touching would read as `**`, the "any depth"
 * wildcard, and there would be no way to tell where one capture's value ends and the next begins.
 * Put a literal character such as `-` or `/` between them.
 *
 * ## Example 1: catch two captures written back to back
 * ```kt
 * shouldThrow<KatachiAdjacentCaptureException> {
 *     architecture {
 *         "ui".group {
 *             "Screen".role {
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
 * A `capture(...)` token sits inside `{...}` or `[...]` of a layout key.
 *
 * A wildcard in there only takes part in matching and is never handed back as a value, so a name
 * given to it could never be read.
 *
 * ## Example 1: catch a capture written inside braces
 * ```kt
 * shouldThrow<KatachiCaptureInsideBracketsException> {
 *     architecture {
 *         "ui".group {
 *             "Screen".role {
 *                 layout { "{${capture("name")},Home}Screen".ktFile() }
 *             }
 *         }
 *     }.flattenLayout()
 * }.opening shouldBe '{'
 * ```
 *
 * @property key the segment the token was found in, with each token shown as `${capture("name")}`.
 * @property name the capture that sits inside the brackets.
 * @property opening `{` or `[`, whichever the capture sits in.
 * @property declaredAt where the segment was written.
 */
@ExperimentalKatachiApi
public class KatachiCaptureInsideBracketsException internal constructor(
    public val key: String,
    public val name: String,
    public val opening: Char,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("The capture \"$name\" in the layout key `$key` at $declaredAt sits inside `$opening...`.")
        appendLine(
            "A wildcard inside {...} or [...] only takes part in matching and is never read back, " +
                "so the capture would never get a value.",
        )
        append("Move the capture out, such as \"\${capture(\"name\")}{Screen,Route}\".")
    },
)

/**
 * `capture(...)` was used outside a layout key, such as in a `description`, a `fileConstraint` name or a template's text.
 *
 * `capture(...)` only has a meaning inside a layout key. To read a captured value in a template,
 * use `captureValue("name")` inside the `.template { }` block.
 *
 * ## Example 1: catch a capture read outside a layout key
 * ```kt
 * shouldThrow<KatachiStrayCaptureTokenException> {
 *     architecture {
 *         "ui".group {
 *             "Screen".role {
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

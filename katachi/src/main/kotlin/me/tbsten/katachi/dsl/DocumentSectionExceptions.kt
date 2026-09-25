package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * Thrown when `documentSection()` was given a blank heading.
 *
 * A section is written out as its heading and the Markdown below it, so a section with no heading
 * is a body attached to nothing. It is raised where the section is declared rather than where a
 * page is generated, because that is where the fix is.
 *
 * ## Example 1: catch a section declared without a heading
 * ```kt
 * val thrown = shouldThrow<KatachiUnnamedDocumentSectionException> { documentSection("  ") }
 * thrown.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
 * ```
 *
 * @see documentSection
 */
public class KatachiUnnamedDocumentSectionException internal constructor(
    /** Where `documentSection()` was called. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Blank document section heading declared at $declaredAt.")
        appendLine("A section is written out as its heading, so there is nothing to write it under.")
        append("""Give documentSection() the heading it should appear under, e.g. documentSection("Testing").""")
    },
)

/**
 * Thrown when a section was declared below more sections than Markdown has heading levels for.
 *
 * A top level section is written as `##` and every level below it adds one `#`, so the seventh
 * level would be written as `#######` — which Markdown reads as text, not as a heading. Rather
 * than generate a page whose structure silently stops matching the declaration, katachi refuses
 * the section where it is declared.
 *
 * ## Example 1: catch a section nested one level too deep
 * ```kt
 * var section = documentSection("Testing")
 * repeat(4) { section = section.documentSection("Deeper") }
 *
 * val thrown = shouldThrow<KatachiDocumentSectionTooDeepException> {
 *     section.documentSection("Too deep")
 * }
 * thrown.depth shouldBe 5
 * ```
 *
 * @see DocumentSection.documentSection
 */
public class KatachiDocumentSectionTooDeepException internal constructor(
    /** The heading of the section that was refused. */
    public val heading: String,
    /** How many levels below a top level section it would have sat. */
    public val depth: Int,
    /** The deepest level a section may sit at. */
    public val maxDepth: Int,
    /** Where `documentSection()` was called. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Document section "$heading" declared at $declaredAt would sit $depth levels """ +
                "below a top level section.",
        )
        appendLine(
            """A top level section is written as "##" and each level below it adds one "#", so """ +
                """this one would be written as "${"#".repeat(depth + 2)}", which Markdown does """ +
                "not read as a heading.",
        )
        append("Declare it at most $maxDepth levels down, or move the sections above it up.")
    },
)

package me.tbsten.katachi.docs

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.KatachiInternalException
import me.tbsten.katachi.docs.internal.writtenAt
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * Two declarations produce the same page of the generated documentation.
 *
 * A page is named after an identifier, and the DSL already refuses a group and a role of the
 * same name in the same container -- so the one way left to collide is a role named `README`,
 * which is the name the container's own page takes.
 *
 * It fails rather than warns, and it fails rather than letting the later page win. Writing one
 * page over another loses whatever the first one said, and a generator that quietly drops a role
 * is worse than one that stops: the reader of the output has no way to notice.
 *
 * ## Example 1: what raises it
 * ```kt
 * import me.tbsten.katachi.dsl.architecture
 *
 * // `domain/README.md` is the group's own page, and the role would be written to the same path.
 * val projectArchitecture = architecture {
 *     "domain".group {
 *         "README" { }
 *     }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class KatachiDocumentPathCollisionException internal constructor(
    /** The page both declarations produce, relative to the output root. */
    public val path: String,
    /** The declaration that reached the path second, as the message names it. */
    public val declaration: String,
    /** Where that declaration was written. */
    public val declaredAt: DeclarationSite,
    /** The declaration that reached the path first, as the message names it. */
    public val firstDeclaration: String,
    /**
     * Where that one was written, or [DeclarationSite.Unknown] for the documentation root, which
     * is not written anywhere.
     */
    public val firstDeclaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Two declarations produce the documentation page \"$path\": " +
                "$declaration${writtenAt(declaredAt)}.",
        )
        appendLine(
            "$firstDeclaration already produced it${writtenAt(firstDeclaredAt)}, and writing one " +
                "over the other would lose everything the first one says.",
        )
        append(
            "A page is named after the identifier, so rename one of them. " +
                "`title` is what the page reads as, and it can keep the name it has.",
        )
    },
)

/**
 * Two declarations produce pages that are one file on a case insensitive filesystem.
 *
 * Distinct from [KatachiDocumentPathCollisionException], which is about two declarations landing
 * on the very same path. Here the paths differ -- `UseCase.md` is not `usecase.md` -- and the
 * generated map holds both, so nothing is lost until the pages are written out. On macOS and
 * Windows one then silently overwrites the other, and on Linux both survive: the same definition
 * produces different documentation depending on who ran the generator.
 *
 * It fails rather than warns for the same reason the exact collision does. A page that is only
 * sometimes there is worse than one that is never there, because nobody looks for it.
 *
 * ## Example 1: what raises it
 * ```kt
 * import me.tbsten.katachi.dsl.architecture
 *
 * // `Readme.md` and the root's own `README.md` are one file on a case insensitive filesystem.
 * val projectArchitecture = architecture {
 *     "Readme" { }
 * }
 * ```
 *
 * @see KatachiDocumentPathCollisionException
 */
@ExperimentalKatachiApi
public class KatachiDocumentPathCaseCollisionException internal constructor(
    /** Each set of pages that are one file, in the order the pages were generated. */
    public val collisions: List<List<String>>,
    owners: Map<String, String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "${collisions.sumOf { it.size }} documentation pages differ only in case, so they " +
                "are one file on a case insensitive filesystem.",
        )
        for ((index, collision) in collisions.withIndex()) {
            // A blank line between sets, so that a reader can tell which pages are one file
            // with which -- four lines in a row would read as one clash of four.
            if (index > 0) appendLine()
            for (path in collision) appendLine("  \"$path\" -- ${owners[path].orEmpty()}")
        }
        appendLine("One page would be written over the other, and whatever it says would be lost.")
        append(
            "A page is named after the identifier, so rename one of them. " +
                "`title` is what the page reads as, and it can keep the name it has.",
        )
    },
)

/**
 * A relative link of the generated documentation that resolves to no generated page.
 *
 * ## Example 1: read what a failure was about
 * ```kt
 * val broken = shouldThrow<KatachiBrokenDocumentLinkException> { projectArchitecture.assert() }
 *
 * broken.links.single().source shouldBe "domain/UseCase.md"
 * broken.links.single().resolved shouldBe "data/Repository.md"
 * ```
 *
 * @see KatachiBrokenDocumentLinkException
 */
@ExperimentalKatachiApi
public class BrokenDocumentLink internal constructor(
    /** The generated page the link is written on, relative to the output root. */
    public val source: String,
    /** The text of the link, which is a `title` wherever katachi wrote the link itself. */
    public val text: String,
    /**
     * The link exactly as it was written, before it was followed from
     * [source][BrokenDocumentLink.source].
     */
    public val target: String,
    /**
     * Where [target][BrokenDocumentLink.target] lands when followed from
     * [source][BrokenDocumentLink.source], or `null` when it climbs above the output root and so
     * lands nowhere katachi could have generated anything.
     */
    public val resolved: String?,
)

/**
 * The generated documentation links somewhere it did not generate.
 *
 * A link between two generated pages is built from the identifiers of the definition and from
 * the distance between the two pages, both of which katachi decides -- so a link that does not
 * resolve is katachi's mistake and not a definition's. That is what makes this an internal
 * error rather than a declaration one, and why it stops generation instead of warning: the
 * output of a run that quietly links nowhere looks exactly like the output of a correct one.
 *
 * Every broken link of the whole run is reported at once. One at a time would mean generating,
 * reading one line, fixing, and generating again, for as many rounds as there are mistakes.
 *
 * ## Example 1: report it instead of treating it as a bad definition
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiBrokenDocumentLinkException) {
 *     println("katachi bug, ${cause.links.size} links point nowhere. Please report it.")
 * }
 * ```
 *
 * @see BrokenDocumentLink
 */
@ExperimentalKatachiApi
public class KatachiBrokenDocumentLinkException internal constructor(
    /** Every link that pointed nowhere, in the order the pages were generated. */
    public val links: List<BrokenDocumentLink>,
) : KatachiInternalException(
    message = buildString {
        appendLine(
            if (links.size == 1) {
                "1 documentation link points at a page katachi did not generate."
            } else {
                "${links.size} documentation links point at pages katachi did not generate."
            },
        )
        for (link in links) {
            appendLine("  ${link.source}: [${link.text}](${link.target}) -> ${link.landing()}")
        }
        appendLine(
            "Every link katachi writes between two generated pages is built from the identifiers " +
                "of the definition, so a link that does not resolve is a bug in katachi. " +
                "Please report it at https://github.com/TBSten/katachi/issues.",
        )
        append(
            "A relative link written by hand in a role's `description` reaches here too, " +
                "because that text is copied as it was written: point it at a page katachi " +
                "generates, or make it absolute.",
        )
    },
)

/** What the link turned out to mean, in the words the message needs. */
private fun BrokenDocumentLink.landing(): String = resolved ?: "outside the output root"

package me.tbsten.katachi.docs

import me.tbsten.katachi.KatachiDeclarationException
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
 * ` at File.kt:42`, or nothing at all.
 *
 * The documentation root is produced by no line of any definition, so there is nothing to point
 * a reader at -- and `at <unknown>:-1` would send them looking for one.
 */
private fun writtenAt(site: DeclarationSite): String =
    if (site == DeclarationSite.Unknown) "" else " at $site"

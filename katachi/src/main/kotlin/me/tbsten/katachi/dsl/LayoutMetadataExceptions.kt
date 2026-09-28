package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * `":".module { }.metadata { }` was called: the root project has no directory of its own for
 * a value to attach to.
 *
 * Every other `.metadata { }` call attaches to the leaf a key returned -- a file, a plain
 * directory, or the directory a module key opened. `":".module { }` opens no directory: its
 * block declares straight into the project root, which every other block of the same
 * `layout { }` shares. Writing a value there silently, on the layout root itself, would
 * describe everything else declared in the same `layout { }` as well as whatever the module
 * block itself declared, so it is refused instead of guessed at.
 *
 * ## Example 1: catch metadata attached to the root project module key
 * ```kt
 * val Owner: MetadataKey<String> = metadata()
 * var MetadataScope.owner: String? by Owner
 *
 * shouldThrow<KatachiMetadataWithoutEntryException> {
 *     layout { ":".module { }.metadata { owner = "mobile" } }
 * }
 * ```
 *
 * @property modulePath always `":"`: the only module key with no directory of its own.
 */
public class KatachiMetadataWithoutEntryException internal constructor(
    public val modulePath: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""`"$modulePath".module { }.metadata { }` has nowhere to attach: the root project has no directory of its own.""")
        appendLine(
            "A `\":\".module { }` block declares straight into the project root, which every other " +
                "block of the same layout { } shares, so a value written here would describe " +
                "everything else declared in the same layout { } as well.",
        )
        append(
            "Attach the metadata to one of the files or directories the block itself declares " +
                "instead, such as `\"README.md\".file().metadata { }`.",
        )
    },
)

/**
 * Two declarations of the same path attached different values under the same metadata key.
 *
 * A role that declares one path twice -- two `/` chains sharing a prefix, or the same key
 * written in two `layout { }` blocks -- folds into one [LayoutEntry], so its metadata has to
 * fold into one value too. When both declarations wrote the same value under a key, it is kept
 * once and nothing is reported; this is only raised when they disagree.
 *
 * ## Example 1: catch two declarations of one path disagreeing about a metadata value
 * ```kt
 * val Owner: MetadataKey<String> = metadata()
 * var MetadataScope.owner: String? by Owner
 *
 * shouldThrow<KatachiConflictingLayoutMetadataException> {
 *     architecture {
 *         "legacy".group {
 *             "Dao" {
 *                 layout {
 *                     "legacy" / "*Dao".ktFile().metadata { owner = "team-a" }
 *                     "legacy" / "*Dao".ktFile().metadata { owner = "team-b" }
 *                 }
 *             }
 *         }
 *     }.flattenLayout()
 * }.key shouldBe "MetadataKey<String>"
 * ```
 *
 * @property role the role whose declarations disagreed, qualified.
 * @property path the path both declarations claim.
 * @property key the metadata key they disagree on, as printed by [MetadataKey.toString].
 * @property firstDeclaredAt where the entry's first declaration was written.
 * @property declaredAt where the disagreeing second declaration was written.
 */
public class KatachiConflictingLayoutMetadataException internal constructor(
    public val role: String,
    public val path: String,
    public val key: String,
    public val firstDeclaredAt: DeclarationSite,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Declarations of "$path" in role "$role", at $firstDeclaredAt and $declaredAt, """ +
                "wrote different values under $key.",
        )
        appendLine(
            "The two declarations fold into one entry, so their metadata has to fold into one " +
                "value too -- which is fine when they agree, and ambiguous when they do not.",
        )
        append("Make the two `.metadata { }` calls agree, or write the value on one of them only.")
    },
)

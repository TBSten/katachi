package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.MetadataBuilder

/**
 * Receiver of `.metadata { }` on one `layout { }` declaration: a file, a directory, or a module.
 *
 * The same extension properties a role or a group carries metadata through — `var
 * MetadataScope.owner by Owner` — can be written here unchanged, because this is a
 * [MetadataScope] too. What differs is what the value is attached to: a role's metadata
 * describes the role, this describes one path in its layout.
 *
 * Metadata attached this way lands on the **leaf** of whatever was declared — the file, or the
 * innermost directory of a `/` chain — and is not inherited: a value on a directory says
 * nothing about what is beneath it. It changes nothing about the flattened layout or the
 * check: the flattened entry's path, kind, `required` and `description` are the same with or
 * without it, and a processor reads it back off [LayoutEntry] with the same `[key]` syntax a
 * role's metadata is read with.
 *
 * ## Example 1: attach metadata to a declared file and read it back off the flattened entry
 * ```kt
 * val Obsolete: MetadataKey<Boolean> = metadata()
 * var MetadataScope.obsolete: Boolean? by Obsolete
 *
 * val arch = architecture {
 *     "legacy".group {
 *         "Dao" {
 *             layout { "legacy" / "*Dao".ktFile().metadata { obsolete = true } }
 *         }
 *     }
 * }
 *
 * arch.flattenLayout().single { it.kind == LayoutEntryKind.File }[Obsolete] shouldBe true
 * ```
 */
@KatachiDsl
public sealed interface LayoutDeclarationScope : MetadataScope

/** Backs [LayoutDeclarationScope]. One instance per call to `.metadata { }`, wrapping the node's own builder. */
internal class LayoutDeclarationScopeImpl(val metadata: MetadataBuilder) : LayoutDeclarationScope

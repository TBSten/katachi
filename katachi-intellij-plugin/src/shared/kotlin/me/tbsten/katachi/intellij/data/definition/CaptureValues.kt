package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel

/** What `capture("name")` took from a file's path. */
internal data class CapturedValue(val name: String, val value: String)

/**
 * The plain captures of a file entry's pattern that [path] gives a value, in the order the pattern names them: `User` for
 * `capture("name")` on the `*Service.kt` of `UserService.kt`. A derived capture (`capture("x").pascalCase`), a capture on a
 * directory entry and a path the pattern does not match give nothing; the first value of a name is the one kept.
 *
 * ```kotlin
 * val values = CaptureValues.of(entry, "src/UserService.kt")   // [CapturedValue("name", "User")]
 * ```
 */
internal object CaptureValues {
    fun of(entry: LayoutEntryModel, path: String): List<CapturedValue> {
        if (entry.kind != LayoutEntryKind.File) return emptyList()
        val glob = try {
            KatachiGlob.compile(entry.pattern)
        } catch (_: KatachiGlobSyntaxException) {
            return emptyList()
        }
        val groups = glob.matchGroups(path) ?: return emptyList()
        return entry.captures.filter { it.transform == null }
            .mapNotNull { capture -> glob.singleGroupValue(groups, capture.segmentIndex, capture.wildcardIndex)?.takeIf { it.isNotEmpty() }?.let { CapturedValue(capture.name, it) } }
            .distinctBy { it.name }
    }
}

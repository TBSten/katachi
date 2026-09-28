package me.tbsten.katachi.intellij.uitest.dialog

import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.dialog.TargetNotice
import me.tbsten.katachi.intellij.presentation.linkKeyOf
import me.tbsten.katachi.intellij.uitest.pbt.DiskKind
import me.tbsten.katachi.intellij.uitest.pbt.placement.formsOf

/*
 * The dialog's rules written apart from the ViewModel, from the pattern's text alone: what the
 * sample path reads, what is carried over when the template changes, and what each thing on disk
 * makes the dialog say. The machine compares the real ViewModel with these.
 */

private val TOKEN = Regex("""\$\{([^}]*)}|<([^>/]*)>""")

/** The names a pattern captures (`${x}`), in order, and the names it derives from (`<x>`). */
internal fun captureNamesOf(pattern: String): List<String> = TOKEN.findAll(pattern).map { it.groupValues[1] }.filter { it.isNotEmpty() }.distinct().toList()

internal fun derivedNamesOf(pattern: String): List<String> = TOKEN.findAll(pattern).map { it.groupValues[2] }.filter { it.isNotEmpty() }.distinct().toList()

/**
 * The file [pattern] names when its captures take [values]: a `<x>` is one of the spellings katachi can
 * make of the value of `x`, the one [pick] chooses.
 */
internal fun filledSegmentsOf(pattern: String, values: Map<String, String>, pick: Int): List<String> = pattern.split('/').map { segment ->
    TOKEN.replace(segment) { match ->
        val capture = match.groupValues[1]
        if (capture.isNotEmpty()) values.getValue(capture) else formsOf(values.getValue(match.groupValues[2])).let { it[pick % it.size] }
    }
}

/** Values for the names of [pattern]: a name a `<x>` reads is a module's, from the values katachi has spellings for. */
internal fun valuesFor(pattern: String, salt: Int, moduleValues: List<String>, otherValues: List<String>): Map<String, String> {
    val derived = derivedNamesOf(pattern).toSet()
    return captureNamesOf(pattern).withIndex().associate { (i, name) ->
        name to if (name in derived) moduleValues[(salt + i) % moduleValues.size] else otherValues[(salt / 7 + i * 3) % otherValues.size]
    }
}

/**
 * The sample path the inputs make (decision 21): every `${x}` is its input, left as `${x}` while
 * blank; a segment with a `<x>` is the origin's own segment as long as everything it reads still has
 * the value the origin decided, and stays `<x>` otherwise.
 */
internal fun expectedTargetPath(pattern: String, input: (String) -> String?, seeds: Map<String, String>, originSegments: List<String>): String =
    pattern.split('/').mapIndexed { i, segment ->
        val tokens = TOKEN.findAll(segment).toList()
        val derived = tokens.any { it.groupValues[2].isNotEmpty() }
        val names = tokens.map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        val fromOrigin = originSegments.getOrNull(i)
        if (fromOrigin != null && derived && names.all { name -> seeds[name]?.let { it == input(name) } == true }) {
            fromOrigin
        } else {
            TOKEN.replace(segment) { match -> match.groupValues[1].takeIf { it.isNotEmpty() }?.let(input)?.takeIf { it.isNotBlank() } ?: match.value }
        }
    }.joinToString("/")

/** `${x}` written the way the New menu writes a capture that is left: `<x>`. */
internal fun asMenuSpelling(path: String): String = TOKEN.replace(path) { match ->
    if (match.groupValues[1].isNotEmpty()) "<${match.groupValues[1]}>" else match.value
}

/** Whether a `<x>` is still in [path]: only katachi knows the file then. */
internal fun hasDerivedLeft(path: String): Boolean = TOKEN.findAll(path).any { it.groupValues[2].isNotEmpty() }

/** What the pre-check answers for each thing on disk (the fake stands where E3's `checkTarget` will). */
internal fun answerOf(kind: DiskKind): TargetState = when (kind) {
    DiskKind.Missing -> TargetState.Absent
    DiskKind.EmptyFile -> TargetState.Empty
    DiskKind.Provisional -> TargetState.OwnProvisional
    DiskKind.Content -> TargetState.HasContent
}

/** What the dialog must tell for each thing on disk (issue 6): the three notices. */
internal fun noticeOf(kind: DiskKind): TargetNotice = when (kind) {
    DiskKind.Missing -> TargetNotice.WillCreate
    DiskKind.EmptyFile, DiskKind.Provisional -> TargetNotice.WillOverwriteEmpty
    DiskKind.Content -> TargetNotice.CannotOverwrite
}

/**
 * What [next] holds for each of its fields after the user switched from [previous] holding [previousValues]
 * (decision 4): a capture the origin decides ([seeds]) takes that value; a field of the same name, kind
 * and type as one of [previous] keeps its value; the rest is empty (`null`).
 */
internal fun expectedCarried(
    previous: ModuleTemplate?,
    previousValues: Map<String, String?>,
    next: ModuleTemplate,
    seeds: Map<String, String>,
): Map<String, String?> {
    val previousKeys = previous?.template?.detail?.let { detail -> allParametersOf(detail).associate { it.name to linkKeyOf(it) } }.orEmpty()
    val detail = next.template.detail ?: return emptyMap()
    return allParametersOf(detail).associate { parameter ->
        val own = previousValues[parameter.name]
        val key = linkKeyOf(parameter)
        parameter.name to when {
            parameter.name in seeds -> seeds.getValue(parameter.name)
            own != null && key != null && previousKeys[parameter.name] == key -> own
            else -> null
        }
    }
}

package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.CaptureMismatch
import me.tbsten.katachi.dsl.internal.CaptureTransform

internal class CaptureTransformFix(val name: String, val transform: String, val level: Int)

internal fun CaptureMismatch.transformFixes(): List<CaptureTransformFix> = occurrences.mapNotNull { occurrence ->
    val held = occurrence.expected
    if (occurrence.transform != null || held == null || held == occurrence.value) return@mapNotNull null
    OFFERED_TRANSFORMS.firstOrNull { it.apply.invoke(held) == occurrence.value }
        ?.let { CaptureTransformFix(occurrence.name, it.spelling, occurrence.level) }
}

// Several conversions can spell a value alike (`home` is the camelCase, kebabCase, snakeCase and flatCase of `Home`):
// flatCase goes first, as the spelling of a package directory.
private val OFFERED_TRANSFORMS: List<CaptureTransform> =
    listOf(CaptureTransform.FlatCase) + CaptureTransform.entries.filter { it != CaptureTransform.FlatCase }

// A captured value never holds a `/`, so the two paths have the same levels and differ only where the move renames one.
internal fun CaptureMismatch.movesIntoNonPackageDirectory(): Boolean {
    val expected = expectedPath ?: return false
    val from = path.split('/')
    val to = expected.split('/')
    if (from.size != to.size) return false
    val sourceRoot = (2 until to.size).firstOrNull { to[it] in SOURCE_ROOTS && to[it - 2] == "src" } ?: return false
    // Below `src/<source set>/kotlin`, every level but the file itself is a package directory.
    return (sourceRoot + 1 until to.lastIndex).any { to[it] != from[it] && !isPackageName(to[it]) }
}

// `java` as well: an Android module keeps its Kotlin sources there too.
private val SOURCE_ROOTS: Set<String> = setOf("kotlin", "java")

// What a `package` line takes without backticks, keywords aside.
private fun isPackageName(name: String): Boolean =
    name.isNotEmpty() && (name.first().isLetter() || name.first() == '_') && name.all { it.isLetterOrDigit() || it == '_' }

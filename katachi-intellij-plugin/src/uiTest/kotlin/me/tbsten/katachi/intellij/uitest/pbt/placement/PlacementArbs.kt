package me.tbsten.katachi.intellij.uitest.pbt.placement

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.patternTemplate
import java.nio.file.Path

/*
 * Patterns built from structure for the placement index: fixed segments, captures alone in a
 * segment, partial captures (`Home${name}Test.kt`), two captures around a separator, the capture of
 * a module and the `<x>` derived from it, and an `<x>` whose module capture is not in the pattern.
 * The oracle here fills a pattern with values and slices it into directories, written apart from
 * the index (it never parses a pattern: it is built from the parts).
 */

internal sealed interface Part {
    data class Lit(val text: String) : Part
    data class Cap(val name: String, val module: Boolean = false) : Part
    data class Der(val name: String) : Part
}

internal data class Seg(val parts: List<Part>) {
    val text: String get() = parts.joinToString("") { part ->
        when (part) {
            is Part.Lit -> part.text
            is Part.Cap -> "\${${part.name}}"
            is Part.Der -> "<${part.name}>"
        }
    }

    /** The New menu's spelling: captures written `<name>` like the derived parts. */
    val shown: String get() = parts.joinToString("") { part ->
        when (part) {
            is Part.Lit -> part.text
            is Part.Cap -> "<${part.name}>"
            is Part.Der -> "<${part.name}>"
        }
    }

    val caps: List<Part.Cap> get() = parts.filterIsInstance<Part.Cap>()
    val hasLiteral: Boolean get() = parts.any { it is Part.Lit }
    val hasDerived: Boolean get() = parts.any { it is Part.Der }
    val isSingleCapture: Boolean get() = parts.count { it !is Part.Lit } == 1 && caps.size == 1
}

internal data class PatternSpec(val segs: List<Seg>) {
    val text: String get() = segs.joinToString("/") { it.text }
    val caps: List<Part.Cap> get() = segs.flatMap { it.caps }
    val moduleNames: Set<String> get() = caps.filter { it.module }.map { it.name }.toSet()

    fun template(role: String): TemplateModel =
        patternTemplate(role, text, captureNames = caps.map { it.name }, moduleCaptures = moduleNames)

    override fun toString(): String = text
}

/** Values for the names of a pattern, and which spelling each `<x>` takes. */
internal data class Filling(val values: Map<String, String>, val pick: Int) {
    fun segmentText(seg: Seg, moduleNames: Set<String>): String = seg.parts.joinToString("") { part ->
        when (part) {
            is Part.Lit -> part.text
            is Part.Cap -> values.getValue(part.name)
            is Part.Der -> values.getValue(part.name).let { source ->
                if (part.name in moduleNames) formsOf(source).let { it[pick % it.size] } else source
            }
        }
    }
}

/** What katachi can write for the module capture value [source] (the oracle's own table, not the index's). */
internal fun formsOf(source: String): List<String> = when (source) {
    "home" -> listOf("home", "Home")
    "settings" -> listOf("settings", "Settings")
    "user" -> listOf("user", "User")
    "fuga-piyo" -> listOf("fuga-piyo", "FugaPiyo", "fugaPiyo", "fugapiyo", "fuga_piyo", "FUGA_PIYO")
    else -> listOf(source)
}

internal val moduleValues = listOf("home", "settings", "user", "fuga-piyo", "日本語")
private val literals = listOf("src", "main", "feature", "kotlin", "日本語", "com", "example", "v2", "app-ui")
private val prefixes = listOf("Home", "User", "テスト", "x")
private val reserved = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
private val safeChars = ('a'..'z').toList() + ('A'..'Z').toList() + ('0'..'9').toList() + "あいうえおカタカナ漢字日本語".toList()
private val separators = listOf('-', '_', '.')

internal val patternArb: Arb<PatternSpec> = arbitrary {
    val dirs = Arb.int(0..4).bind()
    val segs = mutableListOf<Seg>()
    var counter = 0
    var module: String? = null
    var unknownUsed = false
    fun nextName() = "c${counter++}"
    repeat(dirs) {
        val kinds = mutableListOf("lit", "cap", "cap", "prefixCap")
        if (module == null) kinds += "module"
        else kinds += "der"
        if (!unknownUsed) kinds += "unknown"
        segs += when (Arb.element(kinds).bind()) {
            "lit" -> Seg(listOf(Part.Lit(Arb.element(literals).bind())))
            "cap" -> Seg(listOf(Part.Cap(nextName())))
            "prefixCap" -> Seg(listOf(Part.Lit(Arb.element(prefixes).bind()), Part.Cap(nextName()), Part.Lit("Dir")))
            "module" -> Seg(listOf(Part.Cap("m", module = true))).also { module = "m" }
            "der" -> Seg(listOf(Part.Der("m")))
            else -> Seg(listOf(Part.Der("u"))).also { unknownUsed = true }
        }
    }
    val fileKinds = mutableListOf("fixed", "capExt", "prefixCap", "twoCaps")
    if (module != null) fileKinds += "derCap"
    segs += when (Arb.element(fileKinds).bind()) {
        "fixed" -> Seg(listOf(Part.Lit("Foo.kt")))
        "capExt" -> Seg(listOf(Part.Cap(nextName()), Part.Lit(".kt")))
        "prefixCap" -> Seg(listOf(Part.Lit(Arb.element(prefixes).bind()), Part.Cap(nextName()), Part.Lit("Screen.kt")))
        "twoCaps" -> Seg(listOf(Part.Cap(nextName()), Part.Lit("-"), Part.Cap(nextName()), Part.Lit(".kt")))
        else -> Seg(listOf(Part.Der("m"), Part.Cap(nextName()), Part.Lit(".kt")))
    }
    PatternSpec(segs)
}

private fun validValue(text: String): Boolean =
    text.isNotEmpty() && !text.endsWith('.') && text.substringBefore('.').uppercase() !in reserved

/** One or more letters, digits or Japanese characters, or an edge value; never a name the OS reserves. */
internal val safeValueArb: Arb<String> = arbitrary {
    if (Arb.int(0..5).bind() == 0) {
        Arb.element("a", "Z", "0", "日本語", "Ab1", "ホーム").bind()
    } else {
        Arb.list(Arb.element(safeChars), 1..6).bind().joinToString("")
    }
}.filter(::validValue)

/** [safeValueArb] that may hold a `-`, `_` or `.` inside: the boundary of what a capture value can be. */
internal val boundaryValueArb: Arb<String> = arbitrary {
    val left = safeValueArb.bind()
    if (Arb.boolean().bind()) left + Arb.element(separators).bind() + safeValueArb.bind() else left
}.filter(::validValue)

internal fun fillingArb(spec: PatternSpec): Arb<Filling> = arbitrary {
    val values = LinkedHashMap<String, String>()
    for (seg in spec.segs) {
        for (part in seg.parts) {
            when (part) {
                is Part.Cap -> values.getOrPut(part.name) {
                    when {
                        part.module -> Arb.element(moduleValues).bind()
                        seg.isSingleCapture -> boundaryValueArb.bind()
                        else -> safeValueArb.bind()
                    }
                }
                is Part.Der -> if (part.name !in spec.moduleNames) values.getOrPut(part.name) { safeValueArb.bind() }
                is Part.Lit -> Unit
            }
        }
    }
    Filling(values, Arb.int(0..5).bind())
}

/** A definition's root: definitions never share one. */
internal fun rootOf(definition: Int): Path = ROOT.resolve("r$definition")

internal fun moduleOf(definition: Int) = module(":d$definition", rootOf(definition), "r$definition")

internal fun Path.resolveSegments(segments: List<String>): Path = segments.fold(this) { path, segment -> path.resolve(segment) }

/** A template that looks like [spec]'s but whose preview failed. */
internal fun failedPreview(spec: PatternSpec, role: String): TemplateModel =
    spec.template(role).let { it.copy(summary = it.summary.copy(conflict = true)) }

/** A template that looks like [spec]'s but has a parameter kind this plugin does not know. */
internal fun unknownKind(spec: PatternSpec, role: String): TemplateModel = spec.template(role).let { model ->
    val detail = checkNotNull(model.detail)
    model.copy(detail = detail.copy(parameters = listOf(ParameterModel.UnknownParam("x", "Future", null, true, "", "FutureParameter"))))
}

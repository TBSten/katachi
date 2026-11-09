package me.tbsten.katachi.script.internal

import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * The guide (index.md) cut into sections. A section starts at `## N.`, `## 6-A.` to `## 6-D.`
 * or `### N-M.`; other headings belong to the numbered section before them, and nothing inside a
 * code fence counts. A chapter with numbered sections keeps only its preamble. The kit's `GUIDE_AWK`.
 */
internal class Guide(text: String) {
    class Section(val id: String, val title: String, val level: Int, val start: Int) {
        var stop: Int = start
        val children: MutableList<String> = mutableListOf()
    }

    private val lines: List<String> = text.lines().let { if (it.isNotEmpty() && it.last().isEmpty()) it.dropLast(1) else it }

    val sections: List<Section>

    init {
        val found = mutableListOf<Section>()
        var fence = false
        val heading = Regex("^(## [0-9]+\\. |## 6-[A-D]\\. |### [0-9]+-[0-9]+\\. )")
        lines.forEachIndexed { index, line ->
            if (line.startsWith("```")) fence = !fence
            if (!fence && heading.containsMatchIn(line)) {
                val id = line.trim().split(Regex("\\s+"))[1].removeSuffix(".")
                val title = line.replaceFirst(Regex("^#+ [^ ]+ "), "")
                found += Section(id, title, if (line.startsWith("### ")) 3 else 2, index)
            }
        }
        found.forEachIndexed { i, section ->
            var end = lines.lastIndex
            val next = found.getOrNull(i + 1)
            if (next != null) {
                end = next.start - 1
                if (section.level == 2 && next.level == 3) {
                    var j = i + 1
                    while (j < found.size && found[j].level == 3) {
                        section.children += found[j].id
                        j++
                    }
                }
            }
            while (end > section.start && lines[end].isBlank()) end--
            section.stop = end
        }
        sections = found
    }

    val ids: List<String> get() = sections.map { it.id }

    fun section(id: String): Section? = sections.firstOrNull { it.id == id }

    fun body(section: Section): List<String> = lines.subList(section.start, section.stop + 1)

    fun next(id: String): String? {
        val index = sections.indexOfFirst { it.id == id }
        return if (index < 0) null else sections.getOrNull(index + 1)?.id
    }

    companion object {
        fun bundled(lang: Lang): Guide = Guide(Bundle.text(lang, Bundle.GUIDE))
    }
}

/** 6-1 to 6-4 in the check list are 6-A to 6-D in the guide. */
internal fun guideSectionId(id: String): String = when (id) {
    "6-1" -> "6-A"
    "6-2" -> "6-B"
    "6-3" -> "6-C"
    "6-4" -> "6-D"
    else -> id
}

private fun ScriptContext.printGuideToc(guide: Guide) {
    for (section in guide.sections) {
        out("${section.id}\t${section.title}\t${section.stop - section.start + 1}")
    }
    say(Messages.Guide.dCheckListGuide)
}

/**
 * `guide [<section id>] [--refresh] [--lang <en|ja>]`. The guide ships with the command, so this
 * works before `init` too; `--lang` picks its language there. `--refresh` puts the bundled copy
 * back into `tmp/cache/index.md`, the file `init` left for people to open.
 */
internal fun ScriptContext.runGuide(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("guide"), args)
    if (parsed.positionals.size > 1) {
        die(Messages.Guide.giveOnlyOneSection(parsed.positionals[0], parsed.positionals[1]))
    }
    val id: String? = parsed.positionals.firstOrNull()
    val refresh = parsed.has("--refresh")
    val langOption = parsed.value("--lang")
    langOption?.let(::setLangOption)

    val guide = Guide.bundled(lang)
    if (refresh) {
        findWorkdir()?.let { workdir ->
            val cached = workdir.resolve("tmp/cache/index.md")
            cached.parent.createDirectories()
            cached.writeText(Bundle.text(lang, Bundle.GUIDE))
            recordInManifest(workdir.resolve("tmp/cache/MANIFEST"), Bundle.origin(lang, Bundle.GUIDE), cached)
        }
    }
    if (id == null) {
        printGuideToc(guide)
        return 0
    }
    val wanted = guideSectionId(id)
    val section = guide.section(wanted)
    if (section == null) {
        warn(Messages.Guide.unknownSectionIdIds(wanted))
        printGuideToc(guide)
        return 1
    }
    guide.body(section).forEach(out)
    if (section.children.isNotEmpty()) {
        out("")
        out(pick(Messages.Guide.sections(section.children.joinToString(", "))))
    }
    guide.next(wanted)?.let { next ->
        // An explicit --lang is carried into the next command: before init nothing is recorded, and without it the next
        // section comes out in the default ja, so an agent that started in en reads on in Japanese and runs init --lang ja.
        val nextCommand = "$AGENT_COMMAND guide $next" + if (langOption != null) " --lang ${lang.code}" else ""
        say("")
        say(Messages.Guide.next(nextCommand))
    }
    return 0
}

/** `--lang` of init / doctor / guide. The kit's `set_lang_option`. */
internal fun ScriptContext.setLangOption(value: String) {
    lang = Lang.of(value) ?: die(Messages.Guide.langMustEnJa(value))
    langExplicit = true
}

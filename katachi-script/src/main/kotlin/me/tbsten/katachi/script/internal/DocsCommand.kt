package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * The topics `docs --topic` cuts out of llms-full.txt, with the page heading of each. A page can be
 * headed `# <title>` or `# [AI-generated] <title>`. When a heading changes, change it here too
 * (`docs --topic <name>` stops with "no heading", so it does not go unnoticed). The kit's `docs_topic_title`.
 */
private val DOCS_TOPICS: List<Pair<String, String>> = listOf(
    "motivation" to "Motivation and where katachi fits",
    "first-architecture" to "Your first architecture definition",
    "faq" to "FAQ",
    "comparison" to "Comparison with other tools",
    "basic-api" to "Basic API",
    "role" to "Role",
    "layout" to "Layout",
    "kt-file-content" to "ktFileContent",
    "document-generation" to "Document generation",
    "template" to "Generating code from a template",
    "baseline" to "Temporarily disable existing violations with baseline",
    "processor" to "ArchitectureProcessor and customizing it",
    "recipes" to "Recipes",
    "android-three-layer" to "Three-layer architecture following the Android architecture guide",
    "gradle" to "Gradle",
    "ai-agent" to "AI Agent",
    "ktlint" to "ktlint",
    "detekt" to "detekt",
    "git" to "Git",
    "github" to "GitHub",
    "roadmap" to "Roadmap",
)

private fun docsTopicList(): String = DOCS_TOPICS.joinToString("\n") { (name, title) -> "$name\t$title" }

/**
 * The page headed [title] in llms-full.txt: from its `# ` heading to the next one, headings inside
 * code fences not counted. The old versions' copies follow later with the same headings, so stopping
 * at the next heading keeps them out. The kit's `extract_docs_topic`.
 */
private fun extractDocsTopic(full: Path, title: String): List<String> {
    val out = mutableListOf<String>()
    var fence = false
    var inside = false
    for (line in full.readLines()) {
        if (line.startsWith("```")) fence = !fence
        if (!fence && line.startsWith("# ")) {
            if (inside) break
            if (line == "# $title" || line == "# [AI-generated] $title") inside = true
        }
        if (inside) out += line
    }
    return out
}

/**
 * `docs [--small | --topic <name> | --topics | --api [module]] [--refresh]`: fetches the docs into
 * the working directory and prints the path (stdout is the path only, for programs; the source goes
 * to stderr). The kit's `cmd_docs`.
 */
internal fun ScriptContext.runDocs(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("docs"), args)
    parsed.positionals.firstOrNull()?.let { die("docs: 知らないオプションです: $it", "docs: unknown option: $it") }
    if (parsed.has("--topics")) {
        out(docsTopicList())
        return 0
    }
    val refresh = parsed.has("--refresh")
    val workdir = requireWorkdir()
    val cache = workdir.resolve("tmp/cache")
    val manifest = cache.resolve("MANIFEST")
    val base = docsBase()

    fun fetch(name: String, url: String): Pair<Path, Boolean> {
        val dest = cache.resolve(name)
        val cached = dest.exists() && dest.fileSize() > 0 && !refresh
        fetchOnce(url, dest, refresh, manifest)
        if (cached) {
            err(pick("取得元: $url（取得済みのものを使いました。取り直すなら --refresh）", "Source: $url (used the copy already fetched; add --refresh to fetch it again)"))
        } else {
            err(pick("取得元: $url", "Source: $url"))
        }
        return dest to cached
    }

    val topic = parsed.value("--topic")
    if (topic != null) {
        val title = DOCS_TOPICS.firstOrNull { it.first == topic }?.second
            ?: die("docs --topic: 知らない名前です: $topic 。使える名前:\n${docsTopicList()}", "docs --topic: unknown topic: $topic . Available topics:\n${docsTopicList()}")
        val (full, cached) = fetch("llms-full.txt", "$base/llms-full.txt")
        val dest = cache.resolve("topics/$topic.md")
        dest.parent.createDirectories()
        val lines = extractDocsTopic(full, title)
        if (lines.isEmpty() && cached) {
            dest.deleteIfExists()
            die(
                "${fileUri(full)} に見出し「$title」がありません。取得済みの全文が古い可能性があります。--refresh を付けて取り直してください（$AGENT_COMMAND docs --topic $topic --refresh）。",
                "No heading \"$title\" in ${fileUri(full)} . The copy fetched earlier may be old. Fetch it again with --refresh ($AGENT_COMMAND docs --topic $topic --refresh).",
            )
        }
        if (lines.isEmpty()) {
            dest.deleteIfExists()
            die(
                "docs --topic $topic: ガイドの全文に見出し「$title」がありません。katachi コマンドの不具合です（このコマンドが知っている見出しが、ガイドとずれています）。\n" +
                    "    ユーザにこの出力を伝えたうえで、全文 ${fileUri(full)} から、この話題にあたるページ（行頭の \"# \" の見出しから次の \"# \" の見出しの手前まで）だけを読んで進めてください。",
                "docs --topic $topic: the full guide has no heading \"$title\". This is a bug in the katachi command (the headings it knows no longer match the guide).\n" +
                    "    Tell the user about this output, then read only the page for this topic (from its line-leading \"# \" heading up to the next one) in the full text ${fileUri(full)} and go on.",
            )
        }
        // A page linking to /0.2/ and the like is an old version's copy: the current heading changed and the copy was picked up.
        if (lines.any { Regex("]\\(/[^)/]+/[0-9]+\\.[0-9]+/").containsMatchIn(it) }) {
            dest.deleteIfExists()
            die(
                "docs --topic $topic: 見出し「$title」で切り出したページが、古い版（/0.2/ など）の写しでした。katachi コマンドの不具合です（このコマンドが知っている見出しが、ガイドの現行ページとずれています）。\n" +
                    "    ユーザにこの出力を伝えたうえで、全文 ${fileUri(full)} の前半（現行の版）から、この話題にあたるページだけを読んで進めてください。",
                "docs --topic $topic: the page cut by heading \"$title\" is a copy of an older version (such as /0.2/). This is a bug in the katachi command (the headings it knows no longer match the guide's current pages).\n" +
                    "    Tell the user about this output, then read only the page for this topic in the first part (the current version) of the full text ${fileUri(full)} and go on.",
            )
        }
        dest.writeText(lines.joinToString("\n", postfix = "\n"))
        err(pick("${lines.size} 行（llms-full.txt の「$title」）", "${lines.size} lines (\"$title\" in llms-full.txt)"))
        out(dest.toString())
        return 0
    }

    val (name, url) = when {
        parsed.has("--api") -> parsed.value("--api")?.let { "api-$it-llms-full.txt" to "$base/api-docs/$it/llms-full.txt" } ?: ("api-llms.txt" to "$base/api-docs/llms.txt")
        parsed.has("--small") -> "llms-small.txt" to "$base/llms-small.txt"
        else -> "llms-full.txt" to "$base/llms-full.txt"
    }
    val (dest, _) = fetch(name, url)
    out(dest.toString())
    return 0
}

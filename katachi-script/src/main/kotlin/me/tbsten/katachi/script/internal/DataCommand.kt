package me.tbsten.katachi.script.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlinx.serialization.json.JsonObject

/** `data get | set | merge`, the kit's `cmd_data`, with the same arguments, messages and files (`.bak`, lock). */
internal fun ScriptContext.runData(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("data"), args)
    val action = parsed.subcommand ?: "get"
    val target = parsed.positionals.getOrNull(0)?.takeIf { it.isNotEmpty() }
        ?: die(
            "data $action: 対象を指定してください（check-list / report / ファイルパス）。",
            "data $action: specify the target (check-list / report / a file path).",
        )
    val html = resolveDataTarget(target)
    val htmlUri = fileUri(html)
    if (!html.isRegularFile()) die("ファイルがありません: $htmlUri", "File not found: $htmlUri")

    var json: Path? = null
    var key = ""
    if (action != "get") {
        val jsonArg = parsed.positionals.getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: die("data $action: JSON ファイルを指定してください。", "data $action: specify the JSON file.")
        json = resolve(jsonArg)
        if (!json.isRegularFile()) die("ファイルがありません: ${fileUri(json)}", "File not found: ${fileUri(json)}")
    } else {
        key = parsed.positionals.getOrNull(1) ?: ""
    }
    parsed.positionals.getOrNull(2)?.let { die("data $action: 余分な引数です: $it", "data $action: unexpected argument: $it") }
    var id = parsed.value("--id") ?: ""
    if (id.isEmpty()) id = guessBlockId(html)
    val noBlockJa = "$htmlUri に id=\"$id\" の JSON ブロックが見つかりません。"
    val noBlockEn = "No JSON block with id=\"$id\" in $htmlUri."

    if (action == "get") {
        val block = extractJsonBlock(html.readText(), id) as? JsonBlock.Found ?: die(noBlockJa, noBlockEn)
        if (key.isEmpty()) {
            out(block.text.removeSuffix("\n"))
            return 0
        }
        val root = parseJsonOrNull(block.text) ?: die(noBlockJa, noBlockEn)
        val node = lookupJsonPath(root, key)
        if (node == null) {
            // Exit 1 without the `[x]` prefix: callers branch on "no such key" rather than stop on it.
            throw KatachiScriptExitException("キーがありません: $key", "No such key: $key", plain = true)
        }
        out(renderJsonValue(node))
        return 0
    }

    val jsonFile = json ?: die("data $action: JSON ファイルを指定してください。", "data $action: specify the JSON file.")
    val jsonText = jsonFile.readText()
    val patch = parseJsonOrNull(jsonText)
        ?: die(
            "${fileUri(jsonFile)} が JSON として読めません。直してからやり直してください。",
            "${fileUri(jsonFile)} is not valid JSON. Fix it and try again.",
        )

    withLock(html) {
        writeDataBlock(html, id, action, jsonText, patch, noBlockJa, noBlockEn)
    }
    say(
        "$htmlUri の id=\"$id\" を更新しました（元は $htmlUri.bak）。",
        "Updated id=\"$id\" in $htmlUri (the original is $htmlUri.bak).",
    )
    return 0
}

private fun ScriptContext.writeDataBlock(
    html: Path,
    id: String,
    action: String,
    jsonText: String,
    patch: kotlinx.serialization.json.JsonElement,
    noBlockJa: String,
    noBlockEn: String,
) {
    val current = html.readText()
    val body = if (action == "merge") {
        val base = extractJsonBlock(current, id) as? JsonBlock.Found
            ?: die(
                "${noBlockJa}開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。",
                "$noBlockEn The opening and closing tags must each be alone at the start of a line.",
            )
        val baseJson = parseJsonOrNull(base.text) ?: die("JSON をマージできませんでした。", "Could not merge the JSON.")
        encodePretty(deepMerge(baseJson, patch)) + "\n"
    } else {
        jsonText
    }
    if (extractJsonBlock(current, id) !is JsonBlock.Found) die(noBlockJa, noBlockEn)

    writeJsonBlock(html, id, body)
}

internal fun ScriptContext.resolveDataTarget(target: String): Path = when (target) {
    "check-list", "checklist" -> requireWorkdir().resolve("check-list.html")
    "report" -> requireWorkdir().resolve("project-code-base-report.html")
    else -> resolve(target)
}

internal fun guessBlockId(html: Path): String = if (html.name.contains("check-list")) "checklist" else "report"

/**
 * Replaces the block [id] of [html] with [json], the way `data set` writes: under the lock, the
 * original kept as `.bak`, the `</script>` inside values made harmless, and the original put back
 * when the result does not read back as JSON.
 */
internal fun ScriptContext.writeJsonBlock(html: Path, id: String, json: String) {
    val htmlUri = fileUri(html)
    withLock(html) {
        val current = html.readText()
        val backup = html.resolveSibling("${html.name}.bak")
        Files.copy(html, backup, StandardCopyOption.REPLACE_EXISTING)
        val replaced = replaceJsonBlock(current, id, escapeScriptEnd(json))
            ?: die("$htmlUri の書き換えに失敗しました。元に戻しました。", "Failed to edit $htmlUri. It was restored.")
        val tmp = html.resolveSibling("${html.name}.tmp.${ProcessHandle.current().pid()}")
        tmp.writeText(replaced)
        Files.move(tmp, html, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

        val written = extractJsonBlock(html.readText(), id) as? JsonBlock.Found
        if (written == null || parseJsonOrNull(written.text) == null) {
            Files.move(backup, html, StandardCopyOption.REPLACE_EXISTING)
            die(
                "差し込んだ結果が読み直せなかったため、元に戻しました。JSON の値に </script> のような、HTML を打ち切る文字列が含まれていないか確認してください。",
                "The result could not be read back, so it was restored. Check that no JSON value contains a string that cuts the HTML short, such as </script>.",
            )
        }
    }
}

/** [writeJsonBlock] for a caller that keeps no `.bak` (the kit's `cmd_data set ... >/dev/null && rm -f .bak`). */
internal fun ScriptContext.replaceJsonBlockQuietly(html: Path, id: String, json: kotlinx.serialization.json.JsonElement) {
    writeJsonBlock(html, id, encodePretty(json))
    html.resolveSibling("${html.name}.bak").deleteIfExists()
}

/**
 * Deep-merges [patch] into the block [id] of [html] and keeps no `.bak`. Returns the reason on
 * failure instead of stopping: the kit's `data_quietly merge`, which init uses so that a broken
 * template is a warning rather than the end of the installation.
 */
internal fun ScriptContext.mergeJsonBlockQuietly(html: Path, id: String, patch: JsonObject): String? = try {
    withLock(html) {
        val block = extractJsonBlock(html.readText(), id) as? JsonBlock.Found
            ?: die("${fileUri(html)} に id=\"$id\" の JSON ブロックが見つかりません。", "No JSON block with id=\"$id\" in ${fileUri(html)}.")
        val base = parseJsonOrNull(block.text) ?: die("JSON をマージできませんでした。", "Could not merge the JSON.")
        replaceJsonBlockQuietly(html, id, deepMerge(base, patch))
    }
    null
} catch (exit: KatachiScriptExitException) {
    pick(exit.ja, exit.en)
}

package me.tbsten.katachi.script.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** `data get | set | merge`, the kit's `cmd_data`, with the same arguments, messages and files (`.bak`, lock). */
internal fun ScriptContext.runData(args: List<String>): Int {
    val action = args.getOrNull(0) ?: ""
    if (action !in setOf("get", "set", "merge")) {
        die(
            "data のサブコマンドは get / set / merge です（指定されたのは '$action'）",
            "The data subcommands are get / set / merge (got '$action')",
        )
    }
    val target = args.getOrNull(1)?.takeIf { it.isNotEmpty() }
        ?: die(
            "data $action: 対象を指定してください（check-list / report / ファイルパス）。",
            "data $action: specify the target (check-list / report / a file path).",
        )
    val html = resolveDataTarget(target)
    val htmlUri = fileUri(html)
    if (!html.isRegularFile()) die("ファイルがありません: $htmlUri", "File not found: $htmlUri")

    var rest = args.drop(2)
    var json: Path? = null
    if (action != "get") {
        val jsonArg = rest.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: die("data $action: JSON ファイルを指定してください。", "data $action: specify the JSON file.")
        rest = rest.drop(1)
        json = resolve(jsonArg)
        if (!json.isRegularFile()) die("ファイルがありません: ${fileUri(json)}", "File not found: ${fileUri(json)}")
    }

    var id = ""
    var key = ""
    var index = 0
    while (index < rest.size) {
        val arg = rest[index]
        when {
            arg == "--id" -> {
                id = needValue(arg, rest.getOrNull(index + 1))
                index += 2
            }

            arg.startsWith("-") -> die("data: 知らないオプションです: $arg", "data: unknown option: $arg")

            action == "get" && key.isEmpty() -> {
                key = arg
                index += 1
            }

            else -> die("data $action: 余分な引数です: $arg", "data $action: unexpected argument: $arg")
        }
    }
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

    val current = html.readText()
    val body = if (action == "merge") {
        val base = extractJsonBlock(current, id) as? JsonBlock.Found
            ?: die(
                "${noBlockJa}開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。",
                "$noBlockEn The opening and closing tags must each be alone at the start of a line.",
            )
        val baseJson = parseJsonOrNull(base.text) ?: die("JSON をマージできませんでした。", "Could not merge the JSON.")
        prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), deepMerge(baseJson, patch)) + "\n"
    } else {
        jsonText
    }
    if (extractJsonBlock(current, id) !is JsonBlock.Found) die(noBlockJa, noBlockEn)

    val lock = WorkdirLock.acquire(this, html)
    try {
        val backup = html.resolveSibling("${html.name}.bak")
        Files.copy(html, backup, StandardCopyOption.REPLACE_EXISTING)
        val replaced = replaceJsonBlock(current, id, escapeScriptEnd(body))
        if (replaced == null) {
            die("$htmlUri の書き換えに失敗しました。元に戻しました。", "Failed to edit $htmlUri. It was restored.")
        }
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
        say(
            "$htmlUri の id=\"$id\" を更新しました（元は $htmlUri.bak）。",
            "Updated id=\"$id\" in $htmlUri (the original is $htmlUri.bak).",
        )
    } finally {
        lock.release()
    }
    return 0
}

private fun ScriptContext.resolveDataTarget(target: String): Path = when (target) {
    "check-list", "checklist" -> requireWorkdir().resolve("check-list.html")
    "report" -> requireWorkdir().resolve("project-code-base-report.html")
    else -> resolve(target)
}

private fun guessBlockId(html: Path): String = if (html.name.contains("check-list")) "checklist" else "report"

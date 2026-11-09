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
        ?: die(Messages.Data.specifyTargetCheckList(action))
    val html = resolveDataTarget(target)
    val htmlUri = fileUri(html)
    if (!html.isRegularFile()) die(Messages.Data.fileNotFound(htmlUri))

    var json: Path? = null
    var key = ""
    if (action != "get") {
        val jsonArg = parsed.positionals.getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: die(Messages.Data.specifyJsonFile(action))
        json = resolve(jsonArg)
        if (!json.isRegularFile()) die(Messages.Data.jsonNotFound(fileUri(json)))
    } else {
        key = parsed.positionals.getOrNull(1) ?: ""
    }
    parsed.positionals.getOrNull(2)?.let { die(Messages.Data.unexpectedArgument(action, it)) }
    var id = parsed.value("--id") ?: ""
    if (id.isEmpty()) id = guessBlockId(html)
    val noBlock = Messages.Data.noJsonBlock(htmlUri, id)

    if (action == "get") {
        val block = extractJsonBlock(html.readText(), id) as? JsonBlock.Found ?: die(noBlock)
        if (key.isEmpty()) {
            out(block.text.removeSuffix("\n"))
            return 0
        }
        val root = parseJsonOrNull(block.text) ?: die(noBlock)
        val node = lookupJsonPath(root, key)
        if (node == null) {
            // Exit 1 without the `[x]` prefix: callers branch on "no such key" rather than stop on it.
            throw KatachiScriptExitException(Messages.Data.noSuchKey(key), plain = true)
        }
        out(renderJsonValue(node))
        return 0
    }

    val jsonFile = json ?: die(Messages.Data.specifyJsonFile(action))
    val jsonText = jsonFile.readText()
    val patch = parseJsonOrNull(jsonText)
        ?: die(Messages.Data.notValidJsonFix(fileUri(jsonFile)))

    withLock(html) {
        writeDataBlock(html, id, action, jsonText, patch, noBlock)
    }
    say(Messages.Data.updatedIdOriginalBak(htmlUri, id))
    return 0
}

private fun ScriptContext.writeDataBlock(
    html: Path,
    id: String,
    action: String,
    jsonText: String,
    patch: kotlinx.serialization.json.JsonElement,
    noBlock: Msg,
) {
    val current = html.readText()
    val body = if (action == "merge") {
        val base = extractJsonBlock(current, id) as? JsonBlock.Found
            ?: die(Messages.Data.openingClosingTagsMust(noBlock))
        val baseJson = parseJsonOrNull(base.text) ?: die(Messages.Data.couldNotMergeJson)
        encodePretty(deepMerge(baseJson, patch)) + "\n"
    } else {
        jsonText
    }
    if (extractJsonBlock(current, id) !is JsonBlock.Found) die(noBlock)

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
            ?: die(Messages.Data.failedEditRestored(htmlUri))
        val tmp = html.resolveSibling("${html.name}.tmp.${ProcessHandle.current().pid()}")
        tmp.writeText(replaced)
        Files.move(tmp, html, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

        val written = extractJsonBlock(html.readText(), id) as? JsonBlock.Found
        if (written == null || parseJsonOrNull(written.text) == null) {
            Files.move(backup, html, StandardCopyOption.REPLACE_EXISTING)
            die(Messages.Data.resultCouldNotRead)
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
            ?: die(Messages.Data.noJsonBlockId(fileUri(html), id))
        val base = parseJsonOrNull(block.text) ?: die(Messages.Data.couldNotMergeJson)
        replaceJsonBlockQuietly(html, id, deepMerge(base, patch))
    }
    null
} catch (exit: KatachiScriptExitException) {
    pick(exit.text)
}

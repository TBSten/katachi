package me.tbsten.katachi.script.internal

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * `answers [show] [--target ...]`, `answers import <json>`, `answers set <id> <answer> [--note ...]`.
 * Answers live under `answers` keyed by question id; summary lists only the unanswered ones.
 */
internal fun ScriptContext.runAnswers(args: List<String>): Int {
    // checklist is an old spelling of check-list that the kit took too.
    val normalized = args.mapIndexed { n, arg -> if (arg == "checklist" && args.getOrNull(n - 1) == "--target") "check-list" else arg }
    val parsed = parseArgs(commandSpec("answers"), normalized)
    val action = parsed.subcommand ?: "show"
    val given = parsed.value("--target") ?: ""
    val note = parsed.value("--note") ?: ""
    val positional = parsed.positionals
    val limit = if (action == "set") 2 else if (action == "import") 1 else 0
    positional.getOrNull(limit)?.let { arg ->
        die("answers: 引数が多すぎます: $arg（回答に空白があれば引用符で囲んでください）", "answers: too many arguments: $arg (quote the answer if it contains spaces)")
    }

    if (action == "show") {
        val target = Target.of(given)
        if (target != null) {
            showAnswers(target)
        } else {
            showAnswers(Target.CHECK_LIST)
            say("")
            showAnswers(Target.REPORT)
        }
        return 0
    }

    val first = positional.getOrNull(0) ?: ""
    val second = positional.getOrNull(1) ?: ""
    if (action == "import") {
        if (first.isEmpty()) die("answers import: ブラウザで書き出した JSON のパスを指定してください。", "answers import: give the path of the JSON exported from the browser.")
        if (!resolve(first).isRegularFile()) die("ファイルがありません: ${fileUri(first)}", "File not found: ${fileUri(first)}")
    } else if (first.isEmpty() || second.isEmpty()) {
        die("answers set: <id> <回答> を指定してください（例: answers set q1 \"はい\" --note \"理由\"）", "answers set: give <id> <answer> (for example: answers set q1 \"yes\" --note \"reason\")")
    }
    // Where to write comes from the id (set) or the JSON (import); there is no default, so nothing lands in the other file unnoticed.
    val target = answersTarget(action, Target.of(given), first)
    val html = requireWorkdir().resolve(target.file)
    if (!html.isRegularFile()) die("ファイルがありません: ${fileUri(html)}", "File not found: ${fileUri(html)}")

    withLock(html) {
        val data = readTree(html, target.blockId, "${fileUri(html)} から JSON を取り出せません。", "Could not extract the JSON from ${fileUri(html)}.")
        say("${pick("対象", "Target")}: ${target.cliName}")
        val questions = data.listOrEmpty("questions")
        val answers = data["answers"].asMap() ?: JMap()
        val ids = questions.mapIndexed { n, q -> questionId(target, q.asMap() ?: JMap(), n) }
        val now = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS))
        val incoming = JMap()
        if (action == "import") {
            val path = resolve(first)
            val doc = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(path.readText()) }
                .getOrElse { e -> fail("JSON として読めません: $first（${e.message}）", "Not valid JSON: $first (${e.message})") }
                .toTree()
            val raw = doc.asMap()?.get("answers").asMap()
                ?: fail("answers が dict ではありません（ブラウザで書き出した JSON をそのまま渡してください）", "answers is not a dict (pass the JSON exported from the browser as is)")
            for ((id, answer) in raw) {
                if (id !in ids) {
                    val other = targetOfQuestionId(id)?.takeIf { it != target }
                    if (other != null) {
                        err(pick("[!] $id は${other.ja}の確認事項なので、ここには入れずに飛ばしました", "[!] Skipped $id: it is a question in ${other.en}, not here"))
                    } else {
                        err(pick("[!] 知らない id を飛ばしました: $id", "[!] Skipped an unknown id: $id"))
                    }
                    continue
                }
                val map = answer.asMap()
                val text = if (map != null) map["answer"] else answer
                val noteValue = map?.get("note")
                if (text == null || pyStr(text).isBlank()) continue
                incoming[id] = JMap().apply {
                    put("answer", pyStr(text))
                    put("note", noteValue?.takeIf { it != "" }?.let(::pyStr))
                    put("answeredAt", map?.get("answeredAt")?.takeIf(::truthy) ?: now)
                }
            }
        } else {
            if (first !in ids) fail("知らない id です: $first（使える id: ${ids.joinToString(", ").ifEmpty { "-" }}）", "Unknown id: $first (valid ids: ${ids.joinToString(", ").ifEmpty { "-" }})")
            incoming[first] = JMap().apply {
                put("answer", second)
                put("note", note.ifEmpty { null })
                put("answeredAt", now)
            }
        }
        val merged = JMap(answers).apply { putAll(incoming) }
        val unanswered = ids.count { !truthy(merged[it]) }
        say("取り込みました: 回答 ${incoming.size} 件（未回答 $unanswered 件）", "Imported: ${incoming.size} answers ($unanswered unanswered)")
        val patch = kotlinx.serialization.json.JsonObject(mapOf("answers" to incoming.toJsonElement()))
        replaceJsonBlockQuietly(html, target.blockId, deepMerge(data.toJsonElement(), patch))
    }
    return 0
}

private fun ScriptContext.showAnswers(target: Target) {
    val html = requireWorkdir().resolve(target.file)
    if (!html.isRegularFile()) die("ファイルがありません: ${fileUri(html)}", "File not found: ${fileUri(html)}")
    val data = readTree(html, target.blockId, "${fileUri(html)} から JSON を取り出せません。", "Could not extract the JSON from ${fileUri(html)}.")
    say("${pick("対象", "Target")}: ${target.cliName}")
    val questions = data.listOrEmpty("questions")
    val answers = data["answers"].asMap() ?: JMap()
    val ids = questions.mapIndexed { n, q -> questionId(target, q.asMap() ?: JMap(), n) }
    val answered = ids.count { truthy(answers[it]) }
    say("回答済み $answered 件 / 未回答 ${ids.size - answered} 件", "Answered $answered / unanswered ${ids.size - answered}")
    questions.forEachIndexed { n, q ->
        val id = ids[n]
        if (truthy(answers[id])) return@forEachIndexed
        out("- $id ${questionLine(q.asMap() ?: JMap())}")
    }
}

/**
 * Where `answers set` / `answers import` writes. set: the id's prefix (q<n> the check list, cq<n> the
 * report), and a contradicting --target stops it. import: the JSON's target > --target > the prefix
 * of its ids when they all agree. The kit's `ANSWERS_TARGET_PY`.
 */
private fun ScriptContext.answersTarget(action: String, given: Target?, arg: String): Target {
    if (action == "set") {
        val found = targetOfQuestionId(arg)
            ?: die(
                "answers set: 確認事項の id ではありません: $arg（チェックリストの確認事項は q<n>、レポートの確認事項は cq<n>）",
                "answers set: not a question id: $arg (questions in the check list are q<n>, in the report cq<n>)",
            )
        if (given != null && given != found) {
            die(
                "answers set: $arg は${found.ja}（${found.cliName}）の確認事項で、--target ${given.cliName} と食い違うので書きませんでした。--target を外してください。",
                "answers set: $arg is a question in ${found.en} (${found.cliName}), which contradicts --target ${given.cliName}. Nothing was written; drop --target.",
            )
        }
        return found
    }
    val doc = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(resolve(arg).readText()).toTree() }.getOrNull()
        // An unreadable JSON is stopped with its reason when it is read for real; only the target is decided here.
        ?: return given ?: Target.REPORT
    val fromJson = doc.asMap()?.get("target")?.let { it as? String }?.let { if (it == "checklist") "check-list" else it } ?: ""
    if (fromJson.isNotEmpty() && given != null && fromJson != given.cliName) {
        die(
            "answers import: JSON の target（$fromJson）と --target ${given.cliName} が食い違います。どちらに取り込むかを確かめてください。",
            "answers import: the target in the JSON ($fromJson) contradicts --target ${given.cliName}. Check which file the answers belong to.",
        )
    }
    if (fromJson.isNotEmpty()) return Target.of(fromJson) ?: die("answers: 対象は report か check-list です: $fromJson", "answers: the target is report or check-list: $fromJson")
    if (given != null) return given
    val sides = doc.asMap()?.get("answers").asMap()?.keys?.map(::targetOfQuestionId)?.toSet() ?: emptySet()
    if (sides.size == 1 && null !in sides) return sides.single()!!
    die(
        "answers import: JSON に target が無く、id からも取り込み先を決められません。--target report か --target check-list を付けてください。",
        "answers import: the JSON has no target, and the ids do not tell where it belongs. Pass --target report or --target check-list.",
    )
}

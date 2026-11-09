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
        die(Messages.Answers.tooManyArgumentsQuote(arg))
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
        if (first.isEmpty()) die(Messages.Answers.givePathJsonExported)
        if (!resolve(first).isRegularFile()) die(Messages.Answers.fileNotFound(fileUri(first)))
    } else if (first.isEmpty() || second.isEmpty()) {
        die(Messages.Answers.giveIdAnswerExample)
    }
    // Where to write comes from the id (set) or the JSON (import); there is no default, so nothing lands in the other file unnoticed.
    val target = answersTarget(action, Target.of(given), first)
    val html = requireWorkdir().resolve(target.file)
    if (!html.isRegularFile()) die(Messages.Answers.htmlNotFound(fileUri(html)))

    withLock(html) {
        val data = readTree(html, target.blockId, Messages.Answers.couldNotExtractJson(fileUri(html)))
        say(Messages.Answers.target(target.cliName))
        val questions = data.listOrEmpty("questions")
        val answers = data["answers"].asMap() ?: JMap()
        val ids = questions.mapIndexed { n, q -> questionId(target, q.asMap() ?: JMap(), n) }
        val now = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS))
        val incoming = JMap()
        if (action == "import") {
            val path = resolve(first)
            val doc = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(path.readText()) }
                .getOrElse { e -> fail(Messages.Answers.notValidJson(first, e.message)) }
                .toTree()
            val raw = doc.asMap()?.get("answers").asMap()
                ?: fail(Messages.Answers.answersNotDictPass)
            for ((id, answer) in raw) {
                if (id !in ids) {
                    val other = targetOfQuestionId(id)?.takeIf { it != target }
                    if (other != null) {
                        err(pick(Messages.Answers.skippedQuestionNotHere(id, other.label)))
                    } else {
                        err(pick(Messages.Answers.skippedUnknownId(id)))
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
            if (first !in ids) fail(Messages.Answers.unknownIdValidIds(first, ids.joinToString(", ").ifEmpty { "-" }))
            incoming[first] = JMap().apply {
                put("answer", second)
                put("note", note.ifEmpty { null })
                put("answeredAt", now)
            }
        }
        val merged = JMap(answers).apply { putAll(incoming) }
        val unanswered = ids.count { !truthy(merged[it]) }
        say(Messages.Answers.importedAnswersUnanswered(incoming.size, unanswered))
        val patch = kotlinx.serialization.json.JsonObject(mapOf("answers" to incoming.toJsonElement()))
        replaceJsonBlockQuietly(html, target.blockId, deepMerge(data.toJsonElement(), patch))
    }
    return 0
}

private fun ScriptContext.showAnswers(target: Target) {
    val html = requireWorkdir().resolve(target.file)
    if (!html.isRegularFile()) die(Messages.Answers.htmlNotFound(fileUri(html)))
    val data = readTree(html, target.blockId, Messages.Answers.couldNotExtractJson(fileUri(html)))
    say(Messages.Answers.target(target.cliName))
    val questions = data.listOrEmpty("questions")
    val answers = data["answers"].asMap() ?: JMap()
    val ids = questions.mapIndexed { n, q -> questionId(target, q.asMap() ?: JMap(), n) }
    val answered = ids.count { truthy(answers[it]) }
    say(Messages.Answers.answeredUnanswered(answered, ids.size - answered))
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
            ?: die(Messages.Answers.notQuestionIdQuestions(arg))
        if (given != null && given != found) {
            die(Messages.Answers.questionWhichContradictsTarget(arg, found.label, found.cliName, given.cliName))
        }
        return found
    }
    val doc = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(resolve(arg).readText()).toTree() }.getOrNull()
        // An unreadable JSON is stopped with its reason when it is read for real; only the target is decided here.
        ?: return given ?: Target.REPORT
    val fromJson = doc.asMap()?.get("target")?.let { it as? String }?.let { if (it == "checklist") "check-list" else it } ?: ""
    if (fromJson.isNotEmpty() && given != null && fromJson != given.cliName) {
        die(Messages.Answers.targetJsonContradictsTarget(fromJson, given.cliName))
    }
    if (fromJson.isNotEmpty()) return Target.of(fromJson) ?: die(Messages.Answers.targetReportCheckList(fromJson))
    if (given != null) return given
    val sides = doc.asMap()?.get("answers").asMap()?.keys?.map(::targetOfQuestionId)?.toSet() ?: emptySet()
    if (sides.size == 1 && null !in sides) return sides.single()!!
    die(Messages.Answers.jsonHasNoTarget)
}

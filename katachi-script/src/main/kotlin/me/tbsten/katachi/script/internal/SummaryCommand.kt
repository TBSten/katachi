package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** `summary`: the message to give back to the user, or, before the end, where to resume. */
internal fun ScriptContext.runSummary(): Int {
    val workdir = requireWorkdir()
    // Only read where the module goes; a missing definition does not stop it (it resumes sessions cut before scaffold).
    val module = resolveModuleDir()
    val checkListPath = workdir.resolve(CHECK_LIST_FILE)
    if (!checkListPath.isRegularFile()) die("${fileUri(checkListPath)} がありません。先に init を実行してください。", "${fileUri(checkListPath)} not found. Run init first.")
    val cl = readTree(checkListPath, "checklist", "チェックリストの JSON を読めません。", "Cannot read the JSON of the check list.")
    // The report's questions too: once they never reached the user before the end.
    val reportPath = workdir.resolve(REPORT_FILE)
    val report = (if (reportPath.isRegularFile()) readJsonBlockOrNull(reportPath, "report")?.toTree().asMap() else null) ?: JMap()
    val definition = definitionDir(module)
    printSummary(cl, report, module, definition, definition.isDirectory(), Guide.bundled(lang).ids)
    return 0
}

private fun ScriptContext.printSummary(cl: JMap, report: JMap, module: ModuleDir, definition: Path, definitionExists: Boolean, toc: List<String>) {
    val templates = report.listOrEmpty("templates").maps()
    val meta = cl.mapOrEmpty("meta")
    val workdir = meta.str("workdir")?.takeIf { it.isNotEmpty() }
    val projectRoot = meta.str("projectRoot")?.takeIf { it.isNotEmpty() }?.let { resolve(it) } ?: cwd
    fun uri(path: String): String = fileUriOf(projectRoot.resolve(path))
    fun inWorkdir(name: String): String = if (workdir == null) pick("<作業用ディレクトリ>/", "<working directory>/") + name else uri("$workdir/$name")
    val workdirLine = pick("  作業用ディレクトリ: %s", "  Working directory: %s").format(workdir?.let(::uri) ?: pick("<作業用ディレクトリ>", "<working directory>"))

    val violations = cl.listOrEmpty("violations")
    val changed = cl.listOrEmpty("changedFiles")
    val items = cl.requiredItems()
    val done = items.filter { truthy(it["done"]) }
    val optionalItems = cl.optionalItems()
    val (reportAnswered, reportUnanswered) = answerCounts(report, Target.REPORT)
    val (clAnswered, clUnanswered) = answerCounts(cl, Target.CHECK_LIST)

    if (done.size < items.size) {
        say("❌ katachi のセットアップができませんでした", "❌ Could not set up katachi")
        say("")
        say("終わっていない項目:", "Unfinished items:")
        for (item in items) if (!truthy(item["done"])) out("- ${pyStr(item["id"])} ${item["label"]?.let(::pyStr) ?: ""}")
        // So that the next session can go on without relying on what was said in this one.
        say("")
        say("再開:", "Resume:")
        out(workdirLine)
        out(pick("  終わった項目: %s", "  Done: %s").format(done.joinToString(" ") { pyStr(it["id"]) }.ifEmpty { "-" }))
        nextSection(items, toc)?.let { section ->
            say("  次のセクション: $AGENT_COMMAND guide $section", "  Next section: $AGENT_COMMAND guide $section")
        }
        if (definitionExists) {
            say("  定義: ${fileUriOf(definition)}", "  Definition: ${fileUriOf(definition)}")
        } else {
            say("  定義: まだ無い（scaffold 未実行。meta.moduleDir = ${module.dir}）", "  Definition: not yet (scaffold has not run; meta.moduleDir = ${module.dir})")
        }
        say(
            "  確認事項: レポート 回答済み $reportAnswered / 未回答 ${reportUnanswered.size}、チェックリスト 回答済み $clAnswered / 未回答 ${clUnanswered.size}",
            "  Questions: report answered $reportAnswered / unanswered ${reportUnanswered.size}, check list answered $clAnswered / unanswered ${clUnanswered.size}",
        )
        say("")
        say("チェックリスト:", "Check list:")
        out(inWorkdir(CHECK_LIST_FILE))
        return
    }

    val modulePath = module.gradlePath
    if (violations.isNotEmpty()) say("⚠️ katachi のセットアップを行いました", "⚠️ Set up katachi") else say("✅ katachi のセットアップを行いました", "✅ Set up katachi")
    say("")
    say("**生成されたコードはあなたのレビューが必要不可欠** です。内容を確認してください。", "**The generated code needs your review.** Please check what it contains.")
    say("")
    say("- チェックリスト: ${inWorkdir(CHECK_LIST_FILE)}", "- Check list: ${inWorkdir(CHECK_LIST_FILE)}")
    say("- コードベース レポート: ${inWorkdir(REPORT_FILE)}", "- Codebase report: ${inWorkdir(REPORT_FILE)}")
    if (violations.isNotEmpty()) {
        say("- `$modulePath:test` の実行結果: ⚠️ ${violations.size} 件のアーキテクチャ違反を残しています", "- `$modulePath:test` result: ⚠️ architecture violations left: ${violations.size}")
    } else {
        say("- `$modulePath:test` の実行結果: ✅ All green", "- `$modulePath:test` result: ✅ All green")
    }
    printQuestions(
        pick("確認したいこと（チェックリスト）", "Questions for you (check list)"),
        pick("チェックリストの「ユーザに確認したいこと」", "\"Questions for the user\" in the check list"),
        clAnswered,
        clUnanswered,
    )
    printQuestions(pick("コードベースの揺れ（レポート）", "Inconsistencies in the codebase (report)"), pick("レポートの questions", "questions in the report"), reportAnswered, reportUnanswered)
    say("- 変更したファイル: ${changed.size} 件", "- Changed files: ${changed.size}")
    out("- Next action:")
    say("    - ${uri(module.dir)}/ 以下の ProjectArchitecture.kt を**レビュー**してください", "    - **Review** ProjectArchitecture.kt under ${uri(module.dir)}/")
    // The optional steps not done yet, as what can be done next (their labels are past tense, so they get their own wording).
    val hints = mapOf(
        "6-1" to pick("実際の実装タスクを1つ回して、定義が機能することを確かめてください", "Run one real implementation task and confirm that the definition works"),
        "6-2" to pick("CI に `$modulePath:test` を組み込んでください", "Add `$modulePath:test` to CI"),
        "6-3" to pick(
            "ドキュメント生成をセットアップできます（`architecture { title / description }` を書き、`./gradlew $modulePath:katachiDocs` で生成。生成物をコミットするかを決めてください）",
            "You can set up documentation generation (write `architecture { title / description }` and generate with `./gradlew $modulePath:katachiDocs`; decide whether to commit the output)",
        ),
    )
    for (item in optionalItems) {
        if (truthy(item["done"])) continue
        val id = pyStr(item["id"])
        if (id == "6-4") {
            // No Role worth a template: recommend nothing.
            if (templates.isEmpty()) continue
            say(
                "    - テンプレートからのコード生成をセットアップできます。提案は次の ${templates.size} 件です（レポートの「テンプレートの提案」）",
                "    - You can set up code generation from templates. Suggestions: ${templates.size} (\"Template suggestions\" in the report)",
            )
            for (template in templates) {
                val files = template.listOrEmpty("files").joinToString(", ") { pyStr(it) }.ifEmpty { pick("(未記入)", "(blank)") }
                val params = template.listOrEmpty("params").joinToString(", ") { pyStr(it) }.ifEmpty { pick("なし", "none") }
                val role = template["role"]?.takeIf(::truthy)?.let(::pyStr) ?: pick("(未記入)", "(blank)")
                say("        - $role: $files（パラメータ: $params）", "        - $role: $files (parameters: $params)")
            }
            continue
        }
        out("    - ${hints[id] ?: item["label"]?.let(::pyStr) ?: id}")
    }

    // The resume block, in the finished form too: the guide's last sections send the next session here.
    val optionalTitles = mapOf(
        "6-1" to ("6-A" to pick("導入できたことを確かめる", "confirm that the installation works")),
        "6-2" to ("6-B" to pick("CI に組み込む", "add it to CI")),
        "6-3" to ("6-C" to pick("ドキュメント生成をセットアップする", "set up documentation generation")),
        "6-4" to ("6-D" to pick("テンプレートからのコード生成をセットアップする", "set up code generation from templates")),
    )
    say("")
    say("再開:", "Resume:")
    out(workdirLine)
    out(pick("  終わった項目: %s", "  Done: %s").format((items + optionalItems).filter { truthy(it["done"]) }.joinToString(" ") { pyStr(it["id"]) }))
    say("  次のセクション（ユーザの依頼に合わせて選ぶ）:", "  Next section (pick the one the user asked for):")
    say("    - レビューの指摘を直す: $AGENT_COMMAND guide 6-0", "    - Fix what the review pointed out: $AGENT_COMMAND guide 6-0")
    for (item in optionalItems) {
        val id = pyStr(item["id"])
        if (truthy(item["done"]) || id !in optionalTitles) continue
        if (id == "6-4" && templates.isEmpty()) continue
        val (section, title) = optionalTitles.getValue(id)
        out("    - $title: $AGENT_COMMAND guide $section")
    }
}

/** (answered, unanswered with their questions) for one file. */
private fun answerCounts(doc: JMap, target: Target): Pair<Int, List<Pair<String, JMap>>> {
    val questions = doc.listOrEmpty("questions")
    val answers = doc["answers"].asMap() ?: JMap()
    val ids = questions.mapIndexed { n, q -> questionId(target, q.asMap() ?: JMap(), n) }
    val answered = ids.count { truthy(answers[it]) }
    val unanswered = ids.zip(questions).filter { (id, _) -> !truthy(answers[id]) }.map { (id, q) -> id to (q.asMap() ?: JMap()) }
    return answered to unanswered
}

/** Answered ones as a count, unanswered ones listed with id, question and recommendation. */
private fun ScriptContext.printQuestions(label: String, where: String, answered: Int, unanswered: List<Pair<String, JMap>>) {
    if (answered == 0 && unanswered.isEmpty()) return
    say("- $label: 回答済み $answered 件 / 未回答 ${unanswered.size} 件（$where）", "- $label: answered $answered / unanswered ${unanswered.size} ($where)")
    for ((id, q) in unanswered) {
        out("    - $id ${questionLine(q)}")
    }
}

/** `<question> → 推奨: <recommendation>`, as summary and answers show an unanswered question. */
internal fun ScriptContext.questionLine(q: JMap): String {
    val text = q["question"]?.takeIf(::truthy)?.let(::pyStr) ?: pick("(未記入)", "(blank)")
    val recommendation = q["recommendation"]?.takeIf(::truthy)?.let(::pyStr)
    return if (recommendation == null) text else text + pick(" → 推奨: $recommendation", " → recommended: $recommendation")
}

internal fun Path.readTextOrNull(): String? = if (isRegularFile()) readText() else null

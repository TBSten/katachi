package me.tbsten.katachi.script.internal

import kotlin.io.path.isRegularFile

// check / uncheck / warn / verify: the commands that move the check list forward.

/** `check <item id>...`: marks items done and names the guide section to read next. Runs lint on 3-2 / 3-3. */
internal fun ScriptContext.runCheck(ids: List<String>): Int {
    if (ids.isEmpty()) die("check: 項目 id を1つ以上指定してください（例: check 1-1 1-2）", "check: specify one or more item ids (for example: check 1-1 1-2)")
    markItems(ids, done = true)
    // Catch a Role that mixes kinds when the definition is written and when it is tidied; the items stay done.
    if (ids.any { it == "3-2" || it == "3-3" }) lintRoles()
    return 0
}

/** `uncheck <item id>...`. */
internal fun ScriptContext.runUncheck(ids: List<String>): Int {
    if (ids.isEmpty()) die("uncheck: 項目 id を1つ以上指定してください。", "uncheck: specify one or more item ids.")
    markItems(ids, done = false)
    return 0
}

private fun ScriptContext.markItems(ids: List<String>, done: Boolean) {
    var items: List<JMap> = emptyList()
    editChecklist { data ->
        val known = LinkedHashMap<String, JMap>()
        for (item in data.allItems()) item.str("id")?.let { known[it] = item }
        val unknown = ids.filter { it !in known }
        if (unknown.isNotEmpty()) {
            fail(
                "知らない項目 id です: ${unknown.joinToString(", ")}\n使える id: ${known.keys.joinToString(", ")}",
                "Unknown item id: ${unknown.joinToString(", ")}\nValid ids: ${known.keys.joinToString(", ")}",
            )
        }
        for (id in ids) known.getValue(id)["done"] = done
        items = known.values.toList()
        true
    }
    if (done) {
        say("完了にしました: ${ids.joinToString(", ")}", "Marked as done: ${ids.joinToString(", ")}")
        nextSection(items, Guide.bundled(lang).ids)?.let { section ->
            say("次: $AGENT_COMMAND guide $section", "Next: $AGENT_COMMAND guide $section")
        }
    } else {
        say("未完了に戻しました: ${ids.joinToString(", ")}", "Marked as not done: ${ids.joinToString(", ")}")
    }
}

/** `warn <step id> <text>` / `warn <step id> --clear`. */
internal fun ScriptContext.runWarn(args: List<String>): Int {
    val step = args.firstOrNull()
        ?: die("warn: ステップ id を指定してください（例: warn 4 \"違反を2件残しました\"）", "warn: specify the step id (for example: warn 4 \"Left 2 violations\")")
    val text: String? = when (val second = args.getOrNull(1)) {
        "--clear" -> null
        null, "" -> die("warn: 警告の文言を指定してください（消すなら --clear）", "warn: specify the warning text (or --clear to remove it)")
        else -> second
    }
    editChecklist { data ->
        val steps = LinkedHashMap<String, JMap>()
        for (s in data.listOrEmpty("steps").maps()) s.str("id")?.let { steps[it] = s }
        val target = steps[step]
            ?: fail(
                "知らないステップ id です: $step\n使える id: ${steps.keys.joinToString(", ")}",
                "Unknown step id: $step\nValid ids: ${steps.keys.joinToString(", ")}",
            )
        target["warning"] = text
        true
    }
    if (text == null) {
        say("ステップ $step の警告を消しました", "Cleared the warning on step $step")
    } else {
        say("ステップ $step の警告を設定しました", "Set a warning on step $step")
    }
    return 0
}

private fun blank(value: Any?): Boolean = value == null || value == "" || (value is List<*> && value.isEmpty())

/** `verify`: lists what is unfinished or blank; with nothing left, marks the last step done and returns 0. */
internal fun ScriptContext.runVerify(): Int {
    val workdir = requireWorkdir()
    val checkListPath = workdir.resolve(CHECK_LIST_FILE)
    val reportPath = workdir.resolve(REPORT_FILE)
    if (!checkListPath.isRegularFile()) die("${fileUri(checkListPath)} がありません。先に init を実行してください。", "${fileUri(checkListPath)} not found. Run init first.")
    var status = 0
    withLock(checkListPath) {
        val cl = readTree(checkListPath, "checklist", "チェックリストの JSON を読めません。", "Cannot read the JSON of the check list.")
        val rp = if (reportPath.isRegularFile()) {
            readTree(
                reportPath,
                "report",
                "レポートの JSON を読めません。開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。",
                "Cannot read the JSON of the report. The opening and closing tags must each be alone at the start of a line.",
            )
        } else {
            null
        }

        // The last required step is "pass verify" itself; counting it would keep verify from ever passing.
        val steps = cl.listOrEmpty("steps").maps()
        val final = steps.filter { it["optional"] != true }.lastOrNull()?.get("id")
        val missing = mutableListOf<String>()
        for ((key, value) in cl.mapOrEmpty("meta")) if (blank(value)) missing += "check-list: meta.$key"
        if (steps.isEmpty()) missing += pick("check-list: steps がありません（テンプレートが壊れています）", "check-list: no steps (the template is broken)")
        for (s in steps) {
            if (s["id"] == final || s["optional"] == true) continue
            for (item in s.listOrEmpty("items").maps()) {
                if (!truthy(item["done"])) {
                    val label = item["label"]?.let(::pyStr) ?: ""
                    missing += pick("check-list: 未チェック ${pyStr(item["id"])} $label", "check-list: not done ${pyStr(item["id"])} $label")
                }
            }
        }
        if (rp != null) {
            for ((key, value) in rp.mapOrEmpty("meta")) if (blank(value)) missing += "report: meta.$key"
            for ((key, value) in rp.mapOrEmpty("overview")) if (blank(value)) missing += "report: overview.$key"
            val architecture = rp.mapOrEmpty("architecture")
            if (blank(architecture["name"])) missing += "report: architecture.name"
            if (blank(architecture["rationale"])) missing += "report: architecture.rationale"
            for (key in listOf("modules", "directoryTree", "roles")) if (blank(rp[key])) missing += "report: $key"
            rp.listOrEmpty("roles").forEachIndexed { n, role ->
                val r = role.asMap() ?: return@forEachIndexed
                for (key in listOf("importance", "name", "layout", "naming", "group")) if (blank(r[key])) missing += "report: roles[$n].$key"
            }
        }
        val warnings = steps.filter { truthy(it["warning"]) }.map { pyStr(it["id"]) to pyStr(it["warning"]) }

        if (missing.isNotEmpty()) {
            say("未完了・未記入が ${missing.size} 件あります。", "Unfinished or blank: ${missing.size}")
            say("")
            for (m in missing) out("  - $m")
            status = 1
        } else {
            for (s in steps) {
                if (s["id"] == final) for (item in s.listOrEmpty("items").maps()) item["done"] = true
            }
            replaceJsonBlockQuietly(checkListPath, "checklist", cl.toJsonElement())
            say("未完了・未記入はありません。ステップ ${pyStr(final)} を完了にしました。", "Nothing is unfinished or blank. Marked step ${pyStr(final)} as done.")
        }
        if (warnings.isNotEmpty()) {
            say("")
            say("警告つきのステップ:", "Steps with warnings:")
            for ((id, text) in warnings) say("  - ステップ $id: $text", "  - step $id: $text")
        }
    }
    return status
}

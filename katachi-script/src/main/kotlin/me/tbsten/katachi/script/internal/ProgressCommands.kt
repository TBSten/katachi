package me.tbsten.katachi.script.internal

import kotlin.io.path.isRegularFile

// check / uncheck / warn / verify: the commands that move the check list forward.

/** `check <item id>...`: marks items done and names the guide section to read next. Runs lint on 3-2 / 3-3. */
internal fun ScriptContext.runCheck(ids: List<String>): Int {
    if (ids.isEmpty()) die(Messages.Progress.specifyOneMoreItem)
    markItems(ids, done = true)
    // Catch a Role that mixes kinds when the definition is written and when it is tidied; the items stay done.
    if (ids.any { it == "3-2" || it == "3-3" }) lintRoles()
    return 0
}

/** `uncheck <item id>...`. */
internal fun ScriptContext.runUncheck(ids: List<String>): Int {
    if (ids.isEmpty()) die(Messages.Progress.uncheckSpecifyItems)
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
            fail(Messages.Progress.unknownItemIdValid(unknown.joinToString(", "), known.keys.joinToString(", ")))
        }
        for (id in ids) known.getValue(id)["done"] = done
        items = known.values.toList()
        true
    }
    if (done) {
        say(Messages.Progress.markedDone(ids.joinToString(", ")))
        nextSection(items, Guide.bundled(lang).ids)?.let { section ->
            say(Messages.Progress.nextGuide(section))
        }
    } else {
        say(Messages.Progress.markedNotDone(ids.joinToString(", ")))
    }
}

/** `warn <step id> <text>` / `warn <step id> --clear`. */
internal fun ScriptContext.runWarn(args: List<String>): Int {
    val step = args.firstOrNull()
        ?: die(Messages.Progress.specifyStepIdExample)
    val text: String? = when (val second = args.getOrNull(1)) {
        "--clear" -> null
        null, "" -> die(Messages.Progress.specifyWarningTextClear)
        else -> second
    }
    editChecklist { data ->
        val steps = LinkedHashMap<String, JMap>()
        for (s in data.listOrEmpty("steps").maps()) s.str("id")?.let { steps[it] = s }
        val target = steps[step]
            ?: fail(Messages.Progress.unknownStepIdValid(step, steps.keys.joinToString(", ")))
        target["warning"] = text
        true
    }
    if (text == null) {
        say(Messages.Progress.clearedWarningStep(step))
    } else {
        say(Messages.Progress.setWarningStep(step))
    }
    return 0
}

private fun blank(value: Any?): Boolean = value == null || value == "" || (value is List<*> && value.isEmpty())

/** `verify`: lists what is unfinished or blank; with nothing left, marks the last step done and returns 0. */
internal fun ScriptContext.runVerify(): Int {
    val workdir = requireWorkdir()
    val checkListPath = workdir.resolve(CHECK_LIST_FILE)
    val reportPath = workdir.resolve(REPORT_FILE)
    if (!checkListPath.isRegularFile()) die(Messages.Progress.notFoundRunInit(fileUri(checkListPath)))
    var status = 0
    withLock(checkListPath) {
        val cl = readTree(checkListPath, "checklist", Messages.Progress.cannotReadJsonCheck)
        val rp = if (reportPath.isRegularFile()) {
            readTree(
                reportPath,
                "report",
                Messages.Progress.cannotReadJsonReport,
            )
        } else {
            null
        }

        // The last required step is "pass verify" itself; counting it would keep verify from ever passing.
        val steps = cl.listOrEmpty("steps").maps()
        val final = steps.filter { it["optional"] != true }.lastOrNull()?.get("id")
        val missing = mutableListOf<String>()
        for ((key, value) in cl.mapOrEmpty("meta")) if (blank(value)) missing += "check-list: meta.$key"
        if (steps.isEmpty()) missing += pick(Messages.Progress.noStepsTemplateBroken)
        for (s in steps) {
            if (s["id"] == final || s["optional"] == true) continue
            for (item in s.listOrEmpty("items").maps()) {
                if (!truthy(item["done"])) {
                    val label = item["label"]?.let(::pyStr) ?: ""
                    missing += pick(Messages.Progress.notDone(pyStr(item["id"]), label))
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
            say(Messages.Progress.unfinishedBlank(missing.size))
            say("")
            for (m in missing) out("  - $m")
            status = 1
        } else {
            for (s in steps) {
                if (s["id"] == final) for (item in s.listOrEmpty("items").maps()) item["done"] = true
            }
            replaceJsonBlockQuietly(checkListPath, "checklist", cl.toJsonElement())
            say(Messages.Progress.nothingUnfinishedBlankMarked(pyStr(final)))
        }
        if (warnings.isNotEmpty()) {
            say("")
            say(Messages.Progress.stepsWarnings)
            for ((id, text) in warnings) say(Messages.Progress.step(id, text))
        }
    }
    return status
}

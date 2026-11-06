package me.tbsten.katachi.script.internal

import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/** One kind of row `add` appends: the array it goes to, its fields, and the fields that take a list. */
private class AddKind(val target: Target, val array: String, val scalars: List<String>, val lists: List<String>, val flags: Map<String, String> = emptyMap())

private val ADD_KINDS: Map<String, AddKind> = linkedMapOf(
    "violation" to AddKind(Target.CHECK_LIST, "violations", listOf("violation", "location", "whyNotFixed", "suggestion"), emptyList()),
    "question" to AddKind(Target.CHECK_LIST, "questions", listOf("question", "observed", "recommendation"), listOf("options")),
    // The report's questions: the same shape, another file. Where step 1 writes what varies in the codebase.
    "codebase-question" to AddKind(Target.REPORT, "questions", listOf("question", "observed", "recommendation"), listOf("options")),
    "changed" to AddKind(Target.CHECK_LIST, "changedFiles", listOf("path", "change", "summary"), emptyList()),
    // What a module is responsible for. Not called role so it is not mistaken for a katachi Role (one kind of file).
    "module" to AddKind(Target.REPORT, "modules", listOf("path", "kind", "responsibility", "buildFile", "deviation"), emptyList()),
    // group is where the Role goes in the definition (nested with dots: feature.ui); verify requires it.
    "role" to AddKind(
        Target.REPORT,
        "roles",
        listOf("importance", "name", "layout", "naming", "count", "note", "group"),
        listOf("allowed", "forbidden", "examples"),
        // A Role that exists only to put a constraint on a Gradle build file; needs --note.
        mapOf("constraint-only" to "constraintOnly"),
    ),
    "tool" to AddKind(Target.REPORT, "tools", listOf("name", "configPath", "declareInKatachi", "note"), emptyList()),
    "excluded" to AddKind(Target.REPORT, "excluded", listOf("path", "reason"), emptyList()),
    // The "what you can do next" of the end of the installation; summary and the report show it.
    "template" to AddKind(Target.REPORT, "templates", listOf("role", "basedOn", "reason"), listOf("files", "params")),
)

/** The kinds whose rows get an id (the browser's answers come back keyed by it). */
private val NUMBERED = setOf("question", "codebase-question")

/** The field that makes two rows the same; violation and question may legitimately repeat. */
private val IDENTITY = mapOf("changed" to "path", "module" to "path", "excluded" to "path", "role" to "name", "tool" to "name", "template" to "role")

/** The files gradle() takes care of (the Gradle rows of lint's kinds). */
private val GRADLE_FILE = Regex("(^|/)(settings\\.gradle(\\.kts)?|build\\.gradle(\\.kts)?|gradle\\.properties|[^/]*\\.versions\\.toml|gradlew(\\.bat)?|gradle-wrapper\\.[A-Za-z0-9]+)$")

/** `add <kind> --<field> <value> ...`: appends one row to an array, unlike `data merge`, which replaces it. */
internal fun ScriptContext.runAdd(args: List<String>): Int {
    val kindName = args.firstOrNull() ?: ""
    if (kindName == "-h" || kindName == "--help") {
        say("使い方: add <種類> --<項目> <値> ...（項目は add <種類> --help）", "Usage: add <kind> --<field> <value> ... (for the fields: add <kind> --help)")
        say("  check-list: violation / question / changed")
        say("  report:     module / role / tool / excluded / codebase-question / template")
        return 0
    }
    if (kindName.isEmpty()) {
        die(
            "add: 種類を指定してください（violation / question / codebase-question / changed / module / role / tool / excluded / template）",
            "add: specify the kind (violation / question / codebase-question / changed / module / role / tool / excluded / template)",
        )
    }
    val kind = ADD_KINDS[kindName] ?: die("add: 知らない種類です: $kindName", "add: unknown kind: $kindName")
    val fields = args.drop(1)

    // --help lists the fields and touches nothing, so it answers before init too.
    if (fields.any { it == "-h" || it == "--help" }) {
        printAddHelp(kindName, kind)
        return 0
    }

    val workdir = requireWorkdir()
    val html = workdir.resolve(kind.target.file)
    if (!html.isRegularFile()) die("ファイルがありません: ${fileUri(html)}", "File not found: ${fileUri(html)}")
    val root = checklistMeta(workdir.resolve(CHECK_LIST_FILE), "projectRoot").takeIf { it.isNotEmpty() }?.let(::resolve) ?: cwd

    // A path .gitignore already excludes is never looked at by katachi, so it is not recorded as excluded.
    if (kindName == "excluded") {
        val path = fields.zipWithNext().lastOrNull { (option, _) -> option == "--path" }?.second
        if (!path.isNullOrEmpty() && root.isDirectory() && pathIsIgnored(root, path)) {
            die(
                "そのパスは .gitignore で除外されています。katachi はもともと見ないので、対象外には書きません: $path",
                "That path is excluded by .gitignore. katachi never looks at it, so it is not recorded as excluded: $path",
            )
        }
    }
    val included = if (kindName == "role") listOf("buildSrc") + includedBuildDirs(root) else emptyList()
    val entry = parseAddFields(kindName, kind, fields, included)

    withLock(html) {
        val data = readTree(html, kind.target.blockId, "${fileUri(html)} から JSON を取り出せません。", "Could not extract the JSON from ${fileUri(html)}.")
        val rows = data[kind.array].asList() ?: JList().also { data[kind.array] = it }

        val identity = IDENTITY[kindName]
        if (identity != null && entry[identity] != null) {
            if (rows.maps().any { it[identity] == entry[identity] }) {
                // A recorded path is often changed again (2-2's build.gradle.kts in 6-C); nothing to record, so not an error.
                if (kindName == "changed") {
                    err(pick("その path は記録済みです: ${entry[identity]}（記録し直さなくて構いません。何も変えていません）", "That path is already recorded: ${entry[identity]} (no need to record it again; nothing was changed)"))
                    return 0
                }
                fail(
                    "その $identity はすでに登録されています: ${entry[identity]}\n二重に記録しないため追記しませんでした。\n書き換えたい場合は data get で取り出し、直してから data set してください。",
                    "That $identity is already recorded: ${entry[identity]}\nNot appended, to avoid recording it twice.\nTo change it, take it out with data get, edit it, and write it back with data set.",
                )
            }
        }

        var row: JMap = entry
        if (kindName in NUMBERED) {
            val prefix = kind.target.questionPrefix
            val used = rows.maps().mapNotNull { questionNumber(kind.target, it["id"]) }.toMutableSet()
            var n = 1
            for (existing in rows.maps()) {
                if (!truthy(existing["id"])) {
                    while (n in used) n++
                    existing["id"] = "$prefix$n"
                    used += n
                }
            }
            row = JMap().apply {
                put("id", "$prefix${(used.maxOrNull() ?: 0) + 1}")
                putAll(entry)
            }
        }

        // The count before, checked after writing (catches a concurrent writer). KATACHI_ADD_EXPECT is for testing that check.
        val before = env["KATACHI_ADD_EXPECT"]?.toIntOrNull() ?: rows.size
        rows += row
        replaceJsonBlockQuietly(html, kind.target.blockId, data.toJsonElement())
        say("${kind.array} に1件追記しました（計 ${rows.size} 件）", "Appended 1 entry to ${kind.array} (${rows.size} in total)")
        if (kindName in NUMBERED) out("id=${row["id"]}")

        val after = readJsonBlockOrNull(html, kind.target.blockId)?.toTree().asMap()?.get(kind.array).asList()?.size
        if (after != null && after != before + 1) {
            die(
                "件数が合いません: ${kind.array} は書き込み前 $before 件、追記後 $after 件（期待 ${before + 1}）。ほかのプロセスが同時に書いていませんか",
                "The count does not add up: ${kind.array} had $before entries before, $after after appending (expected ${before + 1}). Is another process writing at the same time?",
            )
        }
    }
    return 0
}

private fun ScriptContext.printAddHelp(kindName: String, kind: AddKind) {
    say("使い方: add $kindName --<項目> <値> ...（${kind.array} に追記）", "Usage: add $kindName --<field> <value> ... (appends to ${kind.array})")
    say("使える項目: ${(kind.scalars + kind.lists).joinToString(", ") { "--$it" }}", "Valid fields: ${(kind.scalars + kind.lists).joinToString(", ") { "--$it" }}")
    if (kind.lists.isNotEmpty()) say("複数回渡せる項目: ${kind.lists.joinToString(", ") { "--$it" }}", "Fields you can pass more than once: ${kind.lists.joinToString(", ") { "--$it" }}")
    if (kind.flags.isNotEmpty()) say("値を取らない印: ${kind.flags.keys.joinToString(", ") { "--$it" }}", "Flags (no value): ${kind.flags.keys.joinToString(", ") { "--$it" }}")
    if (kindName == "role") {
        say(
            "--group は定義での束ね先（入れ子は . 区切り: feature.ui）。Gradle のビルドファイルは gradle() が受け持つので --layout に書けません（buildSrc / includeBuild したビルドは除く）。制約を付けるための Role なら --constraint-only --note <理由> で記録します。",
            "--group is where the Role goes in the definition (nested with dots: feature.ui). Gradle build files cannot be a --layout since gradle() takes care of them (except buildSrc and included builds). A Role that exists only to attach a constraint is recorded with --constraint-only --note <reason>.",
        )
    }
    if (kindName == "module") say("--deviation は同じ kind の他のモジュールと構成が違う点（無ければ省略）。", "--deviation is how this module differs from the other modules of the same kind (omit if none).")
    if (kindName in NUMBERED) {
        val p = kind.target.questionPrefix
        say(
            "id は ${p}1, ${p}2, ... と自動で採番されます（ブラウザの一括回答と answers がこの id で答えを結びつけます）。",
            "The id is numbered automatically as ${p}1, ${p}2, ... (the answers in the browser and answers use it).",
        )
    }
}

/** The row `add` appends, every field present (null / empty list / false when not given). */
private fun ScriptContext.parseAddFields(kindName: String, kind: AddKind, args: List<String>, included: List<String>): JMap {
    if (args.isEmpty()) {
        fail(
            "項目を1つも指定していません: add $kindName\nこのまま追記すると中身が空の行ができます。\n使えるもの: ${(kind.scalars + kind.lists).joinToString(", ")}",
            "No fields given: add $kindName\nAppending as is would add an empty row.\nValid fields: ${(kind.scalars + kind.lists).joinToString(", ")}",
        )
    }
    val entry = JMap()
    kind.scalars.forEach { entry[it] = null }
    kind.lists.forEach { entry[it] = JList() }
    kind.flags.values.forEach { entry[it] = false }
    var i = 0
    while (i < args.size) {
        val name = args[i].trimStart('-')
        kind.flags[name]?.let {
            entry[it] = true
            i++
            continue
        }
        if (i + 1 >= args.size) fail("--$name に値がありません", "--$name needs a value")
        val value = args[i + 1]
        i += 2
        when (name) {
            in kind.lists -> entry.getValue(name).asList()!!.add(value)
            in kind.scalars -> entry[name] = if (name == "importance" || name == "count") {
                value.trim().toIntOrNull() ?: fail("--$name は数値で指定してください: $value", "--$name must be a number: $value")
            } else {
                value
            }
            else -> {
                val hint = if (kindName == "module" && name == "role") {
                    "\n" + pick(
                        "モジュールの受け持ちは --responsibility です。katachi の Role（1種類のファイル）とは別物で、モジュールは定義では group になります。",
                        "What a module is responsible for goes in --responsibility. It is not a katachi Role (one kind of file); a module becomes a group in the definition.",
                    )
                } else {
                    ""
                }
                val valid = (kind.scalars + kind.lists).joinToString(", ")
                fail("--$name は $kindName で使えません$hint\n使えるもの: $valid", "--$name cannot be used with $kindName$hint\nValid fields: $valid")
            }
        }
    }
    // gradle() takes care of Gradle build files; they are not Role candidates unless the Role only carries a constraint.
    if (kindName == "role") {
        val layout = entry["layout"] as? String ?: ""
        val constraintOnly = entry["constraintOnly"] == true
        if (constraintOnly && !truthy(entry["note"])) {
            fail("--constraint-only には --note で理由（何を検査するか）を書いてください", "--constraint-only needs --note with the reason (what the constraint checks)")
        }
        if (GRADLE_FILE.containsMatchIn(layout) && !inIncludedBuild(layout, included) && !constraintOnly) {
            fail(
                "Gradle のビルドファイルは gradle() が受け持ちます。Role 候補には書きません: $layout（buildSrc / includeBuild したビルドは除く。制約を付けるための Role なら --constraint-only --note <理由>）",
                "gradle() takes care of Gradle build files. They are not Role candidates: $layout (except buildSrc and included builds; for a Role that exists only to attach a constraint, use --constraint-only --note <reason>)",
            )
        }
    }
    return entry
}

private fun inIncludedBuild(path: String, included: List<String>): Boolean {
    val p = path.replace("\\", "/").replace(":", "/").trimStart('.', '/')
    return included.any { p == it || p.startsWith("$it/") }
}

package me.tbsten.katachi.script.internal

import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * One kind of row `add` appends: the array it goes to, its fields, and the fields that take a list.
 * [docs] and [samples] are for the script builders generated from COMMAND_SPECS (`katachi.add.role.group(...)`).
 */
internal class AddKind(
    val target: Target,
    val array: String,
    val scalars: List<String>,
    val lists: List<String>,
    val flags: Map<String, String> = emptyMap(),
    val docs: Map<String, String> = emptyMap(),
    val samples: Map<String, String> = emptyMap(),
)

private val QUESTION_DOCS = mapOf(
    "question" to "the question to ask",
    "observed" to "what was seen in the codebase that raised it",
    "recommendation" to "the answer recommended",
    "options" to "the answers to choose from",
)

internal val ADD_KINDS: Map<String, AddKind> = linkedMapOf(
    "violation" to AddKind(
        Target.CHECK_LIST,
        "violations",
        listOf("violation", "location", "whyNotFixed", "suggestion"),
        emptyList(),
        docs = mapOf(
            "violation" to "what the definition reports",
            "location" to "where it is",
            "whyNotFixed" to "why it is left as it is",
            "suggestion" to "how it could be fixed",
        ),
        samples = mapOf("violation" to "UseCase depends on Repository", "location" to "feature/ui/HomeViewModel.kt"),
    ),
    "question" to AddKind(
        Target.CHECK_LIST,
        "questions",
        listOf("question", "observed", "recommendation"),
        listOf("options"),
        docs = QUESTION_DOCS,
        samples = mapOf("question" to "Nest groups?", "options" to "yes"),
    ),
    // The report's questions: the same shape, another file. Where step 1 writes what varies in the codebase.
    "codebase-question" to AddKind(
        Target.REPORT,
        "questions",
        listOf("question", "observed", "recommendation"),
        listOf("options"),
        docs = QUESTION_DOCS,
        samples = mapOf("question" to "Is data/ a module of its own?", "options" to "yes"),
    ),
    "changed" to AddKind(
        Target.CHECK_LIST,
        "changedFiles",
        listOf("path", "change", "summary"),
        emptyList(),
        docs = mapOf("path" to "the file changed", "change" to "how it changed, such as `New`", "summary" to "what was changed in it"),
        samples = mapOf("path" to "settings.gradle.kts", "change" to "New"),
    ),
    // What a module is responsible for. Not called role so it is not mistaken for a katachi Role (one kind of file).
    "module" to AddKind(
        Target.REPORT,
        "modules",
        listOf("path", "kind", "responsibility", "buildFile", "deviation"),
        emptyList(),
        docs = mapOf(
            "path" to "the directory of the module",
            "kind" to "the kind of module, such as `feature`",
            "responsibility" to "what the module is responsible for",
            "buildFile" to "its build file",
            "deviation" to "how it differs from the other modules of the same kind. Leave it out when it does not",
        ),
        samples = mapOf("path" to "feature/home", "kind" to "feature"),
    ),
    // group is where the Role goes in the definition (nested with dots: feature.ui); verify requires it.
    "role" to AddKind(
        Target.REPORT,
        "roles",
        listOf("importance", "name", "layout", "naming", "count", "note", "group"),
        listOf("allowed", "forbidden", "examples"),
        // A Role that exists only to put a constraint on a Gradle build file; needs --note.
        mapOf("constraint-only" to "constraintOnly"),
        docs = mapOf(
            "importance" to "how much the Role matters, as a number such as `5`",
            "name" to "the name of the Role",
            "layout" to "where its files go",
            "naming" to "how its files are named",
            "count" to "how many files it has, as a number",
            "note" to "a note; the reason, with `constraint-only`",
            "group" to "where the Role goes in the definition, nested with dots: `feature.ui`",
            "allowed" to "what its files may depend on",
            "forbidden" to "what its files must not depend on",
            "examples" to "files that belong to it",
            "constraint-only" to "records a Role that exists only to put a constraint on a Gradle build file. Needs a `note`",
        ),
        samples = mapOf("name" to "UseCase", "group" to "feature.domain", "examples" to "GetUserUseCase.kt"),
    ),
    "tool" to AddKind(
        Target.REPORT,
        "tools",
        listOf("name", "configPath", "declareInKatachi", "note"),
        emptyList(),
        docs = mapOf(
            "name" to "the name of the tool",
            "configPath" to "where its configuration is",
            "declareInKatachi" to "whether the definition declares it, and why",
            "note" to "a note",
        ),
        samples = mapOf("name" to "detekt", "configPath" to "config/detekt.yml"),
    ),
    "excluded" to AddKind(
        Target.REPORT,
        "excluded",
        listOf("path", "reason"),
        emptyList(),
        docs = mapOf("path" to "the path left out of the definition. Not one `.gitignore` leaves out already", "reason" to "why it is left out"),
        samples = mapOf("path" to "legacy", "reason" to "to be removed"),
    ),
    // The "what you can do next" of the end of the installation; summary and the report show it.
    "template" to AddKind(
        Target.REPORT,
        "templates",
        listOf("role", "basedOn", "reason"),
        listOf("files", "params"),
        docs = mapOf(
            "role" to "the Role the template makes files of",
            "basedOn" to "the existing file it is made from",
            "reason" to "why it helps",
            "files" to "the files it makes",
            "params" to "the names filled in when the template is used, such as `name`",
        ),
        samples = mapOf("role" to "UseCase", "params" to "name"),
    ),
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
        say(Messages.Add.usageAddKindField)
        say("  check-list: violation / question / changed")
        say("  report:     module / role / tool / excluded / codebase-question / template")
        return 0
    }
    if (kindName.isEmpty()) {
        die(Messages.Add.specifyKindViolationQuestion)
    }
    val kind = ADD_KINDS[kindName] ?: die(Messages.Add.unknownKind(kindName))
    val fields = args.drop(1)

    // --help lists the fields and touches nothing, so it answers before init too.
    if (fields.any { it == "-h" || it == "--help" }) {
        printAddHelp(kindName, kind)
        return 0
    }

    val workdir = requireWorkdir()
    val html = workdir.resolve(kind.target.file)
    if (!html.isRegularFile()) die(Messages.Add.fileNotFound(fileUri(html)))
    val root = checklistMeta(workdir.resolve(CHECK_LIST_FILE), "projectRoot").takeIf { it.isNotEmpty() }?.let(::resolve) ?: cwd

    // A path .gitignore already excludes is never looked at by katachi, so it is not recorded as excluded.
    if (kindName == "excluded") {
        val path = fields.zipWithNext().lastOrNull { (option, _) -> option == "--path" }?.second
        if (!path.isNullOrEmpty() && root.isDirectory() && pathIsIgnored(root, path)) {
            die(Messages.Add.pathExcludedGitignoreKatachi(path))
        }
    }
    val included = if (kindName == "role") listOf("buildSrc") + includedBuildDirs(root) else emptyList()
    val entry = parseAddFields(kindName, kind, fields, included)

    withLock(html) {
        val data = readTree(html, kind.target.blockId, Messages.Add.couldNotExtractJson(fileUri(html)))
        val rows = data[kind.array].asList() ?: JList().also { data[kind.array] = it }

        val identity = IDENTITY[kindName]
        if (identity != null && entry[identity] != null) {
            if (rows.maps().any { it[identity] == entry[identity] }) {
                // A recorded path is often changed again (2-2's build.gradle.kts in 6-C); nothing to record, so not an error.
                if (kindName == "changed") {
                    err(pick(Messages.Add.pathAlreadyRecordedNo(entry[identity])))
                    return 0
                }
                fail(Messages.Add.alreadyRecordedNotAppended(identity, entry[identity]))
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
        say(Messages.Add.appendedEntryTotal(kind.array, rows.size))
        if (kindName in NUMBERED) out("id=${row["id"]}")

        val after = readJsonBlockOrNull(html, kind.target.blockId)?.toTree().asMap()?.get(kind.array).asList()?.size
        if (after != null && after != before + 1) {
            die(Messages.Add.countNotAddUp(kind.array, before, after, before + 1))
        }
    }
    return 0
}

private fun ScriptContext.printAddHelp(kindName: String, kind: AddKind) {
    say(Messages.Add.usageAddFieldValue(kindName, kind.array))
    say(Messages.Add.validFields((kind.scalars + kind.lists).joinToString(", ") { "--$it" }))
    if (kind.lists.isNotEmpty()) say(Messages.Add.fieldsYouCanPass(kind.lists.joinToString(", ") { "--$it" }))
    if (kind.flags.isNotEmpty()) say(Messages.Add.flagsNoValue(kind.flags.keys.joinToString(", ") { "--$it" }))
    if (kindName == "role") {
        say(Messages.Add.groupWhereRoleGoes)
    }
    if (kindName == "module") say(Messages.Add.deviationHowModuleDiffers)
    if (kindName in NUMBERED) {
        val p = kind.target.questionPrefix
        say(Messages.Add.idNumberedAutomaticallyAnswers(p))
    }
}

/** The row `add` appends, every field present (null / empty list / false when not given). */
private fun ScriptContext.parseAddFields(kindName: String, kind: AddKind, args: List<String>, included: List<String>): JMap {
    if (args.isEmpty()) {
        fail(Messages.Add.noFieldsGivenAdd(kindName, (kind.scalars + kind.lists).joinToString(", ")))
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
        if (i + 1 >= args.size) fail(Messages.Add.needsValue(name))
        val value = args[i + 1]
        i += 2
        when (name) {
            in kind.lists -> entry.getValue(name).asList()!!.add(value)
            in kind.scalars -> entry[name] = if (name == "importance" || name == "count") {
                value.trim().toIntOrNull() ?: fail(Messages.Add.mustNumber(name, value))
            } else {
                value
            }
            else -> {
                val hint = if (kindName == "module" && name == "role") {
                    "\n" + pick(Messages.Add.whatModuleResponsibleGoes)
                } else {
                    ""
                }
                val valid = (kind.scalars + kind.lists).joinToString(", ")
                fail(Messages.Add.cannotUsedValidFields(name, kindName, hint, valid))
            }
        }
    }
    // gradle() takes care of Gradle build files; they are not Role candidates unless the Role only carries a constraint.
    if (kindName == "role") {
        val layout = entry["layout"] as? String ?: ""
        val constraintOnly = entry["constraintOnly"] == true
        if (constraintOnly && !truthy(entry["note"])) {
            fail(Messages.Add.constraintOnlyNeedsNote)
        }
        if (GRADLE_FILE.containsMatchIn(layout) && !inIncludedBuild(layout, included) && !constraintOnly) {
            fail(Messages.Add.gradleTakesCareGradle(layout))
        }
    }
    return entry
}

private fun inIncludedBuild(path: String, included: List<String>): Boolean {
    val p = path.replace("\\", "/").replace(":", "/").trimStart('.', '/')
    return included.any { p == it || p.startsWith("$it/") }
}

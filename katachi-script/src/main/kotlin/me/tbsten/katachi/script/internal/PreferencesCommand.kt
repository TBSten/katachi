package me.tbsten.katachi.script.internal

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

private val PREFERENCE_KEYS = listOf("nestedGroups", "moduleDir", "testFramework", "dependencyStyle", "constraintNames", "oneRolePerFile")

/** The values a preference takes; `path` for moduleDir. The kit's `preference_values_of`. */
private fun preferenceValuesOf(key: String): List<String> = when (key) {
    "nestedGroups", "oneRolePerFile" -> listOf("yes", "no")
    "moduleDir" -> listOf("path")
    "testFramework" -> TEST_FRAMEWORKS
    "dependencyStyle" -> listOf("catalog", "literal")
    "constraintNames" -> listOf("plain", "numbered")
    else -> emptyList()
}

private fun ScriptContext.preferenceQuestionOf(key: String): String = when (key) {
    "nestedGroups" -> pick("group を入れ子にするか", "Nest groups?")
    "moduleDir" -> pick("検査用モジュールの置き場所", "Where the test module goes")
    "testFramework" -> pick("テストのフレームワーク", "Test framework")
    "dependencyStyle" -> pick("依存の書き方", "How dependencies are written")
    "constraintNames" -> pick("制約名に確認事項の番号を入れるか", "Put the question number in constraint names?")
    "oneRolePerFile" -> pick("1 Role 1 ファイルで書くか", "One Role per file?")
    else -> ""
}

/** A JSON string, or null for "" (the kit's `json_value`). */
internal fun jsonValue(value: String?): JsonPrimitive = if (value.isNullOrEmpty()) JsonNull else JsonPrimitive(value)

/**
 * Overwrites `meta.<key>` of the check list with [value] (nothing when it is empty). The kit's
 * `update_checklist_meta`; a failure is a warning.
 */
internal fun ScriptContext.updateChecklistMeta(checkList: java.nio.file.Path, key: String, value: String) {
    if (!checkList.isRegularFile() || value.isEmpty()) return
    val error = mergeJsonBlockQuietly(checkList, "checklist", JsonObject(mapOf("meta" to JsonObject(mapOf(key to jsonValue(value))))))
    if (error != null) {
        warn(
            "チェックリストの meta.$key を $value にできませんでした。data merge で直してください。\n$error",
            "Could not set meta.$key of the check list to $value. Fix it with data merge.\n$error",
        )
    }
}

/** `preferences [show]` / `preferences set key=value...`. The kit's `cmd_preferences`. */
internal fun ScriptContext.runPreferences(args: List<String>): Int {
    var rest = args
    val action = when (val first = args.firstOrNull() ?: "show") {
        "show", "set" -> {
            rest = args.drop(1)
            first
        }
        else -> if (first.contains('=')) "set" else die("preferences のサブコマンドは show / set です（指定されたのは '$first'）", "The preferences subcommands are show / set (got '$first')")
    }
    val workdir = requireWorkdir()
    val module = resolveModuleDir()
    val checkList = workdir.resolve(CHECK_LIST_FILE)
    if (!checkList.isRegularFile()) die("${fileUri(checkList)} がありません。先に init を実行してください。", "${fileUri(checkList)} not found. Run init first.")

    if (action == "set") {
        if (rest.isEmpty()) die("preferences set: key=value を 1 つ以上指定してください（例: preferences set nestedGroups=no）", "preferences set: give one or more key=value (for example: preferences set nestedGroups=no)")
        // Check everything before writing anything, so that nothing is half recorded.
        val patch = LinkedHashMap<String, JsonPrimitive>()
        var moduleDir = ""
        for (kv in rest) {
            if (!kv.contains('=')) die("preferences set は key=value の形です: $kv", "preferences set takes key=value: $kv")
            val key = kv.substringBefore('=')
            val value = kv.substringAfter('=')
            val values = preferenceValuesOf(key)
            if (values.isEmpty()) die("知らないキーです: $key（使えるキー: ${PREFERENCE_KEYS.joinToString(" ")}）", "Unknown key: $key (valid keys: ${PREFERENCE_KEYS.joinToString(" ")})")
            if (values == listOf("path")) {
                validateModuleDir(value)
                moduleDir = value
                continue
            }
            if (value !in values) {
                die(
                    "$key の値が違います: $value（使える値: ${values.joinToString("/")}）",
                    "Invalid value for $key: $value (valid values: ${values.joinToString("/")})",
                )
            }
            patch[key] = JsonPrimitive(value)
        }
        withLock(checkList) {
            if (patch.isNotEmpty()) {
                val error = mergeJsonBlockQuietly(checkList, "checklist", JsonObject(mapOf("preferences" to JsonObject(patch))))
                if (error != null) fail(error, error)
            }
            if (moduleDir.isNotEmpty()) updateChecklistMeta(checkList, "moduleDir", moduleDir)
        }
        say("記録しました: ${rest.joinToString(" ")}", "Recorded: ${rest.joinToString(" ")}")
        return 0
    }

    // show: one line per key, `key = value (recommended / recorded / detected: <file>)`, detected in the project root.
    val recordedRoot = checklistMeta(checkList, "projectRoot")
    val root = if (recordedRoot.isNotEmpty() && resolve(recordedRoot).isDirectory()) resolve(recordedRoot) else cwd
    say(
        "書き方の方針（推奨から変えたいものだけ '$AGENT_COMMAND preferences set key=value' で記録してください）:",
        "Preferences (record only what you want to change from the recommendation with '$AGENT_COMMAND preferences set key=value'):",
    )
    for (key in PREFERENCE_KEYS) {
        var value: String
        var state: String
        if (key == "moduleDir") {
            value = module.dir
            state = if (module.source == ModuleDirSource.CHECKLIST) pick("記録済み", "recorded") else pick("推奨", "recommended")
        } else {
            value = preference(key)
            if (value.isNotEmpty()) {
                state = pick("記録済み", "recorded")
            } else {
                state = pick("推奨", "recommended")
                when (key) {
                    "nestedGroups", "oneRolePerFile" -> value = "yes"
                    "constraintNames" -> value = "plain"
                    "testFramework" -> {
                        val (detected, from) = detectTestFramework(root, module.dir)
                        value = detected
                        if (from != null) state = pick("検出: $from", "detected: $from")
                    }
                    "dependencyStyle" -> if (root.resolve(CATALOG_FILE).isRegularFile()) {
                        value = "catalog"
                        state = pick("検出: $CATALOG_FILE", "detected: $CATALOG_FILE")
                    } else {
                        value = "literal"
                    }
                }
            }
        }
        out("$key = $value${pick("（", " (")}$state${pick("）", ")")}   # ${preferenceQuestionOf(key)} (${preferenceValuesOf(key).joinToString("/")})")
    }
    return 0
}

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
    "nestedGroups" -> pick(Messages.Preferences.nestGroups)
    "moduleDir" -> pick(Messages.Preferences.whereTestModuleGoes)
    "testFramework" -> pick(Messages.Preferences.testFramework)
    "dependencyStyle" -> pick(Messages.Preferences.howDependenciesWritten)
    "constraintNames" -> pick(Messages.Preferences.putQuestionNumberConstraint)
    "oneRolePerFile" -> pick(Messages.Preferences.oneRolePerFile)
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
        warn(Messages.Preferences.couldNotSetMeta(key, value, error))
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
        else -> if (first.contains('=')) "set" else die(Messages.Preferences.preferencesSubcommandsShowSet(first))
    }
    val workdir = requireWorkdir()
    val module = resolveModuleDir()
    val checkList = workdir.resolve(CHECK_LIST_FILE)
    if (!checkList.isRegularFile()) die(Messages.Preferences.notFoundRunInit(fileUri(checkList)))

    if (action == "set") {
        if (rest.isEmpty()) die(Messages.Preferences.giveOneMoreKey)
        // Check everything before writing anything, so that nothing is half recorded.
        val patch = LinkedHashMap<String, JsonPrimitive>()
        var moduleDir = ""
        for (kv in rest) {
            if (!kv.contains('=')) die(Messages.Preferences.preferencesSetTakesKey(kv))
            val key = kv.substringBefore('=')
            val value = kv.substringAfter('=')
            val values = preferenceValuesOf(key)
            if (values.isEmpty()) die(Messages.Preferences.unknownKeyValidKeys(key, PREFERENCE_KEYS.joinToString(" ")))
            if (values == listOf("path")) {
                validateModuleDir(value)
                moduleDir = value
                continue
            }
            if (value !in values) {
                die(Messages.Preferences.invalidValueValidValues(key, value, values.joinToString("/")))
            }
            patch[key] = JsonPrimitive(value)
        }
        withLock(checkList) {
            if (patch.isNotEmpty()) {
                val error = mergeJsonBlockQuietly(checkList, "checklist", JsonObject(mapOf("preferences" to JsonObject(patch))))
                if (error != null) fail(Msg(error, error))
            }
            if (moduleDir.isNotEmpty()) updateChecklistMeta(checkList, "moduleDir", moduleDir)
        }
        say(Messages.Preferences.recorded(rest.joinToString(" ")))
        return 0
    }

    // show: one line per key, `key = value (recommended / recorded / detected: <file>)`, detected in the project root.
    val recordedRoot = checklistMeta(checkList, "projectRoot")
    val root = if (recordedRoot.isNotEmpty() && resolve(recordedRoot).isDirectory()) resolve(recordedRoot) else cwd
    say(Messages.Preferences.preferencesRecordOnlyWhat)
    for (key in PREFERENCE_KEYS) {
        var value: String
        var state: String
        if (key == "moduleDir") {
            value = module.dir
            state = if (module.source == ModuleDirSource.CHECKLIST) pick(Messages.Preferences.recordedLower) else pick(Messages.Preferences.recommended)
        } else {
            value = preference(key)
            if (value.isNotEmpty()) {
                state = pick(Messages.Preferences.recordedLower)
            } else {
                state = pick(Messages.Preferences.recommended)
                when (key) {
                    "nestedGroups", "oneRolePerFile" -> value = "yes"
                    "constraintNames" -> value = "plain"
                    "testFramework" -> {
                        val (detected, from) = detectTestFramework(root, module.dir)
                        value = detected
                        if (from != null) state = pick(Messages.Preferences.detectedFrom(from))
                    }
                    "dependencyStyle" -> if (root.resolve(CATALOG_FILE).isRegularFile()) {
                        value = "catalog"
                        state = pick(Messages.Preferences.detectedCatalog)
                    } else {
                        value = "literal"
                    }
                }
            }
        }
        out(pick(Messages.Preferences.preferenceLine(key, value, state, preferenceQuestionOf(key), preferenceValuesOf(key).joinToString("/"))))
    }
    return 0
}

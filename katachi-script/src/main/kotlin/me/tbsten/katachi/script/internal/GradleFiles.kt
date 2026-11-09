package me.tbsten.katachi.script.internal

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

// Reading and editing the user's Gradle files (settings, the root build file, the version catalog).
// Ported from the install kit's sh, function by function. **These are the user's files**: when one
// is not shaped as expected, nothing is rewritten and the line to add is handed back instead.

/** The ids of the plugins inside kotlin-gradle-plugin; any of them puts kotlin("jvm") on the classpath too. */
internal const val KOTLIN_KGP_IDS: String = "jvm|multiplatform|android|js|kapt"

internal const val KATACHI_PLUGIN_ID: String = "me.tbsten.katachi"

internal const val JUNIT_VERSION: String = "5.14.4"

internal const val KOTEST_VERSION: String = "6.2.5"

// ---------------------------------------------------------------- lines

/** The lines of [this] as awk sees them: a final newline does not make an empty last line. */
internal fun Path.editLines(): List<String> {
    val text = readText()
    if (text.isEmpty()) return emptyList()
    val lines = text.split("\n")
    return if (text.endsWith("\n")) lines.dropLast(1) else lines
}

/** Writes [lines] through a temporary file, every line ending in a newline, as awk prints them. */
internal fun Path.writeEditLines(lines: List<String>) {
    val tmp = resolveSibling("$name.katachi.${ProcessHandle.current().pid()}")
    tmp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n"))
    Files.move(tmp, this, StandardCopyOption.REPLACE_EXISTING)
}

/** Rewrites [file] with [edit], keeping a .bak until it is written and restoring it on failure. */
internal fun ScriptContext.editWithBackup(file: Path, edit: (List<String>) -> List<String>) {
    val bak = file.resolveSibling("${file.name}.bak")
    Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING)
    try {
        file.writeEditLines(edit(file.editLines()))
        bak.deleteIfExists()
    } catch (e: IOException) {
        Files.move(bak, file, StandardCopyOption.REPLACE_EXISTING)
        die(Messages.GradleFiles.failedEditRestored(fileUri(file), e.message))
    }
}

private fun Path.linesOrEmpty(): List<String> = if (isRegularFile()) runCatching { editLines() }.getOrDefault(emptyList()) else emptyList()

/** `sed 's://.*::'`: everything from the first `//`. */
private fun List<String>.naiveStripped(): List<String> = map(::stripLineCommentNaive)

// ---------------------------------------------------------------- finding build files

/**
 * Build files under [root] up to [maxDepth] levels, as `find -maxdepth` sees them (relative,
 * without `./`). Directories named build / node_modules, hidden ones, [skipModule] and [extraPruned]
 * are not entered.
 */
internal fun findBuildFiles(root: Path, skipModule: String?, extraPruned: Set<String> = emptySet(), maxDepth: Int = 6): List<String> {
    val skip = skipModule?.let { root.resolve(it).normalize() }
    val found = mutableListOf<String>()
    Files.walkFileTree(
        root,
        emptySet(),
        maxDepth,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir == root) return FileVisitResult.CONTINUE
                val name = dir.name
                if (name == "build" || name == "node_modules" || name.startsWith(".") || name in extraPruned || dir.normalize() == skip) return FileVisitResult.SKIP_SUBTREE
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (file.name == "build.gradle.kts" || file.name == "build.gradle") found.add(root.relativize(file).toString().replace('\\', '/'))
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        },
    )
    return found
}

// ---------------------------------------------------------------- included builds

private val INCLUDE_BUILD = Regex("includeBuild\\s*\\(?\\s*[\"'][^\"']+[\"']")
private val INCLUDE_BUILD_HEAD = Regex("^includeBuild\\s*\\(?\\s*[\"']")

/**
 * The directories settings pulls in with includeBuild (build-logic and the like), relative, without
 * `.` and anything outside the repository. buildSrc is not one. The kit's `included_build_dirs`; lint
 * has the same extraction.
 */
internal fun includedBuildDirs(root: Path): List<String> {
    val settings = listOf("settings.gradle.kts", "settings.gradle").map(root::resolve).firstOrNull { it.isRegularFile() } ?: return emptyList()
    return settings.linesOrEmpty().naiveStripped()
        .flatMap { line -> INCLUDE_BUILD.findAll(line).map { it.value }.toList() }
        .map {
            it.replaceFirst(INCLUDE_BUILD_HEAD, "").replace(Regex("[\"']$"), "")
                .replace('\\', '/').removePrefix("./").removeSuffix("/")
        }
        .filter { it.isNotEmpty() && it != "." && !it.startsWith("..") }
}

// ---------------------------------------------------------------- plugins { } and buildscript { }

private val BLOCK_START = Regex("^\\s*(plugins|buildscript)\\s*\\{")

/**
 * The lines inside the top-level plugins { } and buildscript { } of [file], line comments removed
 * (only a `//` not after `:`, so `https://` survives). Braces in strings do not count. The kit's
 * `root_plugin_blocks`.
 */
internal fun rootPluginBlocks(file: Path): List<String> {
    val out = mutableListOf<String>()
    var inBlock = false
    var depth = 0
    for (line in file.linesOrEmpty().map(::stripLineComment)) {
        if (!inBlock && depth == 0 && BLOCK_START.containsMatchIn(line)) inBlock = true
        if (inBlock) out += line
        val bare = line.replace(Regex("/\\*[^*]*\\*/"), "")
            .replace(Regex("\"([^\"\\\\]|\\\\.)*\""), "")
            .replace(Regex("'([^'\\\\]|\\\\.)'"), "")
        depth += bare.count { it == '{' } - bare.count { it == '}' }
        if (depth < 0) depth = 0
        if (inBlock && depth == 0) inBlock = false
    }
    return out
}

// ---------------------------------------------------------------- the version catalog

/** One `alias = ...` of a catalog table: the alias as written, its content (group:name, id or value) and version.ref. */
internal class CatalogEntry(val alias: String, val content: String, val versionRef: String)

private fun unquote(s: String) = s.replace(Regex("^[\\s\"']+|[\\s\"']+$"), "")

private fun catalogField(value: String, name: String): String {
    val match = Regex("(^|[{,\\s])$name\\s*=\\s*[\"'][^\"']*[\"']").find(value) ?: return ""
    return unquote(match.value.replaceFirst(Regex("^[^=]*=\\s*"), ""))
}

/** The one-line entries of `[table]` in gradle/libs.versions.toml. The kit's `catalog_entries`. */
internal fun catalogEntries(root: Path, table: String): List<CatalogEntry> {
    val result = mutableListOf<CatalogEntry>()
    var section = ""
    for (raw in root.resolve(CATALOG_FILE).linesOrEmpty()) {
        val line = raw.substringBefore('#')
        if (Regex("^\\s*\\[").containsMatchIn(line)) {
            section = line.replace(Regex("[\\[\\]\\s]"), "")
            continue
        }
        if (section != table || !line.contains('=')) continue
        val key = line.replace(Regex("\\s*=.*"), "").replace(Regex("[\\s\"']"), "")
        val value = line.replaceFirst(Regex("^[^=]*=\\s*"), "")
        var content = ""
        var ref = ""
        if (value.startsWith("\"") || value.startsWith("'")) {
            val v = unquote(value)
            content = if (table == "versions") {
                v
            } else {
                val parts = v.split(":")
                if (table == "libraries" && parts.size >= 2) "${parts[0]}:${parts[1]}" else parts[0]
            }
        } else if (table == "libraries") {
            content = catalogField(value, "module")
            if (content.isEmpty()) {
                val group = catalogField(value, "group")
                val name = catalogField(value, "name")
                if (group.isNotEmpty() && name.isNotEmpty()) content = "$group:$name"
            }
            ref = catalogField(value, "version.ref")
        } else if (table == "plugins") {
            content = catalogField(value, "id")
            ref = catalogField(value, "version.ref")
        }
        if (key.isNotEmpty() && content.isNotEmpty()) result += CatalogEntry(key, content, ref)
    }
    return result
}

/**
 * The accessors (as regex) of the catalog aliases that point at the Kotlin Gradle plugin: the
 * `[plugins]` ones for org.jetbrains.kotlin.<id> and the `[libraries]` ones for kotlin-gradle-plugin,
 * judged by content since an alias can be anything. The kit's `catalog_kotlin_accessors`.
 */
internal fun catalogKotlinAccessors(root: Path, kind: String): List<String> {
    val result = mutableListOf<String>()
    var section = ""
    val pluginHit = Regex("[\"']org\\.jetbrains\\.kotlin\\.($KOTLIN_KGP_IDS)([\"':]|$)")
    for (raw in root.resolve(CATALOG_FILE).linesOrEmpty()) {
        val line = raw.substringBefore('#')
        if (Regex("^\\s*\\[").containsMatchIn(line)) {
            section = line
            continue
        }
        val hit = (kind == "plugins" && Regex("^\\s*\\[plugins]").containsMatchIn(section) && pluginHit.containsMatchIn(line)) ||
            (
                kind == "libraries" && Regex("^\\s*\\[libraries]").containsMatchIn(section) &&
                    (
                        line.contains("org.jetbrains.kotlin:kotlin-gradle-plugin") ||
                            (Regex("org\\.jetbrains\\.kotlin[\"']").containsMatchIn(line) && Regex("name\\s*=\\s*[\"']kotlin-gradle-plugin[\"']").containsMatchIn(line))
                        )
                )
        if (!hit) continue
        val key = line.trimStart().replace(Regex("\\s*=.*"), "").replace(Regex("[\"']"), "").replace(Regex("[-_.]"), "\\\\.")
        result += if (kind == "plugins") "libs\\.plugins\\.$key([^A-Za-z0-9_.]|$)" else "libs\\.$key([^A-Za-z0-9_.]|$)"
    }
    return result
}

/** [catalogKotlinAccessors] of every kind in [kinds] joined with `|`; "" when none. */
internal fun catalogKotlinAccessorsJoined(root: Path, vararg kinds: String): String = kinds.flatMap { catalogKotlinAccessors(root, it) }.joinToString("|")

private fun normAlias(alias: String) = alias.replace('_', '.').replace('-', '.')

private fun catalogAliasFor(root: Path, table: String, content: String) = catalogEntries(root, table).firstOrNull { it.content == content }?.alias

internal fun catalogVersionRefFor(root: Path, table: String, content: String): String = catalogEntries(root, table).firstOrNull { it.content == content }?.versionRef ?: ""

/** The first version.ref of a `[libraries]` entry in [group]. The kit's `catalog_version_ref_for_group`. */
internal fun catalogVersionRefForGroup(root: Path, group: String): String =
    catalogEntries(root, "libraries").firstOrNull { it.content.startsWith("$group:") && it.versionRef.isNotEmpty() }?.versionRef ?: ""

private fun catalogAliasEntry(root: Path, table: String, alias: String) = catalogEntries(root, table).firstOrNull { normAlias(it.alias) == normAlias(alias) }

private fun catalogAliasHasPrefixIn(root: Path, table: String, alias: String): Boolean {
    val a = normAlias(alias)
    return catalogEntries(root, table).any { val k = normAlias(it.alias); k != a && a.startsWith("$k.") }
}

private fun catalogAliasCamel(alias: String): String {
    val parts = alias.split(Regex("[-_.]"))
    return parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
}

/** The Kotlin DSL accessor of [alias]; `.asProvider()` when another alias sits under it. The kit's `catalog_accessor_of_alias`. */
private fun catalogAccessorOfAlias(root: Path, table: String, alias: String): String {
    val a = normAlias(alias)
    val prefix = if (table == "plugins") "libs.plugins." else "libs."
    val grouped = catalogEntries(root, table).any { val k = normAlias(it.alias); k != a && k.startsWith("$a.") }
    return if (grouped) "$prefix$a.asProvider()" else "$prefix$a"
}

/** Whether settings maps `libs` to something other than gradle/libs.versions.toml. The kit's `catalog_is_remapped`. */
internal fun catalogIsRemapped(root: Path, settings: GradleFile): Boolean {
    val text = root.resolve(settings.name).linesOrEmpty().naiveStripped().joinToString("\n")
    if (!Regex("versionCatalogs\\s*\\{").containsMatchIn(text)) return false
    return !Regex("[\"'](\\./)?gradle/libs\\.versions\\.toml[\"']").containsMatchIn(text)
}

/** What scaffold adds to the catalog and the accessors build.gradle.kts uses. The kit's `SC_CAT_*` / `SC_ACC_*`. */
internal class CatalogPlan {
    val versions = mutableListOf<String>()
    val libraries = mutableListOf<String>()
    val plugins = mutableListOf<String>()

    /** Why the catalog cannot be used (ja to en); null when it can. */
    var fallback: Msg? = null
    var plugin = ""
    var katachi = ""
    var konsist = ""
    var junitBom = ""
    var junitJupiter = ""
    var junitLauncher = ""
    var kotest = ""

    val added: Boolean get() = versions.isNotEmpty() || libraries.isNotEmpty() || plugins.isNotEmpty()

    /** `[table] line` for each addition, in the table order. The kit's `catalog_additions`. */
    fun additions(): List<String> = versions.map { "[versions] $it" } + libraries.map { "[libraries] $it" } + plugins.map { "[plugins] $it" }
}

private class CatalogConflict : RuntimeException()

/** Decides what to add to the catalog. The kit's `plan_catalog_entries`. */
internal fun planCatalogEntries(root: Path, version: String, konsist: Boolean, testFramework: String): CatalogPlan {
    val plan = CatalogPlan()

    fun planVersion(alias: String, value: String): String {
        val have = catalogAliasEntry(root, "versions", alias)
        if (have != null) {
            if (have.content == value) return have.alias
            plan.fallback = Messages.GradleFiles.aliasTakenVersion(alias, have.content)
            throw CatalogConflict()
        }
        plan.versions += "$alias = \"$value\""
        return alias
    }

    fun planItem(table: String, wanted: String, content: String, body: String): String {
        catalogAliasFor(root, table, content)?.let { return catalogAccessorOfAlias(root, table, it) }
        fun conflict(alias: String) {
            val other = catalogAliasEntry(root, table, alias)?.content ?: return
            if (other == content) return
            plan.fallback = Messages.GradleFiles.aliasTakenItem(alias, table, other)
            throw CatalogConflict()
        }
        conflict(wanted)
        var alias = wanted
        if (catalogAliasHasPrefixIn(root, table, alias)) {
            val camel = catalogAliasCamel(alias)
            if (camel != alias) {
                conflict(camel)
                alias = camel
            }
        }
        val line = "$alias = { $body }"
        if (table == "libraries") plan.libraries += line else plan.plugins += line
        return catalogAccessorOfAlias(root, table, alias)
    }

    try {
        var ref = catalogVersionRefForGroup(root, "me.tbsten.katachi").ifEmpty { catalogVersionRefFor(root, "plugins", KATACHI_PLUGIN_ID) }
        if (ref.isEmpty()) ref = planVersion("katachi", version)
        plan.plugin = planItem("plugins", "katachi", KATACHI_PLUGIN_ID, "id = \"$KATACHI_PLUGIN_ID\", version.ref = \"$ref\"")
        plan.katachi = planItem("libraries", "katachi", "me.tbsten.katachi:katachi", "module = \"me.tbsten.katachi:katachi\", version.ref = \"$ref\"")
        if (konsist) {
            plan.konsist = planItem("libraries", "katachiKonsist", "me.tbsten.katachi:katachi-konsist", "module = \"me.tbsten.katachi:katachi-konsist\", version.ref = \"$ref\"")
        }
        var junitRef = catalogVersionRefFor(root, "libraries", "org.junit:junit-bom").ifEmpty { catalogVersionRefFor(root, "libraries", "org.junit.jupiter:junit-jupiter") }
        if (junitRef.isEmpty()) junitRef = planVersion("junit", JUNIT_VERSION)
        plan.junitBom = planItem("libraries", "junit-bom", "org.junit:junit-bom", "module = \"org.junit:junit-bom\", version.ref = \"$junitRef\"")
        if (testFramework == "junit5") {
            plan.junitJupiter = planItem("libraries", "junit-jupiter", "org.junit.jupiter:junit-jupiter", "module = \"org.junit.jupiter:junit-jupiter\"")
        }
        plan.junitLauncher = planItem("libraries", "junit-platform-launcher", "org.junit.platform:junit-platform-launcher", "module = \"org.junit.platform:junit-platform-launcher\"")
        if (testFramework == "kotest") {
            var kotestRef = catalogVersionRefForGroup(root, "io.kotest")
            if (kotestRef.isEmpty()) kotestRef = planVersion("kotest", KOTEST_VERSION)
            plan.kotest = planItem("libraries", "kotest-runner-junit5", "io.kotest:kotest-runner-junit5", "module = \"io.kotest:kotest-runner-junit5\", version.ref = \"$kotestRef\"")
        }
    } catch (_: CatalogConflict) {
        // plan.fallback says why; scaffold writes literals instead.
    }
    return plan
}

/**
 * Writes [plan]'s additions at the end of each table (before its trailing blank lines), creating
 * missing tables at the end of the file. The kit's `write_catalog_entries`.
 */
internal fun ScriptContext.writeCatalogEntries(root: Path, plan: CatalogPlan) {
    if (!plan.added) return
    val pending = mapOf("versions" to plan.versions, "libraries" to plan.libraries, "plugins" to plan.plugins)
    editWithBackup(root.resolve(CATALOG_FILE)) { lines ->
        val out = mutableListOf<String>()
        val done = mutableSetOf<String>()
        val blanks = mutableListOf<String>()
        var section = ""
        fun flush() {
            val adding = pending[section].orEmpty()
            if (section.isNotEmpty() && adding.isNotEmpty() && section !in done) {
                out += adding
                done += section
            }
            out += blanks
            blanks.clear()
        }
        for (line in lines) {
            when {
                Regex("^\\s*\\[").containsMatchIn(line) -> {
                    flush()
                    section = line.replace(Regex("[\\[\\]\\s]"), "")
                    out += line
                }

                line.isBlank() -> blanks += line

                else -> {
                    out += blanks
                    blanks.clear()
                    out += line
                }
            }
        }
        flush()
        for ((name, adding) in pending) {
            if (adding.isNotEmpty() && name !in done) {
                out += ""
                out += "[$name]"
                out += adding
            }
        }
        out
    }
}

// ---------------------------------------------------------------- test framework

/** junit5 or kotest, with the file that said kotest. The kit's `detect_test_framework`. */
internal fun detectTestFramework(root: Path, moduleDir: String): Pair<String, String?> {
    if (catalogEntries(root, "libraries").any { it.content.startsWith("io.kotest:") }) return "kotest" to CATALOG_FILE
    val hit = findBuildFiles(root, moduleDir).sortedBy { "./$it" }
        .filter { it.contains('/') }
        .firstOrNull { file -> root.resolve(file).linesOrEmpty().naiveStripped().any { it.contains("io.kotest") } }
    return if (hit != null) "kotest" to hit else "junit5" to null
}

/** The Kotest version to write as a literal. The kit's `kotest_version_literal`. */
internal fun kotestVersionLiteral(root: Path): String {
    val ref = catalogVersionRefForGroup(root, "io.kotest")
    if (ref.isEmpty()) return KOTEST_VERSION
    return catalogEntries(root, "versions").firstOrNull { it.alias == ref }?.content ?: KOTEST_VERSION
}

// ---------------------------------------------------------------- where kotlin("jvm") comes from

/** Whether [file]'s plugins { } / buildscript { } put the Kotlin Gradle plugin on the classpath. The kit's `kotlin_plugin_declared_in`. */
internal fun kotlinPluginDeclaredIn(root: Path, file: Path): Boolean {
    if (!file.isRegularFile()) return false
    var pattern = "org\\.jetbrains\\.kotlin\\.($KOTLIN_KGP_IDS)([\"']|$)|kotlin\\(\"($KOTLIN_KGP_IDS)\"\\)|kotlin-gradle-plugin|libs\\.plugins\\.kotlin[.]?([jJ]vm|[mM]ultiplatform|[aA]ndroid)([^A-Za-z0-9_.]|$)"
    val accessors = catalogKotlinAccessorsJoined(root, "plugins", "libraries")
    if (accessors.isNotEmpty()) pattern += "|$accessors"
    val regex = Regex(pattern)
    return rootPluginBlocks(file).any { regex.containsMatchIn(it) }
}

/** Whether the root build file applies a version-less dotted id (a convention plugin). The kit's `root_applies_convention_plugin`. */
internal fun rootAppliesConventionPlugin(file: Path): Boolean {
    if (!file.isRegularFile()) return false
    return rootPluginBlocks(file)
        .filter { Regex("id[\\s(]+[\"'][^\"']*\\.[^\"']*[\"']").containsMatchIn(it) }
        .filter { !Regex("version\\s+").containsMatchIn(it) }
        .any { !Regex("[\"'](org\\.jetbrains\\.kotlin|com\\.android)\\.").containsMatchIn(it) }
}

/** Whether buildSrc or an applied included build depends on the Kotlin Gradle plugin. The kit's `kotlin_plugin_in_buildsrc`. */
internal fun kotlinPluginInBuildSrc(root: Path, rootBuild: Path): Boolean {
    var pattern = "kotlin-gradle-plugin|kotlin\\(\"gradle-plugin\""
    val accessors = catalogKotlinAccessorsJoined(root, "libraries")
    if (accessors.isNotEmpty()) pattern += "|$accessors"
    val regex = Regex(pattern)
    val dependency = Regex("(implementation|api|runtimeOnly)[\\s(]")
    fun hasIt(dir: String) = listOf("build.gradle.kts", "build.gradle").map { root.resolve(dir).resolve(it) }.any { f ->
        f.linesOrEmpty().naiveStripped().any { dependency.containsMatchIn(it) && regex.containsMatchIn(it) }
    }
    if (hasIt("buildSrc")) return true
    if (!rootAppliesConventionPlugin(rootBuild)) return false
    return includedBuildDirs(root).any(::hasIt)
}

/** Whether a subproject (not in another build) requests a Kotlin plugin with a version. The kit's `kotlin_plugin_requested_by_subprojects`. */
internal fun kotlinPluginRequestedBySubprojects(root: Path, moduleDir: String): Boolean {
    var pattern = "kotlin\\(\"($KOTLIN_KGP_IDS)\"\\)\\s+version|id[\\s(]+[\"']org\\.jetbrains\\.kotlin\\.($KOTLIN_KGP_IDS)[\"']\\)?\\s+version"
    val accessors = catalogKotlinAccessorsJoined(root, "plugins")
    if (accessors.isNotEmpty()) pattern += "|alias\\(($accessors)"
    val regex = Regex(pattern)
    for (file in findBuildFiles(root, moduleDir, extraPruned = setOf("buildSrc"))) {
        var dir = file.substringBeforeLast('/', "")
        if (dir.isEmpty()) continue
        var otherBuild = false
        while (dir.isNotEmpty()) {
            if (root.resolve(dir).resolve("settings.gradle.kts").isRegularFile() || root.resolve(dir).resolve("settings.gradle").isRegularFile()) {
                otherBuild = true
                break
            }
            dir = dir.substringBeforeLast('/', "")
        }
        if (otherBuild) continue
        if (root.resolve(file).linesOrEmpty().naiveStripped().any { regex.containsMatchIn(it) }) return true
    }
    return false
}

internal enum class KotlinPlacement(val cliName: String) { ROOT("root"), ADD("add"), MODULE("module") }

/** How :architecture-test's kotlin("jvm") resolves. The kit's `kotlin_plugin_placement`. */
internal fun kotlinPluginPlacement(root: Path, rootBuild: Path, moduleDir: String): KotlinPlacement = when {
    kotlinPluginDeclaredIn(root, rootBuild) || kotlinPluginInBuildSrc(root, rootBuild) -> KotlinPlacement.ROOT
    kotlinPluginRequestedBySubprojects(root, moduleDir) -> KotlinPlacement.MODULE
    else -> KotlinPlacement.ADD
}

// ---------------------------------------------------------------- editing the root build file

/**
 * Declares the Kotlin JVM plugin with apply false in the root build file. Returns the line to add by
 * hand when the file has a top-level buildscript { } and no plugins { } (null when written). The
 * kit's `add_root_plugin`.
 */
internal fun ScriptContext.addRootPlugin(root: Path, rootBuild: GradleFile, kotlin: String): String? {
    val line = if (rootBuild.dsl == Dsl.GROOVY) {
        "    id 'org.jetbrains.kotlin.jvm' version '$kotlin' apply false"
    } else {
        "    id(\"org.jetbrains.kotlin.jvm\") version \"$kotlin\" apply false"
    }
    val file = root.resolve(rootBuild.name)
    if (!file.isRegularFile()) {
        file.writeEditLines(listOf("plugins {", line, "}"))
        return null
    }
    val lines = file.editLines()
    val pluginsStart = Regex("^plugins\\s*\\{")
    if (lines.any { pluginsStart.containsMatchIn(it) }) {
        editWithBackup(file) { current ->
            val at = current.indexOfFirst { pluginsStart.containsMatchIn(it) }
            current.take(at + 1) + line + current.drop(at + 1)
        }
        return null
    }
    if (lines.any { Regex("^buildscript\\s*\\{").containsMatchIn(it) }) return line
    editWithBackup(file) { current ->
        val out = mutableListOf<String>()
        var placed = false
        var seenImport = false
        for (l in current) {
            if (!placed && Regex("^import\\s").containsMatchIn(l)) {
                out += l
                seenImport = true
                continue
            }
            if (!placed && seenImport && l.isBlank()) {
                out += l
                continue
            }
            if (!placed) {
                out += listOf("plugins {", line, "}", "")
                placed = true
            }
            out += l
        }
        if (!placed) out += listOf("plugins {", line, "}")
        out
    }
    return null
}

// ---------------------------------------------------------------- editing settings

/**
 * Adds the include of [gradlePath] after the last top-level include statement (at the end when there
 * is none). Returns the line to add by hand when the end of that statement cannot be told. The kit's
 * `add_settings_include`.
 */
internal fun addSettingsInclude(root: Path, settings: GradleFile, gradlePath: String): String? {
    val line = if (settings.dsl == Dsl.GROOVY) "include '$gradlePath'" else "include(\"$gradlePath\")"
    val file = root.resolve(settings.name)
    val lines = file.editLines()
    val at = settingsIncludeEnd(lines) ?: return line
    if (at == 0) {
        val text = file.readText()
        file.writeText(text + "\n" + line + "\n")
    } else {
        file.writeEditLines(lines.take(at) + line + lines.drop(at))
    }
    return null
}

/**
 * The line (1-based) where the last top-level include statement ends; 0 when there is none, null
 * when it cannot be told. The kit's `settings_include_end`.
 */
internal fun settingsIncludeEnd(lines: List<String>): Int? {
    var paren = 0
    var brace = 0
    var open = false
    var last = 0
    for ((index, raw) in lines.naiveStripped().withIndex()) {
        val line = raw.trimEnd()
        if (!open && brace == 0 && Regex("^\\s*include[^A-Za-z0-9_]").containsMatchIn(line)) {
            open = true
            paren = 0
        }
        var quote: Char? = null
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (quote != null) {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == quote) quote = null
            } else {
                when (c) {
                    '"', '\'' -> quote = c
                    '(' -> paren++
                    ')' -> paren--
                    '{' -> brace++
                    '}' -> brace--
                }
            }
            i++
        }
        if (open && paren <= 0 && !line.endsWith(",")) {
            open = false
            last = index + 1
        }
    }
    return if (open) null else last
}

/** Where a top-level settings block and its repositories { } are (1-based lines, 0 when absent). The kit's `settings_block_info`. */
internal class SettingsBlock(val start: Int, val end: Int, val repo: Int, val repoEnd: Int, val oneLine: Boolean)

internal fun settingsBlockInfo(lines: List<String>, outer: String): SettingsBlock {
    var start = 0
    var end = 0
    var repo = 0
    var repoEnd = 0
    var oneLine = false
    var depth = 0
    val outerStart = Regex("^\\s*$outer\\s*[{]")
    val repoStart = Regex("^\\s*repositories\\s*[{]")
    for ((index, line) in lines.naiveStripped().withIndex()) {
        val nr = index + 1
        val opened = line.count { it == '{' }
        val closed = line.count { it == '}' }
        if (start == 0 && depth == 0 && outerStart.containsMatchIn(line)) {
            start = nr
            if (opened > 0 && opened == closed) {
                oneLine = true
                if (Regex("[{]\\s*repositories\\s*[{]").containsMatchIn(line)) {
                    repo = nr
                    repoEnd = nr
                }
            }
        }
        if (start != 0 && end == 0 && repo == 0 && depth == 1 && repoStart.containsMatchIn(line)) {
            repo = nr
            if (opened > 0 && opened == closed) {
                oneLine = true
                repoEnd = nr
            }
        }
        depth += opened - closed
        if (repo != 0 && repoEnd == 0 && nr > repo && depth == 1) repoEnd = nr
        if (start != 0 && end == 0 && depth == 0) end = nr
    }
    return SettingsBlock(start, end, repo, repoEnd, oneLine)
}

private fun rangeHas(lines: List<String>, from: Int, to: Int, text: String): Boolean =
    lines.subList((from - 1).coerceAtLeast(0), to.coerceAtMost(lines.size)).naiveStripped().any { it.contains(text) }

/** What pluginManagement { repositories { } } lacks (mavenLocal() only when [mavenLocal]). The kit's `plugin_repositories_missing`. */
internal fun pluginRepositoriesMissing(root: Path, settings: GradleFile, mavenLocal: Boolean): List<String> {
    val lines = root.resolve(settings.name).editLines()
    val info = settingsBlockInfo(lines, "pluginManagement")
    return listOfNotNull("mavenLocal".takeIf { mavenLocal }, "mavenCentral")
        .filter { info.repo == 0 || !rangeHas(lines, info.repo, info.repoEnd, "$it()") }
        .map { "$it()" }
}

/**
 * Adds [wanted] to pluginManagement { repositories { } }. Returns what to add by hand when the block
 * is on one line (null when written). The kit's `add_plugin_repositories`.
 */
internal fun ScriptContext.addPluginRepositories(root: Path, settings: GradleFile, wanted: List<String>): String? {
    val file = root.resolve(settings.name)
    val info = settingsBlockInfo(file.editLines(), "pluginManagement")
    val hasLocal = wanted.any { it.startsWith("mavenLocal") }
    val hasCentral = wanted.any { it.startsWith("mavenCentral") }
    if (info.oneLine) return wanted.joinToString(" ")
    editWithBackup(file) { lines ->
        val out = mutableListOf<String>()
        if (info.start == 0 || info.repo == 0) {
            val lastImport = lines.indexOfLast { Regex("^import\\s").containsMatchIn(it) } + 1
            fun repos() = buildList {
                add("    repositories {")
                if (hasLocal) add("        mavenLocal()")
                add("        gradlePluginPortal()")
                add("        mavenCentral()")
                add("    }")
            }
            fun block() = listOf("pluginManagement {") + repos() + listOf("}", "")
            if (info.start == 0 && lastImport == 0) out += block()
            for ((index, line) in lines.withIndex()) {
                val nr = index + 1
                out += line
                if (info.start == 0 && lastImport > 0 && nr == lastImport) {
                    out += ""
                    out += block()
                }
                if (info.start > 0 && nr == info.start) out += repos()
            }
        } else {
            val indent = lines[info.repo - 1].takeWhile { it.isWhitespace() } + "    "
            for ((index, line) in lines.withIndex()) {
                val nr = index + 1
                if (nr == info.repoEnd && hasCentral) out += "${indent}mavenCentral()"
                out += line
                if (nr == info.repo && hasLocal) out += "${indent}mavenLocal()"
            }
        }
        out
    }
    return null
}

internal enum class DependencyMavenLocal { ADDED, PRESENT, MANUAL }

/** For developers (KATACHI_MAVEN_LOCAL): mavenLocal() for dependencies too. The kit's `add_dependency_maven_local`. */
internal fun addDependencyMavenLocal(root: Path, settings: GradleFile): DependencyMavenLocal {
    val file = root.resolve(settings.name)
    val lines = file.editLines()
    val info = settingsBlockInfo(lines, "dependencyResolutionManagement")
    if (info.start == 0 || info.repo == 0 || info.oneLine) return DependencyMavenLocal.MANUAL
    if (rangeHas(lines, info.repo, info.repoEnd, "mavenLocal()")) return DependencyMavenLocal.PRESENT
    val indent = lines[info.repo - 1].takeWhile { it.isWhitespace() } + "    "
    file.writeEditLines(lines.take(info.repo) + "${indent}mavenLocal()" + lines.drop(info.repo))
    return DependencyMavenLocal.ADDED
}

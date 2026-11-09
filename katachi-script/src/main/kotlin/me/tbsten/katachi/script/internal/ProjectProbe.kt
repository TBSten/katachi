package me.tbsten.katachi.script.internal

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.streams.asSequence

// What a project has to have for katachi, and how the command finds out. Ported from the install
// kit's sh; each function names the shell function it replaces so the two can be read side by side.

/** katachi's artifacts are built with languageVersion 2.2; an older compiler cannot read their metadata. */
internal const val KOTLIN_MIN_MAJOR: Int = 2

internal const val KOTLIN_MIN_MINOR: Int = 2

/** The guide and what scaffold writes use katachi 0.3 features (`"Name".role { }`, capture binding, `-Dkatachi.maxViolations=all`). */
internal const val KATACHI_MIN_MAJOR: Int = 0

internal const val KATACHI_MIN_MINOR: Int = 3

/** Kotlin Gradle Plugin 2.2 needs at least this Gradle. */
internal const val KGP_MIN_GRADLE: String = "7.6.3"

/** The JDK katachi needs, both to run Gradle and as the N of `jvmToolchain(N)`. */
internal const val JDK_MIN_MAJOR: Int = 17

internal const val CATALOG_FILE: String = "gradle/libs.versions.toml"

internal enum class Dsl { KTS, GROOVY }

internal class GradleFile(val name: String, val dsl: Dsl)

/** settings.gradle(.kts) in [root], or stop. The kit's `require_gradle_root`. */
internal fun ScriptContext.requireGradleRoot(root: Path): GradleFile = when {
    root.resolve("settings.gradle.kts").isRegularFile() -> GradleFile("settings.gradle.kts", Dsl.KTS)
    root.resolve("settings.gradle").isRegularFile() -> GradleFile("settings.gradle", Dsl.GROOVY)
    else -> die(Messages.Probe.settingsGradleKtsSettings(fileUri(root)))
}

/** The root build file, or the name to create it under. The kit's `resolve_root_build_file`. */
internal fun rootBuildFile(root: Path, settings: GradleFile): GradleFile = when {
    root.resolve("build.gradle.kts").isRegularFile() -> GradleFile("build.gradle.kts", Dsl.KTS)
    root.resolve("build.gradle").isRegularFile() -> GradleFile("build.gradle", Dsl.GROOVY)
    settings.dsl == Dsl.GROOVY -> GradleFile("build.gradle", Dsl.GROOVY)
    else -> GradleFile("build.gradle.kts", Dsl.KTS)
}

// ---------------------------------------------------------------- git

/** Runs git in [dir]; null when git is not installed. */
internal fun runGit(dir: Path, vararg args: String): ProcessResult? = runProcess(listOf("git") + args, dir)

internal class ProcessResult(val status: Int, val stdout: String)

/** Runs [command] in [dir] with stderr dropped; null when the program is not there. */
internal fun runProcess(command: List<String>, dir: Path, timeoutSeconds: Long = 60): ProcessResult? = try {
    val process = ProcessBuilder(command)
        .directory(dir.toFile())
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
        .start()
    val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        null
    } else {
        ProcessResult(process.exitValue(), output)
    }
} catch (_: IOException) {
    null
}

/** The kit's `is_git_repo`. */
internal fun isGitRepo(dir: Path): Boolean = runGit(dir, "rev-parse", "--is-inside-work-tree")?.status == 0

/** Whether git ignores [path] (relative to [dir]); without git, a plain look at `.gitignore`. The kit's `path_is_ignored`. */
internal fun pathIsIgnored(dir: Path, path: String): Boolean {
    if (isGitRepo(dir)) return runGit(dir, "check-ignore", "-q", path)?.status == 0
    val gitignore = dir.resolve(".gitignore")
    if (!gitignore.isRegularFile()) return false
    val pattern = runCatching { Regex("^/?$path/?$") }.getOrNull() ?: return false
    return gitignore.readLines().any { pattern.containsMatchIn(it) }
}

// ---------------------------------------------------------------- versions

/** "2.4.10" -> (2, 4). Suffixes such as -RC are dropped; null when unreadable. The kit's `kotlin_major_minor`. */
internal fun majorMinor(version: String): Pair<Int, Int>? {
    val match = Regex("^([0-9]+)\\.([0-9]+)").find(version) ?: return null
    return match.groupValues[1].toInt() to match.groupValues[2].toInt()
}

internal fun kotlinTooOld(version: String): Boolean {
    val (major, minor) = majorMinor(version) ?: return false
    return major < KOTLIN_MIN_MAJOR || (major == KOTLIN_MIN_MAJOR && minor < KOTLIN_MIN_MINOR)
}

/** Whether [a] is older than [b], looking at the first three numbers. The kit's `version_lt`. */
internal fun versionLt(a: String, b: String): Boolean {
    val x = a.split(".")
    val y = b.split(".")
    for (i in 0 until 3) {
        val p = x.getOrNull(i)?.let(::leadingNumber) ?: 0
        val q = y.getOrNull(i)?.let(::leadingNumber) ?: 0
        if (p < q) return true
        if (p > q) return false
    }
    return false
}

/** awk's `x + 0`: the leading digits of [text], 0 when there are none. */
private fun leadingNumber(text: String): Int = Regex("^[0-9]+").find(text.trim())?.value?.toIntOrNull() ?: 0

/** The Gradle wrapper's version from gradle-wrapper.properties; null when unreadable. */
internal fun detectGradleWrapperVersion(root: Path): String? {
    val file = root.resolve("gradle/wrapper/gradle-wrapper.properties")
    if (!file.isRegularFile()) return null
    val pattern = Regex("^\\s*distributionUrl\\s*=.*gradle-([0-9][0-9.]*[0-9A-Za-z.-]*)-(bin|all)\\.zip.*")
    return file.readLines().firstNotNullOfOrNull { pattern.find(it)?.groupValues?.get(1) }
}

/** Why an old Kotlin stops katachi and what to ask the user. init and doctor warn with it; scaffold stops with it. */
internal fun ScriptContext.kotlinTooOldMessage(root: Path, version: String, forScaffold: Boolean): String {
    val gradle = detectGradleWrapperVersion(root)
    val min = "$KOTLIN_MIN_MAJOR.$KOTLIN_MIN_MINOR"
    val gradleLine = when {
        gradle == null -> pick(Messages.Probe.kotlinGradlePluginNeeds(min))

        versionLt(gradle, KGP_MIN_GRADLE) -> pick(Messages.Probe.gradleNeedsUpgradingToo(min, gradle))

        else -> pick(Messages.Probe.gradleRecentEnoughKotlin(gradle, min))
    }
    val whenLine = if (forScaffold) {
        pick(Messages.Probe.askUserWhenKotlin)
    } else {
        pick(Messages.Probe.scaffoldStepWillStop)
    }
    return pick(Messages.Probe.projectUsesKotlinKatachi(version, min, whenLine, gradleLine))
}

private val VERSION_TOKEN = "[0-9][0-9.]*[0-9A-Za-z.-]*"

/** The kit's `sed 's://.*::'`: everything from the first `//` on is dropped, URLs included. */
internal fun stripLineCommentNaive(line: String): String = line.substringBefore("//")

/** The kit's `sed -E 's#(^|[^:])//.*#\1#'`: a `//` not right after `:` starts a comment, so `https://` survives. */
internal fun stripLineComment(line: String): String = LINE_COMMENT.replaceFirst(line, "$1")

private val LINE_COMMENT = Regex("(^|[^:])//.*")

/**
 * The Kotlin version the project uses: `kotlin = "..."` under `[versions]` of the catalog, then
 * the plugin declarations of the build files, then `kotlin.version` style keys of gradle.properties.
 * Null when none is readable. The kit's `detect_kotlin_version`.
 */
internal fun detectKotlinVersion(root: Path): String? {
    val catalog = root.resolve(CATALOG_FILE)
    if (catalog.isRegularFile()) {
        var section = ""
        for (line in catalog.readLines()) {
            if (Regex("^\\s*\\[").containsMatchIn(line)) {
                section = line
                continue
            }
            if (Regex("^\\s*\\[versions]").containsMatchIn(section) && Regex("^\\s*kotlin\\s*=").containsMatchIn(line)) {
                val value = Regex("^[^=]*=\\s*\"").replaceFirst(line, "").let { if (it == line) it else it.substringBefore('"') }
                if (Regex("^[0-9]").containsMatchIn(value)) return value
                break
            }
        }
    }

    val buildFiles = Files.walk(root, 3).use { stream ->
        stream.asSequence()
            .filter { it.isRegularFile() && (it.name == "build.gradle.kts" || it.name == "build.gradle") }
            .sorted()
            .toList()
    }
    val pluginLine = Regex("org\\.jetbrains\\.kotlin|kotlin\\(\"(jvm|android|multiplatform)\"\\)")
    val versionCall = Regex(".*version\\s*[(\"']{1,2}($VERSION_TOKEN).*")
    val classpath = Regex(".*kotlin-gradle-plugin:($VERSION_TOKEN).*")
    for (file in buildFiles) {
        for (raw in readLinesLenient(file)) {
            val line = stripLineCommentNaive(raw)
            if (!pluginLine.containsMatchIn(line)) continue
            versionCall.matchEntire(line)?.let { return it.groupValues[1] }
            classpath.matchEntire(line)?.let { return it.groupValues[1] }
        }
    }

    val properties = root.resolve("gradle.properties")
    if (properties.isRegularFile()) {
        val key = Regex("^\\s*(kotlin\\.version|kotlinVersion|kotlin_version)\\s*=\\s*($VERSION_TOKEN).*")
        properties.readLines().firstNotNullOfOrNull { key.matchEntire(it)?.groupValues?.get(2) }?.let { return it }
    }
    return null
}

internal fun readLinesLenient(file: Path): List<String> = runCatching { String(Files.readAllBytes(file), Charsets.UTF_8).lines() }.getOrDefault(emptyList())

// ---------------------------------------------------------------- JDK / toolchain

/** The major of the `java` executable at [java]: 21 for 21.0.9, 8 for 1.8.0_402. Null when unreadable. */
internal fun javaMajorOf(java: Path): Int? {
    val result = try {
        val process = ProcessBuilder(java.toString(), "-version").redirectErrorStream(true).start()
        val text = process.inputStream.readBytes().toString(Charsets.UTF_8)
        process.waitFor(30, TimeUnit.SECONDS)
        text
    } catch (_: IOException) {
        return null
    }
    val pattern = Regex("^.*version \"([0-9]+)\\.?([0-9]*).*\".*")
    val match = result.lineSequence().firstNotNullOfOrNull { pattern.matchEntire(it) } ?: return null
    val a = match.groupValues[1]
    val b = match.groupValues[2]
    return if (a == "1" && b.isNotEmpty()) b.toIntOrNull() else a.toIntOrNull()
}

internal class GradleJdk(val major: Int?, val source: Msg?)

/**
 * The JDK Gradle runs on, in Gradle's own order: the daemon toolchain, org.gradle.java.home,
 * JAVA_HOME, then java on PATH. The kit's `detect_gradle_jdk`.
 */
internal fun ScriptContext.detectGradleJdk(root: Path): GradleJdk {
    val daemon = root.resolve("gradle/gradle-daemon-jvm.properties")
    if (daemon.isRegularFile()) {
        val pattern = Regex("^\\s*toolchainVersion\\s*=\\s*([0-9]+).*")
        daemon.readLines().firstNotNullOfOrNull { pattern.matchEntire(it)?.groupValues?.get(1) }?.let {
            return GradleJdk(it.toInt(), Messages.Probe.toolchainversionGradleGradleDaemon)
        }
    }
    val gradleHome = gradlePropertiesJavaHome(root)
    if (gradleHome != null && Path(gradleHome, "bin", "java").isExecutable()) {
        return GradleJdk(javaMajorOf(Path(gradleHome, "bin", "java")), Messages.Probe.gradleJavaHome(gradleHome))
    }
    val javaHome = env["JAVA_HOME"]?.takeIf { it.isNotEmpty() }
    if (javaHome != null && Path(javaHome, "bin", "java").isExecutable()) {
        return GradleJdk(javaMajorOf(Path(javaHome, "bin", "java")), Messages.Probe.javaHome(javaHome))
    }
    val onPath = findOnPath("java")
    if (onPath != null) return GradleJdk(javaMajorOf(onPath), Messages.Probe.javaPath(onPath))
    return GradleJdk(null, null)
}

/** The kit's `command -v`. */
internal fun ScriptContext.findOnPath(program: String): Path? =
    (env["PATH"] ?: "").split(':').filter { it.isNotEmpty() }
        .map { Path(it, program) }
        .firstOrNull { it.isRegularFile() && it.isExecutable() }

private fun ScriptContext.gradlePropertiesJavaHome(root: Path): String? {
    val userHome = env["GRADLE_USER_HOME"]?.takeIf { it.isNotEmpty() } ?: "${env["HOME"] ?: ""}/.gradle"
    for (file in listOf(root.resolve("gradle.properties"), Path(userHome, "gradle.properties"))) {
        if (!file.isRegularFile()) continue
        val pattern = Regex("^\\s*org\\.gradle\\.java\\.home\\s*=\\s*(.*)")
        file.readLines().firstNotNullOfOrNull { pattern.matchEntire(it)?.groupValues?.get(1) }?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

/** Stops when Gradle's JDK is older than 17; only warns when it cannot be found or read. The kit's `check_gradle_jdk`. */
internal fun ScriptContext.checkGradleJdk(root: Path) {
    val jdk = detectGradleJdk(root)
    if (jdk.source == null) {
        warn(Messages.Probe.couldNotFindJdk)
        return
    }
    val from = pick(jdk.source)
    if (jdk.major == null) {
        warn(Messages.Probe.couldNotReadVersion(from))
        return
    }
    if (jdk.major < JDK_MIN_MAJOR) {
        die(Messages.Probe.jdkRunsGradleKatachi(jdk.major, jdk.source))
    }
    note(Messages.Probe.gradleSJdk(jdk.major, jdk.source))
}

/** The leading number of `libs.versions.<accessor>` under `[versions]`; null when unreadable. The kit's `catalog_version_number`. */
internal fun catalogVersionNumber(root: Path, accessor: String): String? {
    val catalog = root.resolve(CATALOG_FILE)
    if (!catalog.isRegularFile()) return null
    fun norm(s: String) = s.replace(Regex("[-_.]"), ".")
    var section = ""
    for (raw in catalog.readLines()) {
        val line = raw.substringBefore('#')
        if (Regex("^\\s*\\[").containsMatchIn(line)) {
            section = line
            continue
        }
        if (!Regex("^\\s*\\[versions]").containsMatchIn(section) || !line.contains('=')) continue
        val key = line.replace(Regex("\\s*=.*"), "").replace(Regex("[\\s\"']"), "")
        if (norm(key) != norm(accessor)) continue
        val value = line.replaceFirst(Regex("^[^=]*=\\s*"), "")
        Regex("^[\"']([0-9]+)").find(value)?.let { return it.groupValues[1] }
    }
    return null
}

private val TOOLCHAIN_PRUNED = setOf("build", ".gradle", ".git", ".kotlin", ".idea", ".local", "node_modules")

/**
 * Every JDK toolchain setting in the build: `jvmToolchain(N)` and `JavaLanguageVersion.of(N)`,
 * N written as a number, a catalog accessor or a `val` in the same file. Each as (N or null, file).
 * The kit's `jvm_toolchain_candidates`.
 */
internal fun jvmToolchainCandidates(root: Path, moduleDir: String): List<Pair<Int?, String>> {
    val skip = root.resolve(moduleDir).normalize()
    val files = mutableListOf<Path>()
    Files.walkFileTree(
        root,
        object : java.nio.file.SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                if (dir != root && (dir.normalize() == skip || dir.name in TOOLCHAIN_PRUNED)) return java.nio.file.FileVisitResult.SKIP_SUBTREE
                return java.nio.file.FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                if (!attrs.isRegularFile) return java.nio.file.FileVisitResult.CONTINUE
                val name = file.name
                val relative = "./" + root.relativize(file).toString()
                val inBuildLogic = listOf("/buildSrc/", "/build-logic/", "/buildLogic/").any { relative.contains(it) }
                if (name.endsWith(".gradle.kts") || name.endsWith(".gradle") || (name.endsWith(".kt") && inBuildLogic)) files.add(file)
                return java.nio.file.FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): java.nio.file.FileVisitResult = java.nio.file.FileVisitResult.CONTINUE
        },
    )
    val number = listOf(
        Regex(".*jvmToolchain\\s*\\(\\s*\"?([0-9]+)\"?\\s*\\).*"),
        Regex(".*JavaLanguageVersion\\.of\\(\\s*\"?([0-9]+)\"?\\s*\\).*"),
    )
    val identifier = listOf(
        Regex(".*jvmToolchain\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\).*"),
        Regex(".*JavaLanguageVersion\\.of\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\).*"),
    )
    val accessor = Regex(".*libs\\.versions\\.([A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*).*")
    val accessorTail = Regex("(\\.(get|toInt|toString|asProvider|orNull|getOrNull))+$")
    val result = mutableListOf<Pair<Int?, String>>()
    for (file in files.sortedBy { "./" + root.relativize(it).toString() }) {
        val lines = readLinesLenient(file).map(::stripLineComment)
        for (line in lines) {
            if (!line.contains("jvmToolchain") && !line.contains(Regex("JavaLanguageVersion\\.of"))) continue
            var n: String? = number.firstNotNullOfOrNull { it.matchEntire(line)?.groupValues?.get(1) }
            if (n == null) {
                val alias = accessor.matchEntire(line)?.groupValues?.get(1)?.replace(accessorTail, "")
                if (!alias.isNullOrEmpty()) n = catalogVersionNumber(root, alias)
            }
            if (n == null) {
                val id = identifier.firstNotNullOfOrNull { it.matchEntire(line)?.groupValues?.get(1) }
                if (id != null) {
                    val declaration = Regex(".*(const\\s+)?val\\s+${Regex.escape(id)}(\\s*:\\s*Int)?\\s*=\\s*\"?([0-9]+)\"?.*")
                    n = lines.firstNotNullOfOrNull { declaration.matchEntire(it)?.groupValues?.get(3) }
                }
            }
            result += n?.toIntOrNull() to root.relativize(file).toString()
        }
    }
    return result
}

internal class Toolchain(val n: Int?, val source: Msg)

/** The N for `jvmToolchain(N)`: the largest one the project already uses, never below 17. The kit's `detect_jvm_toolchain`. */
internal fun detectJvmToolchain(root: Path, moduleDir: String): Toolchain {
    var best: Pair<Int, String>? = null
    var old: Pair<Int, String>? = null
    var unknown: String? = null
    for ((n, file) in jvmToolchainCandidates(root, moduleDir)) {
        when {
            n == null -> if (unknown == null) unknown = file
            n < JDK_MIN_MAJOR -> if (old == null) old = n to file
            best == null || n > best.first -> best = n to file
        }
    }
    return when {
        best != null -> Toolchain(best.first, Messages.Probe.toolchainFrom(best.first, best.second))
        old != null -> Toolchain(
            null,
            Messages.Probe.notWrittenExistingToolchain(old.first, old.second),
        )
        unknown != null -> Toolchain(
            null,
            Messages.Probe.notWrittenSetsToolchain(unknown),
        )
        else -> Toolchain(null, Messages.Probe.notWrittenProjectSets)
    }
}

// ---------------------------------------------------------------- the katachi version

/** Where the katachi version came from. Only [LATEST] is not someone's choice, so KATACHI_MAVEN_LOCAL never skips its check. */
internal enum class VersionSource { EXPLICIT, INIT, CLI, LATEST }

/** The developer hook KATACHI_MAVEN_LOCAL: the environment variable, or what init recorded. The kit's `maven_local_enabled`. */
internal fun ScriptContext.mavenLocalEnabled(): Boolean {
    if (env["KATACHI_MAVEN_LOCAL"] in setOf("1", "yes", "true")) return true
    return findWorkdir()?.resolve("tmp/cache/maven-local")?.isRegularFile() == true
}

/** Stops when [version] is older than katachi 0.3. The kit's `require_katachi_version`. */
internal fun ScriptContext.requireKatachiVersion(version: String, source: VersionSource) {
    if (source != VersionSource.LATEST && mavenLocalEnabled()) return
    val (major, minor) = majorMinor(version) ?: return
    if (major < KATACHI_MIN_MAJOR || (major == KATACHI_MIN_MAJOR && minor < KATACHI_MIN_MINOR)) {
        val min = "$KATACHI_MIN_MAJOR.$KATACHI_MIN_MINOR.0"
        die(Messages.Probe.katachiTooOldGuide(version, min))
    }
}

/** The katachi version this command was released with, or null for a build that does not know it. */
internal fun cliKatachiVersion(): String? = ScriptVersion.current().takeIf { it != ScriptVersion.UNKNOWN }

/** Whether [path] is a directory with at least one entry. */
internal fun Path.isNonEmptyDirectory(): Boolean = isDirectory() && Files.list(this).use { it.findFirst().isPresent }

/** The text of [path] or "" when it is not a readable file. */
internal fun Path.textOrEmpty(): String = if (isRegularFile()) runCatching { readText() }.getOrDefault("") else ""

internal fun Path.existsAsFile(): Boolean = exists() && isRegularFile()

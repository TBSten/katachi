package me.tbsten.katachi.script.internal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Commands git would rather have; without them some checks are skipped. The kit's `REC_ALL`. */
private fun ScriptContext.warnMissingGit() {
    if (findOnPath("git") != null) return
    err(
        "\n[!] " + pick(Messages.Init.theseCommandsNotFound) + "\n" +
            "    - git: " + pick(Messages.Init.xcodeSelectInstallBrew) + "\n" +
            "      " + pick(Messages.Init.withoutGitCommandCannot),
    )
}

/**
 * `init`: makes the working directory and places the check list, the report and the guide there.
 * The kit's `cmd_init`. The templates and the guide ship with this command, so nothing is
 * downloaded; `--offline` only keeps it from asking GitHub for a version when it knows none.
 */
internal fun ScriptContext.runInit(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("init"), args)
    parsed.positionals.firstOrNull()?.let { die(Messages.Init.unknownOption(it)) }
    val workdirArg = parsed.value("--workdir") ?: ""
    val moduleDirArg = parsed.value("--module-dir")?.also(::validateModuleDir) ?: ""
    // Accept the tag form v0.3.0 too; a kept v would slip past the version check and land in build.gradle.kts.
    var version = parsed.value("--katachi")?.removePrefix("v") ?: ""
    var versionSource = VersionSource.EXPLICIT
    val offline = parsed.has("--offline")
    val force = parsed.has("--force")
    parsed.value("--lang")?.let(::setLangOption)
    warnMissingGit()

    val root = cwd
    val settings = requireGradleRoot(root)
    say(Messages.Init.startingKatachiInstallation)
    note(Messages.Init.projectRoot(fileUri(root)))
    note(Messages.Init.settings(fileUri(settings.name)))

    // The version: the one this command was released with, unless --katachi says otherwise.
    if (version.isEmpty()) {
        cliKatachiVersion()?.let {
            version = it
            versionSource = VersionSource.CLI
        }
    }
    if (version.isEmpty() && !offline) {
        version = fetchLatestVersion() ?: ""
        versionSource = VersionSource.LATEST
    }
    if (version.isEmpty()) {
        warn(Messages.Init.couldNotTellKatachi)
        version = "UNKNOWN"
    } else {
        requireKatachiVersion(version, versionSource)
        note("katachi:            $version")
    }
    if (mavenLocalEnabled()) {
        note(Messages.Init.developersScaffoldAddsMavenlocal)
    }

    val git = isGitRepo(root)
    if (git) {
        note(Messages.Init.gitUnderVersionControl)
    } else {
        warn(Messages.Init.projectNotUnderGit)
    }

    // The working directory: inside an ignored place when there is one, otherwise tmp/.
    var workdirName = workdirArg
    if (workdirName.isEmpty()) {
        workdirName = listOf(".local", ".tmp", "tmp", ".scratch").firstOrNull { pathIsIgnored(root, it) }?.let { "$it/install-katachi" } ?: "tmp/install-katachi"
    }
    val workdir = resolve(workdirName)
    val cache = workdir.resolve("tmp/cache")
    cache.createDirectories()
    val workdirUri = fileUri(workdir)
    note(Messages.Init.workingDirectory(workdirUri))
    val existing = workdir.listDirectoryEntries().count { it.name !in setOf(CHECK_LIST_FILE, REPORT_FILE, "tmp") && !it.name.endsWith(".bak") }
    if (existing > 0) note(Messages.Init.thereExistingFilesThey(existing))

    // The test module: --module-dir > meta.moduleDir of a kept check list > the default.
    val checkList = workdir.resolve(CHECK_LIST_FILE)
    val report = workdir.resolve(REPORT_FILE)
    val recordedModuleDir = checklistMeta(checkList, "moduleDir")
    val moduleDir = when {
        moduleDirArg.isNotEmpty() -> moduleDirArg
        recordedModuleDir.isNotEmpty() && !force -> recordedModuleDir.also(::validateModuleDir)
        else -> MODULE_DIR_DEFAULT
    }
    val modulePath = moduleGradlePathOf(moduleDir)
    note(Messages.Init.testModuleGradlePath(moduleDir, modulePath))

    // katachi checks untracked files that are not ignored, so a visible working directory fails step 4 for sure.
    if (!pathIsIgnored(root, workdirName)) {
        warn(Messages.Init.visibleGitStepWill(workdirUri))
    }
    if (git) {
        val untracked = runGit(root, "ls-files", "--others", "--exclude-standard", "--directory", "--no-empty-directory")?.stdout.orEmpty()
            .lines().filter { it.isNotEmpty() }.map { it.removeSuffix("/") }
            .filter { it != workdirName && !"$workdirName/".startsWith("$it/") && !"$it/".startsWith("$workdirName/") }
        if (untracked.isNotEmpty()) {
            warn(Messages.Init.thereUntrackedFilesNot)
            for (entry in untracked) err("       ${fileUri(entry)}")
        }
    }

    val kotlin = detectKotlinVersion(root)
    var kotlinOld = false
    if (kotlin != null) {
        note(Messages.Init.kotlinDetectedProject(kotlin))
        if (kotlinTooOld(kotlin)) {
            kotlinOld = true
            warn(kotlinTooOldMessage(root, kotlin, forScaffold = false))
        }
    } else {
        warn(Messages.Init.couldNotDetectKotlin)
    }

    // What later commands read back: the language, the docs origin, the developer hook and the version.
    val manifest = cache.resolve("MANIFEST")
    cache.resolve("lang").writeText(lang.code)
    val docs = env["KATACHI_DOCS"]?.takeIf { it.isNotEmpty() } ?: KATACHI_DOCS_DEFAULT
    if (docs == KATACHI_DOCS_DEFAULT) {
        cache.resolve("docs").deleteIfExists()
    } else {
        cache.resolve("docs").writeText(docs)
        note(Messages.Init.docsSourceKatachiDocs(docs))
    }
    if (env["KATACHI_MAVEN_LOCAL"] in setOf("1", "yes", "true")) cache.resolve("maven-local").writeText("yes")
    // Without --katachi, a rerun keeps the version the previous init recorded.
    val versionFile = cache.resolve("version")
    val recordedVersion = versionFile.textIfNotEmpty() ?: ""
    if (versionSource != VersionSource.EXPLICIT && recordedVersion.isNotEmpty() && !force) {
        if (recordedVersion != version) {
            note(Messages.Init.keepingRecordedPreviousInit(recordedVersion, version))
        }
        version = recordedVersion
    } else if (version == "UNKNOWN") {
        versionFile.deleteIfExists()
    } else {
        versionFile.writeText(version)
    }

    // The check list and the report. **What is already there is never overwritten**, so a rerun keeps what was filled in.
    if (checkList.exists() && checkList.fileSize() > 0 && !force) {
        note(Messages.Init.keptAlreadyThere(fileUri(checkList)))
        if (version != "UNKNOWN") updateChecklistMeta(checkList, "version", version)
        if (moduleDir != recordedModuleDir) updateChecklistMeta(checkList, "moduleDir", moduleDir)
        if (kotlin != null) updateChecklistMeta(checkList, "kotlin", kotlin)
    } else {
        placeBundled(Bundle.CHECK_LIST_TEMPLATE, checkList, manifest)
        writeChecklistData(checkList, workdirName, version, root, kotlin ?: "", if (git) "yes" else "no", moduleDir)
        note(Messages.Init.placed(fileUri(checkList)))
    }
    if (report.exists() && report.fileSize() > 0 && !force) {
        note(Messages.Init.keptReport(fileUri(report)))
    } else {
        placeBundled(Bundle.REPORT_TEMPLATE, report, manifest)
        writeReportData(report, root)
        note(Messages.Init.placedReport(fileUri(report)))
    }
    // The guide too, for people to open; `katachi guide <id>` prints it section by section. Rewritten every time, so a language change leaves nothing behind.
    placeBundled(Bundle.GUIDE, cache.resolve("index.md"), manifest)
    note(Messages.Init.guideGuideTableContents(fileUri(cache.resolve("index.md"))))

    say("")
    say(Messages.Init.initResult)
    say("KATACHI_WORKDIR=$workdir")
    say("KATACHI_VERSION=$version")
    say("KATACHI_PROJECT_ROOT=$root")
    say("KATACHI_KOTLIN=${kotlin ?: ""}")
    say("KATACHI_GIT=${if (git) "yes" else "no"}")
    say("KATACHI_SETTINGS=${root.resolve(settings.name)}")
    say("KATACHI_LANG=${lang.code}")
    say("KATACHI_MODULE_DIR=$moduleDir")
    say("KATACHI_MODULE_PATH=$modulePath")
    say("========================================================")
    say("")
    say(Messages.Init.nowRunCommandsSubcommand(workdir))
    say("")
    if (kotlinOld) {
        say(Messages.Init.nextAskUserWhether(kotlin))
    } else {
        say(Messages.Init.nextCheckReadSection)
    }
    return 0
}

/**
 * Writes the bundled [name] to [dest]. The pages link to each other by their published names, and the
 * working directory keeps them under other names, so the links are rewritten (the kit's `relink_fetched_page`).
 */
internal fun ScriptContext.placeBundled(name: String, dest: Path, manifest: Path?) {
    var text = Bundle.text(lang, name)
    if (dest.name.endsWith(".html")) {
        text = text.replace("\"./install-check-list.html\"", "\"./$CHECK_LIST_FILE\"")
            .replace("\"./project-code-base-report-template.html\"", "\"./$REPORT_FILE\"")
    }
    dest.parent?.createDirectories()
    val tmp = dest.resolveSibling("${dest.name}.part.${ProcessHandle.current().pid()}")
    tmp.writeText(text)
    Files.move(tmp, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    recordInManifest(manifest, Bundle.origin(lang, name), dest)
}

/** Fills the check list's meta with what init knows, by deep merge (a `data set` would drop the template's steps). The kit's `write_checklist_data`. */
private fun ScriptContext.writeChecklistData(file: Path, workdir: String, version: String, root: Path, kotlin: String, git: String, moduleDir: String) {
    if (!file.isRegularFile()) return
    val now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").format(ZonedDateTime.now(clock))
    val meta = linkedMapOf(
        "workdir" to jsonValue(workdir),
        "version" to jsonValue(version),
        "projectRoot" to jsonValue(root.toString()),
        "ranAt" to jsonValue(now),
        "kotlin" to jsonValue(kotlin),
        "git" to jsonValue(git),
        "moduleDir" to jsonValue(moduleDir),
    )
    val error = mergeJsonBlockQuietly(file, "checklist", JsonObject(mapOf("meta" to JsonObject(meta))))
    if (error != null) {
        warn(Messages.Init.couldNotInitializeMeta(fileUri(file), error))
    }
}

/** The report's meta that can be known mechanically; otherwise verify keeps counting five blanks. The kit's `write_report_data`. */
private fun ScriptContext.writeReportData(file: Path, root: Path) {
    if (!file.isRegularFile()) return
    val now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ").format(ZonedDateTime.now(clock))
    val namePattern = Regex("^\\s*rootProject\\.name\\s*=\\s*[\"']([^\"']*)[\"'].*")
    var name = ""
    for (settings in listOf("settings.gradle.kts", "settings.gradle").map(root::resolve)) {
        if (!settings.isRegularFile()) continue
        name = settings.readText().lines().firstNotNullOfOrNull { namePattern.matchEntire(it)?.groupValues?.get(1) } ?: ""
        if (name.isNotEmpty()) break
    }
    var count: JsonPrimitive = jsonValue(null)
    var gitRoot = ""
    if (isGitRepo(root)) {
        runGit(root, "ls-files")?.stdout?.let { out -> count = JsonPrimitive(out.lines().count { it.isNotEmpty() }) }
        gitRoot = runGit(root, "rev-parse", "--show-toplevel")?.stdout?.trim().orEmpty()
    }
    val meta = linkedMapOf(
        "projectName" to jsonValue(name),
        "analyzedAt" to jsonValue(now),
        "fileCount" to count,
        "gradleRoot" to jsonValue(root.toString()),
        "gitRoot" to jsonValue(gitRoot),
    )
    val error = mergeJsonBlockQuietly(file, "report", JsonObject(mapOf("meta" to JsonObject(meta))))
    if (error != null) {
        warn(Messages.Init.couldNotInitializeReportMeta(fileUri(file), error))
    }
}

/** `doctor`: what this command needs, Gradle's JDK and the project's Kotlin. The kit's `cmd_doctor`. */
internal fun ScriptContext.runDoctor(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("doctor"), args)
    parsed.positionals.firstOrNull()?.let { die(Messages.Init.doctorUnknownOption(it)) }
    parsed.value("--lang")?.let(::setLangOption)
    warnMissingGit()
    say(Messages.Init.everythingNeedsAvailableKatachi(ScriptVersion.current()))
    checkGradleJdk(cwd)
    val kotlin = detectKotlinVersion(cwd)
    if (kotlin != null) {
        if (kotlinTooOld(kotlin)) warn(kotlinTooOldMessage(cwd, kotlin, forScaffold = false)) else note("Kotlin: $kotlin")
    }
    return 0
}

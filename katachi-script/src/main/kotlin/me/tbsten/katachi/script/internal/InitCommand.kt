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
        "\n[!] " + pick("次のコマンドが見つかりません。無くても進めますが、あったほうが確実です:", "These commands were not found. You can go on without them, but it is safer to have them:") + "\n" +
            "    - git: " + pick(
                "macOS: xcode-select --install か brew install git / Debian・Ubuntu: sudo apt-get install git / Fedora: sudo dnf install git / Alpine: apk add git",
                "macOS: xcode-select --install or brew install git / Debian, Ubuntu: sudo apt-get install git / Fedora: sudo dnf install git / Alpine: apk add git",
            ) + "\n" +
            "      " + pick("git が無いと Git 管理下かを判定できず、未追跡のファイルの確認も飛ばします。", "Without git, the command cannot tell whether the project is under Git, and skips the check for untracked files."),
    )
}

/**
 * `init`: makes the working directory and places the check list, the report and the guide there.
 * The kit's `cmd_init`. The templates and the guide ship with this command, so nothing is
 * downloaded; `--offline` only keeps it from asking GitHub for a version when it knows none.
 */
internal fun ScriptContext.runInit(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("init"), args)
    parsed.positionals.firstOrNull()?.let { die("init: 知らないオプションです: $it", "init: unknown option: $it") }
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
    say("katachi のインストールを開始します", "Starting the katachi installation")
    note("プロジェクトルート: ${fileUri(root)}", "Project root:       ${fileUri(root)}")
    note("settings:          ${fileUri(settings.name)}", "settings:           ${fileUri(settings.name)}")

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
        warn(
            "katachi のバージョンが分かりませんでした。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。",
            "Could not tell the katachi version. Look it up at $KATACHI_RELEASES_PAGE and pass it with --katachi.",
        )
        version = "UNKNOWN"
    } else {
        requireKatachiVersion(version, versionSource)
        note("katachi:            $version")
    }
    if (mavenLocalEnabled()) {
        note("mavenLocal:         有効（開発者向け。scaffold が mavenLocal() を足す）", "mavenLocal:         on (for developers; scaffold adds mavenLocal())")
    }

    val git = isGitRepo(root)
    if (git) {
        note("Git:               管理下にあります", "Git:                under version control")
    } else {
        warn(
            "Git で管理されていません。失敗したときに変更を戻せないので、先にコミットするかバックアップを取ってください。",
            "The project is not under Git. You cannot undo the changes if something goes wrong, so commit or back up first.",
        )
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
    note("作業用ディレクトリ:  $workdirUri", "Working directory:  $workdirUri")
    val existing = workdir.listDirectoryEntries().count { it.name !in setOf(CHECK_LIST_FILE, REPORT_FILE, "tmp") && !it.name.endsWith(".bak") }
    if (existing > 0) note("既存のファイルがあります（$existing 件）。上書きはしません。", "There are existing files ($existing). They are not overwritten.")

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
    note("検査用モジュール:    $moduleDir/（Gradle のパス $modulePath）", "Test module:        $moduleDir/ (Gradle path $modulePath)")

    // katachi checks untracked files that are not ignored, so a visible working directory fails step 4 for sure.
    if (!pathIsIgnored(root, workdirName)) {
        warn(
            "$workdirUri は git から見えています。このままだとステップ4で必ず失敗します。\n" +
                "    katachi は未追跡でも ignore されていないファイルを検査するため、この作業用ディレクトリ\n" +
                "    自体が [UnexpectedFile] になります。ユーザに .gitignore への追加を提案してください\n" +
                "    （勝手に書き換えないこと）。",
            "$workdirUri is visible to git. As it is, step 4 will fail for sure.\n" +
                "    katachi checks untracked files that are not ignored, so this working directory itself\n" +
                "    becomes an [UnexpectedFile]. Suggest to the user that it be added to .gitignore\n" +
                "    (do not edit .gitignore yourself).",
        )
    }
    if (git) {
        val untracked = runGit(root, "ls-files", "--others", "--exclude-standard", "--directory", "--no-empty-directory")?.stdout.orEmpty()
            .lines().filter { it.isNotEmpty() }.map { it.removeSuffix("/") }
            .filter { it != workdirName && !"$workdirName/".startsWith("$it/") && !"$it/".startsWith("$workdirName/") }
        if (untracked.isNotEmpty()) {
            warn(
                "未追跡で ignore もされていないものがあります。これらもステップ4で [UnexpectedFile] になります。\n    宣言するか、.gitignore に足すかをユーザに確認してください。",
                "There are untracked files that are not ignored. These also become [UnexpectedFile] in step 4.\n    Ask the user whether to declare them or add them to .gitignore.",
            )
            for (entry in untracked) err("       ${fileUri(entry)}")
        }
    }

    val kotlin = detectKotlinVersion(root)
    var kotlinOld = false
    if (kotlin != null) {
        note("Kotlin:             $kotlin（プロジェクトから検出）", "Kotlin:             $kotlin (detected from the project)")
        if (kotlinTooOld(kotlin)) {
            kotlinOld = true
            warn(kotlinTooOldMessage(root, kotlin, forScaffold = false))
        }
    } else {
        warn("Kotlin のバージョンを検出できませんでした。scaffold に --kotlin で渡してください。", "Could not detect the Kotlin version. Pass it to scaffold with --kotlin.")
    }

    // What later commands read back: the language, the docs origin, the developer hook and the version.
    val manifest = cache.resolve("MANIFEST")
    cache.resolve("lang").writeText(lang.code)
    val docs = env["KATACHI_DOCS"]?.takeIf { it.isNotEmpty() } ?: KATACHI_DOCS_DEFAULT
    if (docs == KATACHI_DOCS_DEFAULT) {
        cache.resolve("docs").deleteIfExists()
    } else {
        cache.resolve("docs").writeText(docs)
        note("配信元:             $docs（KATACHI_DOCS。以降のコマンドもこれを使う）", "Docs source:        $docs (KATACHI_DOCS; later commands use it too)")
    }
    if (env["KATACHI_MAVEN_LOCAL"] in setOf("1", "yes", "true")) cache.resolve("maven-local").writeText("yes")
    // Without --katachi, a rerun keeps the version the previous init recorded.
    val versionFile = cache.resolve("version")
    val recordedVersion = versionFile.textIfNotEmpty() ?: ""
    if (versionSource != VersionSource.EXPLICIT && recordedVersion.isNotEmpty() && !force) {
        if (recordedVersion != version) {
            note(
                "                    → 前回の init が記録した $recordedVersion を使い続けます（今回の既定は $version。変えるなら --katachi を付けて実行し直す）",
                "                    → keeping $recordedVersion, recorded by the previous init (this time's default is $version; to change it, run again with --katachi)",
            )
        }
        version = recordedVersion
    } else if (version == "UNKNOWN") {
        versionFile.deleteIfExists()
    } else {
        versionFile.writeText(version)
    }

    // The check list and the report. **What is already there is never overwritten**, so a rerun keeps what was filled in.
    if (checkList.exists() && checkList.fileSize() > 0 && !force) {
        note("すでにあるので残しました: ${fileUri(checkList)}", "Kept (already there): ${fileUri(checkList)}")
        if (version != "UNKNOWN") updateChecklistMeta(checkList, "version", version)
        if (moduleDir != recordedModuleDir) updateChecklistMeta(checkList, "moduleDir", moduleDir)
        if (kotlin != null) updateChecklistMeta(checkList, "kotlin", kotlin)
    } else {
        placeBundled(Bundle.CHECK_LIST_TEMPLATE, checkList, manifest)
        writeChecklistData(checkList, workdirName, version, root, kotlin ?: "", if (git) "yes" else "no", moduleDir)
        note("配置しました:        ${fileUri(checkList)}", "Placed:             ${fileUri(checkList)}")
    }
    if (report.exists() && report.fileSize() > 0 && !force) {
        note("すでにあるので残しました: ${fileUri(report)}", "Kept (already there): ${fileUri(report)}")
    } else {
        placeBundled(Bundle.REPORT_TEMPLATE, report, manifest)
        writeReportData(report, root)
        note("配置しました:        ${fileUri(report)}", "Placed:             ${fileUri(report)}")
    }
    // The guide too, for people to open; `katachi guide <id>` prints it section by section. Rewritten every time, so a language change leaves nothing behind.
    placeBundled(Bundle.GUIDE, cache.resolve("index.md"), manifest)
    note(
        "手順書:             ${fileUri(cache.resolve("index.md"))} （$CLI_COMMAND guide で目次）",
        "Guide:              ${fileUri(cache.resolve("index.md"))} ($CLI_COMMAND guide for the table of contents)",
    )

    say("")
    say("== init の結果 ==========================================", "== init result =========================================")
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
    say(
        "以降のコマンドは、このプロジェクトのルートで '$CLI_COMMAND <サブコマンド>' として実行してください（作業用ディレクトリは自動で見つけます。別の場所からは KATACHI_WORKDIR=$workdir を付ける）。",
        "From now on, run '$CLI_COMMAND <subcommand>' in the root of this project (it finds the working directory itself; from elsewhere, set KATACHI_WORKDIR=$workdir).",
    )
    say("")
    if (kotlinOld) {
        say(
            "次: Kotlin $kotlin を上げてよいかをユーザに確認してください（上の [!]。手順書 0-2 の「Kotlin が古いとき」）。上げるまでステップ 1 に進まないこと。",
            "Next: ask the user whether Kotlin $kotlin may be upgraded (the [!] above; \"When Kotlin is too old\" in step 0-2 of the guide). Do not go on to step 1 until it is upgraded.",
        )
    } else {
        say(
            "次: $CLI_COMMAND check 0-1 0-2（続けて、出力の「次:」のセクションを読む）",
            "Next: $CLI_COMMAND check 0-1 0-2 (then read the section its \"Next:\" line names)",
        )
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
        warn(
            "チェックリストのメタ欄を初期化できませんでした。${fileUri(file)} を直接確認してください。\n$error",
            "Could not initialize the meta fields of the check list. Check ${fileUri(file)} directly.\n$error",
        )
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
        warn(
            "レポートのメタ欄を初期化できませんでした。${fileUri(file)} を直接確認してください。\n$error",
            "Could not initialize the meta fields of the report. Check ${fileUri(file)} directly.\n$error",
        )
    }
}

/** `doctor`: what this command needs, Gradle's JDK and the project's Kotlin. The kit's `cmd_doctor`. */
internal fun ScriptContext.runDoctor(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("doctor"), args)
    parsed.positionals.firstOrNull()?.let { die("doctor: 知らないオプションです: $it", "doctor: unknown option: $it") }
    parsed.value("--lang")?.let(::setLangOption)
    warnMissingGit()
    say(
        "必要なものはそろっています（katachi ${ScriptVersion.current()}。ガイドとテンプレートは同梱）。",
        "Everything this needs is available (katachi ${ScriptVersion.current()}; the guide and the templates ship with it).",
    )
    checkGradleJdk(cwd)
    val kotlin = detectKotlinVersion(cwd)
    if (kotlin != null) {
        if (kotlinTooOld(kotlin)) warn(kotlinTooOldMessage(cwd, kotlin, forScaffold = false)) else note("Kotlin: $kotlin")
    }
    return 0
}

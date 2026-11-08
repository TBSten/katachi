package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeText

private val PACKAGE_NAME = Regex("^[a-z][a-zA-Z0-9_]*(\\.[a-z][a-zA-Z0-9_]*)*$")

/** A value with where it came from, in both languages. */
private class Sourced(val value: String, val ja: String, val en: String)

/**
 * `scaffold --package <pkg> ...`: creates the test module (build.gradle.kts, ProjectArchitecture.kt,
 * the test) and wires it into settings, the root build file and the catalog. Runs in the Gradle
 * root. The kit's `cmd_scaffold`.
 */
internal fun ScriptContext.runScaffold(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("scaffold"), args)
    parsed.positionals.firstOrNull()?.let { die("scaffold: 知らないオプションです: $it", "scaffold: unknown option: $it") }
    val pkg = parsed.value("--package") ?: die("--package が必要です（例: --package com.example.app）", "--package is required (for example: --package com.example.app)")
    if (!PACKAGE_NAME.matches(pkg)) die("--package がパッケージ名として不正です: $pkg", "--package is not a valid package name: $pkg")
    var version = parsed.value("--katachi")?.removePrefix("v") ?: ""
    var kotlin = parsed.value("--kotlin") ?: ""
    val konsist = !parsed.has("--no-konsist")
    val force = parsed.has("--force")
    val dryRun = parsed.has("--dry-run")
    val tfArg = parsed.value("--test-framework")
    val depsArg = parsed.value("--deps")

    val root = cwd
    val settings = requireGradleRoot(root)
    val rootBuild = rootBuildFile(root, settings)

    val module = resolveModuleDir(parsed.value("--module-dir"))
    if (module.source == ModuleDirSource.ARG && module.recorded.isNotEmpty() && module.recorded != module.dir) {
        note(
            "--module-dir ${module.dir} は init の記録（meta.moduleDir = ${module.recorded}）と違います。記録を ${module.dir} に書き換えます。",
            "--module-dir ${module.dir} differs from what init recorded (meta.moduleDir = ${module.recorded}). The record is updated to ${module.dir}.",
        )
    }
    checkGradleJdk(root)
    val toolchain = detectJvmToolchain(root, module.dir)

    // The version: --katachi > what init recorded > the one this command was released with > GitHub's latest.
    var versionSource = VersionSource.EXPLICIT
    if (version.isEmpty()) {
        findWorkdir()?.resolve("tmp/cache/version")?.textIfNotEmpty()?.let {
            version = it
            versionSource = VersionSource.INIT
        }
    }
    if (version.isEmpty()) {
        cliKatachiVersion()?.let {
            version = it
            versionSource = VersionSource.CLI
        }
    }
    if (version.isEmpty()) {
        version = fetchLatestVersion() ?: ""
        versionSource = VersionSource.LATEST
    }
    if (version.isEmpty()) {
        die(
            "katachi のバージョンが分かりません。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。",
            "Cannot tell the katachi version. Look it up at $KATACHI_RELEASES_PAGE and pass it with --katachi.",
        )
    }
    requireKatachiVersion(version, versionSource)
    val mavenLocal = mavenLocalEnabled()

    if (kotlin.isEmpty()) kotlin = detectKotlinVersion(root) ?: ""
    if (kotlin.isEmpty()) die("Kotlin のバージョンを検出できませんでした。--kotlin で指定してください。", "Could not detect the Kotlin version. Pass it with --kotlin.")
    val (kotlinMajor, kotlinMinor) = majorMinor(kotlin) ?: die("Kotlin のバージョンを読み取れませんでした: $kotlin", "Could not read the Kotlin version: $kotlin")
    if (kotlinMajor < KOTLIN_MIN_MAJOR || (kotlinMajor == KOTLIN_MIN_MAJOR && kotlinMinor < KOTLIN_MIN_MINOR)) {
        val message = kotlinTooOldMessage(root, kotlin, forScaffold = true)
        die("$message\n    上げてから、scaffold をもう一度実行してください。", "$message\n    After upgrading, run scaffold again.")
    }
    // Context parameters entered the language in 2.4; 2.2 / 2.3 need -Xcontext-parameters, and 2.4+ warns that it is redundant.
    val contextFlag = kotlinMajor == 2 && kotlinMinor < 4

    if (root.resolve(module.dir).exists() && !force) {
        die("${fileUri(module.dir)}/ がすでに存在します。上書きするなら --force を付けてください。", "${fileUri(module.dir)}/ already exists. Add --force to overwrite it.")
    }
    val srcDir = "${module.dir}/src/test/kotlin/${pkg.replace('.', '/')}/test/architecture"

    val placement = kotlinPluginPlacement(root, root.resolve(rootBuild.name), module.dir)
    val rootNeedsPlugin = placement == KotlinPlacement.ADD
    var kotlinPlugin = if (placement == KotlinPlacement.MODULE) "kotlin(\"jvm\") version \"$kotlin\"" else "kotlin(\"jvm\")"

    // The test framework: --test-framework > preferences.testFramework > detected > junit5.
    val (detectedTf, detectedTfFrom) = detectTestFramework(root, module.dir)
    val testFramework = when {
        tfArg != null -> Sourced(tfArg, "--test-framework", "--test-framework")
        else -> {
            val pref = preference("testFramework")
            when {
                pref in TEST_FRAMEWORKS -> Sourced(pref, "preferences.testFramework", "preferences.testFramework")
                else -> {
                    if (pref.isNotEmpty()) {
                        warn(
                            "preferences.testFramework の値を知りません（$pref。junit5 / kotest / kotest-free など）。検出した値を使います。",
                            "Unknown value in preferences.testFramework ($pref; expected junit5, kotest, kotest-free and so on). Using the detected value instead.",
                        )
                    }
                    if (detectedTfFrom != null) {
                        Sourced(detectedTf, "検出: $detectedTfFrom", "detected from $detectedTfFrom")
                    } else {
                        Sourced(detectedTf, "既定。kotest の依存が見つからない", "the default; no kotest dependency found")
                    }
                }
            }
        }
    }
    // `kotest-free` is kotest with the test written as a FreeSpec; plain `kotest` is a FunSpec (KATACHI-301).
    val tf = if (testFramework.value.startsWith("kotest")) "kotest" else testFramework.value
    val kotestStyle = if (tf == "kotest") testFramework.value.removePrefix("kotest").removePrefix("-").ifEmpty { DEFAULT_KOTEST_STYLE } else DEFAULT_KOTEST_STYLE

    // How dependencies are written: --deps > preferences.dependencyStyle > catalog when there is one > literal.
    var deps = when {
        depsArg != null -> Sourced(depsArg, "--deps", "--deps")
        else -> {
            val pref = preference("dependencyStyle")
            when {
                pref == "catalog" || pref == "literal" -> Sourced(pref, "preferences.dependencyStyle", "preferences.dependencyStyle")
                else -> {
                    if (pref.isNotEmpty()) {
                        warn(
                            "preferences.dependencyStyle の値を知りません（$pref。catalog か literal）。検出した値を使います。",
                            "Unknown value in preferences.dependencyStyle ($pref; expected catalog or literal). Using the detected value instead.",
                        )
                    }
                    if (root.resolve(CATALOG_FILE).isRegularFile()) {
                        Sourced("catalog", "検出: $CATALOG_FILE", "detected: $CATALOG_FILE")
                    } else {
                        Sourced("literal", "$CATALOG_FILE が無い", "no $CATALOG_FILE")
                    }
                }
            }
        }
    }
    var depsNote: Pair<String, String>? = null
    var plan = CatalogPlan()
    if (deps.value == "catalog") {
        depsNote = when {
            !root.resolve(CATALOG_FILE).isRegularFile() -> "$CATALOG_FILE が無い" to "no $CATALOG_FILE"
            catalogIsRemapped(root, settings) ->
                "${fileUri(settings.name)} の versionCatalogs { } が $CATALOG_FILE 以外を使っている" to
                    "versionCatalogs { } in ${fileUri(settings.name)} uses something other than $CATALOG_FILE"
            else -> {
                plan = planCatalogEntries(root, version, konsist, tf)
                plan.fallback
            }
        }
        if (depsNote != null) {
            if (depsArg != null) die("${depsNote.first}。--deps literal にするか、カタログの別名を直してください。", "${depsNote.second}. Use --deps literal, or fix the alias in the catalog.")
            deps = Sourced("literal", "${deps.ja}。catalog には書かない", "${deps.en}; not written to the catalog")
        }
    }
    // placement module with a Kotlin JVM alias in the catalog: apply it by the alias rather than a literal version.
    if (deps.value == "catalog" && placement == KotlinPlacement.MODULE) {
        catalogEntries(root, "plugins").firstOrNull { it.content == "org.jetbrains.kotlin.jvm" }?.let {
            val norm = it.alias.replace('_', '.').replace('-', '.')
            val grouped = catalogEntries(root, "plugins").any { other -> val k = other.alias.replace('_', '.').replace('-', '.'); k != norm && k.startsWith("$norm.") }
            kotlinPlugin = "alias(libs.plugins.$norm${if (grouped) ".asProvider()" else ""})"
        }
    }
    val kotestVersion = if (tf == "kotest") kotestVersionLiteral(root) else KOTEST_VERSION

    // Already included? Matches both "architecture-test" and ":architecture-test".
    val includePattern = Regex(
        "^\\s*include[\\s(]+[^)]*[\"']:?${Regex.escape(module.gradlePath.removePrefix(":"))}[\"']",
    )
    val settingsNeedsInclude = root.resolve(settings.name).editLines().map(::stripLineCommentNaive).none { includePattern.containsMatchIn(it) }
    // katachi's Gradle plugin is on Maven Central; pluginManagement needs mavenCentral() to resolve it.
    val pluginReposMissing = pluginRepositoriesMissing(root, settings, mavenLocal)
    val settingsNeedsPluginRepos = pluginReposMissing.isNotEmpty()
    val yesNo = { b: Boolean -> if (b) "yes" else "no" }

    say("作成する内容", "What will be created")
    note("パッケージ:   $pkg", "Package:      $pkg")
    when (versionSource) {
        VersionSource.INIT -> note("katachi:      $version（init が記録した版）", "katachi:      $version (recorded by init)")
        VersionSource.CLI -> note("katachi:      $version（この katachi コマンドの版）", "katachi:      $version (the version of this katachi command)")
        VersionSource.LATEST -> note("katachi:      $version（GitHub の最新リリース）", "katachi:      $version (the latest release on GitHub)")
        VersionSource.EXPLICIT -> note("katachi:      $version")
    }
    note("Kotlin:       $kotlin")
    note("JVM toolchain: ${toolchain.ja}", "JVM toolchain: ${toolchain.en}")
    note("konsist:      ${yesNo(konsist)}")
    if (tf == "kotest") {
        note("テスト:       kotest の ${kotestSpecClass(kotestStyle)}（${testFramework.ja}）", "Test framework: kotest, ${kotestSpecClass(kotestStyle)} (${testFramework.en})")
    } else {
        note("テスト:       $tf（${testFramework.ja}）", "Test framework: $tf (${testFramework.en})")
    }
    if (deps.value == "catalog") {
        note("依存:         catalog（${deps.ja}。${fileUri(CATALOG_FILE)} に追記）", "Dependencies: catalog (${deps.en}; appended to ${fileUri(CATALOG_FILE)})")
        if (plan.added) {
            plan.additions().forEach { out("                $it") }
        } else {
            note("              追記なし（必要な項目はすべてある）", "              nothing to append (every entry is already there)")
        }
    } else {
        note("依存:         literal（${deps.ja}）", "Dependencies: literal (${deps.en})")
        depsNote?.let { note("              ${it.first}", "              ${it.second}") }
    }
    when (module.source) {
        ModuleDirSource.ARG -> note("モジュール:   ${fileUri(module.dir)}/（${module.gradlePath}。--module-dir）", "Module:       ${fileUri(module.dir)}/ (${module.gradlePath}; from --module-dir)")
        ModuleDirSource.CHECKLIST -> note(
            "モジュール:   ${fileUri(module.dir)}/（${module.gradlePath}。init が記録した meta.moduleDir）",
            "Module:       ${fileUri(module.dir)}/ (${module.gradlePath}; meta.moduleDir recorded by init)",
        )
        ModuleDirSource.DEFAULT -> note("モジュール:   ${fileUri(module.dir)}/（${module.gradlePath}。既定）", "Module:       ${fileUri(module.dir)}/ (${module.gradlePath}; the default)")
    }
    note("ソース:       ${fileUri(srcDir)}/", "Sources:      ${fileUri(srcDir)}/")
    note("ルートに追加: ${yesNo(rootNeedsPlugin)} (${fileUri(rootBuild.name)})", "Add to root:  ${yesNo(rootNeedsPlugin)} (${fileUri(rootBuild.name)})")
    if (placement == KotlinPlacement.MODULE) {
        note(
            "              サブプロジェクトが Kotlin プラグインを版付きで宣言しているため、ルートには足さず ${module.gradlePath} に版を書きます（$kotlinPlugin）\n" +
                "              Gradle が「The Kotlin Gradle plugin was loaded multiple times ...」と警告しますが、この構成では想定どおりです",
            "              A subproject declares the Kotlin plugin with a version, so it is not added to the root; the version goes into ${module.gradlePath} ($kotlinPlugin)\n" +
                "              Gradle warns \"The Kotlin Gradle plugin was loaded multiple times ...\"; that is expected with this setup",
        )
    }
    note("include 追加: ${yesNo(settingsNeedsInclude)} (${fileUri(settings.name)})", "Add include:  ${yesNo(settingsNeedsInclude)} (${fileUri(settings.name)})")
    val missingText = pluginReposMissing.joinToString(" ")
    note(
        "pluginManagement に追加: ${yesNo(settingsNeedsPluginRepos)}${if (settingsNeedsPluginRepos) "（$missingText）" else ""}",
        "Add to pluginManagement: ${yesNo(settingsNeedsPluginRepos)}${if (settingsNeedsPluginRepos) " ($missingText)" else ""}",
    )
    if (mavenLocal) note("mavenLocal:   有効（開発者向け）", "mavenLocal:   on (for developers)")

    if (dryRun) {
        say("")
        say("--dry-run のため何も書きませんでした。", "Nothing was written (--dry-run).")
        return 0
    }

    val src = root.resolve(srcDir)
    src.createDirectories()
    // The catalog first: the accessors build.gradle.kts uses come from what is added here.
    if (deps.value == "catalog") writeCatalogEntries(root, plan)
    writeModuleBuild(
        root.resolve(module.dir).resolve("build.gradle.kts"),
        ModuleBuild(version, konsist, contextFlag, pkg, kotlinPlugin, toolchain.n, deps.value == "catalog", tf, kotestVersion, plan),
    )
    // **Never overwritten, not even with --force**: it holds the architecture { } people and agents wrote.
    val architectureKt = src.resolve("ProjectArchitecture.kt")
    if (architectureKt.isRegularFile() && architectureKt.fileSize() > 0) {
        note("すでにあるので残しました: ${fileUri(architectureKt)}", "Kept (already there): ${fileUri(architectureKt)}")
    } else {
        architectureKt.writeText("package $pkg.test.architecture\n\nimport me.tbsten.katachi.dsl.architecture\n\nval projectArchitecture = architecture {\n}\n")
    }
    writeTestKt(src.resolve("ProjectArchitectureTest.kt"), pkg, konsist, tf, kotestStyle)

    val rootPluginManual = if (rootNeedsPlugin) addRootPlugin(root, rootBuild, kotlin) else null
    val settingsIncludeManual = if (settingsNeedsInclude) addSettingsInclude(root, settings, module.gradlePath) else null
    val settingsManual = if (settingsNeedsPluginRepos) addPluginRepositories(root, settings, pluginReposMissing) else null
    val dependencyMavenLocal = if (mavenLocal) addDependencyMavenLocal(root, settings) else null

    say("")
    say("== 作成しました ========================================", "== Created =============================================")
    val rootBuildUri = fileUri(rootBuild.name)
    val settingsUri = fileUri(settings.name)
    say(fileUri("${module.dir}/build.gradle.kts"))
    say(fileUri(architectureKt))
    say(fileUri(src.resolve("ProjectArchitectureTest.kt")))
    if (deps.value == "catalog") {
        if (plan.added) {
            say("${fileUri(CATALOG_FILE)} （次の項目を追記）", "${fileUri(CATALOG_FILE)} (appended the following)")
            plan.additions().forEach { out("    $it") }
        } else {
            say("${fileUri(CATALOG_FILE)} （追記なし。必要な項目はすべてあった）", "${fileUri(CATALOG_FILE)} (nothing appended; every entry was already there)")
        }
    }
    if (rootNeedsPlugin && rootPluginManual == null) say("$rootBuildUri （Kotlin JVM プラグインを apply false で追加）", "$rootBuildUri (added the Kotlin JVM plugin with apply false)")
    if (settingsNeedsInclude && settingsIncludeManual == null) say("$settingsUri （include を追加）", "$settingsUri (added the include)")
    if (settingsNeedsPluginRepos && settingsManual == null) {
        say("$settingsUri （pluginManagement の repositories に $missingText を追加）", "$settingsUri (added $missingText to the repositories of pluginManagement)")
    }
    if (dependencyMavenLocal == DependencyMavenLocal.ADDED) {
        say(
            "$settingsUri （dependencyResolutionManagement の repositories に mavenLocal() を追加。開発者向け）",
            "$settingsUri (added mavenLocal() to the repositories of dependencyResolutionManagement; for developers)",
        )
    }
    say("========================================================")
    val failsJa = "足すまで ./gradlew ${module.gradlePath}:test は失敗します。"
    val failsEn = "./gradlew ${module.gradlePath}:test fails until you add it."
    if (settingsManual != null) {
        warn(
            "$settingsUri の pluginManagement { } の形を読み取れなかったため、自動で書き換えませんでした。",
            "Could not read the shape of pluginManagement { } in $settingsUri, so it was not edited automatically.",
        )
        say("")
        say(
            "$settingsUri の pluginManagement { repositories { } } に、次を足してください\n（pluginManagement { } が無ければ、ファイルの先頭に作ってください）。",
            "Add the following to pluginManagement { repositories { } } in $settingsUri\n(if there is no pluginManagement { }, create one at the top of the file).",
        )
        say("")
        say(settingsManual)
        say("")
        say("katachi の Gradle plugin（$KATACHI_PLUGIN_ID）は Maven Central にあります。$failsJa", "katachi's Gradle plugin ($KATACHI_PLUGIN_ID) is on Maven Central. $failsEn")
    }
    if (settingsIncludeManual != null) {
        warn(
            "$settingsUri の include 文の終わりを読み取れなかったため、include を自動で足しませんでした。",
            "Could not find where the include statement in $settingsUri ends, so the include was not added automatically.",
        )
        say("")
        say(
            "次の1行を、$settingsUri の既存の include 文の後ろ（閉じ括弧より後）に足してください。",
            "Add the following line after the existing include statement in $settingsUri (after its closing parenthesis).",
        )
        say("")
        say(settingsIncludeManual)
        say("")
        say(failsJa, failsEn)
    }
    if (dependencyMavenLocal == DependencyMavenLocal.MANUAL) {
        warn(
            "$settingsUri に dependencyResolutionManagement { repositories { } } が無いため、依存の解決先に mavenLocal() を足せませんでした（開発者向けの KATACHI_MAVEN_LOCAL）。",
            "$settingsUri has no dependencyResolutionManagement { repositories { } }, so mavenLocal() could not be added to where dependencies are resolved (KATACHI_MAVEN_LOCAL, for developers).",
        )
        say(
            "  ${module.gradlePath} が依存を解決している repositories { } に mavenLocal() を手で足してください。",
            "  Add mavenLocal() by hand to the repositories { } that ${module.gradlePath} resolves its dependencies from.",
        )
    }
    if (rootPluginManual != null) {
        warn("$rootBuildUri は buildscript { } を持つため、自動で書き換えませんでした。", "$rootBuildUri has a buildscript { } block, so it was not edited automatically.")
        say("")
        say(
            "次の1行を、$rootBuildUri の buildscript { } の**後ろ**にある\nplugins { } の中に足してください（plugins { } が無ければ作ってください）。",
            "Add the following line inside the plugins { } block that comes **after** buildscript { }\nin $rootBuildUri (create plugins { } if there is none).",
        )
        say("")
        say(rootPluginManual)
        say("")
        say(failsJa, failsEn)
    }

    // The Kotlin actually used, the toolchain and the module's place, so that verify and the later commands agree.
    findWorkdir()?.resolve(CHECK_LIST_FILE)?.let { checkList ->
        updateChecklistMeta(checkList, "kotlin", kotlin)
        updateChecklistMeta(checkList, "jvmToolchain", pick(toolchain.ja, toolchain.en))
        if (module.dir != module.recorded) updateChecklistMeta(checkList, "moduleDir", module.dir)
    }

    say("")
    say(
        "次: $AGENT_COMMAND test\n    architecture { } が空なので、すべてのファイルが Unexpected として報告されて\n    失敗するのが正常です。Unexpected 以外のエラーが出た場合だけが問題です。",
        "Next: $AGENT_COMMAND test\n    architecture { } is empty, so every file is reported as Unexpected and the test\n    fails. That is expected. Only errors other than Unexpected are a problem.",
    )
    return 0
}

private class ModuleBuild(
    val version: String,
    val konsist: Boolean,
    val contextFlag: Boolean,
    val pkg: String,
    val kotlinPlugin: String,
    val toolchain: Int?,
    val catalog: Boolean,
    val testFramework: String,
    val kotestVersion: String,
    val plan: CatalogPlan,
)

/**
 * `<module>/build.gradle.kts`. Its comments follow the language, since they stay in the user's
 * repository. No repositories { }: a project with FAIL_ON_PROJECT_REPOS (Android) fails on them.
 * The kit's `write_module_build`.
 */
private fun ScriptContext.writeModuleBuild(file: Path, b: ModuleBuild) {
    val ja = lang == Lang.JA
    val cacheNote = if (ja) {
        "    // このタスクはキャッシュさせない。katachi は実行時にリポジトリ全体を歩くが、\n" +
            "    // Gradle から見える入力はこのモジュールのテストソースと classpath だけ。他の場所で\n" +
            "    // ファイルが増減してもキーが変わらないので、UP-TO-DATE / FROM-CACHE になって\n" +
            "    // 検査が一度も走らない。黙って通るガードは、ガードが無いより悪い。"
    } else {
        "    // Never let this task be cached. katachi walks the whole repository when the test\n" +
            "    // runs, but the only inputs Gradle can see are the test sources and the classpath of\n" +
            "    // this module. A file added or moved anywhere else leaves the key unchanged, so Gradle\n" +
            "    // answers UP-TO-DATE or FROM-CACHE and the check never runs. A guard that silently\n" +
            "    // passes is worse than no guard at all."
    }
    val logNote = if (ja) {
        "        // 違反の一覧は AssertionError のメッセージに入っている。FULL にしないと\n" +
            "        // \"KatachiArchitectureAssertionError at ProjectArchitectureTest.kt:12\" の1行しか出ず、\n" +
            "        // 中身を見るのに build/test-results/**/*.xml を読む羽目になる（CI のログでも同じ）。"
    } else {
        "        // The violation list lives in the AssertionError message. Without FULL you only get\n" +
            "        // \"KatachiArchitectureAssertionError at ProjectArchitectureTest.kt:12\", and reading the\n" +
            "        // detail means opening build/test-results/**/*.xml (the same goes for CI logs)."
    }
    val stdNote = if (ja) "        // MissingDescription などの警告は stdout に出る。" else "        // Warnings such as MissingDescription are printed to stdout."
    val pluginNote = if (ja) {
        "    // processor ごとのタスク（katachiDocs・katachiTemplate など）を足す。\n    // katachi の依存は足さないので、下の dependencies { } は別に要る。"
    } else {
        "    // Adds a task per processor (katachiDocs, katachiTemplate, and so on). It does not add\n    // katachi as a dependency, so the dependencies { } block below is still needed."
    }
    val archNote = if (ja) {
        "    // architecture { } を持つトップレベル val の完全修飾名。名前やパッケージを\n    // 変えたらここも直す。"
    } else {
        "    // The fully qualified name of the top-level val holding architecture { }. Update it\n    // if you rename the val or move it to another package."
    }
    val toolchainBlock = b.toolchain?.let { "    jvmToolchain($it)\n\n" } ?: ""
    val kotlinBlock = when {
        b.contextFlag -> {
            val contextNote = if (ja) {
                "    // Kotlin 2.4 未満では、context parameters を呼ぶ側にこのオプションが要る。\n" +
                    "    // katachi の DSL（mainSourceSet / ktFile など）はすべて context parameters\n" +
                    "    // なので、無いと1つも書けない。Kotlin を 2.4 以降に上げたらこの2行は消すこと\n" +
                    "    // （2.4 以降で付けたままだと redundant の警告が出る）。"
            } else {
                "    // Kotlin before 2.4 needs this on the calling side to use context parameters.\n" +
                    "    // Every katachi DSL entry point (mainSourceSet / ktFile ...) is a contextual\n" +
                    "    // declaration, so without it you cannot write a single one. Drop these two lines once\n" +
                    "    // the project moves to Kotlin 2.4 or later - from 2.4 on the flag warns that it is\n" +
                    "    // redundant."
            }
            "kotlin {\n$toolchainBlock$contextNote\n    compilerOptions.freeCompilerArgs.add(\"-Xcontext-parameters\")\n}\n\n"
        }
        b.toolchain != null -> "kotlin {\n    jvmToolchain(${b.toolchain})\n}\n\n"
        // Nothing to write: without jvmToolchain it compiles with the JDK running Gradle.
        else -> ""
    }
    val p = b.plan
    val pluginLine: String
    val katachiLine: String
    var konsistLine: String
    val bomLine: String
    val engineLine: String
    val launcherLine: String
    if (b.catalog) {
        pluginLine = "    alias(${p.plugin})"
        katachiLine = "    testImplementation(${p.katachi})"
        konsistLine = "    testImplementation(${p.konsist})"
        bomLine = "    testImplementation(platform(${p.junitBom}))"
        engineLine = if (b.testFramework == "kotest") "    testImplementation(${p.kotest})" else "    testImplementation(${p.junitJupiter})"
        launcherLine = "    testRuntimeOnly(${p.junitLauncher})"
    } else {
        pluginLine = "    id(\"$KATACHI_PLUGIN_ID\") version \"${b.version}\""
        katachiLine = "    testImplementation(\"me.tbsten.katachi:katachi:${b.version}\")"
        konsistLine = "    testImplementation(\"me.tbsten.katachi:katachi-konsist:${b.version}\")"
        bomLine = "    testImplementation(platform(\"org.junit:junit-bom:$JUNIT_VERSION\"))"
        engineLine = if (b.testFramework == "kotest") "    testImplementation(\"io.kotest:kotest-runner-junit5:${b.kotestVersion}\")" else "    testImplementation(\"org.junit.jupiter:junit-jupiter\")"
        launcherLine = "    testRuntimeOnly(\"org.junit.platform:junit-platform-launcher\")"
    }
    if (!b.konsist) konsistLine = ""
    // kotlin("jvm") stays version-less even with the catalog; say why, so nobody turns it into an alias that fails.
    val kotlinPluginNote = if (b.catalog && b.kotlinPlugin == "kotlin(\"jvm\")") {
        if (ja) {
            "    // alias(libs.plugins.kotlin.jvm) と書かない。build-logic（includeBuild）経由で Kotlin plugin が\n" +
                "    // classpath にあると \"already on the classpath with an unknown version\" で失敗する。版なしの\n" +
                "    // kotlin(\"jvm\") か、カタログに寄せるなら id(libs.plugins.kotlin.jvm.get().pluginId) を使う。\n"
        } else {
            "    // Do not write alias(libs.plugins.kotlin.jvm). When the Kotlin plugin is already on the\n" +
                "    // classpath through build-logic (includeBuild), that fails with \"already on the classpath\n" +
                "    // with an unknown version\". Use the version-less kotlin(\"jvm\"), or\n" +
                "    // id(libs.plugins.kotlin.jvm.get().pluginId) if you want it to come from the catalog.\n"
        }
    } else {
        ""
    }
    file.parent.createDirectories()
    file.writeText(
        """
        |plugins {
        |$kotlinPluginNote    ${b.kotlinPlugin}
        |$pluginNote
        |$pluginLine
        |}
        |
        |${kotlinBlock}katachi {
        |$archNote
        |    architecture = "${b.pkg}.test.architecture.projectArchitecture"
        |}
        |
        |tasks.test {
        |$cacheNote
        |    outputs.upToDateWhen { false }
        |    outputs.cacheIf { false }
        |
        |    useJUnitPlatform()
        |    testLogging {
        |        events("passed", "failed", "skipped")
        |$logNote
        |        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        |$stdNote
        |        showStandardStreams = true
        |    }
        |}
        |
        |dependencies {
        |$katachiLine
        |$konsistLine
        |$bomLine
        |$engineLine
        |$launcherLine
        |}
        |
        """.trimMargin(),
    )
}

/** The spec style of plain `--test-framework kotest` (KATACHI-301). */
private const val DEFAULT_KOTEST_STYLE: String = "fun"

/** The Kotest class of a spec style, the part after `kotest-` in `--test-framework kotest-free`: `free` is `FreeSpec`. */
private fun kotestSpecClass(style: String): String = style.replaceFirstChar(Char::uppercaseChar) + "Spec"

/**
 * ProjectArchitectureTest.kt. The test name follows the language too. With konsist it passes
 * FileConstraintCheck() (an experimental API), without which ktFileContent { } and the like are not
 * evaluated. No maxViolations: `-Dkatachi.maxViolations=all` shows them all. The kit's `write_test_kt`.
 * A Kotest test is written in [kotestStyle] (KATACHI-301).
 */
private fun ScriptContext.writeTestKt(file: Path, pkg: String, konsist: Boolean, testFramework: String, kotestStyle: String) {
    val testName = pick("プロジェクトの構成が定義どおりになっている", "the project matches its declaration")
    val assertion = if (konsist) "projectArchitecture.assert(FileConstraintCheck())" else "projectArchitecture.assert()"
    val katachiImports = if (konsist) {
        "import me.tbsten.katachi.ExperimentalKatachiApi\nimport me.tbsten.katachi.check.FileConstraintCheck\nimport me.tbsten.katachi.check.assert\n"
    } else {
        "import me.tbsten.katachi.check.assert\n"
    }
    val text = if (testFramework == "kotest") {
        val specClass = kotestSpecClass(kotestStyle)
        val optIn = if (konsist) "@file:OptIn(ExperimentalKatachiApi::class)\n\n" else ""
        "${optIn}package $pkg.test.architecture\n\nimport io.kotest.core.spec.style.$specClass\n$katachiImports\n" +
            kotestSpecBody(kotestStyle, specClass, testName, assertion)
    } else {
        val optIn = if (konsist) "    @OptIn(ExperimentalKatachiApi::class)\n" else ""
        "package $pkg.test.architecture\n\n${katachiImports}import org.junit.jupiter.api.Test\n\n" +
            "class ProjectArchitectureTest {\n$optIn    @Test\n    fun `$testName`() {\n        $assertion\n    }\n}\n"
    }
    file.writeText(text)
}

/** The class of ProjectArchitectureTest.kt in a Kotest [style], running [assertion] in a test named [testName]. */
private fun kotestSpecBody(style: String, specClass: String, testName: String, assertion: String): String {
    if (style == "annotation") {
        return "class ProjectArchitectureTest : $specClass() {\n    @Test\n    fun `$testName`() {\n        $assertion\n    }\n}\n"
    }
    // The styles that need a container around the test name it after the object the test checks.
    val container = "\"ProjectArchitecture\""
    val test = "\"$testName\""
    val blocks = when (style) {
        "string", "free" -> listOf(test)
        "should" -> listOf("should($test)")
        "describe" -> listOf("describe($container)", "it($test)")
        "behavior" -> listOf("Given($container)", "Then($test)")
        "word" -> listOf("$container should", test)
        "feature" -> listOf("feature($container)", "scenario($test)")
        "expect" -> listOf("context($container)", "expect($test)")
        else -> listOf("test($test)")
    }
    return "class ProjectArchitectureTest : $specClass({\n${nestBlocks(blocks, assertion, depth = 1)}})\n"
}

/** [blocks] opened one inside another (`describe(...) {`, then `it(...) {`), with [body] in the innermost. */
private fun nestBlocks(blocks: List<String>, body: String, depth: Int): String {
    val indent = "    ".repeat(depth)
    val block = blocks.firstOrNull() ?: return "$indent$body\n"
    return "$indent$block {\n${nestBlocks(blocks.drop(1), body, depth + 1)}$indent}\n"
}

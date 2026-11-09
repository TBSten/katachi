package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.writeText

private val PACKAGE_NAME = Regex("^[a-z][a-zA-Z0-9_]*(\\.[a-z][a-zA-Z0-9_]*)*$")

/** A value with where it came from, in both languages. */
private class Sourced(val value: String, val source: Msg)

/**
 * `scaffold --package <pkg> ...`: creates the test module (build.gradle.kts, ProjectArchitecture.kt,
 * the test) and wires it into settings, the root build file and the catalog. Runs in the Gradle
 * root. The kit's `cmd_scaffold`.
 */
internal fun ScriptContext.runScaffold(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("scaffold"), args)
    parsed.positionals.firstOrNull()?.let { die(Messages.Scaffold.unknownOption(it)) }
    val pkg = parsed.value("--package") ?: die(Messages.Scaffold.packageRequiredExamplePackage)
    if (!PACKAGE_NAME.matches(pkg)) die(Messages.Scaffold.packageNotValidPackage(pkg))
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
        note(Messages.Scaffold.moduleDirDiffersWhat(module.dir, module.recorded))
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
        die(Messages.Scaffold.cannotTellKatachiVersion)
    }
    requireKatachiVersion(version, versionSource)
    val mavenLocal = mavenLocalEnabled()

    if (kotlin.isEmpty()) kotlin = detectKotlinVersion(root) ?: ""
    if (kotlin.isEmpty()) die(Messages.Scaffold.couldNotDetectKotlin)
    val (kotlinMajor, kotlinMinor) = majorMinor(kotlin) ?: die(Messages.Scaffold.couldNotReadKotlin(kotlin))
    if (kotlinMajor < KOTLIN_MIN_MAJOR || (kotlinMajor == KOTLIN_MIN_MAJOR && kotlinMinor < KOTLIN_MIN_MINOR)) {
        val message = kotlinTooOldMessage(root, kotlin, forScaffold = true)
        die(Messages.Scaffold.afterUpgradingRunScaffold(message))
    }
    // Context parameters entered the language in 2.4; 2.2 / 2.3 need -Xcontext-parameters, and 2.4+ warns that it is redundant.
    val contextFlag = kotlinMajor == 2 && kotlinMinor < 4

    if (root.resolve(module.dir).exists() && !force) {
        die(Messages.Scaffold.alreadyExistsAddForce(fileUri(module.dir)))
    }
    val srcDir = "${module.dir}/src/test/kotlin/${pkg.replace('.', '/')}/test/architecture"

    val placement = kotlinPluginPlacement(root, root.resolve(rootBuild.name), module.dir)
    val rootNeedsPlugin = placement == KotlinPlacement.ADD
    var kotlinPlugin = if (placement == KotlinPlacement.MODULE) "kotlin(\"jvm\") version \"$kotlin\"" else "kotlin(\"jvm\")"

    // The test framework: --test-framework > preferences.testFramework > detected > junit5.
    val (detectedTf, detectedTfFrom) = detectTestFramework(root, module.dir)
    val testFramework = when {
        tfArg != null -> Sourced(tfArg, Msg("--test-framework", "--test-framework"))
        else -> {
            val pref = preference("testFramework")
            when {
                pref in TEST_FRAMEWORKS -> Sourced(pref, Msg("preferences.testFramework", "preferences.testFramework"))
                else -> {
                    if (pref.isNotEmpty()) {
                        warn(Messages.Scaffold.unknownValuePreferencesTestframework(pref))
                    }
                    if (detectedTfFrom != null) {
                        Sourced(detectedTf, Messages.Scaffold.detected(detectedTfFrom))
                    } else {
                        Sourced(detectedTf, Messages.Scaffold.defaultNoKotestDependency)
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
        depsArg != null -> Sourced(depsArg, Msg("--deps", "--deps"))
        else -> {
            val pref = preference("dependencyStyle")
            when {
                pref == "catalog" || pref == "literal" -> Sourced(pref, Msg("preferences.dependencyStyle", "preferences.dependencyStyle"))
                else -> {
                    if (pref.isNotEmpty()) {
                        warn(Messages.Scaffold.unknownValuePreferencesDependencystyle(pref))
                    }
                    if (root.resolve(CATALOG_FILE).isRegularFile()) {
                        Sourced("catalog", Messages.Scaffold.detectedCatalog)
                    } else {
                        Sourced("literal", Messages.Scaffold.noCatalogFile)
                    }
                }
            }
        }
    }
    var depsNote: Msg? = null
    var plan = CatalogPlan()
    if (deps.value == "catalog") {
        depsNote = when {
            !root.resolve(CATALOG_FILE).isRegularFile() -> Messages.Scaffold.noCatalogFile
            catalogIsRemapped(root, settings) -> Messages.Scaffold.catalogRemapped(fileUri(settings.name))
            else -> {
                plan = planCatalogEntries(root, version, konsist, tf)
                plan.fallback
            }
        }
        if (depsNote != null) {
            if (depsArg != null) die(Messages.Scaffold.useDepsLiteralFix(depsNote))
            deps = Sourced("literal", Messages.Scaffold.notWrittenCatalog(deps.source))
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

    say(Messages.Scaffold.whatWillCreated)
    note(Messages.Scaffold.packageName(pkg))
    when (versionSource) {
        VersionSource.INIT -> note(Messages.Scaffold.recordedInit(version))
        VersionSource.CLI -> note(Messages.Scaffold.versionKatachiCommand(version))
        VersionSource.LATEST -> note(Messages.Scaffold.latestReleaseGithub(version))
        VersionSource.EXPLICIT -> note("katachi:      $version")
    }
    note("Kotlin:       $kotlin")
    note("JVM toolchain: ${pick(toolchain.source)}")
    note("konsist:      ${yesNo(konsist)}")
    if (tf == "kotest") {
        note(Messages.Scaffold.testFrameworkKotest(kotestSpecClass(kotestStyle), testFramework.source))
    } else {
        note(Messages.Scaffold.testFramework(tf, testFramework.source))
    }
    if (deps.value == "catalog") {
        note(Messages.Scaffold.dependenciesCatalogAppended(deps.source, fileUri(CATALOG_FILE)))
        if (plan.added) {
            plan.additions().forEach { out("                $it") }
        } else {
            note(Messages.Scaffold.nothingAppendEveryEntry)
        }
    } else {
        note(Messages.Scaffold.dependenciesLiteral(deps.source))
        depsNote?.let { note("              ${pick(it)}") }
    }
    when (module.source) {
        ModuleDirSource.ARG -> note(Messages.Scaffold.moduleModuleDir(fileUri(module.dir), module.gradlePath))
        ModuleDirSource.CHECKLIST -> note(Messages.Scaffold.moduleMetaModuledirRecorded(fileUri(module.dir), module.gradlePath))
        ModuleDirSource.DEFAULT -> note(Messages.Scaffold.moduleDefault(fileUri(module.dir), module.gradlePath))
    }
    note(Messages.Scaffold.sources(fileUri(srcDir)))
    // Without it, a project that ignores only the root's /build sees the module's build/ as Unexpected files on the first test.
    val buildDirIgnored = pathIsIgnored(root, "${module.dir}/build")
    note(Messages.Scaffold.ignoreBuild(buildDirIgnored, "${fileUri(module.dir)}/.gitignore"))
    note(Messages.Scaffold.addRoot(yesNo(rootNeedsPlugin), fileUri(rootBuild.name)))
    if (placement == KotlinPlacement.MODULE) {
        note(Messages.Scaffold.subprojectDeclaresKotlinPlugin(module.gradlePath, kotlinPlugin))
    }
    note(Messages.Scaffold.addInclude(yesNo(settingsNeedsInclude), fileUri(settings.name)))
    val missingText = pluginReposMissing.joinToString(" ")
    note(Messages.Scaffold.addPluginmanagement(yesNo(settingsNeedsPluginRepos), missingText.takeIf { settingsNeedsPluginRepos }))
    if (mavenLocal) note(Messages.Scaffold.developers)

    if (dryRun) {
        say("")
        say(Messages.Scaffold.nothingWrittenDryRun)
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
        note(Messages.Scaffold.keptAlreadyThere(fileUri(architectureKt)))
    } else {
        architectureKt.writeText("package $pkg.test.architecture\n\nimport me.tbsten.katachi.dsl.architecture\n\nval projectArchitecture = architecture {\n}\n")
    }
    writeTestKt(src.resolve("ProjectArchitectureTest.kt"), pkg, konsist, tf, kotestStyle)
    val moduleGitignore = root.resolve(module.dir).resolve(".gitignore")
    val gitignoreWritten = !buildDirIgnored && addBuildToGitignore(moduleGitignore)

    val rootPluginManual = if (rootNeedsPlugin) addRootPlugin(root, rootBuild, kotlin) else null
    val settingsIncludeManual = if (settingsNeedsInclude) addSettingsInclude(root, settings, module.gradlePath) else null
    val settingsManual = if (settingsNeedsPluginRepos) addPluginRepositories(root, settings, pluginReposMissing) else null
    val dependencyMavenLocal = if (mavenLocal) addDependencyMavenLocal(root, settings) else null

    say("")
    say(Messages.Scaffold.created)
    val rootBuildUri = fileUri(rootBuild.name)
    val settingsUri = fileUri(settings.name)
    say(fileUri("${module.dir}/build.gradle.kts"))
    say(fileUri(architectureKt))
    say(fileUri(src.resolve("ProjectArchitectureTest.kt")))
    if (gitignoreWritten) say(Messages.Scaffold.addedBuild(fileUri("${module.dir}/.gitignore")))
    if (deps.value == "catalog") {
        if (plan.added) {
            say(Messages.Scaffold.appendedFollowing(fileUri(CATALOG_FILE)))
            plan.additions().forEach { out("    $it") }
        } else {
            say(Messages.Scaffold.nothingAppendedEveryEntry(fileUri(CATALOG_FILE)))
        }
    }
    if (rootNeedsPlugin && rootPluginManual == null) say(Messages.Scaffold.addedKotlinJvmPlugin(rootBuildUri))
    if (settingsNeedsInclude && settingsIncludeManual == null) say(Messages.Scaffold.addedInclude(settingsUri))
    if (settingsNeedsPluginRepos && settingsManual == null) {
        say(Messages.Scaffold.addedRepositoriesPluginmanagement(settingsUri, missingText))
    }
    if (dependencyMavenLocal == DependencyMavenLocal.ADDED) {
        say(Messages.Scaffold.addedMavenlocalRepositoriesDependencyresolutionmanagement(settingsUri))
    }
    say("========================================================")
    val fails = Messages.Scaffold.testFailsUntilAdded(module.gradlePath)
    if (settingsManual != null) {
        warn(Messages.Scaffold.couldNotReadShape(settingsUri))
        say("")
        say(Messages.Scaffold.addFollowingPluginmanagementRepositories(settingsUri))
        say("")
        say(settingsManual)
        say("")
        say(Messages.Scaffold.katachiSGradlePlugin(fails))
    }
    if (settingsIncludeManual != null) {
        warn(Messages.Scaffold.couldNotFindWhere(settingsUri))
        say("")
        say(Messages.Scaffold.addFollowingLineAfter(settingsUri))
        say("")
        say(settingsIncludeManual)
        say("")
        say(fails)
    }
    if (dependencyMavenLocal == DependencyMavenLocal.MANUAL) {
        warn(Messages.Scaffold.hasNoDependencyresolutionmanagementRepositories(settingsUri))
        say(Messages.Scaffold.addMavenlocalHandRepositories(module.gradlePath))
    }
    if (rootPluginManual != null) {
        warn(Messages.Scaffold.hasBuildscriptBlockNot(rootBuildUri))
        say("")
        say(Messages.Scaffold.addFollowingLineInside(rootBuildUri))
        say("")
        say(rootPluginManual)
        say("")
        say(fails)
    }

    // The Kotlin actually used, the toolchain and the module's place, so that verify and the later commands agree.
    findWorkdir()?.resolve(CHECK_LIST_FILE)?.let { checkList ->
        updateChecklistMeta(checkList, "kotlin", kotlin)
        updateChecklistMeta(checkList, "jvmToolchain", pick(toolchain.source))
        if (module.dir != module.recorded) updateChecklistMeta(checkList, "moduleDir", module.dir)
    }

    say("")
    say(Messages.Scaffold.nextTestArchitectureEmpty)
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
    val cacheNote = pick(Messages.ScaffoldBuildFile.cacheNote)
    val logNote = pick(Messages.ScaffoldBuildFile.logNote)
    val stdNote = pick(Messages.ScaffoldBuildFile.stdNote)
    val pluginNote = pick(Messages.ScaffoldBuildFile.pluginNote)
    val archNote = pick(Messages.ScaffoldBuildFile.archNote)
    val toolchainBlock = b.toolchain?.let { "    jvmToolchain($it)\n\n" } ?: ""
    val kotlinBlock = when {
        b.contextFlag -> {
            val contextNote = pick(Messages.ScaffoldBuildFile.contextNote)
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
        pick(Messages.ScaffoldBuildFile.kotlinPluginNote)
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
    val testName = pick(Messages.Scaffold.projectMatchesDeclaration)
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

/**
 * Adds `/build` to the module's [gitignore] (creating it), as Android Studio does for each module. Returns false when the
 * line was already there.
 */
private fun addBuildToGitignore(gitignore: Path): Boolean {
    val lines = if (gitignore.isRegularFile()) gitignore.readLines() else emptyList()
    if (lines.any { it.trim() in setOf("/build", "/build/", "build", "build/") }) return false
    val text = lines.joinToString("\n")
    gitignore.writeText(if (text.isEmpty()) "/build\n" else "$text\n/build\n")
    return true
}

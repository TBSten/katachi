plugins {
    id("org.jetbrains.dokka")
}

dokka {
    moduleName.set(rootProject.name)

    dokkaPublications.named("html") {
        outputDirectory.set(layout.projectDirectory.dir("docs/public/api-docs"))
    }

    // :tool:dokka's settings for the aggregating run. The modules' runs get theirs from
    // katachi-kotlin-library.
    pluginsConfiguration {
        registerBinding(
            buildsrc.convention.KatachiDokkaPluginParameters::class,
            buildsrc.convention.KatachiDokkaPluginParameters::class,
        )
        register<buildsrc.convention.KatachiDokkaPluginParameters>("katachi") {
            // The aggregated output root, docs/public/api-docs/, as the docs site serves it.
            baseUrl.set(providers.gradleProperty("katachi.apiDocsBaseUrl"))
            // The tagline of README.md.
            projectSummary.set(
                "Declare your Android/KMP project architecture in a Kotlin DSL, and get both a " +
                        "test and documentation out of the same definition.",
            )
        }
    }
}

dependencies {
    dokka(project(":katachi"))
    dokka(project(":katachi-konsist"))
    dokka(project(":katachi-gradle-plugin"))

    dokkaHtmlPlugin(project(":tool:dokka"))
}

tasks.register("generateApiDocs") {
    group = "documentation"
    description = "Aggregates the :katachi and :katachi-konsist Dokka HTML into " +
            "docs/public/api-docs/ for the docs site's API reference page."
    dependsOn("dokkaGenerateHtml")
    finalizedBy("verifyApiDocs")
}

/**
 * Checks that :tool:dokka did its part of `generateApiDocs`: the Featured node of the sidebar and
 * the Featured sections of the pages, the llms files and Markdown pages, and no intermediate
 * fragment or unfinished llms link left behind.
 *
 * - Every module has its Featured node first in the aggregated sidebar (`navigation.html`, which
 *   every page loads), listing as many declarations as the Featured section of its own `llms.txt`.
 * - Every module's packages are nested by name in the sidebar, not Dokka's flat list of full names.
 * - The top page has a Featured section above "All modules:" listing all of them, and each module
 *   page one above "Packages".
 * - Every HTML page has a Markdown version at its URL + `.md`, and every page that is a directory
 *   (a module, a package, a type) an `llms.txt` and an `llms-full.txt` next to it.
 * - Every link of all of these leads to a file that exists.
 *
 * List items without a summary are only counted, as a warning. Only reads the output; the
 * plugin's own behaviour is covered by the tests of :tool:dokka.
 */
tasks.register("verifyApiDocs") {
    group = "verification"
    description = "Checks that docs/public/api-docs/ has the Featured node in its sidebar, the " +
            "Featured sections, the llms files and the Markdown pages, and that their links lead somewhere."
    mustRunAfter("dokkaGenerateHtml")
    val apiDocs = layout.projectDirectory.dir("docs/public/api-docs").asFile
    val modules = listOf("katachi", "katachi-konsist")
    val baseUrl = providers.gradleProperty("katachi.apiDocsBaseUrl").map { it.removeSuffix("/") + "/" }.orNull
    doLast {
        val problems = mutableListOf<String>()
        fun file(path: String): File? {
            val file = File(apiDocs, path)
            if (file.isFile) return file
            problems += "$path is missing"
            return null
        }

        /** Records [link], written in [from], unless it leads to a file of the output. */
        fun checkLink(from: File, link: String) {
            val target = link.substringBefore('#')
            val file = when {
                baseUrl != null && target.startsWith(baseUrl) -> File(apiDocs, target.removePrefix(baseUrl))
                // Another library's documentation, such as the Kotlin standard library.
                Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(target) -> return
                else -> File(from.parentFile, target)
            }
            if (!file.normalize().isFile) problems += "${from.relativeTo(apiDocs)} links to $link, which does not exist"
        }

        /** The items of the `## Featured` section of the llms.txt at [path]. */
        fun llmsFeaturedCount(path: String): Int {
            val text = File(apiDocs, path).takeIf { it.isFile }?.readText() ?: return 0
            return text.substringAfter("\n## Featured\n", "").substringBefore("\n## ")
                .lines().count { it.startsWith("- [") }
        }

        file("llms.txt")
        modules.forEach { module ->
            file("$module/llms.txt")
            file("$module/llms-full.txt")
        }

        // The sidebar as navigation-loader.js puts it on every page: a toc--part per module, whose
        // first nested toc--part is Featured. Its links are relative to the output root.
        val navigation = file("navigation.html")?.readText().orEmpty()
        val tocLink = Regex("""<a href="([^"]*)" class="toc--link">(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        modules.forEach { module ->
            val start = navigation.indexOf("""id="$module-nav-submenu-0"""")
            val stop = navigation.indexOf("""id="$module-nav-submenu-1"""", startIndex = start.coerceAtLeast(0))
            if (start < 0 || stop < 0) {
                problems += "navigation.html has no Featured node under $module"
                return@forEach
            }
            val links = tocLink.findAll(navigation.substring(start, stop)).map { match ->
                match.groupValues[1] to match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            }.toList()
            if (links.firstOrNull()?.second != "⭐️ Featured") {
                problems += "navigation.html does not have ⭐️ Featured first under $module, but ${links.firstOrNull()?.second}"
                return@forEach
            }
            val entries = links.drop(1)
            val expected = llmsFeaturedCount("$module/llms.txt")
            if (entries.isEmpty() || entries.size != expected) {
                problems += "navigation.html lists ${entries.size} featured declarations under $module, but " +
                        "$module/llms.txt lists $expected"
            }
        }
        // The packages of each module are nested by name: no package directly under the module
        // node has one of its sub-packages next to it, as Dokka's flat list of full names would.
        modules.forEach { module ->
            val partLink = Regex(
                """id="$module-nav-submenu[-\d]*"[^>]*data-nesting-level="(\d+)">\s*<div class="toc--row">\s*""" +
                        """(?:<button[^>]*></button>)?\s*<a href="$module/([^/"]+)/index\.html" class="toc--link">""",
            )
            val packages = partLink.findAll(navigation).map { it.groupValues[1].toInt() to it.groupValues[2] }.toList()
            if (packages.isEmpty()) {
                problems += "navigation.html has no package node under $module"
                return@forEach
            }
            val topLevel = packages.filter { (level, _) -> level == 1 }.map { it.second }
            topLevel.forEach { parent ->
                val flat = topLevel.filter { it.startsWith("$parent.") }
                if (flat.isNotEmpty()) {
                    problems += "navigation.html lists $flat next to $parent under $module instead of inside it: " +
                            "the packages are not nested"
                }
            }
        }
        // Every link of the sidebar, the package tree included. Its links are relative to the root.
        tocLink.findAll(navigation).map { it.groupValues[1] }.distinct()
            .forEach { checkLink(File(apiDocs, "navigation.html"), it) }
        // The Featured sections of the pages: one on the top page above "All modules:", listing
        // every module's featured declarations, and one on each module page above "Packages".
        val tableRow = Regex("""class="table-row""")
        val href = Regex("""<a href="([^"]*)"""")
        fun featuredSection(path: String, before: String): String? {
            val html = File(apiDocs, path).takeIf { it.isFile }?.readText() ?: return null
            val start = html.indexOf(">⭐️ Featured<")
            val stop = html.indexOf(before)
            if (start < 0 || stop < start) {
                problems += "$path has no Featured section above \"$before\""
                return null
            }
            val section = html.substring(start, stop)
            href.findAll(section).forEach { checkLink(File(apiDocs, path), it.groupValues[1]) }
            return section
        }
        featuredSection("index.html", before = "All modules:")?.let { section ->
            val rows = tableRow.findAll(section).count()
            val expected = modules.sumOf { llmsFeaturedCount("$it/llms.txt") }
            if (rows != expected) problems += "index.html lists $rows featured declarations, but the modules' llms.txt list $expected"
        }
        modules.forEach { featuredSection("$it/index.html", before = ">Packages<") }

        // Every HTML page has its Markdown version at its URL + ".md", and every page that is a
        // directory (a module, a package, a type) has its llms files.
        apiDocs.walkTopDown().filter { it.isFile && it.extension == "html" && it.name != "navigation.html" }
            .forEach { html ->
                if (!File(html.path + ".md").isFile) problems += "${html.relativeTo(apiDocs)} has no Markdown version"
                if (html.name == "index.html" && html.parentFile != apiDocs) {
                    listOf("llms.txt", "llms-full.txt").filterNot { File(html.parentFile, it).isFile }.forEach {
                        problems += "${html.parentFile.relativeTo(apiDocs)}/ has no $it"
                    }
                }
            }

        val markdownLink = Regex("""\]\(([^)\s]+)\)""")
        // A list item of an index with no summary after its link: `- [name](link)`.
        val bareItem = Regex("""^- \[[^\]]*\]\([^)\s]+\)$""", RegexOption.MULTILINE)
        var bareItems = 0
        apiDocs.walkTopDown()
            .filter { it.isFile && (it.name in setOf("llms.txt", "llms-full.txt") || it.name.endsWith(".html.md")) }
            .forEach { file ->
                val text = file.readText()
                if ("katachi-dokka-path:" in text || "<katachi-dokka-link" in text) problems += "${
                    file.relativeTo(
                        apiDocs
                    )
                } still has a link the aggregating run should have finished"
                markdownLink.findAll(text).forEach { checkLink(file, it.groupValues[1]) }
                if (file.name == "llms.txt" || file.name.endsWith(".md")) bareItems += bareItem.findAll(text).count()
            }
        if (bareItems > 0) {
            logger.warn(
                "verifyApiDocs: $bareItems list item(s) in the llms.txt files and Markdown pages have no " +
                        "summary. Add a KDoc, or an @llm line, to the declarations they link to.",
            )
        }
        apiDocs.walkTopDown().filter { it.name == "katachi-dokka-fragment.json" }.forEach {
            problems += "${it.relativeTo(apiDocs)} was left in the output"
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "docs/public/api-docs/ is not what :tool:dokka should have made of it:\n" +
                        problems.distinct().joinToString("\n") { "  - $it" } +
                        "\nRun ./gradlew :tool:dokka:check to see which part of the plugin broke.",
            )
        }
    }
}

/**
 * A `template { }` run against a sample, checked the way a user would meet it: generate, then
 * the sample's own `assert()` test and a compile of the generated sources, then delete what was
 * generated.
 *
 * [generatedFiles] are relative to the sample and are the *only* paths the clean-up deletes.
 * They restate what the template and the layout decide together, so the check task refuses to
 * run when one of them is missing -- a list out of step with the template fails loudly instead
 * of leaving files behind.
 */
data class SampleTemplate(
    val args: List<String>,
    val generatedFiles: List<String>,
    val verifyTasks: List<String>,
)

data class SampleBuild(
    val name: String,
    val defaultTasks: List<String>,
    val needsAndroidSdk: Boolean,
    val template: SampleTemplate? = null,
)

val sampleBuilds = listOf(
    SampleBuild(
        "jvm",
        listOf(
            "check",
            // One task per processor. Gradle attaches an option to the task right before it,
            // so each `--arg` follows the task that takes it.
            "katachiLayout",
            "katachiRoleNames",
            "--arg",
            "prefix=domain",
            "katachiDocs",
            "--arg",
            "mode=check",
        ),
        needsAndroidSdk = false,
        template = SampleTemplate(
            args = listOf("roleName=Service", "name=KatachiSmoke"),
            generatedFiles = listOf("src/main/kotlin/com/example/service/KatachiSmokeService.kt"),
            verifyTasks = listOf(
                ":architecture-test:test",
                "--tests",
                "com.example.ProjectArchitectureTest",
                "--rerun",
                ":compileKotlin",
            ),
        ),
    ),
    SampleBuild(
        "android",
        listOf("check", "katachiLayout", "katachiDocs", "--arg", "mode=check"),
        needsAndroidSdk = true,
        template = SampleTemplate(
            args = listOf("roleName=Component", "name=KatachiSmoke"),
            generatedFiles = listOf("ui/src/main/kotlin/com/example/sample/ui/component/AppKatachiSmoke.kt"),
            verifyTasks = listOf(
                ":architecture-test:test",
                "--tests",
                "com.example.sample.ProjectArchitectureTest",
                "--rerun",
                ":ui:compileDebugKotlin",
            ),
        ),
    ),
    SampleBuild(
        "kmp",
        listOf(
            ":architecture-test:test",
            ":app:android:testDebugUnitTest",
            ":architecture-test:katachiLayout",
            ":architecture-test:katachiDocs",
            "--arg",
            "mode=check",
        ),
        needsAndroidSdk = true,
        template = SampleTemplate(
            args = listOf("roleName=Repository", "name=KatachiSmoke"),
            generatedFiles = listOf(
                "data/src/commonMain/kotlin/com/example/kmp/data/user/KatachiSmokeRepository.kt",
                "data/src/commonMain/kotlin/com/example/kmp/data/user/KatachiSmokeRepositoryImpl.kt",
            ),
            verifyTasks = listOf(
                ":architecture-test:test",
                "--tests",
                "com.example.kmp.ProjectLayoutSpec",
                "--rerun",
                ":data:compileAndroidMain",
            ),
        ),
    ),
    SampleBuild(
        "custom-processor",
        listOf(
            "check",
            "katachiRoleFileCount",
            "katachiRoleTable",
            "--arg",
            "groups=core,testing",
            "--arg",
            "sortBy=Declaration",
            "katachiRoleDocCoverage",
            "katachiDocs",
            "--arg",
            "mode=check",
        ),
        needsAndroidSdk = false,
    ),
)

fun sampleTasksOf(sample: SampleBuild): List<String> =
    providers.gradleProperty("katachi.sample.${sample.name}.task")
        .orElse(providers.gradleProperty("katachi.sample.task"))
        .orNull
        ?.split(" ")
        ?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() }
        ?: sample.defaultTasks

private val gradlewCommand: List<String> =
    if (providers.systemProperty("os.name").get().lowercase().startsWith("windows")) {
        listOf("cmd", "/c", "gradlew.bat")
    } else {
        listOf("./gradlew")
    }

private val androidSdkFromEnvironment: String? =
    providers.environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .orNull
        ?.takeIf { it.isNotBlank() }

private val wellKnownAndroidSdkDirs: List<File> =
    providers.systemProperty("user.home").get().let { home ->
        listOf(
            File(home, "Library/Android/sdk"), // macOS / Android Studio
            File(home, "Android/Sdk"), // Linux / Android Studio
        )
    }

val checkSamples = tasks.register("checkSamples") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs every standalone sample build under sample/ through its own wrapper. " +
            "sample/android and sample/kmp need an Android SDK: set ANDROID_HOME (or ANDROID_SDK_ROOT), " +
            "or write sdk.dir into sample/<name>/local.properties."
}

/**
 * Every task that runs a sample's wrapper, in the order they must run in. A sample build and
 * the root build share katachi's own `build/` through the composite build, so no two of them
 * may run at once -- and the configuration cache lets tasks of one project run in parallel.
 */
val registeredSamples = mutableListOf<TaskProvider<out Task>>()

/** Keeps [this] away from the root build's own katachi tasks, for the reason above. */
fun Task.runsAfterRootBuilds() {
    mustRunAfter(":katachi:check", ":katachi:build", ":katachi:jar", ":katachi:test")
    mustRunAfter(
        ":katachi-konsist:check",
        ":katachi-konsist:build",
        ":katachi-konsist:jar",
        ":katachi-konsist:test",
    )

    mustRunAfter(
        ":katachi-gradle-plugin:check",
        ":katachi-gradle-plugin:build",
        ":katachi-gradle-plugin:jar",
        ":katachi-gradle-plugin:test",
    )

    mustRunAfter(":architecture-test:test")
}

/** Runs [tasks] through the wrapper of the sample in [sampleDir]. */
fun Exec.runSampleWrapper(sample: SampleBuild, sampleDir: File, tasks: List<String>) {
    workingDir = sampleDir
    commandLine(gradlewCommand + tasks + listOf("--console=plain"))

    if (sample.needsAndroidSdk &&
        androidSdkFromEnvironment == null &&
        !File(sampleDir, "local.properties").exists()
    ) {
        wellKnownAndroidSdkDirs.firstOrNull { it.isDirectory }
            ?.let { environment("ANDROID_HOME", it.absolutePath) }
    }
    runsAfterRootBuilds()
}

sampleBuilds.forEach { sample ->
    val suffix = sample.name.split("-").joinToString("") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }
    val sampleTasks = sampleTasksOf(sample)
    val sampleDir = layout.projectDirectory.dir("sample/${sample.name}").asFile
    val predecessors = registeredSamples.toList()
    val sdkNote = if (sample.needsAndroidSdk) {
        " Needs an Android SDK: set ANDROID_HOME (or ANDROID_SDK_ROOT)," +
                " or write sdk.dir into sample/${sample.name}/local.properties."
    } else {
        ""
    }
    val template = sample.template
    // Read inside doFirst { } instead of `sample`, so the configuration cache has only a
    // String to store.
    val sampleName = sample.name

    // A sample without a template keeps checkSample<Name> as the one Exec it always was.
    // With one, that name becomes the lifecycle task over the build and the template run.
    val buildTaskName = if (template == null) "checkSample$suffix" else "checkSample${suffix}Build"
    val buildTask = tasks.register<Exec>(buildTaskName) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs `${sampleTasks.joinToString(" ")}` in the standalone sample build " +
                "sample/${sample.name}.$sdkNote"
        runSampleWrapper(sample, sampleDir, sampleTasks)
        mustRunAfter(predecessors)
    }
    registeredSamples += buildTask

    if (template == null) {
        checkSamples.configure { dependsOn(buildTask) }
        return@forEach
    }

    val generatedFiles = template.generatedFiles.map { File(sampleDir, it) }
    // Written just before generating and read by the clean-up: without it, a generation that
    // refused to start because the files were already there would have them deleted anyway.
    val marker = layout.buildDirectory.file("sample-template/${sample.name}.generated").get().asFile
    val generateArgs = listOf(":architecture-test:katachiTemplate") +
            template.args.flatMap { listOf("--arg", it) }

    val deleteTask = tasks.register<Delete>("deleteSample${suffix}Template") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Deletes the files generateSample${suffix}Template wrote into " +
                "sample/${sample.name}, and nothing else."
        onlyIf("generateSample${suffix}Template started generating") { marker.isFile }
        delete(generatedFiles + marker)
    }

    val generateTask = tasks.register<Exec>("generateSample${suffix}Template") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs `${generateArgs.joinToString(" ")}` in sample/${sample.name}. " +
                "deleteSample${suffix}Template always follows it.$sdkNote"
        runSampleWrapper(sample, sampleDir, generateArgs)
        mustRunAfter(buildTask)
        finalizedBy(deleteTask)
        doFirst {
            // A marker left by a run that was killed outright says nothing about this run.
            marker.delete()
            val leftovers = generatedFiles.filter { it.exists() }
            if (leftovers.isNotEmpty()) {
                throw GradleException(
                    "The template run would write over files that are already there, and the " +
                            "clean-up after it would then delete them:\n" +
                            leftovers.joinToString("\n") { "  ${it.path}" } +
                            "\nIf they are left over from an earlier interrupted run, delete them " +
                            "by hand and run again.",
                )
            }
            marker.parentFile.mkdirs()
            marker.writeText(generatedFiles.joinToString("\n", postfix = "\n") { it.path })
        }
    }

    val checkTemplateTask = tasks.register<Exec>("checkSample${suffix}Template") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs `${template.verifyTasks.joinToString(" ")}` in sample/${sample.name} " +
                "while the files generateSample${suffix}Template wrote are still there.$sdkNote"
        runSampleWrapper(sample, sampleDir, template.verifyTasks)
        dependsOn(generateTask)
        doFirst {
            val missing = generatedFiles.filterNot { it.isFile }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "The template run did not write these files, so the list in the root " +
                            "build.gradle.kts no longer matches the template of sample/$sampleName:\n" +
                            missing.joinToString("\n") { "  ${it.path}" } +
                            "\nFix the list, or the clean-up will leave generated files behind.",
                )
            }
        }
    }
    deleteTask.configure { mustRunAfter(checkTemplateTask) }
    registeredSamples += listOf(generateTask, checkTemplateTask, deleteTask)

    val task = tasks.register("checkSample$suffix") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs checkSample${suffix}Build, then generates the files of a template " +
                "in sample/${sample.name}, checks the sample with them in place and deletes them."
        dependsOn(buildTask, checkTemplateTask)
    }
    checkSamples.configure { dependsOn(task) }
}

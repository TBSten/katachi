plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.warnings-as-errors")
    id("buildsrc.convention.katachi-kotlin-library")
    id("buildsrc.convention.katachi-publish")
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("me.tbsten.katachi.InternalKatachiApi")
        optIn.add("me.tbsten.katachi.ExperimentalKatachiApi")
    }
}

dependencies {
    implementation(project(":katachi"))
    implementation(libs.kotlinxSerializationJson)
    // For the `*.katachi.kts` script definition. Not `compileOnly`: a build that depends on katachi-script
    // and has a `*.katachi.kts` gets the definition discovered, and loading it needs the scripting API.
    implementation(libs.kotlinScriptingCommon)
    implementation(libs.kotlinScriptingJvm)

    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    // Compiles a real `*.katachi.kts` against the definition, as `kotlin` and the IDE do.
    testImplementation(libs.kotlinScriptingJvmHost)
}

// The script builders are generated from COMMAND_SPECS by CommandBuildersSpec; it rewrites them with
// `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.
tasks.test {
    providers.systemProperty("katachi.golden.update").orNull?.let { systemProperty("katachi.golden.update", it) }
}

// `katachi --version` reads this; MANIFEST's Implementation-Version is null from a class directory.
tasks.processResources {
    val katachiVersion = version.toString()
    inputs.property("katachiVersion", katachiVersion)
    filesMatching("META-INF/katachi-script/version.properties") {
        expand("katachiVersion" to katachiVersion)
    }
}

// The setup steps of the katachi skill and the two templates `init` places and `guide` reads, bundled
// so that neither needs the network (KATACHI-228). The setup steps are one file per section under
// skills/katachi/references/setup (KATACHI-318); `guide` cuts sections out of one text, so they are
// joined here in reading order: the README, then step0, step1, … with each step's README first.
val skillSetupDir = rootProject.layout.projectDirectory.dir("skills/katachi/references/setup")
val bundleSetupGuide = tasks.register("bundleSetupGuide") {
    description = "Joins the katachi skill's setup steps into the guide that `katachi agent guide` prints."
    val source = skillSetupDir
    val output = layout.buildDirectory.file("generated/setupGuide/guide.md")
    inputs.dir(source)
    outputs.file(output)
    doLast {
        val root = source.asFile
        val steps = root.listFiles { file -> file.isDirectory && file.name.matches(Regex("step[0-9]+")) }!!
            .sortedBy { it.name.removePrefix("step").toInt() }
        val files = listOf(root.resolve("README.md")) + steps.flatMap { step ->
            step.listFiles { file -> file.name.endsWith(".md") }!!
                .sortedWith(compareBy<File> { it.name != "README.md" }.thenBy { it.name })
        }
        output.get().asFile.apply { parentFile.mkdirs() }.writeText(files.joinToString("\n") { it.readText() })
    }
}
val bundleInstallKit = tasks.register<Sync>("bundleInstallKit") {
    description = "Copies the setup guide and the templates into the resources."
    from(bundleSetupGuide) {
        into("META-INF/katachi-script/install")
    }
    from(rootProject.layout.projectDirectory.dir("docs/public/install")) {
        include("ja/install-check-list.html", "en/install-check-list.html")
        include("ja/project-code-base-report-template.html", "en/project-code-base-report-template.html")
        into("META-INF/katachi-script/install")
    }
    into(layout.buildDirectory.dir("generated/installKitResources"))
}

sourceSets.main {
    resources.srcDir(bundleInstallKit.map { layout.buildDirectory.dir("generated/installKitResources").get() })
}

// What `brew install tbsten/tap/katachi` unpacks: the `.main.kts` entry point pinned to this
// version and its `bin/katachi` wrapper. Every subcommand is in the katachi-script artifact the
// entry point pulls in, so the install kit script is no longer part of it (KATACHI-229).
val cliDist = tasks.register<Sync>("cliDist") {
    group = "distribution"
    description = "Assembles the katachi command (katachi.main.kts, bin/katachi) under build/cli."
    val katachiVersion = version.toString()
    inputs.property("katachiVersion", katachiVersion)
    from(layout.projectDirectory.dir("cli")) {
        filesMatching("katachi.main.kts") {
            expand("katachiVersion" to katachiVersion)
        }
    }
    into(layout.buildDirectory.dir("cli/katachi-cli-$katachiVersion"))
    filePermissions { unix("rwxr-xr-x") }
}

tasks.register<Tar>("cliTar") {
    group = "distribution"
    description = "Packs build/cli into katachi-cli-<version>.tar.gz, the asset the Homebrew formula downloads."
    from(cliDist)
    archiveBaseName.set("katachi-cli")
    archiveVersion.set(version.toString())
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

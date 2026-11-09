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

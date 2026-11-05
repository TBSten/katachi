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

    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
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
// version, its `bin/katachi` wrapper, and the install kit script the unported subcommands are
// handed to. The kit script is the one the docs site serves, copied rather than linked so the
// tarball stands on its own.
val cliDist = tasks.register<Sync>("cliDist") {
    group = "distribution"
    description = "Assembles the katachi command (katachi.main.kts, bin/katachi, katachi-install.sh) under build/cli."
    val katachiVersion = version.toString()
    inputs.property("katachiVersion", katachiVersion)
    from(layout.projectDirectory.dir("cli")) {
        filesMatching("katachi.main.kts") {
            expand("katachiVersion" to katachiVersion)
        }
    }
    from(rootProject.layout.projectDirectory.file("docs/public/install/katachi-install.sh"))
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

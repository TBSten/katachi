plugins {
    alias(libs.plugins.kotlinJvm)
    id("me.tbsten.katachi")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    systemProperty(
        "katachi.snapshot.update",
        providers.systemProperty("katachi.snapshot.update").getOrElse("false"),
    )
}

dependencies {
    testImplementation(libs.katachi)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    // `ProjectArchitectureTest` is a plain JUnit 5 test -- the form a user writes. kotest's
    // runner puts the Jupiter API on the compile classpath but registers only kotest's own
    // engine, so without `junit-jupiter` (API plus engine) that test would compile and then be
    // discovered by nothing. The BOM also lines up the junit-platform kotest brings in.
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

katachi {
    architecture = "com.example.projectArchitecture"
    processors {
        register("roleFileCount", "com.example.processors.RoleFileCount")
        register("roleTable", "com.example.processors.RoleTable") {
            arg("sortBy", "Name")
        }
        register("roleDocCoverage", "com.example.processors.RoleDocCoverage")
        docs {
            outputDir = rootProject.layout.projectDirectory.dir("docs")
        }
    }
}

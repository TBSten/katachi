plugins {
    alias(libs.plugins.kotlinJvm)
    id("me.tbsten.katachi")
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
    testImplementation(sampleLibs.junitJupiter)
}

katachi {
    architecture = "com.example.sample.projectArchitecture"
    processors {
        register("layout", "me.tbsten.katachi.check.LayoutCheck")
        docs {
            outputDir = rootProject.layout.projectDirectory.dir("docs")
        }
    }
}

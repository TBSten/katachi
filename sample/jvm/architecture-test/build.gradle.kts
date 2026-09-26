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
    testImplementation(libs.katachiKonsist)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    testRuntimeOnly(sampleLibs.junitJupiterEngine)
}

katachi {
    architecture = "com.example.projectArchitecture"
    processors {
        register("layout", "me.tbsten.katachi.check.LayoutCheck")
        register("fileConstraint", "me.tbsten.katachi.check.FileConstraintCheck")
        register("roleNames", "com.example.processors.RoleNames")

        docs {
            outputDir = rootProject.layout.projectDirectory.dir("docs")
        }
    }
}

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    id("me.tbsten.katachi")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
    systemProperty(
        "katachi.snapshot.update",
        providers.systemProperty("katachi.snapshot.update").getOrElse("false"),
    )
}

dependencies {
    testImplementation(libs.katachi)
    testCompileOnly(libs.jetbrainsAnnotations)
    testImplementation(libs.katachiKonsist)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

katachi {
    architecture = "com.example.projectArchitecture"
    report {
        sarif = true
    }
    processors {
        register("layout", "me.tbsten.katachi.check.LayoutCheck")
        register("fileConstraint", "me.tbsten.katachi.check.FileConstraintCheck")
        register("roleNames", "com.example.processors.RoleNames")

        docs {
            outputDir = rootProject.layout.projectDirectory.dir("docs")
        }
    }
}

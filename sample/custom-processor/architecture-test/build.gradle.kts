import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    id("me.tbsten.katachi")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    jvmToolchain(17)
    // Every compiler warning fails the build, so none accumulate unnoticed.
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
    // The tests check the whole repository, which is not an input of this task: without this,
    // adding or moving a file would leave the task UP-TO-DATE and the check would be skipped.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    testLogging {
        events("passed", "failed", "skipped")
        // The violations are listed in the assertion message; without FULL the console shows
        // only the exception type and the line that threw it.
        exceptionFormat = TestExceptionFormat.FULL
    }
    systemProperty(
        "katachi.snapshot.update",
        providers.systemProperty("katachi.snapshot.update").getOrElse("false"),
    )
}

dependencies {
    testImplementation(libs.katachi)
    // DocumentSections.kt の allowedContents / forbiddenContents に付けた @Language("markdown") 用。
    // 実行時には要らない注釈なので testCompileOnly。
    testCompileOnly(libs.jetbrainsAnnotations)
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

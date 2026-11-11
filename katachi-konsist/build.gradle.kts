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
        optIn.add("me.tbsten.katachi.ExperimentalKatachiApi")
    }
}

dependencies {
    api(project(":katachi"))
    api(libs.konsist)

    testImplementation(testFixtures(project(":katachi")))
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}

// The property-based specs of ktFileContent { } run few trials by default, as the IDE plugin's do.
// `-Pkatachi.pbt.scale=20` runs 20 times as many and `-Pkatachi.pbt.seed=N` tries another seed; both
// reach the test JVM as system properties (KtFileContentPbtSpecSupport.kt reads them).
tasks.test {
    listOf("katachi.pbt.seed", "katachi.pbt.scale").forEach { key ->
        providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
    }
    // SARIF の golden（src/test/resources/sarif/）を今の出力で書き直す: `./gradlew :katachi-konsist:test -Dkatachi.golden.update=true`
    providers.systemProperty("katachi.golden.update").orNull?.let { systemProperty("katachi.golden.update", it) }
    // Konsist keeps every file it has parsed in caches of its own for the life of the JVM, and a scaled
    // run parses tens of thousands of fixtures: more than a test worker's default heap holds.
    if ((providers.gradleProperty("katachi.pbt.scale").orNull?.toDoubleOrNull() ?: 1.0) > 1.0) maxHeapSize = "4g"
}

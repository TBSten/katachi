plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

group = "com.example"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.example.ApplicationKt")
}

dependencies {
    implementation(sampleLibs.ktorServerCore)
    implementation(sampleLibs.ktorServerNetty)
    implementation(sampleLibs.ktorServerContentNegotiation)
    implementation(sampleLibs.ktorSerializationKotlinxJson)
    implementation(sampleLibs.logbackClassic)

    // No version is written here either: `libs.katachi` points at
    // `me.tbsten.katachi:katachi:0.1.0-SNAPSHOT`, which does not exist in any repository.
    // The `includeBuild("../..")` in settings.gradle.kts substitutes it with the local
    // project, so a broken composite build fails loudly instead of silently resolving.
    testImplementation(libs.katachi)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(sampleLibs.ktorServerTestHost)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

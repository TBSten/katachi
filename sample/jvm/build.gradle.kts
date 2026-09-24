plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

group = "com.example"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
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

    // katachi is not a dependency of this module. The architecture definition and the
    // test that asserts it live in `:architecture-test`, a module of their own, because
    // they describe the whole project and belong to no layer of it. kotest stays: this
    // module still has tests of its own (HealthRouteTest).
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

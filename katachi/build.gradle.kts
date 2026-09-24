plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.katachi-kotlin-library")
    id("buildsrc.convention.katachi-publish")
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("me.tbsten.katachi.InternalKatachiApi")
        optIn.add("me.tbsten.katachi.ExperimentalKatachiApi")
        // `AbstractDecoder` は @ExperimentalSerializationApi。`StringMapDecoder` はそれを継承する
        // ので、ファイルごとの @OptIn ではなくモジュール全体で opt-in する（katachi 自身の2つの
        // マーカーと同じ扱い）。
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    api(libs.kotlinxSerializationCore)

    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}

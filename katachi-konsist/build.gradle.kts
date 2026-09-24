plugins {
    id("buildsrc.convention.kotlin-jvm")
    // Kotlin の互換設定（languageVersion 2.2）と Dokka。**katachi-publish より先に**書く:
    // あちらの javadoc jar は、ここで生える dokkaGeneratePublicationJavadoc の出力を包む。
    id("buildsrc.convention.katachi-kotlin-library")
    // Maven Central へ出すのは :katachi / :katachi-konsist / :katachi-gradle-plugin の3つだけ。
    // :architecture-test には付けない。
    id("buildsrc.convention.katachi-publish")
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

kotlin {
    // Every declaration that is part of the published surface has to say so.
    explicitApi()

    // `@ExperimentalKatachiApi` only, and deliberately module wide: `ConstraintScope.constraint`
    // carries that marker, so every entry point of this module would otherwise repeat the same
    // `@OptIn` for no reader's benefit.
    //
    // `@InternalKatachiApi` is deliberately absent. This module is the proof that the konsist
    // integration sits entirely on katachi's *public* surface: if a declaration here ever needs
    // an internal of `:katachi`, the build fails rather than quietly opting in. Test sources are
    // the one exception and write `@file:OptIn(InternalKatachiApi::class)` themselves, because a
    // spec has to hand `validate` a file system rooted at a fixture.
    compilerOptions {
        optIn.add("me.tbsten.katachi.ExperimentalKatachiApi")
    }
}

dependencies {
    // `api`, not `implementation`, for both: `ConstraintScope` is the context parameter type of
    // `konsist { }` and `KoScope` is the block's receiver, so a consumer writing `konsist { }`
    // needs both on its own compile classpath.
    api(project(":katachi"))
    api(libs.konsist)

    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}

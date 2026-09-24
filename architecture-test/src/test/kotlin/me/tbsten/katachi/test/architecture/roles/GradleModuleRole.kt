package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.module

/** The role of what every module brings with it, whatever else lives inside it. */
fun DeclarationContainerScope.gradleModule() = "GradleModule" {
    title = "モジュールのビルド"
    summary = "各モジュールが必ず持つ build.gradle.kts と、その生成物の非検査"
    example("katachi/build.gradle.kts", "ライブラリ本体のビルド定義")
    example("architecture-test/build.gradle.kts", "この定義を持つモジュールのビルド定義")
    example("katachi-gradle-plugin/build.gradle.kts", "Gradle plugin のビルド定義")
    layout {
        // Empty on purpose. The only thing this role has to say about a module is what
        // every module brings with it, and `.module { }` is precisely that: the
        // directory, its `build.gradle.kts`, and `"build".ignore()`. Where each
        // module's sources may live is the business of the roles that own them.
        ":katachi".module { }
        ":katachi-konsist".module { }
        ":katachi-gradle-plugin".module { }
        ":architecture-test".module { }
    }
}

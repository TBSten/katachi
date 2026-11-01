package me.tbsten.katachi.test.architecture.gradle

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

fun DeclarationContainerScope.buildLogic() = "BuildLogic".role {
    title = "ビルドロジック"
    summary = "buildSrc の convention plugin と、それらが共有する型。モジュール探索には出てこない別ビルド"
    description = """
        ビルドの関心事なので、`tool` ではなく `gradleGroup()` の `gradle { }` の中でこの role を足しています。
    """.trimIndent()
    example("kotlin-jvm.gradle.kts", "jvmToolchain(17) と useJUnitPlatform() を配る")
    layout {
        // buildSrc is a build of its own, not a subproject of this one, so no module path
        // resolves to it and `gradle()` does not declare its build script. Its `build` directory
        // and `build.gradle.kts` are spelled out here.
        "buildSrc" {
            "build".ignore()
            "build.gradle".ktsFile()
            "settings.gradle".ktsFile()
            "src" / "main" / "kotlin" / "*.gradle".ktsFile()
            // Types the convention plugins share, such as the typed settings of :tool:dokka.
            "src" / "main" / "kotlin" / "*".ktFile()
        }
    }
}

package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The role of `buildSrc`, a build of its own that module discovery never reaches. */
fun DeclarationContainerScope.buildLogic() = "BuildLogic" {
    title = "ビルドロジック"
    summary = "buildSrc の convention plugin。モジュール探索には出てこない別ビルド"
    example("kotlin-jvm.gradle.kts", "jvmToolchain(21) と useJUnitPlatform() を配る")
    layout {
        // Written as a plain directory rather than `":buildSrc".module { }`: buildSrc
        // is a build of its own, not a subproject of this one, so no module path
        // resolves to it. The two lines `.module { }` would have injected are spelled
        // out instead.
        "buildSrc" {
            "build".ignore()
            "build.gradle".ktsFile()
            "settings.gradle".ktsFile()
            "src" / "main" / "kotlin" / "*.gradle".ktsFile()
        }
    }
}

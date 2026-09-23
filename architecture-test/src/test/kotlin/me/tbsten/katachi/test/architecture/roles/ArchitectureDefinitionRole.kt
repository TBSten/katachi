package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.test.architecture.testPackage

/** The role of this definition itself, which belongs to no layer of the library. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
    example("ProjectArchitecture.kt", "定義の入口。group ごとの拡張関数を呼ぶ")
    example("roles/DslRole.kt", "Dsl の役割と、その3本の制約を宣言する拡張関数")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / testPackage / "ProjectArchitecture".ktFile()
            // Two shared helpers, next to the definition they serve because roles of more
            // than one group read them. Neither holds a wildcard, so both are required:
            // moving one back under a package is reported rather than quietly allowed.
            testSourceSet / kotlin / testPackage / "LayerImports".ktFile()
            testSourceSet / kotlin / testPackage / "KdocExamples".ktFile()
            // One declaration per file, and the file name says which kind it is. Only two
            // directories are allowed here, and only the matching suffix in each, so a
            // helper dropped into either is reported as an unexpected file rather than
            // quietly becoming a third kind of file.
            testSourceSet / kotlin / testPackage / "groups" / "*Group".ktFile()
            testSourceSet / kotlin / testPackage / "roles" / "*Role".ktFile()
        }
    }
}

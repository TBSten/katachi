package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

/** The role of this definition itself, which belongs to no layer of the library. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
    example("ProjectArchitecture.kt", "定義の入口。group ごとの拡張関数を呼ぶ")
    example("roles/DslRole.kt", "Dsl の役割と、その4本の制約を宣言する拡張関数")
    layout {
        "architecture-test" {
            testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "ProjectArchitecture".ktFile()
            // Three shared helpers, next to the definition they serve because roles of more
            // than one group read them. None holds a wildcard, so all three are required:
            // moving one back under a package is reported rather than quietly allowed.
            testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "LayerImports".ktFile()
            testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "InternalPackages".ktFile()
            testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "KdocExamples".ktFile()
            // One declaration per file, and the file name says which kind it is. Only two
            // directories are allowed here, and only the matching suffix in each, so a
            // helper dropped into either is reported as an unexpected file rather than
            // quietly becoming a third kind of file.
            testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "groups" / "*Group".ktFile()
            testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "roles" / "*Role".ktFile()
        }
    }
}

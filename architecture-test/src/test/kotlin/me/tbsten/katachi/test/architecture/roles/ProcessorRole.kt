package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.PACKAGE_MATCHES_PATH_RULE
import me.tbsten.katachi.test.architecture.importsLaterLayerThan
import me.tbsten.katachi.test.architecture.laterLayersOf
import me.tbsten.katachi.test.architecture.mainPackage
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.showsExample

/** The role of the entry point a user implements to turn one walk into whatever they need. */
fun DeclarationContainerScope.processor() = "Processor" {
    title = "プロセッサ"
    summary = "1度の走査の結果を受け取って、好きな形に変換する入口"
    example("ProjectModel.kt", "走査を高々1度に抑えたうえで宣言と実体の両方を見せるモデル")
    example("ArchitectureProcessor.kt", "利用者が実装する変換")
    layout {
        ":katachi".module {
            importsOnlyEarlierLayers()
            packageMatchesPath()
            publicDeclarationsShowExample()
            mainSourceSet / kotlin / mainPackage / "processor" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "processor" / "**" / "*".ktFile()
        }
    }
}

/** The layer this role is, named once so the rule and its wording cannot drift apart. */
private const val LAYER: String = "processor"

// Written here rather than in a shared file: `konsist { }` captures the first frame outside
// katachi as its declaration site, so a shared wrapper would make every layer role report the
// same line.
private fun LayoutScope.importsOnlyEarlierLayers() =
    "${laterLayersOf(LAYER)} を import しないこと".konsist {
        files.mustNot(importsLaterLayerThan(LAYER))
    }

private fun LayoutScope.packageMatchesPath() =
    PACKAGE_MATCHES_PATH_RULE.konsist {
        packages.must { it.hasMatchingPath }
    }

private fun LayoutScope.publicDeclarationsShowExample() =
    KDOC_EXAMPLE_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::showsExample)
    }

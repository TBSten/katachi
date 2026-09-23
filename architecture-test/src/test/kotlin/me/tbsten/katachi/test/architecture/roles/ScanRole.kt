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

/** The role of the single traversal: one walk of the tree, guided by what was declared. */
fun DeclarationContainerScope.scan() = "Scan" {
    title = "走査"
    summary = "宣言に導かれてツリーを1度だけ歩き、違反と「役割ごとのファイル」を同時に出す"
    example("Scan.kt", "唯一の走査")
    example("Violation.kt", "報告される1件")
    layout {
        ":katachi".module {
            importsOnlyEarlierLayers()
            packageMatchesPath()
            publicDeclarationsShowExample()
            mainSourceSet / kotlin / mainPackage / "scan" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "scan" / "**" / "*".ktFile()
        }
    }
}

/** The layer this role is, named once so the rule and its wording cannot drift apart. */
private const val LAYER: String = "scan"

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

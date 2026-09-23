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

/**
 * The role of the last layer: the one line a user writes, and the report it throws.
 *
 * The function name shadows nothing — `kotlin.check` always takes a `Boolean`, so the
 * zero-argument call in `groups/LibraryGroup.kt` can only resolve to this extension. The naming
 * rule is followed rather than bent, because `DeclarationSiteSpec` checks it mechanically.
 */
fun DeclarationContainerScope.check() = "Check" {
    title = "検査"
    summary = "利用者が呼ぶ assert() と、その報告の組み立て"
    example("Assert.kt", "利用者がテストに書く1行")
    example("ViolationReport.kt", "違反1件を人が読める1ブロックにする")
    layout {
        ":katachi".module {
            importsOnlyEarlierLayers()
            packageMatchesPath()
            publicDeclarationsShowExample()
            mainSourceSet / kotlin / mainPackage / "check" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "check" / "**" / "*".ktFile()
        }
    }
}

/** The layer this role is, named once so the rule and its wording cannot drift apart. */
private const val LAYER: String = "check"

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

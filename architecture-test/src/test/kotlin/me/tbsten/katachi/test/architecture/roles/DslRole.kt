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
 * The role of the layer that receives a definition and holds the declared model.
 *
 * The other place where the kind of file and the package layer come apart: `dsl.gradle` and
 * `dsl.kotlin` are the layout vocabulary rather than the core of the DSL, and they are still
 * this layer because that is where they sit in the dependency table. See the `description`.
 */
fun DeclarationContainerScope.dsl() = "Dsl" {
    title = "DSL"
    summary = "architecture { } / group { } / role { } / layout { } の受け皿と、宣言されたモデル"
    description = """
        `dsl.gradle` と `dsl.kotlin` もこの層です。`layout { }` の上に context parameter で
        書かれた語彙で、種類としては別物ですが依存としては同じ層にいます。
        `dsl/LayoutScopeImpl.kt` が `dsl.kotlin.ktsFile` を import しているので
        （`.module { }` が `build.gradle.kts` を注入するため）、
        「コアは語彙を import しない」という規則は今日の時点で落ちます。
    """.trimIndent()
    example("LayoutScope.kt", "layout { } の受け皿。コアの語彙はこれで全部")
    example("Architecture.kt", "宣言し終わった1つの定義")
    example("Modules.kt", "\":core:data\".module { } — dsl.gradle の語彙")
    layout {
        ":katachi".module {
            importsOnlyEarlierLayers()
            packageMatchesPath()
            publicDeclarationsShowExample()
            mainSourceSet / kotlin / mainPackage / "dsl" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "dsl" / "**" / "*".ktFile()
        }
    }
}

/** The layer this role is, named once so the rule and its wording cannot drift apart. */
private const val LAYER: String = "dsl"

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

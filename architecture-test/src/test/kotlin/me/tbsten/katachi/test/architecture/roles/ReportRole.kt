package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.INTERNAL_PACKAGE_RULE
import me.tbsten.katachi.test.architecture.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.KDOC_TAG_ORDER_RULE
import me.tbsten.katachi.test.architecture.PACKAGE_MATCHES_PATH_RULE
import me.tbsten.katachi.test.architecture.importsLaterLayerThan
import me.tbsten.katachi.test.architecture.laterLayersOf
import me.tbsten.katachi.test.architecture.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.showsExample
import me.tbsten.katachi.test.architecture.KATACHI_MAIN_PACKAGE

/**
 * The role of the machine-readable report: the violations of one run as a SARIF 2.1.0 file,
 * for GitHub code scanning and reviewdog to turn into annotations on a pull request.
 *
 * It shares a layer with `check`: `assertWith` writes the report, and the report reads the
 * violations and the report blocks, so neither can come before the other. See `LayerImports.kt`.
 */
fun DeclarationContainerScope.report() = "Report" {
    title = "レポート出力"
    summary = "1度の検査の違反を SARIF 2.1.0 のファイルにして、GitHub の PR に注釈として載せるためのもの"
    example("Sarif.kt", "利用者に開いている純粋な変換 toSarif と、書き出し writeSarif")
    example("SarifLocations.kt", "違反を置く場所の決め方。実在するファイル、宣言した行、architecture { } の順")
    example("SarifWrite.kt", "assert() が -Dkatachi.report.sarif を見て書き出す入口")
    layout {
        "katachi" {
            importsOnlyEarlierLayers()
            packageMatchesPath()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "report" / "*".ktFile()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "report" / "internal" / "*".ktFile()
            // The version `tool.driver.semanticVersion` reports. Written as `version=${katachiVersion}`
            // and expanded by `processResources`, so the running katachi can read its own version
            // from a class directory as well as from a jar.
            mainSourceSet / "resources" / "META-INF" / "katachi" / "version.properties".file()
        }
    }
}

/** The layer this role is, named once so the rule and its wording cannot drift apart. */
private const val LAYER: String = "report"

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

private fun LayoutScope.publicDeclarationsKeepTagsLast() =
    KDOC_TAG_ORDER_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::keepsBlockTagsLast)
    }

private fun LayoutScope.internalDeclarationsInInternalPackage() =
    INTERNAL_PACKAGE_RULE.konsist {
        val sealedParents = classesAndInterfaces(includeNested = true)
            .filter { it.hasSealedModifier }
            .map { it.name }
            .toSet()
        files.flatMap { misplacedDeclarationsOf(it, sealedParents) }.mustBeEmpty()
    }

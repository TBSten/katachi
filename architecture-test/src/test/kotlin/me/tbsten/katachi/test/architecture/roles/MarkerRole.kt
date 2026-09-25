package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.INTERNAL_PACKAGE_RULE
import me.tbsten.katachi.test.architecture.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.PACKAGE_MATCHES_PATH_RULE
import me.tbsten.katachi.test.architecture.importsLaterLayerThan
import me.tbsten.katachi.test.architecture.laterLayersOf
import me.tbsten.katachi.test.architecture.mainPackage
import me.tbsten.katachi.test.architecture.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.showsExample

/**
 * The role of the root package of `:katachi`.
 *
 * One of the two places where the kind of file and the package layer come apart, and the role
 * follows the kind: these files are markers and exception bases rather than a layer of their
 * own, which is why the layer they are checked as is the empty one that everything may depend
 * on.
 */
fun DeclarationContainerScope.marker() = "Marker" {
    title = "マーカーと例外基底"
    summary = "どの層にも属さず、すべての層が依存してよいもの"
    example("ExperimentalKatachiApi.kt", "利用者に開いているが、まだ形が動く API の opt-in マーカー")
    example("InternalKatachiApi.kt", "ライブラリの他モジュール専用の API の opt-in マーカー")
    example("Exceptions.kt", "利用者が catch する例外の基底")
    layout {
        ":katachi".module {
            importsOnlyEarlierLayers()
            packageMatchesPath()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            // The root package, and only it: `*` never crosses a `/`, so the layer
            // directories one level down are untouched by this.
            mainSourceSet / kotlin / mainPackage / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "internal" / "*".ktFile()
        }
    }
}

/** The empty name is the root package itself, the first entry of the layer table. */
private const val LAYER: String = ""

// The four rules are written here, in the role's own file, rather than in a shared one:
// `konsist { }` captures the first frame outside katachi as its declaration site, so a shared
// wrapper would make every layer role report this same line. Private per file, the report keeps
// naming the role that owns the rule.
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

private fun LayoutScope.internalDeclarationsInInternalPackage() =
    INTERNAL_PACKAGE_RULE.konsist {
        val sealedParents = classesAndInterfaces(includeNested = true)
            .filter { it.hasSealedModifier }
            .map { it.name }
            .toSet()
        files.flatMap { misplacedDeclarationsOf(it, sealedParents) }.mustBeEmpty()
    }

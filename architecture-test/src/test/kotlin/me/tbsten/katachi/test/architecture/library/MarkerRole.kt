package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.INTERNAL_PACKAGE_RULE
import me.tbsten.katachi.test.architecture.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.KDOC_TAG_ORDER_RULE
import me.tbsten.katachi.test.architecture.laterLayerImportsOf
import me.tbsten.katachi.test.architecture.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.showsExample
import me.tbsten.katachi.test.architecture.KATACHI_MAIN_PACKAGE

fun DeclarationContainerScope.marker() = "Marker".role {
    title = "マーカーと例外基底"
    summary = "どの層にも属さず、すべての層が依存してよいもの"
    example("ExperimentalKatachiApi.kt", "利用者に開いているが、まだ形が動く API の opt-in マーカー")
    example("InternalKatachiApi.kt", "ライブラリの他モジュール専用の API の opt-in マーカー")
    example("Exceptions.kt", "利用者が catch する例外の基底")
    layout {
        "katachi" {
            packageAndLayerImports()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            // The root package, and only it: `*` never crosses a `/`, so the layer
            // directories one level down are untouched by this.
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "*".ktFile()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "internal" / "*".ktFile()
        }
    }
}

private const val LAYER: String = ""

// The rules are written here, in the role's own file, rather than in a shared one: katachi
// captures the first frame outside itself as the declaration site of a `konsist { }`, of a
// `ktFileContent { }` and of each line in it, so a shared wrapper would make every layer role
// report this same line. Private per file, the report keeps naming the role that owns the rule.
@OptIn(ExperimentalKatachiApi::class)
private fun LayoutScope.packageAndLayerImports() =
    ktFileContent {
        `package`(matchingDirectory)
        `import`("**")
        laterLayerImportsOf(LAYER).forEach { not.`import`(it) }
        // What the root package is for: the opt-in markers and the exception bases a user catches,
        allow(public, annotation, `class`, "*KatachiApi")
        allow(public, abstract, `class`, "Katachi*Exception")
        // and, in `internal`, the helpers every layer shares, closed to the library.
        allow(internal, anythingElse)
        allow(private, anythingElse)
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

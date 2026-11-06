package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
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

fun DeclarationContainerScope.processor() = "Processor".role {
    title = "プロセッサ"
    summary = "1度の走査の結果を受け取って、好きな形に変換する入口"
    example("ArchitectureProcessContext.kt", "走査を高々1度に抑えたうえで、宣言と実体の両方を processor に見せる入口")
    example("ArchitectureProcessor.kt", "利用者が実装する変換")
    example("StringMapDecoder.kt", "--arg key=value を processor の Args 型に読み替える")
    example("KatachiEntryPoint.kt", "Gradle plugin が生成する object が実装する唯一の型。main() が名前で読むのはここだけ")
    example("ProcessorRun.kt", "CLI から複数の processor を型消去された経路で走らせる")
    layout {
        "katachi" {
            packageAndLayerImports()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "processor" / "*".ktFile()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "processor" / "**" / "*".ktFile()
        }
    }
}

private const val LAYER: String = "processor"

// Written here rather than in a shared file: katachi captures the first frame outside itself as
// the declaration site of a `konsist { }`, of a `ktFileContent { }` and of each line in it, so a
// shared wrapper would make every layer role report the same line.
@OptIn(ExperimentalKatachiApi::class)
private fun LayoutScope.packageAndLayerImports() =
    ktFileContent {
        `package`(matchingDirectory)
        `import`("**")
        laterLayerImportsOf(LAYER).forEach {
            // runMain writes definition-errors.json even when the entry point cannot be read, so it cannot call ide through a registry
            if (fileName == "DefinitionErrorReport") {
                not.`import`(it).except(*DEFINITION_ERROR_REPORT_IMPORTS)
            } else {
                not.`import`(it)
            }
        }
        // What a declaration may be is left to the `konsist { }` rules below, which read the KDoc and the `.internal` line.
        allow(anythingElse)
    }

private val DEFINITION_ERROR_REPORT_IMPORTS: Array<String> = arrayOf(
    "me.tbsten.katachi.ide.internal.DeclarationSiteJson",
    "me.tbsten.katachi.ide.internal.siteOf",
    "me.tbsten.katachi.template.internal.encodeToJson",
    "me.tbsten.katachi.template.internal.writeFileAtomically",
)

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

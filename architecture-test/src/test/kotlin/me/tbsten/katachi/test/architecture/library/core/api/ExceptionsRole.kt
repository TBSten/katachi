package me.tbsten.katachi.test.architecture.library.core.api

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.*
import me.tbsten.katachi.test.architecture.konsist.*

fun DeclarationContainerScope.exceptions() = "Exceptions".role {
    title = "例外の基底"
    summary = "どの層にも属さず、すべての層が依存してよい、利用者が catch する例外の基底"
    example("Exceptions.kt", "利用者が catch する例外の基底")
    layout {
        "katachi" {
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "Exceptions".ktFile()
        }
    }
    packageAndLayerImports()
    publicDeclarationsShowExample()
    publicDeclarationsKeepTagsLast()
    internalDeclarationsInInternalPackage()
}

private const val LAYER: String = ""

// The rules are written here, in the role's own file, rather than in a shared one: katachi
// captures the first frame outside itself as the declaration site of a `konsist { }`, of a
// `ktFileContent { }` and of each line in it, so a shared wrapper would make every layer role
// report this same line. Private per file, the report keeps naming the role that owns the rule.
@OptIn(ExperimentalKatachiApi::class)
private fun RoleScope.packageAndLayerImports() =
    ktFileContent {
        `package`(matchingDirectory)
        `import`("**")
        laterLayerImportsOf(LAYER).forEach { not.`import`(it) }
        // The bases a user catches.
        allow(public, abstract, `class`, "Katachi*Exception")
        // The helpers every layer shares live in `internal`, which `Internal › Util` owns, so nothing
        // `internal` belongs in the root package itself.
        allow(private, anythingElse)
    }

private fun RoleScope.publicDeclarationsShowExample() =
    KDOC_EXAMPLE_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::showsExample)
    }

private fun RoleScope.publicDeclarationsKeepTagsLast() =
    KDOC_TAG_ORDER_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::keepsBlockTagsLast)
    }

private fun RoleScope.internalDeclarationsInInternalPackage() =
    INTERNAL_PACKAGE_RULE.konsist {
        val sealedParents = classesAndInterfaces(includeNested = true)
            .filter { it.hasSealedModifier }
            .map { it.name }
            .toSet()
        files.flatMap { misplacedDeclarationsOf(it, sealedParents) }.mustBeEmpty()
    }

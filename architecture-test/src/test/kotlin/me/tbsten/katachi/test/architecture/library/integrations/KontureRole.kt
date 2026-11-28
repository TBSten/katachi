package me.tbsten.katachi.test.architecture.library.integrations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.konsist.INTERNAL_PACKAGE_RULE
import me.tbsten.katachi.test.architecture.konsist.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.konsist.KDOC_TAG_ORDER_RULE
import me.tbsten.katachi.test.architecture.konsist.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.konsist.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.konsist.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.konsist.showsExample

private const val KONTURE_MAIN_PACKAGE: String = "me/tbsten/katachi/konture"

fun DeclarationContainerScope.kontureModule() = "Konture".role {
    title = "Konture 連携 (katachi-konture)"
    summary = "konture { } を layout { } の中に書けるようにする。katachi の public な面だけに乗る"
    description = """
        katachi の public な面だけに乗ります。`katachi-konture/build.gradle.kts` で `@ExperimentalKatachiApi` だけに opt-in し、`@InternalKatachiApi` には opt-in しないことで、ビルドが強制しています。
    """.trimIndent()
    example("Konture.kt", "\"...\".konture { } の入口")
    example("KontureConstraintScope.kt", "Konture の規則の入口 files / classes / functions / properties")
    layout {
        "katachi-konture" {
            mainSourceSet / kotlin / KONTURE_MAIN_PACKAGE / "*".ktFile()
            mainSourceSet / kotlin / KONTURE_MAIN_PACKAGE / "internal" / "*".ktFile()
        }
    }
    packageMatchesDirectory()
    publicDeclarationsShowExample()
    publicDeclarationsKeepTagsLast()
    internalDeclarationsInInternalPackage()
}

// Written here rather than in a shared file: katachi captures the first frame outside itself as
// the declaration site of a `konsist { }`, of a `ktFileContent { }` and of each line in it, so a
// shared wrapper would make every role that declares these rules report the same line.
@OptIn(ExperimentalKatachiApi::class)
private fun RoleScope.packageMatchesDirectory() =
    ktFileContent {
        `package`(matchingDirectory)
        // No layer comes after this one, and what of katachi it may reach is the build's opt-in (see `katachi-konture/build.gradle.kts`).
        `import`("**")
        // The one file reading Konture's internal parser suppresses the visibility checks for it.
        fileAnnotation("Suppress(*INVISIBLE_*)")
        // What a declaration may be is left to the `konsist { }` rules below, which read the KDoc and the `.internal` line.
        allow(anythingElse)
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

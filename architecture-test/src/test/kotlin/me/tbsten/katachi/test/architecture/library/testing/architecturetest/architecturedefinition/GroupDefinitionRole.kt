package me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturedefinition

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

fun DeclarationContainerScope.groupDefinition() = "GroupDefinition".role {
    title = "group の定義"
    summary = "group 1つを宣言する拡張関数だけを持つ <Name>Group.kt。group の名前のディレクトリに置く"
    example("library/LibraryGroup.kt", "Library の group と、その子の group")
    layout {
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "**" / "*Group".ktFile()
    }
    groupFileHoldsOnlyTheGroup()
}

// A role written here instead of in its own `<Name>Role.kt` fails twice: DeclarationSiteSpec
// sees the wrong file name, and this block sees a second function.
@OptIn(ExperimentalKatachiApi::class)
private fun RoleScope.groupFileHoldsOnlyTheGroup() =
    "group のファイルは group の拡張関数1つだけを持つ".ktFileContent {
        `package`(matchingDirectory)

        `import`("me.tbsten.katachi.dsl.**")
        // A group names the groups below it, which sit in packages of their own.
        `import`("me.tbsten.katachi.test.architecture.**")

        required(`fun`, "DeclarationContainerScope", "*Group")()
    }

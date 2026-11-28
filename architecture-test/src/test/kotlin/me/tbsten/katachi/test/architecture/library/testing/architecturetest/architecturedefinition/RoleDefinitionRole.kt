package me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturedefinition

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

fun DeclarationContainerScope.roleDefinition() = "RoleDefinition".role {
    title = "役割の定義"
    summary = "役割1つを宣言する拡張関数と、その役割だけが使う private な部品を持つ <Name>Role.kt。呼ぶ group のディレクトリに置く"
    example("library/base/api/DslRole.kt", "Dsl の役割と、その制約を組み立てる private な関数")
    layout {
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "**" / "*Role".ktFile()
    }
    roleFileHoldsOneRoleAndItsPrivateParts()
}

// The role function carries no modifier on purpose: an `inline` one would make katachi capture
// a declaration site nobody wrote, and the line below does not let one through.
@OptIn(ExperimentalKatachiApi::class)
private fun RoleScope.roleFileHoldsOneRoleAndItsPrivateParts() =
    "役割のファイルは役割の拡張関数と private な部品だけを持ち、他の group の役割を import しない".ktFileContent {
        `package`(matchingDirectory)

        `import`("me.tbsten.katachi.**")
        `import`("com.lemonappdev.konsist.**")
        // What several roles share sits next to the entry; the Konsist rules in `konsist` are shared by every layer's role; a role of another group is not one of those.
        not.`import`("me.tbsten.katachi.test.architecture.*.*.**")
            .except("me.tbsten.katachi.test.architecture.konsist.*")

        required(`fun`, "DeclarationContainerScope", "*")()
        allow(private, anythingElse)
    }

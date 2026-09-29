package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The definition this very file is part of.
 *
 * It is not test code and belongs to no layer of the app, so it gets a role of its own instead
 * of hiding inside [test]: "a file with no role does not exist" applies to katachi's own module
 * too.
 */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "Architecture definition"
    summary = "src/test of the :architecture-test module. This project's definition, written in the katachi DSL. " +
        "It belongs to no layer, so it lives in a dedicated module"
    description = """
        The code that describes the shape of this project with katachi. The file you are reading
        belongs to this role too. It is kept apart from test code: the definition's job is to
        describe the project, and the test that checks it against the real directories
        (`ProjectArchitectureTest`) is a single line.

        It sits in a dedicated `:architecture-test` module because this is KMP. katachi is a JVM
        library, and the other modules of this build are KMP with only Android and iOS, so there
        is no `commonTest` anywhere to hold the definition. Preparing one `kotlin("jvm")` module
        is also the shape katachi recommends.

        The files follow the rule of one file per declaration: the role `"UiCore"` is in
        `roles/UiCoreRole.kt`, and the group `"app"` in `groups/AppGroup.kt`.
        `ProjectArchitectureSpec` checks that rule itself by reading declaration sites back from
        the source, so it fails if `inline` is added to a group / role function.

        The layout is deliberately loose and takes one package level with `*`: adding a new
        file to `roles` passes without touching the definition. A strict form that spells out
        package names is in sample/android; having both shows that you can choose.
    """.trimIndent()
    allowedContents = """
        - The definition (the `groups` / `roles` packages) and its entry point `ProjectArchitecture.kt`
        - `ProjectArchitectureTest.kt`, which checks the definition against the repository (the only test a user writes)
        - `*Spec.kt`, which check the definition
        - Custom processors in the `processor` package (those that read custom metadata such as `owner`)
    """.trimIndent()
    forbiddenContents = """
        - App code. This module belongs to no layer of the app
    """.trimIndent()
    example("ProjectArchitecture.kt", "The entry point of the definition")
    example("roles/ComponentRole.kt", "The declaration of one role")
    // Two patterns: the entry point and the specs sit in `com/example/kmp` itself, and each
    // concern gets one package below it — `groups`, `roles`, `processor`. The `*` in the
    // middle is that package.
    //
    // Deliberately left this loose rather than naming the two packages: a file dropped
    // into `roles` under any name still passes here. sample/android is the one that writes
    // the stricter form, and having one of each is what shows the choice exists.
    //
    // The package is written out rather than derived: `modulePackage` would turn
    // `:architecture-test` into `com/example/kmp/architectureTest`, and this module
    // deliberately holds `com.example.kmp` itself, next to nothing else.
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/kmp" / "*".ktFile()
            testSourceSet / kotlin / "com/example/kmp" / "*" / "*".ktFile()
        }
    }
}

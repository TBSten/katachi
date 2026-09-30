package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role that only exists because this is a KMP project: the `expect` half.
 *
 * `commonMain` declares `expect`, and [actualImplementation] supplies the `actual` in
 * `androidMain` / `iosMain`. The files have to sit in the same package, so the package is part
 * of what these two roles describe.
 */
fun DeclarationContainerScope.expectDeclaration() = "ExpectDeclaration" {
    title = "Expect declaration"
    summary = "The :data module's platform package. The expect declaration in commonMain that the common side calls"
    description = """
        A role that exists only because this sample is KMP. Write `expect` in `commonMain`, in
        the `platform` package of `:data`. The common side (the ViewModels, through `:data`)
        calls it as if it were an ordinary function; the ActualImplementation role holds the
        `actual` that each platform supplies.

        It is a role of its own, separate from the `actual` files, because the two are different
        kinds of file: one is the contract, written once in common code, and the other is
        written once per platform. Only the contract is visible to the common side.

        A caveat: only the compiler can check that expect and actual match, and the iOS half is
        not compiled on CI (the reason is in `app/ios/README.md`). So even if the `actual` of one
        platform were forgotten, katachi would not report `[MissingFile]`: every file of the
        ActualImplementation role is a wildcard. That is the hole here.
    """.trimIndent()
    allowedContents = """
        - The `expect` declaration of a thin entry to a platform API that cannot be written from common
    """.trimIndent()
    forbiddenContents = """
        - Anything that can be written in common. Every `expect`/`actual` adds two
          implementations to write, and the more shareable things you bring in, the less it pays off
        - Anything about UI. This sample has no platform difference around screens at all
    """.trimIndent()
    example("PlatformInfo.kt", "The expect declaration in commonMain")
    layout {
        ":data".module {
            "commonMain".sourceSet / kotlin / modulePackage / "platform" / "*".ktFile()
        }
    }
}

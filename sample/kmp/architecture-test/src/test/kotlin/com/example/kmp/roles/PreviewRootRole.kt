package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The `preview` package of `:ui`.
 *
 * `PreviewRoot` is the only thing in it, and every `@Preview` in the sample goes through it
 * instead of writing `AppTheme { }` itself. Kept apart from [preview], whose name it is a
 * prefix of, because the two are different things: one is the wrapper, the other is what the
 * wrapper is used by.
 */
fun DeclarationContainerScope.previewRoot() = "PreviewRoot" {
    title = "プレビューの土台"
    summary = ":ui モジュールの preview package。@Preview の中身を AppTheme と Surface で包む"
    example("PreviewRoot", "すべての @Preview が使う wrapper")
    // One of the file names in this sample written without a wildcard, so it is also
    // one of the declarations that is reported as `[MissingFile]` when it disappears.
    layout {
        ":ui".module {
            "commonMain".sourceSet / kotlin / modulePackage / "preview" / "PreviewRoot".ktFile()
        }
    }
}

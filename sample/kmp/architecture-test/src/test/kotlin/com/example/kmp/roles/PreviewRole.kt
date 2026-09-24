package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The `@Preview` functions themselves.
 *
 * Unlike sample/android, which keeps its `@Preview` functions in the same file as the
 * composable they render, this sample puts them in a `<Target>Preview.kt` file beside it.
 * Both shapes are common; having one sample of each is the point.
 *
 * It is also the one role of this sample that opens two places instead of one, which is why
 * both of them carry a `description`: a role spread over more than one place has to say what
 * tells the two apart, and katachi reports `[MissingDescription]` when it does not.
 */
fun DeclarationContainerScope.preview() = "Preview" {
    title = "プレビュー"
    summary = "@Preview を付けた private @Composable。対象の Composable と同じ package の " +
        "<対象>Preview.kt に置き、中身は PreviewRoot で包む"
    example("PrimaryButtonPreview", "PrimaryButton のプレビュー")
    example("HomeLoadedPreview", "読み込み済みの HomeContent のプレビュー")
    // A preview lives beside what it renders, so this role claims a file name in two
    // different modules instead of a directory of its own. `component/*.kt` of
    // `Component` covers the same file as well: two roles may claim one path, and
    // from v0.3 the generated documentation lists both.
    //
    // katachi reports that overlap as `[AmbiguousLayout]` on the file both patterns match,
    // which is a Warning and never fails `assert()`. It is kept rather than designed away:
    // a preview belongs beside the component it renders, and the report saying so out loud
    // is what this sample wants to show — see `OmittedRoleSelfCheckSpec`, which pins it.
    //
    // In a feature module the name is tied to the module the same way the screen it
    // renders is: `:feature:home` may hold `Home*Preview.kt` and nothing else.
    layout {
        ":feature:*".module {
            description = "画面のプレビュー。その画面を持つ feature モジュールに置く"
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcards[0].pascalCase}*Preview".ktFile()
        }
        ":ui".module {
            description = "部品のプレビュー。どの画面にも属さないので :ui に置く"
            "commonMain".sourceSet / kotlin / modulePackage / "component" / "*Preview".ktFile()
        }
    }
}

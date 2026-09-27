package com.example.kmp

import com.example.kmp.groups.appGroup
import com.example.kmp.groups.dataGroup
import com.example.kmp.groups.featureGroup
import com.example.kmp.groups.gradleGroup
import com.example.kmp.groups.testingGroup
import com.example.kmp.groups.toolGroup
import com.example.kmp.groups.uiGroup
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.architecture

/**
 * **This is katachi's own verification, not something a user of katachi writes.**
 *
 * [ProjectArchitectureTest] proves that the check passes on a repository that matches its
 * definition. On its own that proves very little: a check that never reports anything passes
 * just as well. So this spec builds definitions that are deliberately incomplete — the real
 * one minus one group of roles — and pins down exactly what katachi reports for each.
 *
 * It uses `validate()`, which hands the violations back instead of throwing. That function is
 * `@InternalKatachiApi` on purpose: a user asserts, and this file is not a user.
 *
 * `validate()` answers with the warnings too, so [PREVIEW_OVERLAP] is in every expectation
 * below. It is not a failure and not a side effect of dropping a group — see its own comment.
 */
@OptIn(InternalKatachiApi::class)
class OmittedRoleSelfCheckSpec : FreeSpec({
    "役割を落とすと、その役割だけが引き受けていたファイルが Unexpected になる" {
        // `tool` owns exactly two files, both at the root -- `.gitignore` and `README.md` --
        // and no other role claims either, so leaving `toolGroup()` out removes exactly those
        // two homes and the expectation can be written out in full.
        val withoutTool = architecture {
            featureGroup()
            uiGroup()
            dataGroup()
            testingGroup()
            appGroup()
            gradleGroup()
        }

        // `validate()` does not read the baseline, so what it holds back is here too.
        withoutTool.validate().labels() shouldContainExactly
            listOf(
                "[UnexpectedFile] .gitignore",
                "[UnexpectedFile] README.md",
                HELD_BACK_BY_BASELINE,
                PREVIEW_OVERLAP,
            )
    }

    "未知のディレクトリは1件だけ報告され、その配下は掘られない" {
        // The `data` group is what claims everything under `data/src`. Without it that
        // directory has no role, and the five files below it are *not* reported one by one:
        // the one directory that has to be explained is, and the walk stops there.
        //
        // `data` itself stays known, because `Gradle/BuildScript` claims
        // `data/build.gradle.kts` through its `":**".module { }`.
        val withoutData = architecture {
            featureGroup()
            uiGroup()
            testingGroup()
            appGroup()
            gradleGroup()
            toolGroup()
        }

        withoutData.validate().labels() shouldContainExactly
            listOf("[UnexpectedDirectory] data/src", PREVIEW_OVERLAP)
    }
})

/**
 * The violation left in the project on purpose, as the demo of `baseline = baselineFile()`, and
 * recorded in `katachi-baseline.json`. `validate()` reports it; `assert()` holds it back. The
 * definition without `data` does not report it, because the walk stops at `data/src`.
 */
private const val HELD_BACK_BY_BASELINE: String =
    "[UnexpectedDirectory] data/src/androidMain/kotlin/com/example/kmp/data/user"

/**
 * The one overlap this sample means to have. In the `component` package of `:ui`, `Component`
 * claims every `.kt` file and `Preview` claims the ones ending in `Preview.kt`, so the one file
 * matching both belongs to both roles.
 *
 * katachi reports it as a Warning, which never fails `assert()` — see `roles/PreviewRole.kt`,
 * where the overlap is declared on purpose. Both definitions above keep `uiGroup()`, so it is
 * in both of their results and says nothing about the group each of them dropped.
 */
private const val PREVIEW_OVERLAP: String =
    "[AmbiguousLayout] ui/src/commonMain/kotlin/com/example/kmp/ui/component/PrimaryButtonPreview.kt"

/**
 * `[UnexpectedFile] path`, with [Violation.path] relative to the project root. A report block's
 * first line has the same shape, but prints the path as a `file:///...` URI.
 */
@OptIn(InternalKatachiApi::class)
private fun List<Violation>.labels(): List<String> = map { "[${it.label}] ${it.path}" }

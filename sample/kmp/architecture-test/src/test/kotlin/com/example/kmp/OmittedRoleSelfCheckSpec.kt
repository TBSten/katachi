package com.example.kmp

import com.example.kmp.application.appRoles
import com.example.kmp.application.dataRoles
import com.example.kmp.application.featureRoles
import com.example.kmp.application.uiRoles
import com.example.kmp.gradle.gradleRoles
import com.example.kmp.testing.testingRoles
import com.example.kmp.tool.toolRoles
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.architecture

/**
 * **This is katachi's own verification, not something a user of katachi writes.**
 *
 * [ProjectLayoutSpec] proves that the check passes on a repository that matches its
 * definition. On its own that proves very little: a check that never reports anything passes
 * just as well. So this spec builds definitions that are deliberately incomplete — the real
 * one minus one group of roles — and pins down exactly what katachi reports for each.
 *
 * It uses `validate()`, which hands the violations back instead of throwing. That function is
 * `@InternalKatachiApi` on purpose: a user asserts, and this file is not a user.
 */
@OptIn(InternalKatachiApi::class)
class OmittedRoleSelfCheckSpec : FreeSpec({
    "役割を落とすと、その役割だけが引き受けていたファイルが Unexpected になる" {
        // `tool/Git` is the only role claiming `.gitignore`, and the only role of its group,
        // so leaving `toolRoles()` out removes exactly one file's home.
        val withoutTool = architecture {
            featureRoles()
            uiRoles()
            dataRoles()
            testingRoles()
            appRoles()
            gradleRoles()
        }

        withoutTool.validate().labels() shouldContainExactly listOf("[UnexpectedFile] .gitignore")
    }

    "未知のディレクトリは1件だけ報告され、その配下は掘られない" {
        // The `data` group is what claims everything under `data/src`. Without it that
        // directory has no role, and the five files below it are *not* reported one by one:
        // the one directory that has to be explained is, and the walk stops there.
        //
        // `data` itself stays known, because `build/GradleModule` claims
        // `data/build.gradle.kts`.
        val withoutData = architecture {
            featureRoles()
            uiRoles()
            testingRoles()
            appRoles()
            gradleRoles()
            toolRoles()
        }

        withoutData.validate().labels() shouldContainExactly
            listOf("[UnexpectedDirectory] data/src")
    }
})

/** `[UnexpectedFile] path`, which is how the first line of each violation block reads. */
@OptIn(InternalKatachiApi::class)
private fun List<Violation>.labels(): List<String> = map { "[${it.label}] ${it.path}" }

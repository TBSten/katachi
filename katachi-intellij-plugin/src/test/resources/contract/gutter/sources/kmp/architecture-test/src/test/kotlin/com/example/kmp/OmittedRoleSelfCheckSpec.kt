package com.example.kmp

import com.example.kmp.groups.appGroup
import com.example.kmp.groups.dataGroup
import com.example.kmp.groups.featureGroup
import com.example.kmp.groups.gradleGroup
import com.example.kmp.groups.testingGroup
import com.example.kmp.groups.toolGroup
import com.example.kmp.groups.uiGroup
import com.example.kmp.roles.featureComponent
import com.example.kmp.roles.route
import com.example.kmp.roles.screenPreview
import com.example.kmp.roles.viewModel
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UnexpectedFile
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
 *
 * `FileConstraintCheck()` is passed as [ProjectArchitectureTest] passes it, so the
 * `ktFileContent { }` of the roles kept in each definition are checked rather than reported as
 * `[UncheckedFileConstraint]`. The files still match them, so they add nothing to the expectations.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
class OmittedRoleSelfCheckSpec : FreeSpec({
    "dropping a role makes the files only that role covered Unexpected" {
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
        withoutTool.validate(FileConstraintCheck()).labels() shouldContainExactly
            listOf(
                "[UnexpectedFile] .gitignore",
                "[UnexpectedFile] README.md",
                HELD_BACK_BY_BASELINE,
                PREVIEW_OVERLAP,
            )
    }

    "an unknown directory is reported once and its contents are not descended into" {
        // The `data` group is what claims everything under `data/src`. Without it that
        // directory has no role, and the six files below it are *not* reported one by one:
        // the one directory that has to be explained is, and the walk stops there.
        //
        // `data` itself stays known, because `Gradle/BuildScript` claims
        // `data/build.gradle.kts` through the module key it declares.
        val withoutData = architecture {
            featureGroup()
            uiGroup()
            testingGroup()
            appGroup()
            gradleGroup()
            toolGroup()
        }

        withoutData.validate(FileConstraintCheck()).labels() shouldContainExactly
            listOf("[UnexpectedDirectory] data/src", PREVIEW_OVERLAP)
    }

    "the nearby locations of a file that lost its role are the roles of its own directory, never Gradle.BuildScript" {
        // Every feature role is written as `"feature" / "*"`, and a place holding a wildcard
        // used to be no candidate at all, which left `build.gradle.kts` as the only thing
        // offered. The wildcards are filled in from the file's own path now, so `HomeScreen.kt`
        // is offered the three roles declared in the directory it sits in, in declaration
        // order; FeatureComponent, one level below, is fourth and therefore not shown.
        val withoutScreen = architecture {
            "feature".group {
                // screen() -- omitted on purpose. `HomeScreen.kt` and `SettingsScreen.kt` lose their role.
                viewModel()
                route()
                featureComponent()
                screenPreview()
            }
            uiGroup()
            dataGroup()
            testingGroup()
            appGroup()
            gradleGroup()
            toolGroup()
        }

        val unexpected = withoutScreen.validate(FileConstraintCheck())
            .filterIsInstance<UnexpectedFile>()
            .single { it.path == "$HOME_SOURCES/HomeScreen.kt" }

        unexpected.nearby.map { Triple(it.role.qualifiedName, it.directory, it.filePattern) } shouldBe listOf(
            Triple("feature.ViewModel", HOME_SOURCES, "*ViewModel.kt"),
            Triple("feature.Route", HOME_SOURCES, "*Route.kt"),
            Triple("feature.ScreenPreview", HOME_SOURCES, "*Preview.kt"),
        )
    }
})

/** The directory of `:feature:home`'s common sources, where `HomeScreen.kt` lives. */
private const val HOME_SOURCES = "feature/home/src/commonMain/kotlin/com/example/kmp/feature/home"

/**
 * The violation left in the project on purpose, as the demo of `baseline()`, and
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
 * katachi reports it as a Warning, which never fails `assert()` — see `roles/ComponentPreviewRole.kt`,
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

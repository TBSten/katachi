package com.example.kmp

import com.example.kmp.app.appGroup
import com.example.kmp.data.dataGroup
import com.example.kmp.feature.featureComponent
import com.example.kmp.feature.featureGroup
import com.example.kmp.feature.route
import com.example.kmp.feature.screenPreview
import com.example.kmp.feature.viewModel
import com.example.kmp.gradle.gradleGroup
import com.example.kmp.testing.testingGroup
import com.example.kmp.tool.toolGroup
import com.example.kmp.ui.uiGroup
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
            listOf("[UnexpectedDirectory] data/src")
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

private const val HOME_SOURCES = "feature/home/src/commonMain/kotlin/com/example/kmp/feature/home"

private const val HELD_BACK_BY_BASELINE: String =
    "[UnexpectedDirectory] data/src/androidMain/kotlin/com/example/kmp/data/user"

@OptIn(InternalKatachiApi::class)
private fun List<Violation>.labels(): List<String> = map { "[${it.label}] ${it.path}" }

package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.FeatureModule
import com.example.sample.groups.eachFeatureModule
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/**
 * The role of a feature module's own unit tests, which stand the ViewModel on `:testing`'s fakes.
 *
 * Named module by module through [FeatureModule], so that the template can write into one of
 * them: `HomeViewModelTest.kt` fits only `:feature:home`'s pattern.
 *
 * Not `":feature:${capture("feature")}".module { }` like FeatureComponent, because what the
 * test arranges differs per screen: the template is a `when` over [FeatureModule], which a new
 * module cannot slip past, while a captured value is only a string.
 */
fun DeclarationContainerScope.featureTest() = "FeatureTest" {
    title = "Screen test"
    summary = "A test in src/test of :feature:<name> that runs the ViewModel with fakes from :testing"
    description = """
        Unit tests of a feature module. For `:feature:home`, put `HomeViewModelTest.kt` in the
        same package under `src/test`; the file name starts with the module name (`Home`) and
        ends with `Test`.

        A ViewModel's Repository is a constructor argument, so a test passes a fake such as
        `FakeUserRepository` from `:testing` and checks the `UiState` that flows out. It touches
        neither Compose nor a real Android device, so it runs on the JVM alone. This is why the
        feature module's `build.gradle.kts` has `testImplementation(project(":testing"))`.

        A template can generate a test that builds that feature's ViewModel with fakes. The id
        is split per screen (`feature.FeatureTest.home` / `.settings`), and `--arg
        template=feature.FeatureTest.home --arg name=ViewModel` produces `HomeViewModelTest.kt`.
        The content differs per screen (the fake and its arguments, the expected `Content`), so
        it is chosen by id rather than by passing values.
    """.trimIndent()
    forbiddenContents = """
        - A real Repository (`*RepositoryImpl`). Swap in the fakes from `:testing` instead
        - Tests of screen rendering. If Compose tests are needed, split the role
    """.trimIndent()
    example("HomeViewModelTest", "A test that runs HomeViewModel with fakes")
    example("SettingsViewModelTest", "A test that runs SettingsViewModel with fakes")
    // What the test arranges differs per screen, so the content is a `when` over the module
    // ([featureTestContent]), which a new FeatureModule entry fails to compile until it is
    // taught here. One `.template` per module, attached inside [eachFeatureModule] with an id
    // made from the module (`home` / `settings`).
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=feature.FeatureTest.home --arg name=ViewModel
    layout {
        eachFeatureModule { feature ->
            testSourceSet / kotlin / modulePackage / "${feature.name}${capture("name")}Test".ktFile()
                .template(id = feature.name.lowercase()) {
                    featureTestContent(feature, captureValue("name"))
                }
        }
    }
}

/** The body [featureTest]'s `.template { }` writes, for one [FeatureModule]. */
private fun featureTestContent(feature: FeatureModule, name: String): String {
    val testClass = "${feature.name}${name}Test"
    val viewModel = "${feature.name}ViewModel"
    val (fake, arrange, expected) = when (feature) {
        FeatureModule.Home -> Triple(
            "FakeUserRepository",
            "userRepository = FakeUserRepository(userName = \"katachi\")",
            "HomeContent(userName = \"katachi\", visitCount = 1)",
        )
        FeatureModule.Settings -> Triple(
            "FakeSettingsRepository",
            "settingsRepository = FakeSettingsRepository(darkThemeEnabled = true)",
            "SettingsContent(darkThemeEnabled = true)",
        )
    }

    return """
        package com.example.sample.feature.${feature.name.lowercase()}

        import com.example.sample.testing.$fake
        import com.example.sample.ui.core.UiState
        import org.junit.Assert.assertEquals
        import org.junit.Test

        class $testClass {
            @Test
                fun `exposes the value read from the repository as Content`() {
                val viewModel = $viewModel($arrange)

                assertEquals(UiState.Content($expected), viewModel.uiState.value)
            }
        }
    """.trimIndent() + "\n"
}

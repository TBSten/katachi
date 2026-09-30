@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template

/**
 * The role of a part one screen draws and no other: the feature-local counterpart of Component.
 *
 * This sample's example of generating into a wildcard module. `:feature:*` is named `feature`,
 * so `--arg feature=home` picks `:feature:home` as the one place the template writes to, and
 * the template reads the same value back with `captureValue("feature")` for the package and the
 * head of the file name. `:feature:home` holds one of them, `HomeUserCard.kt`, which the home
 * screen draws; `checkSampleKmp` generates another and checks it before deleting it again.
 */
fun DeclarationContainerScope.featureComponent() = "FeatureComponent" {
    title = "Screen component"
    summary = "A @Composable used by only one screen. Placed as <Name>*.kt in the component package of the commonMain of :feature:<name>"
    description = """
        A part dedicated to one screen, split out when the Screen grows. For `:feature:home`, put
        it in `commonMain` like `component/HomeUserCard.kt`, and start the file name with the
        module name (`Home`). A feature may have any number of them.

        The difference from the shared component (ui/Component) is the number of screens that
        use it. When a second screen wants it, move it to the component package of `:ui`.
        Features do not depend on each other, so left here it cannot be called from other screens.

        It can be generated from a template. The `*` of `:feature:*` is named `feature`, so
        `--arg feature=home --arg name=UserCard` puts `HomeUserCard.kt` into `:feature:home`.
        Only the name of an existing feature module can be passed as `feature`.
    """.trimIndent()
    allowedContents = """
        - An `internal` `@Composable` that receives values and callbacks
        - A description of the placement that combines Component / Theme from `:ui`
    """.trimIndent()
    forbiddenContents = """
        - Dependencies on a ViewModel. State comes from the Screen as values
        - `public` declarations. Only the Route is visible from outside the feature
    """.trimIndent()
    example("HomeUserCard", "Shows one user, used only on the home screen")
    // The module is chosen by `--arg feature=...`, the name the layout gave `:feature:*`.
    // A module that does not exist is refused rather than created.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=feature.FeatureComponent --arg feature=home --arg name=UserCard
    layout {
        ":feature:${capture("feature")}".module {
            "commonMain".sourceSet / kotlin / modulePackage / "component" /
                "${wildcard("feature").pascalCase}${capture("name")}".ktFile()
                    .template {
                        val feature = captureValue("feature")
                        val component = "${feature.pascalCase}${captureValue("name")}"
                        """
                            package com.example.kmp.feature.$feature.component

                            import androidx.compose.material3.Text
                            import androidx.compose.runtime.Composable
                            import androidx.compose.ui.Modifier

                            /** Part of the $feature screen that no other screen uses. */
                            @Composable
                            internal fun $component(
                                name: String,
                                modifier: Modifier = Modifier,
                            ) {
                                Text(text = name, modifier = modifier)
                            }
                        """.trimIndent() + "\n"
                    }
        }
    }
}

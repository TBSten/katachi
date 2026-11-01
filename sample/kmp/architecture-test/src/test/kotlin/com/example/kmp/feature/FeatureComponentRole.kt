@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.feature

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.testing.test
import com.example.kmp.ui.component
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.declarations.ktFileContent

fun DeclarationContainerScope.featureComponent() = "FeatureComponent".role {
    title = "Screen component"
    summary = "A @Composable used by only one screen. Placed as <Name>*.kt in the component package of the commonMain of :feature:<name>"
    description = """
        A part dedicated to one screen, split out when the Screen grows. For `:feature:home`, put
        it in `commonMain` like `component/HomeUserCard.kt`, and start the file name with the
        module name (`Home`). A feature may have any number of them.

        The difference from the shared component (ui/Component) is the number of screens that
        use it. When a second screen wants it, move it to the component package of `:ui`.
        Features do not depend on each other, so left here it cannot be called from other screens.

        It can be generated from a template. The module directory and its package directory are
        both named `feature`, so `--arg feature=home --arg name=HomeUserCard` puts
        `HomeUserCard.kt` into `:feature:home`, and the check holds the two directories to the
        same name. `name` is the whole file name, including the module's name; the check does
        not tie the file name to the module.
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
    // The module and its package are chosen by one `--arg feature=...`, the name the layout gave
    // both directories. A `feature` that is not a module is not refused; the directory is
    // created as given.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=feature.FeatureComponent --arg feature=home --arg name=HomeUserCard
    layout {
        "feature" / capture("feature") / "commonMain".sourceSet / kotlin / "com/example/kmp/feature" /
            capture("feature") / "component" /
            capture("name").ktFile()
                    .template {
                        val feature = captureValue("feature")
                        val component = captureValue("name")
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
    // The package is the captured `feature` of the file being checked, and the part is
    // `internal`: only the Route is visible from outside a feature.
    ktFileContent {
        `package`("com.example.kmp.feature.${captureValue("feature")}.component")

        `import`("androidx.compose.**")
        `import`("com.example.kmp.ui.**")

        required(annotated("Composable"), internal, `fun`, fileName)
    }
}

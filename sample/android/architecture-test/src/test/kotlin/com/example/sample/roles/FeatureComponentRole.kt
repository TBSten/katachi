package com.example.sample.roles

import com.example.sample.allowedContents
import com.example.sample.forbiddenContents
import com.example.sample.groups.featureSources
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template

/**
 * The role of a widget one screen uses and no other: the feature-local counterpart of Component.
 *
 * Its layout is `":feature:*"` with the wildcard named `feature`, and a file name has to start
 * with that module's name. The name is what lets the template below pick its module:
 * `--arg feature=home` binds the `*` to `:feature:home`, so katachi has one place to put the
 * file, and the template reads the same value back with `captureValue("feature")` to build the
 * package and the file name from it.
 */
fun DeclarationContainerScope.featureComponent() = "FeatureComponent" {
    title = "Screen part"
    summary = "A @Composable used by only one screen. Placed as <Name>*.kt in the component package of :feature:<name>"
    description = """
        A part dedicated to a screen, split out when the Screen grows large. For `:feature:home`
        it goes in the `component` package of the feature module, like
        `component/HomeUserCard.kt`, and the file name starts with the module name (`Home`). A
        feature may have any number of them.

        The difference from the shared component role is the number of screens that use it. Once
        a second feature wants to call it, move it to the component package of `:ui` and name it
        `App*`. Features do not depend on each other, so a part left here cannot be called from
        another feature.

        Can be generated from a template. The `*` of `:feature:*` is named `feature`, so `--arg
        template=feature.FeatureComponent --arg feature=home --arg name=UserCard` puts
        `HomeUserCard.kt` into `:feature:home`. `feature` accepts only the name of a feature
        module that exists.
    """.trimIndent()
    allowedContents = """
        - An `internal` `@Composable` that takes values and callbacks
        - The `@Preview` of that part (wrapped in `PreviewRoot { }`; see the Preview role)
    """.trimIndent()
    forbiddenContents = """
        - Dependencies on a ViewModel. State comes from the Screen as values
        - Types of other features, and `public` declarations. Only the Route is visible from outside
    """.trimIndent()
    example("HomeUserCard", "A card used only by the home screen (example)")
    // The module is chosen by `--arg feature=...`, the name the layout gave `:feature:*`.
    // A module that does not exist is refused rather than created.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=feature.FeatureComponent --arg feature=home --arg name=UserCard
    layout {
        ":feature:${capture("feature")}".module {
            featureSources() / "component" / "${wildcard("feature").pascalCase}${capture("name")}".ktFile()
                .template {
                    val feature = captureValue("feature")
                    val name = captureValue("name")
                    val withPreview by booleanParameter(default = true)
                    val component = "${feature.pascalCase}$name"
                    val previewImports = if (withPreview) {
                        """
                            import androidx.compose.ui.tooling.preview.Preview
                            import com.example.sample.ui.preview.PreviewRoot

                        """.trimIndent()
                    } else {
                        ""
                    }
                    val preview = if (withPreview) {
                        "\n\n" + """
                            @Preview(showBackground = true)
                            @Composable
                            private fun ${component}Preview() = PreviewRoot {
                                $component(title = "$name")
                            }
                        """.trimIndent()
                    } else {
                        ""
                    }

                    """
                        |package com.example.sample.feature.$feature.component
                        |
                        |import androidx.compose.foundation.layout.Arrangement
                        |import androidx.compose.foundation.layout.Column
                        |import androidx.compose.material3.MaterialTheme
                        |import androidx.compose.material3.Text
                        |import androidx.compose.runtime.Composable
                        |import androidx.compose.ui.Modifier
                        |import androidx.compose.ui.unit.dp
                        |$previewImports
                        |/** Part of the $feature screen that no other screen uses. */
                        |@Composable
                        |internal fun $component(
                        |    title: String,
                        |    modifier: Modifier = Modifier,
                        |) {
                        |    Column(
                        |        modifier = modifier,
                        |        verticalArrangement = Arrangement.spacedBy(8.dp),
                        |    ) {
                        |        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        |    }
                        |}$preview
                    """.trimMargin()
                }
        }
    }
}

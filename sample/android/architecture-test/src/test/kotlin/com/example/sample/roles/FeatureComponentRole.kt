@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import com.example.sample.allowedContents
import com.example.sample.forbiddenContents
import com.example.sample.groups.featureSources
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/**
 * The role of a widget one screen uses and no other: the feature-local counterpart of Component.
 *
 * Its layout is the directory `"feature" / "*"` with the module directory named `feature` and the
 * package directory below it named `featurePackage`. The names are what let the template below
 * pick its place: `--arg feature=home --arg featurePackage=home` binds both, so katachi has one
 * place to put the file, and the template reads the same values back with `captureValue(...)`
 * to build the package from them. They are two names because a capture name may be used only
 * once in a path. The price of the loose directory is that a `feature` that is not a module is
 * created as it is, and the file name is not tied to the module name.

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

        Can be generated from a template. The module directory is named `feature` and its
        package directory `featurePackage`, so `--arg template=feature.FeatureComponent --arg
        feature=home --arg featurePackage=home --arg name=HomeUserCard` puts
        `HomeUserCard.kt` into `:feature:home`. `name` is the whole file name, including the
        module's name; the check does not tie the two together.
    """.trimIndent()
    allowedContents = """
        - An `internal` `@Composable` that takes values and callbacks
        - The `@Preview` of that part (a `private` function at the end of the same file, wrapped in `PreviewRoot { }`)
    """.trimIndent()
    forbiddenContents = """
        - Dependencies on a ViewModel. State comes from the Screen as values
        - Types of other features, and `public` declarations. Only the Route is visible from outside
    """.trimIndent()
    example("HomeUserCard", "A card used only by the home screen (example)")
    // The module is chosen by `--arg feature=...` and its package by `--arg featurePackage=...`,
    // the names the layout gave the two directories. A `feature` that is not a module is not
    // refused; the directory is created as given.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=feature.FeatureComponent --arg feature=home --arg featurePackage=home \
    //       --arg name=HomeUserCard
    layout {
        featureSources(module = capture("feature"), packageName = capture("featurePackage")) /
            "component" / capture("name").ktFile()
                .template {
                    val featurePackage = captureValue("featurePackage")
                    val component = captureValue("name")
                    val withPreview by booleanParameter(default = true)
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
                                $component(title = "$component")
                            }
                        """.trimIndent()
                    } else {
                        ""
                    }

                    """
                        |package com.example.sample.feature.$featurePackage.component
                        |
                        |import androidx.compose.foundation.layout.Arrangement
                        |import androidx.compose.foundation.layout.Column
                        |import androidx.compose.material3.MaterialTheme
                        |import androidx.compose.material3.Text
                        |import androidx.compose.runtime.Composable
                        |import androidx.compose.ui.Modifier
                        |import androidx.compose.ui.unit.dp
                        |$previewImports
                        |/** Part of the $featurePackage screen that no other screen uses. */
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

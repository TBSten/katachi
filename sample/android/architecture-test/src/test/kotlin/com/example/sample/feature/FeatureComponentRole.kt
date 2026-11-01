@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.feature

import com.example.sample.allowedContents
import com.example.sample.feature.featureSources
import com.example.sample.forbiddenContents
import com.example.sample.ui.component
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.declarations.ktFileContent

fun DeclarationContainerScope.featureComponent() = "FeatureComponent".role {
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

        Can be generated from a template. The module directory and its package directory are
        both named `feature`, so `--arg template=feature.FeatureComponent --arg feature=home
        --arg name=HomeUserCard` puts `HomeUserCard.kt` into `:feature:home`, and the check
        holds the two directories to the same name. `name` is the whole file name, including
        the module's name; the check does not tie the file name to the module.
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
    // The module and its package are chosen by one `--arg feature=...`, the name the layout gave
    // both directories. A `feature` that is not a module is not refused; the directory is
    // created as given.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=feature.FeatureComponent --arg feature=home --arg name=HomeUserCard
    layout {
        featureSources(capture("feature")) / "component" / capture("name").ktFile()
                .template {
                    val feature = captureValue("feature")
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
    // The package is the captured `feature` of the file being checked. A part may import its own
    // feature, but not its ViewModel (state comes from the Screen as values) and no other
    // feature.
    ktFileContent {
        val feature = "com.example.sample.feature.${captureValue("feature")}"
        `package`("$feature.component")

        `import`("androidx.compose.**")
        `import`("com.example.sample.ui.**")
        `import`("$feature.**")
        not.`import`("$feature.*ViewModel")

        required(annotated("Composable"), internal, `fun`, fileName)
        allow(annotated("Preview"), private, `fun`, "*Preview")
    }
}

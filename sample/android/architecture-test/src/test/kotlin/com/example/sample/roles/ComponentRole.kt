package com.example.sample.roles

import com.example.sample.allowedContents
import com.example.sample.forbiddenContents
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role of a widget shared across features, rather than owned by one of them. */
fun DeclarationContainerScope.component() = "Component" {
    title = "Shared component"
    summary = "A part in the component package of the :ui module, used across features"
    description = """
        A Compose part called from several screens. Like `AppButton`, it is a thin layer over
        Material3, so that a change of shape or emphasis reaches every screen at once.

        How the look is chosen is expressed with types inside this package. `AppButton` takes an
        `emphasis: AppButtonEmphasis` and handles `Filled` versus `Outlined` internally. The aim
        of this role is that callers need not pick between Material3's `Button` and
        `OutlinedButton` themselves, so a part, the enum for it and its `@Preview` go in the
        same file.

        File names are not restricted to `*.kt`, because parts are expected to multiply.

        Can be generated from a template. The whole file name is `capture("name")`, so `--arg
        template=Component --arg name=AppLabel` produces `AppLabel.kt` (starting with `App` is a
        convention of this role, not something the layout enforces).
    """.trimIndent()
    allowedContents = """
        Only what two or more features use, or are decided to use, belongs here. A part used by
        a single screen goes in that feature module.
    """.trimIndent()
    forbiddenContents = """
        - State held in the part. Pass values and callbacks (`text`, `onClick`) instead of
          keeping them with `remember`
        - Dependencies on `:data` or `:feature:*`. `:ui` knows neither the layers below nor the
          features beside it
        - Hard-coded colors or typography. Read them from `MaterialTheme` (see the Theme role)
    """.trimIndent()
    example("AppButton", "The app-wide button")
    // The whole file name is one capture: `component/` comes from the layout, and the name
    // passed to `--arg name=` becomes both the file name and the composable's own name. The
    // preview is wrapped in `PreviewRoot { }` from the start, as the Preview role asks.
    //   ./gradlew :architecture-test:katachiTemplate --arg template=Component --arg name=AppLabel
    layout {
        ":ui".module {
            mainSourceSet / kotlin / modulePackage / "component" / capture("name").ktFile()
                .template {
                    val name = captureValue("name")
                    val previewText by stringParameter(default = name)

                    """
                        package com.example.sample.ui.component

                        import androidx.compose.material3.Text
                        import androidx.compose.runtime.Composable
                        import androidx.compose.ui.Modifier
                        import androidx.compose.ui.tooling.preview.Preview
                        import com.example.sample.ui.preview.PreviewRoot

                        /** Shared ${name.lowercase()}, called by feature modules instead of Material's own. */
                        @Composable
                        fun $name(
                            text: String,
                            modifier: Modifier = Modifier,
                        ) {
                            Text(text = text, modifier = modifier)
                        }

                        @Preview(showBackground = true)
                        @Composable
                        private fun ${name}Preview() = PreviewRoot {
                            $name(text = "$previewText")
                        }
                    """.trimIndent() + "\n"
                }
        }
    }
}

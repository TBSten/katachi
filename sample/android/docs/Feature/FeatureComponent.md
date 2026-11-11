[katachi-sample-android](../README.md) / [Structure of each screen](README.md)

# Screen part

A @Composable used by only one screen. Placed as <Name>*.kt in the component package of :feature:<name>

A part dedicated to a screen, split out when the Screen grows large. For `:feature:home`
it goes in the `component` package of the feature module, like
`component/HomeUserCard.kt`, and the file name starts with the module name (`Home`). A
feature may have any number of them.

The difference from the shared component role is the number of screens that use it. Once
a second feature wants to call it, move it to the component package of `:ui` and name it
`App*`. Features do not depend on each other, so a part left here cannot be called from
another feature.

Can be generated from a template. The module directory and its package directory are
both named `feature`, so `--arg template=Feature.FeatureComponent --arg feature=home
--arg name=HomeUserCard` puts `HomeUserCard.kt` into `:feature:home`, and the check
holds the two directories to the same name. `name` is the whole file name, including
the module's name; the check does not tie the file name to the module.

## Placement

| Path | When to use |
|---|---|
| `feature/*/src/main/kotlin/com/example/sample/feature/*/component/*.kt` |  |

## Constraints

- [ktFileContent](#kotlin-file-content)

## Kotlin file content

Example: `HomeUserCard.kt`

| Package |
|---|
| `com.example.sample.feature.<feature>.component` |

| Import | |
|---|---|
| `androidx.compose.**` | optional |
| `com.example.sample.ui.**` | optional |
| `com.example.sample.feature.<feature>.**` | optional |
| `com.example.sample.feature.<feature>.*ViewModel` | not allowed |

| Declaration | |
|---|---|
| `@Composable internal fun HomeUserCard(...)` | required |
| `@Preview private fun *Preview(...)` | optional |

## Examples

- `HomeUserCard` ... A card used only by the home screen (example)

## Allowed contents

- An `internal` `@Composable` that takes values and callbacks
- The `@Preview` of that part (a `private` function at the end of the same file, wrapped in `PreviewRoot { }`)

## Forbidden contents

- Dependencies on a ViewModel. State comes from the Screen as values
- Types of other features, and `public` declarations. Only the Route is visible from outside

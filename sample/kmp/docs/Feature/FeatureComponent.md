[katachi-sample-kmp](../README.md) / [Feature](README.md)

# Screen component

A @Composable used by only one screen. Placed as <Name>*.kt in the component package of the commonMain of :feature:<name>

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

## Placement

| Path | When to use |
|---|---|
| `feature/*/src/commonMain/kotlin/com/example/kmp/feature/*/component/*.kt` |  |

## Constraints

- [ktFileContent](#kotlin-file-content)

## Kotlin file content

Example: `HomeUserCard.kt`

| Package |
|---|
| `com.example.kmp.feature.<feature>.component` |

| Import | |
|---|---|
| `androidx.compose.**` | optional |
| `com.example.kmp.ui.**` | optional |

| Declaration | |
|---|---|
| `@Composable internal fun HomeUserCard(...)` | required |

## Examples

- `HomeUserCard` ... Shows one user, used only on the home screen

## Allowed contents

- An `internal` `@Composable` that receives values and callbacks
- A description of the placement that combines Component / Theme from `:ui`

## Forbidden contents

- Dependencies on a ViewModel. State comes from the Screen as values
- `public` declarations. Only the Route is visible from outside the feature

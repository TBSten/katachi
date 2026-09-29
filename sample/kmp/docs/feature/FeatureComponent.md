[katachi-sample-kmp](../README.md) / [Feature](README.md)

# Screen component

A @Composable used by only one screen. Placed as <Name>*.kt in the component package of the commonMain of :feature:<name>

A part dedicated to one screen, split out when the Screen grows. For `:feature:home`, put
it in `commonMain` like `component/HomeUserCard.kt`, and start the file name with the
module name (`Home`). A feature may have any number of them.

The difference from the shared component (ui/Component) is the number of screens that
use it. When a second screen wants it, move it to the component package of `:ui`.
Features do not depend on each other, so left here it cannot be called from other screens.

It can be generated from a template. The `*` of `:feature:*` is named `feature`, so
`--arg feature=home --arg name=UserCard` puts `HomeUserCard.kt` into `:feature:home`.
Only the name of an existing feature module can be passed as `feature`.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:<feature>` | `src/commonMain/kotlin/**/component/<feature>*.kt` |  |

## Examples

- `HomeUserCard` ... Shows one user, used only on the home screen

## Allowed contents

- An `internal` `@Composable` that receives values and callbacks
- A description of the placement that combines Component / Theme from `:ui`

## Forbidden contents

- Dependencies on a ViewModel. State comes from the Screen as values
- `public` declarations. Only the Route is visible from outside the feature

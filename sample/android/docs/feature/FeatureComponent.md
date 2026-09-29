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

Can be generated from a template. The `*` of `:feature:*` is named `feature`, so `--arg
template=feature.FeatureComponent --arg feature=home --arg name=UserCard` puts
`HomeUserCard.kt` into `:feature:home`. `feature` accepts only the name of a feature
module that exists.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:<feature>` | `src/main/kotlin/**/component/<feature>*.kt` |  |

## Examples

- `HomeUserCard` ... A card used only by the home screen (example)

## Allowed contents

- An `internal` `@Composable` that takes values and callbacks
- The `@Preview` of that part (wrapped in `PreviewRoot { }`; see the Preview role)

## Forbidden contents

- Dependencies on a ViewModel. State comes from the Screen as values
- Types of other features, and `public` declarations. Only the Route is visible from outside

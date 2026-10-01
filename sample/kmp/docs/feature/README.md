[katachi-sample-kmp](../README.md)

# Feature

One module per screen. :feature:<name> always holds a Screen / ViewModel / Route

A layer of modules that grows by one per screen. There are `:feature:home` and
`:feature:settings`; adding a screen means adding a module.

Screen / ViewModel / Route are collected here because each of them exists for a single
screen. All three are written as globs under the directory `feature/*`, and by convention the
file is named after the module: `:feature:home` has `HomeScreen.kt` / `HomeViewModel.kt` /
`HomeRoute.kt`. The check looks at the place and the suffix, not at the pairing.

It is separate from the ui group because the two grow differently. A feature is a place
where modules are expected to multiply, and one declaration of the directory `feature/*`
covers any number of them. Adding something to `:ui` or `:navigation`, on the
other hand, is a design decision. Merging them into one group would erase that difference
from the generated documentation.

A screen's `@Preview` functions (ScreenPreview) sit in the same module as the screen they
draw.

Only the screen parts (FeatureComponent) grow in number inside a single feature, and they
can be generated from a template. The module directory is named `feature` and its
package directory `featurePackage`, so `--arg feature=home --arg featurePackage=home`
picks the module to generate into.

Every role in this group is `commonMain`. There is no `androidMain` / `iosMain` around the
screens. Platform differences stay inside ExpectDeclaration and ActualImplementation of the data group.

| Role | Summary |
|---|---|
| [Screen](./Screen.md) | The @Composable of one screen. Subscribes to the ViewModel's StateFlow and draws by combining Components |
| [ViewModel](./ViewModel.md) | An androidx.lifecycle.ViewModel that holds screen state. Converts values fetched from the Repository into a UiState and exposes it as a StateFlow |
| [Route](./Route.md) | Ties a screen to a navigation Destination and also takes on creating the ViewModel |
| [Screen component](./FeatureComponent.md) | A @Composable used by only one screen. Placed as <Name>*.kt in the component package of the commonMain of :feature:<name> |
| [Screen preview](./ScreenPreview.md) | A private @Composable annotated with @Preview for a screen. Placed in Home*Preview.kt in the feature module that holds the screen, with the content wrapped in PreviewRoot |

## Placement in this group

```
feature/*/src/commonMain/kotlin/com/example/kmp/feature/*/
  *Screen.kt      Screen
  *ViewModel.kt   ViewModel
  *Route.kt       Route
  component/*.kt  Screen component
  *Preview.kt     Screen preview
```

## Forbidden contents

- Parts used by several screens. Those are Component in `:ui`
- Fetching data. It lives in `:data`, and a feature reads it through an interface
- Dependencies on other features. Screens do not connect directly; they go through
  `Destination` in `:navigation`

[katachi-sample-android](../README.md)

# UI (shared layer)

UI shared across features: the four packages of :ui, and :navigation

The UI used by every screen, and its foundation. `:ui` is one module split into
packages: `component` (shared parts), `theme` (colors and typography), `core` (the
vocabulary of the UI layer) and `preview` (the base for previews). `:navigation` is a
separate module that holds only the entry point for screen transitions.

`:ui` knows neither the layers below nor the features beside it, so no dependency on
`:data` or `:feature:*` comes in.

The feature-side Screen / ViewModel / Route are not here because they grow differently.
That is a place that grows just by adding a module, whereas here every addition is a
decision about whether every screen will use it, so the groups are separate.

`:navigation` is a separate module from `:ui` because of the direction of dependencies.
Features depend only on the `AppNavigator` interface and never touch
`NavHostController`. It also keeps code that just wants screen parts from pulling in the
navigation dependency. Building the graph is done by `:app`.

| Role | Summary |
|---|---|
| [Shared component](./Component.md) | A part in the component package of the :ui module, used across features |
| [Theme](./Theme.md) | Colors, typography and shapes, kept in the theme package of the :ui module |
| [UI foundation](./UiCore.md) | Types that form the foundation of the UI layer, kept in the core package of the :ui module |
| [Preview](./Preview.md) | A private @Composable annotated with @Preview. Placed in the same file as its target Composable, with the body wrapped in PreviewRoot |
| [Preview base](./PreviewRoot.md) | The base every @Preview wraps its body in, kept in the preview package of :ui. Decides the theme and background in one place, and takes darkTheme to show light and dark |
| [Screen navigation](./Navigation.md) | Movement between screens, kept in :navigation |

## Placement in this group

```
:ui
  src/main/kotlin/**/
    component/*.kt          Shared component
    theme/AppTheme.kt       Theme
    core/*.kt               UI foundation
    preview/PreviewRoot.kt  Preview base

:navigation
  src/main/kotlin/**/*.kt   Screen navigation
```

## Allowed contents

Only what two or more features use, or are decided to use, belongs here.
Anything used by a single screen goes in that feature module.

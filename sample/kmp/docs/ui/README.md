[katachi-sample-kmp](../README.md)

# UI

:ui and :navigation. Shared UI that belongs to no screen, and the definition of destinations

Two shared modules that screens draw from. Whatever belongs to no particular screen
gathers here.

`:ui` is one module split into packages: `component` (parts), `theme` (appearance),
`core` (the types for screen state) and `preview` (the base for previews), each one role.
The previews of components are here too, next to the components; the previews of screens
are in the feature group.
They used to be separate modules such as `:ui:component`, but were merged into packages.
katachi needs to be able to say "this role is this package of this module", and it is
also the shape more often seen in real projects.

`:navigation` sits in the same group but does not depend on Compose. It holds only the
list of destinations and the current location; how to show them is the job of `AppRoot`
in `:app:android`. It is on the side that screens draw from, like `:ui`, so it is placed
in this group.

Every layout starts from a module path, and the package below it is derived from
`modulePackage`. Instead of copying directory names, the policy of the whole
sample is to write exactly what the build says.

| Role | Summary |
|---|---|
| [Shared component](./Component.md) | The component package of the :ui module. @Composable parts used by several screens |
| [Theme](./Theme.md) | The theme package of the :ui module. The MaterialTheme setup and the design tokens for color and spacing |
| [UI core](./UiCore.md) | The core package of the :ui module. The screen-independent foundation of the UI, such as UiState |
| [Component preview](./ComponentPreview.md) | A private @Composable annotated with @Preview for a component. Placed in <Target>Preview.kt in the component package of :ui, with the content wrapped in PreviewRoot |
| [Preview root](./PreviewRoot.md) | The preview package of the :ui module. Wraps the content of a @Preview in AppTheme and Surface |
| [Navigation](./Navigation.md) | The definition of destinations, and the Navigator that holds the current location |

## Placement in this group

```
:ui
  src/commonMain/kotlin/**/
    component/
      *.kt                       Shared component
      *Preview.kt                Component preview
    theme/*.kt                   Theme
    core/*.kt                    UI core
    preview/PreviewRoot.kt       Preview root

:navigation
  src/commonMain/kotlin/**/*.kt  Navigation
```

## Forbidden contents

- Per-screen Screen / ViewModel / Route. Those belong to the feature group
- Dependencies on feature modules. Dependencies always point from a feature to `:ui` /
  `:navigation`; a single reference the other way makes the shared modules grow with every
  screen added

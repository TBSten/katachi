[katachi-sample-kmp](../README.md) / [Feature](README.md)

# Screen preview

A private @Composable annotated with @Preview for a screen. Placed in Home*Preview.kt in the feature module that holds the screen, with the content wrapped in PreviewRoot

A `private @Composable` annotated with `@Preview` that draws a screen. It is written in
`<Target>Preview.kt` in the same package as the screen, inside the feature module that
holds the screen. Keeping it in the same file as the target is also possible, but this
sample splits the files (the co-located form is in sample/android).

The file is written as a `*Preview.kt` glob in any feature module, and named after the
screen it renders by convention (`HomeLoadedPreview.kt` for the home screen).

This `@Preview` is the one from Compose Multiplatform's
`org.jetbrains.compose.ui:ui-tooling-preview`. Its fully qualified annotation name is
exactly the same as that of the Android-only `androidx.compose.ui:ui-tooling-preview`,
so if IDE completion adds the latter, the iOS target can no longer be resolved.
Previews can be written in `commonMain` because the former is used.

## Placement

| Path | When to use |
|---|---|
| `feature/*/src/commonMain/kotlin/com/example/kmp/feature/*/*Preview.kt` |  |

## Examples

- `HomeLoadedPreview` ... The home screen, loaded

## Allowed contents

- Previews of stateless Composables that take state as arguments. It calls
  `HomeContent`, not `HomeScreen`, so it can be drawn without building a ViewModel
- Several per target, one per state. Loading, loaded and failed can be viewed side by side

## Forbidden contents

- `public` previews. Nothing else calls them, so make them `private`
- Writing `AppTheme { }` directly in a preview. Wrapping is the job of `PreviewRoot`
- Anything that touches a real Repository or the network. Write values as literals
- Previews of components. They belong to the ComponentPreview role in `:ui`

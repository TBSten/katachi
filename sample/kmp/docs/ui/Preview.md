[katachi-sample-kmp](../README.md) / [UI](README.md)

# Preview

A private @Composable annotated with @Preview. Placed in <Target>Preview.kt in the same package as the target Composable, with the content wrapped in PreviewRoot

A `private @Composable` annotated with `@Preview`. It is written in `<Target>Preview.kt`
in the same package as what it draws. Keeping it in the same file as the target is also
possible, but this sample splits the files (the co-located form is in sample/android).

There are two places. A screen's preview goes in the feature module that holds the
screen, and a component's preview goes in `:ui` because it belongs to no screen. Both
follow from the same rule, "next to what it draws".

This `@Preview` is the one from Compose Multiplatform's
`org.jetbrains.compose.ui:ui-tooling-preview`. Its fully qualified annotation name is
exactly the same as that of the Android-only `androidx.compose.ui:ui-tooling-preview`,
so if IDE completion adds the latter, the iOS target can no longer be resolved.
Previews can be written in `commonMain` because the former is used.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:<feature>` | `src/commonMain/kotlin/**/<feature>*Preview.kt` | A screen's preview. Placed in the feature module that holds the screen |
| `:ui` | `src/commonMain/kotlin/**/component/*Preview.kt` | A component's preview. Placed in :ui because it belongs to no screen |

## Examples

- `PrimaryButtonPreview` ... The preview of PrimaryButton
- `HomeLoadedPreview` ... The home screen, loaded

## Allowed contents

- Previews of stateless Composables that take state as arguments. It calls
  `HomeContent`, not `HomeScreen`, so it can be drawn without building a ViewModel
- Several per target, one per state. Loading, loaded and failed can be viewed side by side

## Forbidden contents

- `public` previews. Nothing else calls them, so make them `private`
- Writing `AppTheme { }` directly in a preview. Wrapping is the job of `PreviewRoot`
- Anything that touches a real Repository or the network. Write values as literals

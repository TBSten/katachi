[katachi-sample-kmp](../README.md) / [UI](README.md)

# Component preview

A private @Composable annotated with @Preview for a component. Placed in <Target>Preview.kt in the component package of :ui, with the content wrapped in PreviewRoot

A `private @Composable` annotated with `@Preview` that draws a shared component. It is
written in `<Target>Preview.kt` in the `component` package of `:ui`, next to the
component, because a component belongs to no screen and so to no feature module.

The Component role claims every `.kt` file of the same package, and this role claims the
ones ending in `Preview.kt`, so the one file matching both belongs to both roles. katachi
reports that overlap as `[AmbiguousLayout]`, which is a Warning and never fails
`assert()`. It is kept rather than designed away: a preview belongs beside the component
it renders, and the report saying so out loud is what this sample wants to show — see
`OmittedRoleSelfCheckSpec`, which pins it.

## Placement

| Path | When to use |
|---|---|
| `ui/src/commonMain/kotlin/com/example/kmp/ui/component/*Preview.kt` |  |

## Examples

- `PrimaryButtonPreview` ... The preview of PrimaryButton

## Allowed contents

- Previews of stateless components that take their state as arguments
- Several per target, one per state

## Forbidden contents

- `public` previews. Nothing else calls them, so make them `private`
- Writing `AppTheme { }` directly in a preview. Wrapping is the job of `PreviewRoot`
- Previews of screens. They belong to the ScreenPreview role in the feature module

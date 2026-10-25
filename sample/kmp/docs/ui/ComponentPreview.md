[katachi-sample-kmp](../README.md) / [UI](README.md)

# Component preview

A private @Composable annotated with @Preview for a component. Placed in <Target>Preview.kt in the component/preview package of :ui, with the content wrapped in PreviewRoot

A `private @Composable` annotated with `@Preview` that draws a shared component. It is
written in `<Target>Preview.kt` in the `preview` package right below the `component`
package of `:ui`, close to the component, because a component belongs to no screen and so
to no feature module.

It has a package of its own so that a file is either a component or a preview of one,
never both. The Component role claims every `.kt` file of `component`; if the previews
sat in the same package, that pattern would claim them too, and one file would belong to
two roles.

## Placement

| Path | When to use |
|---|---|
| `ui/src/commonMain/kotlin/com/example/kmp/ui/component/preview/*Preview.kt` |  |

## Examples

- `PrimaryButtonPreview` ... The preview of PrimaryButton

## Allowed contents

- Previews of stateless components that take their state as arguments
- Several per target, one per state

## Forbidden contents

- `public` previews. Nothing else calls them, so make them `private`
- Writing `AppTheme { }` directly in a preview. Wrapping is the job of `PreviewRoot`
- Previews of screens. They belong to the ScreenPreview role in the feature module

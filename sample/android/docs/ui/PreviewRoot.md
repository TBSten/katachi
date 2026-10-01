[katachi-sample-android](../README.md) / [UI (shared layer)](README.md)

# Preview base

The base every @Preview wraps its body in, kept in the preview package of :ui. Decides the theme and background in one place, and takes darkTheme to show light and dark

Just `PreviewRoot`. It wraps the content in `AppTheme` and lays down a `Surface` to
decide the preview background. It takes `darkTheme` as an argument and passes it
straight to `AppTheme`, so light and dark of the same Composable can be lined up in two
`@Preview`s.

A `@Preview` is not called from inside the app, so nothing above it provides a theme.
Each preview could write `AppTheme { }` itself and it would work, but then whether a
background is laid down and how `darkTheme` is passed would drift from preview to
preview. This role exists to decide "what a preview sits on" in one place.

The `layout` names `PreviewRoot.kt` exactly instead of using a wildcard, and removing it
fails the check with `[MissingFile]`. It is the base every `@Preview` depends on, so it
is made not to disappear silently.

## Placement

| Path | When to use |
|---|---|
| `ui/src/main/kotlin/com/example/sample/ui/preview/PreviewRoot.kt` |  |

## Examples

- `PreviewRoot` ... The base shared by previews

## Forbidden contents

- Anything called from a production screen. The theme of the real screen is given by `MainActivity` with `AppTheme { }`
- Dummy data for previews. Each `@Preview` writes the state it passes on the spot

[katachi-sample-kmp](../README.md) / [UI](README.md)

# Preview root

The preview package of the :ui module. Wraps the content of a @Preview in AppTheme and Surface

A package that holds only a wrapper for previews. `PreviewRoot` wraps the content in
`AppTheme` and `Surface`: `AppTheme` gives the same colors as production and `Surface`
paints their background behind it. Without it, every preview would copy the same two
lines, and they would all drift apart the day the theme gains an argument.

It is a different role from ui/Preview, whose name is similar. This one is the wrapping
side and that one the wrapped side; no `@Preview` is written here. Conversely, nothing
other than previews calls `PreviewRoot`.

It sits in `commonMain`, not `commonTest`, because the `@Preview` that uses it lives in
the `commonMain` of the feature modules. A test source set cannot be referenced from other
modules, so it would not be reachable from there.

This role is one of the few declarations that spell out the file name without a
wildcard, so if `PreviewRoot.kt` is deleted or renamed it is reported as `[MissingFile]`.

## Placement

| Path | When to use |
|---|---|
| `ui/src/commonMain/kotlin/com/example/kmp/ui/preview/PreviewRoot.kt` |  |

## Examples

- `PreviewRoot` ... The wrapper every @Preview uses

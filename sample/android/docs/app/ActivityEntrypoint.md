[katachi-sample-android](../README.md) / [Entrypoint layer](README.md)

# Activity entrypoint

The single Activity Android launches, kept in :app

The `Activity` Android touches first at launch. `:app` has one `MainActivity`, named
exactly. If it is gone, the check fails with `[MissingFile]`, so that an app that cannot
launch never passes.

`MainActivity` contains only `setContent { AppTheme { AppNavHost() } }`. `AppNavHost` is
a private `@Composable` in the same file that lines up the Routes exposed by
`:feature:*` and builds the navigation graph. `:app` is the only module allowed to know
every feature, and that knowledge stays inside this file.

This role writes its package directly as `com/example/sample` instead of using
`modulePackage`. `:app` is the application itself and has no mapping to the module path
like `:ui` to `com.example.sample.ui`.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:app` | `src/main/kotlin/com/example/sample/MainActivity.kt` |  |

## Examples

- `MainActivity` ... The Activity shown at launch

## Forbidden contents

Screen contents. `:app` only connects features; the UI lives in `:ui` and `:feature:*`.
If Composables start piling up here, they should move into a feature module.

[katachi-sample-kmp](../README.md) / [UI](README.md)

# Theme

The theme package of the :ui module. The MaterialTheme setup and the design tokens for color and spacing

A package that gathers the raw material of the app's look in one place. `AppTheme` is the
app's only theme, and the entry point (`AppRoot`) and `PreviewRoot`, which every preview
goes through, wrap their content in it. To keep the theme from splitting in two, do not
write `MaterialTheme { }` directly anywhere else.

`AppSpacing` sits next to `AppTheme` because Material 3 has no scheme for spacing.
Colors can be distributed through `ColorScheme`, but there is no mechanism for spacing,
so it is an `object` that components read directly. Tokens are added in this package.

The theme is in Kotlin, not in Android's `res/values/themes.xml`, because this is KMP.
iOS has no `res/`, so a look written in resource XML cannot be shared. Only what the
Android build requires, such as the app name, stays in AndroidResource of `:app:android`.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:ui` | `src/commonMain/kotlin/**/theme/*.kt` |  |

## Examples

- `AppTheme` ... The theme of the whole app
- `AppSpacing` ... The spacing tokens

## Forbidden contents

- Colors and sizes used by only one screen. Those are constants inside that screen
- `@Composable` components. They go in the `component` package

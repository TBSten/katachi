[katachi-sample-kmp](../README.md) / [App](README.md)

# App root

The whole-app @Composable that MainActivity calls via setContent, kept in :app:android

The `@Composable` that assembles the whole app: the theme, the navigation bar, and the
Route for the current destination. `MainActivity` (the ActivityEntrypoint role) only
calls it, so everything that decides what the app looks like sits here.

It is a role of its own because it is a different kind of file from the Activity: a
`@Composable` that takes its dependencies as arguments and knows nothing about Android.
That is what makes it the part that can be shared once `app/ios` gets a
`ComposeUIViewController`.

The package is written out (`com.example.kmp.app`) for the same reason as in
ActivityEntrypoint: it does not follow the module path `:app:android`.

## Placement

| Path | When to use |
|---|---|
| `app/android/src/main/kotlin/com/example/kmp/app/AppRoot.kt` |  |

## Examples

- `AppRoot` ... The Composable that assembles the whole app

## Allowed contents

- The `@Composable` that assembles the whole app

## Forbidden contents

- The screens themselves. Screens live in the feature modules; this role only calls a Route
- Creating dependencies. `AppRoot` receives them as arguments; creating them is the Activity's job

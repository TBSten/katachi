[katachi-sample-kmp](../README.md) / [App](README.md)

# Entrypoint

The starting point of the Android app: a ComponentActivity and the whole-app @Composable it calls via setContent

Where the Android app starts running. It goes in `src/main` of `:app:android`, not in the
`commonMain` of a KMP module. `:app:android` is an Android application module, so its
sources sit in `src/main` like any other Android module.

The content has two levels. `MainActivity` is wiring only: it creates
`UserRepositoryImpl` and calls `setContent { AppRoot(...) }`. Assembling the theme, the
navigation bar and the Route for the current destination is `AppRoot`'s job. They are
split because `AppRoot` is the part that can be shared once `app/ios` gets a
`ComposeUIViewController`. The Activity stays Android-specific.

The package of this module is `com.example.kmp.app`, which cannot be derived from the
module path `:app:android`. That is why the layout writes the package out instead of
using `modulePackage`. When something breaks the rule, it is more honest to say so.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:app:android` | `src/main/kotlin/com/example/kmp/app/*.kt` |  |

## Examples

- `MainActivity` ... The Activity shown at launch
- `AppRoot` ... The Composable that assembles the whole app

## Allowed contents

- The platform entry point (`ComponentActivity`) and its minimal wiring
- The `@Composable` that assembles the whole app

## Forbidden contents

- The screens themselves. Screens live in the feature modules; this role only calls a Route
- State. Screen state belongs to the ViewModel and the current location to `Navigator` in `:navigation`
- Parts meant to be shared. Anything written here is invisible to iOS

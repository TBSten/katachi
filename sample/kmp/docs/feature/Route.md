[katachi-sample-kmp](../README.md) / [Feature](README.md)

# Route

Ties a screen to a navigation Destination and also takes on creating the ViewModel

The only entry reachable from outside a feature module. `object HomeRoute` has
`destination` (which `Destination` this screen is) and `Content()` (the call that draws
it), and the caller knows only those two.

The key point is that this role takes on creating the ViewModel. `Content()` calls
`viewModel { HomeViewModel(repository) }`, so the AppRoot of `:app:android` can show the
screen without knowing that the type HomeViewModel exists. Dependencies (currently
UserRepository) are received as arguments. This sample has no DI container and hands
them over by hand.

It is fixed to `commonMain` so that the same Route can be called from the Android
`AppRoot` and, later, from `app/ios` once it has a `ComposeUIViewController`.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:<feature>` | `src/commonMain/kotlin/**/<feature>Route.kt` |  |

## Examples

- `HomeRoute` ... The destination of the home screen
- `SettingsRoute` ... The destination of the settings screen

## Forbidden contents

- The content of the screen. Building the Compose tree is the Screen's job
- The definition of the destination itself. `Destination` is in `:navigation`, and the Route only points at it
- Control of navigation. Holding where we are now is the job of `Navigator` in `:navigation`

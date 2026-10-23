[katachi-sample-android](../README.md) / [Structure of each screen](README.md)

# ViewModel

An androidx.lifecycle.ViewModel that exposes screen state as a StateFlow and receives events

One `<Name>ViewModel.kt` per `:feature:<name>`. It exposes the screen state as
`StateFlow<UiState<T>>` and emits the next state on receiving events from the screen
(`refresh()`, `setDarkThemeEnabled(enabled)`). As with the Screen, the file name is
`<Name>ViewModel.kt`, where `<Name>` is the feature's name.

The state container is `UiState` from the `core` package of `:ui`, and the content its
`Content` wraps (`HomeContent` / `SettingsContent`) is a data class in the same file as
the ViewModel. It is a type used by a single screen, so it is not lifted into `:ui`.

The Repository is received as a constructor argument, with the production implementation
as the default (`userRepository: UserRepository = UserRepositoryImpl()`). This lets
`viewModel()` build it without a factory; if DI is introduced, only this default
disappears and the shape does not change.

## Placement

| Path | When to use |
|---|---|
| `feature/*/src/main/kotlin/com/example/sample/feature/*/*ViewModel.kt` |  |

## Constraints

- [Must not import Compose or Android (ktFileContent)](#kotlin-file-content)

## Kotlin file content

Example: `HomeViewModel.kt`

| Import | |
|---|---|
| `androidx.lifecycle.**` | optional |
| `kotlinx.coroutines.**` | optional |
| `com.example.sample.data.**` | optional |
| `com.example.sample.ui.core.**` | optional |

| Declaration | |
|---|---|
| `internal data class HomeContent` | required |
| `internal class HomeViewModel : ViewModel` | required |

### Members of `class HomeViewModel`

| Member | |
|---|---|
| `public val uiState: StateFlow<UiState<HomeContent>>` | required |
| `public fun *(...)` | optional |
| `private` (anything) | optional |

## Examples

- `HomeViewModel` ... The state of the home screen
- `SettingsViewModel` ... The state of the settings screen

## Forbidden contents

- Dependencies on Compose. It imports no `androidx.compose.*` and writes no
  `@Composable`. Because this line is drawn, state assembly can be read without Compose
- Android's Context / View / resources. Apart from extending
  `androidx.lifecycle.ViewModel`, it does not touch Android
- The screen layout. Drawing is the Screen's job

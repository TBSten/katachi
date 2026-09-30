[katachi-sample-kmp](../README.md) / [Feature](README.md)

# ViewModel

An androidx.lifecycle.ViewModel that holds screen state. Converts values fetched from the Repository into a UiState and exposes it as a StateFlow

The place that holds screen state. One file, `<Name>ViewModel.kt`, in the `commonMain` of
`:feature:<name>`. As with Screen, the file name is bound to the module name, so two
ViewModels never sit side by side in one feature module.

It extends `androidx.lifecycle.ViewModel`, which is the Compose Multiplatform version
(`org.jetbrains.androidx.lifecycle`), so it can be written in `commonMain` and works on
iOS too. A package name starting with `androidx` does not make it Android-only, and this
is an easy place to trip in KMP.

It only receives the Repository as an argument and never creates it. Creating it is the Route's job.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:<feature>` | `src/commonMain/kotlin/**/<feature>ViewModel.kt` |  |

## Examples

- `HomeViewModel` ... The state of the home screen
- `SettingsViewModel` ... The state of the settings screen

## Allowed contents

- `private val mutableState = MutableStateFlow(...)` and a `val state: StateFlow<UiState<...>>`
  that exposes it with `asStateFlow()`
- Operations called from the screen (such as `reload()`) and loading with `viewModelScope`
- A data class shaped for display (like `SettingsUi`; it may live in the same file)

## Forbidden contents

- `@Composable`. Drawing is the Screen's job
- `android.*` imports and `Context`. Push whatever needs them down to ExpectDeclaration and ActualImplementation
  (expect/actual) in `:data`; written here, `commonMain` would not compile
- Dependencies on the ViewModel of another feature

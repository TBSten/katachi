[katachi-sample-kmp](../README.md) / [UI](README.md)

# UI core

The core package of the :ui module. The screen-independent foundation of the UI, such as UiState

A package for the vocabulary of the UI layer that is neither look nor component. Right now
it holds only `UiState`, a sealed interface for the three states every screen shares:
loading, loaded and failed.

`UiState` deliberately does not depend on Compose. It is the type at both ends, created by
the ViewModel and read by the `@Composable`, so keeping it plain Kotlin lets both sides
be tested without the Compose runtime. In fact `SampleModulesSpec` of `:app:android`
checks the behavior of `valueOrNull()` without starting Compose.

The name `core` says nothing beyond "foundation", so it tends to become a place where
anything goes. Judge by whether it satisfies both "knows no screen" and "knows no Compose".

## Placement

| Module | Path | When to use |
|---|---|---|
| `:ui` | `src/commonMain/kotlin/**/core/*.kt` |  |

## Examples

- `UiState` ... The type that represents screen state
- `valueOrNull` ... The extension function that extracts the value

## Allowed contents

- Types that represent screen state, and their small extension functions
- Vocabulary used in common by several screens, on the UI side only

## Forbidden contents

- `@Composable`. Components go in `component` and the look in `theme`
- Domain types. Users and their lists belong to `:data`, and `UiState` only wraps them
- State meaningful to only one screen. Write it on the feature module side

[katachi-sample-kmp](../README.md) / [Feature](README.md)

# Screen

The @Composable of one screen. Subscribes to the ViewModel's StateFlow and draws by combining Components

The look of one whole screen. Put exactly one file, `<Name>Screen.kt`, in the
`commonMain` of `:feature:<name>`. The file name is decided by the module name, so
`ProfileScreen.kt` cannot be added to `:feature:home`. To add a screen, add a module.

The file is built in two levels. `HomeScreen` receives the ViewModel and subscribes with
`collectAsState()`, and `internal fun HomeContent` receives a `UiState` as an argument and
draws it. Separating what cannot be drawn without creating a ViewModel from what can be
drawn once given a state lets both previews (ui/Preview) and tests deal with the latter only.

It is `commonMain` only so that Android and iOS share the same screen code through
Compose Multiplatform. If something needs platform-dependent behavior, push it down to
PlatformImplementation (expect/actual) in `:data`, not into a screen.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:<feature>` | `src/commonMain/kotlin/**/<feature>Screen.kt` |  |

## Examples

- `HomeScreen` ... The home screen
- `SettingsScreen` ... The settings screen

## Allowed contents

- The `@Composable` of that screen, and a stateless `<Name>Content` that receives state as an argument
- A description of the placement that combines Component / Theme / UiCore from `:ui`

## Forbidden contents

- Direct calls to a Repository. The ViewModel turns data into a `UiState` before handing it over
- Screens of other feature modules, or their internal types
- `androidMain` / `iosMain` versions of a screen. This role declares only `commonMain`

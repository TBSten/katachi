[katachi-sample-kmp](../README.md) / [Data](README.md)

# Repository

The :data module's user package. The way into data, placed as an interface and an implementation

The entry through which the app touches data. In the `user` package of `:data`, place
the interface (`*Repository.kt`) and the implementation (`*RepositoryImpl.kt`) side by
side. The caller (the ViewModel) depends only on the interface.

The layout is declared in two parts because the name of an interface does not match the
file name of an implementation. A partial-match capture, like `*`, does not cross the
string that follows it, so picking up the implementation needs the implementation's own
pattern. Trying to loosen a single pattern to cover both would let through shapes that
were never declared.

The file declarations of the interface and the implementation each carry one `.template`. Running
`--arg template=data.Repository.repository,data.Repository.repositoryImpl --arg name=Cache`
creates both at once, while specifying only `data.Repository.repository` creates
only the interface (write the implementation by hand, or add it later with
`data.Repository.repositoryImpl`).

Only `commonMain` is declared. What changes its implementation per platform is taken on
by the neighboring PlatformImplementation (expect/actual), not by this role. Adding
`androidMain` here would scatter the same "absorbing platform differences" over two places.

Unlike sample/android, this sample has no repository for settings. The settings screen
is served by just reading `UserRepository` and `platformName()`. Writing an unused
package in the definition would make the documentation point at a directory that does not exist.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:data` | `src/commonMain/kotlin/**/user/*Repository.kt` |  |
| `:data` | `src/commonMain/kotlin/**/user/*RepositoryImpl.kt` |  |

## Examples

- `UserRepository` ... The interface that fetches users
- `UserRepositoryImpl` ... The implementation of UserRepository

## Forbidden contents

- UI types. `UiState` lives in the core package of `:ui`, and `:data` does not know about it
- Fake implementations for tests. `FakeUserRepository` belongs to the Fake role of `:testing`

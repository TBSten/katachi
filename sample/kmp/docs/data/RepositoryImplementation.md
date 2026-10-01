[katachi-sample-kmp](../README.md) / [Data](README.md)

# Repository implementation

The :data module's user package. The implementation of a repository interface

The implementation of a RepositoryInterface. It goes in the `user` package of `:data`
as `*RepositoryImpl.kt`, beside the interface it implements. `MainActivity` creates it
and hands it to `AppRoot`; every other caller sees the interface.

It is a role of its own, not a second layout of RepositoryInterface, because the two are
different kinds of file. `*RepositoryImpl.kt` is also a name the interface pattern
`*Repository.kt` cannot match, so a file in the wrong place is reported as a violation
instead of quietly passing.

The file declaration carries one `.template` whose `name` capture is shared with the
interface's template: running
`--arg template=data.RepositoryInterface,data.RepositoryImplementation --arg name=Cache`
creates both at once, and the `item` parameter binds both when given once.

## Placement

| Path | When to use |
|---|---|
| `data/src/commonMain/kotlin/com/example/kmp/data/user/*RepositoryImpl.kt` |  |

## Examples

- `UserRepositoryImpl` ... The implementation of UserRepository

## Forbidden contents

- Fake implementations for tests. `FakeUserRepository` belongs to the Fake role of `:testing`
- UI types. `UiState` lives in the core package of `:ui`, and `:data` does not know about it

[katachi-sample-android](../README.md) / [Data layer](README.md)

# Repository interface

The interface for fetching and storing data, in :data, in one package per subject (user / settings)

The interface of a repository, kept in `:data`. Packages are split per subject
(`user` / `settings`), and the file name starts with the package name
(`User*Repository.kt` for `user`). Callers depend only on this interface, and the
implementation's name is never written in a ViewModel's arguments.

`:data` depends on no other module and is the lowest layer of this app. It touches
neither Android nor Compose, so the interfaces read as plain Kotlin. A ViewModel
receives the interface through its constructor, and tests swap it for `Fake*` from
`:testing`.

Only the `User*Repository.kt` part can be generated from a template
(`UserRepository.kt` itself is handwritten in each domain from the start and is not a
generation target). The id is split per domain, and
`--arg template=data.RepositoryInterface.user,data.RepositoryImplementation.user
--arg name=Cache` creates the interface and its implementation at once.

## Placement

| Path | When to use |
|---|---|
| `data/src/main/kotlin/com/example/sample/data/user/UserRepository.kt` |  |
| `data/src/main/kotlin/com/example/sample/data/user/User*Repository.kt` |  |
| `data/src/main/kotlin/com/example/sample/data/settings/SettingsRepository.kt` |  |
| `data/src/main/kotlin/com/example/sample/data/settings/Settings*Repository.kt` |  |

## Examples

- `UserRepository` ... The interface for fetching and storing the user

## Forbidden contents

- Types for screens. Repacking into `UiState` is the ViewModel's job, and `:data` does not know `:ui`
- The implementation (`*RepositoryImpl.kt`). It is the RepositoryImplementation role
- Packages that span subjects. If a `user` type gets mixed into `settings`, split the packages again

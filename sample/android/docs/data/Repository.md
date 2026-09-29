[katachi-sample-android](../README.md) / [Data layer](README.md)

# Repository

Fetching and storing data. Interfaces and implementations sit side by side in :data, in one package per subject (user / settings)

The only role of `:data`, taking on fetching and storing data. Packages are split per
subject (`user` / `settings`), and the interface (`*Repository.kt`) and the
implementation (`*RepositoryImpl.kt`) sit side by side in each. File names start with
the package name (`User*Repository.kt` for `user`). Callers depend only on the
interface, and the `Impl` name is never written in a ViewModel's arguments.

This role has two `layout { }` blocks. The place is the same package and only the file
name pattern differs, so the `description` of each `layout` explains which is the
interface and which is the implementation. It is also a real example of one role having
several places.

`:data` depends on no other module and is the lowest layer of this app. It touches
neither Android nor Compose, so the interfaces read as plain Kotlin. A ViewModel
receives the interface through its constructor, and tests swap it for `Fake*` from
`:testing`.

Only the `User*Repository.kt` / `User*RepositoryImpl.kt` part can be generated from a
template (`UserRepository.kt` / `UserRepositoryImpl.kt` themselves are handwritten in each
domain from the start and are not generation targets). The id is split per domain, and
`--arg template=data.Repository.user,data.Repository.userImpl --arg name=Cache` creates
both at once.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:data` | `src/main/kotlin/**/user/UserRepository.kt` | The interface. The type callers depend on |
| `:data` | `src/main/kotlin/**/user/User*Repository.kt` | The interface. The type callers depend on |
| `:data` | `src/main/kotlin/**/settings/SettingsRepository.kt` | The interface. The type callers depend on |
| `:data` | `src/main/kotlin/**/settings/Settings*Repository.kt` | The interface. The type callers depend on |
| `:data` | `src/main/kotlin/**/user/UserRepositoryImpl.kt` | The interface. The type callers depend on |
| `:data` | `src/main/kotlin/**/user/User*RepositoryImpl.kt` | The interface. The type callers depend on |
| `:data` | `src/main/kotlin/**/settings/SettingsRepositoryImpl.kt` | The interface. The type callers depend on |
| `:data` | `src/main/kotlin/**/settings/Settings*RepositoryImpl.kt` | The interface. The type callers depend on |

## Examples

- `UserRepository` ... The interface for fetching and storing the user
- `UserRepositoryImpl` ... The implementation of UserRepository

## Forbidden contents

- Types for screens. Repacking into `UiState` is the ViewModel's job, and `:data` does not know `:ui`
- Files other than `*Repository.kt` and `*RepositoryImpl.kt`. If you want to split out
  DTOs or data sources, add a role first
- Packages that span subjects. If a `user` type gets mixed into `settings`, split the packages again

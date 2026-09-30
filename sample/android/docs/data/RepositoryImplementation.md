[katachi-sample-android](../README.md) / [Data layer](README.md)

# Repository implementation

The implementation of a repository interface, in :data, next to the interface

The implementation of a repository, kept in `:data` in the same package as its interface
(`user/UserRepositoryImpl.kt`). Callers never name it: the interface is what a
ViewModel receives, and wiring the implementation in is the only place it appears.

Only the `User*RepositoryImpl.kt` part can be generated from a template
(`UserRepositoryImpl.kt` itself is handwritten in each domain from the start). Each id
shares its interface template's `name` capture, so passing `name` once binds both when
they are generated together
(`--arg template=data.RepositoryInterface.user,data.RepositoryImplementation.user`).

## Placement

| Module | Path | When to use |
|---|---|---|
| `:data` | `src/main/kotlin/**/user/UserRepositoryImpl.kt` |  |
| `:data` | `src/main/kotlin/**/user/User*RepositoryImpl.kt` |  |
| `:data` | `src/main/kotlin/**/settings/SettingsRepositoryImpl.kt` |  |
| `:data` | `src/main/kotlin/**/settings/Settings*RepositoryImpl.kt` |  |

## Examples

- `UserRepositoryImpl` ... The implementation of UserRepository

## Forbidden contents

- The interface (`*Repository.kt`). It is the RepositoryInterface role
- Types for screens. `:data` does not know `:ui`

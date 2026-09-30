[katachi-sample-android](../README.md)

# Data layer

What :data holds: fetching and storing data

The `:data` module. For now it has two roles, RepositoryInterface and
RepositoryImplementation, with the interface and its implementation side by side in one
package per subject (`user` / `settings`). Each package is one entry of `DataDomain`; to
add a subject, add one line there.

It is the lowest layer of this app and depends on no other module. It touches neither
Android nor Compose, so it can be read without pulling in `:ui` or `:feature:*`. A
ViewModel receives the interface here through its constructor, and tests swap it for a
fake from `:testing`.

It is a group even though it is small because this is where things will grow. Data sources
or DTOs go here when they need to be split out. For now, any file in `:data` other than
a repository interface or implementation is a violation, so there is no "put it down first
and think later".

| Role | Summary |
|---|---|
| [Repository interface](./RepositoryInterface.md) | The interface for fetching and storing data, in :data, in one package per subject (user / settings) |
| [Repository implementation](./RepositoryImplementation.md) | The implementation of a repository interface, in :data, next to the interface |

## Placement in this group

```
:data
  src/main/kotlin/**/
    user/
      UserRepository.kt           Repository interface
      User*Repository.kt          Repository interface
      UserRepositoryImpl.kt       Repository implementation
      User*RepositoryImpl.kt      Repository implementation
    settings/
      SettingsRepository.kt       Repository interface
      Settings*Repository.kt      Repository interface
      SettingsRepositoryImpl.kt   Repository implementation
      Settings*RepositoryImpl.kt  Repository implementation
```

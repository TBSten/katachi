[katachi-sample-android](../README.md)

# Data layer

What :data holds: fetching and storing data

The `:data` module. For now it has a single Repository role, with the interface and its
implementation side by side in one package per subject (`user` / `settings`). Each
package is one entry of `DataDomain`; to add a subject, add one line there.

It is the lowest layer of this app and depends on no other module. It touches neither
Android nor Compose, so it can be read without pulling in `:ui` or `:feature:*`. A
ViewModel receives the interface here through its constructor, and tests swap it for a
fake from `:testing`.

It is a group even with one role because this is where things will grow. Data sources
or DTOs go here when they need to be split out. For now, any file in `:data` other than
a Repository is a violation, so there is no "put it down first and think later".

| Role | Summary |
|---|---|
| [Repository](./Repository.md) | Fetching and storing data. Interfaces and implementations sit side by side in :data, in one package per subject (user / settings) |

## Placement in this group

```
:data
  src/main/kotlin/**/
    user/
      UserRepository.kt           Repository
      User*Repository.kt          Repository
      UserRepositoryImpl.kt       Repository
      User*RepositoryImpl.kt      Repository
    settings/
      SettingsRepository.kt       Repository
      Settings*Repository.kt      Repository
      SettingsRepositoryImpl.kt   Repository
      Settings*RepositoryImpl.kt  Repository
```

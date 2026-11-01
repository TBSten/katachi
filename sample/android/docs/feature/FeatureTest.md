[katachi-sample-android](../README.md) / [Structure of each screen](README.md)

# Screen test

A test in src/test of :feature:<name> that runs the ViewModel with fakes from :testing

Unit tests of a feature module. For `:feature:home`, put `HomeViewModelTest.kt` in the
same package under `src/test`; the file name starts with the module name (`Home`) and
ends with `Test`.

A ViewModel's Repository is a constructor argument, so a test passes a fake such as
`FakeUserRepository` from `:testing` and checks the `UiState` that flows out. It touches
neither Compose nor a real Android device, so it runs on the JVM alone. This is why the
feature module's `build.gradle.kts` has `testImplementation(project(":testing"))`.

A template can generate a test that builds that feature's ViewModel with fakes. The id
is split per screen (`feature.FeatureTest.home` / `.settings`), and `--arg
template=feature.FeatureTest.home --arg name=ViewModel` produces `HomeViewModelTest.kt`.
The content differs per screen (the fake and its arguments, the expected `Content`), so
it is chosen by id rather than by passing values.

Unlike the other feature roles, this one lists the modules by name, through
`FeatureModule` in `FeatureGroup.kt`. A test file in a module missing from it is
reported as a violation.

## Placement

| Path | When to use |
|---|---|
| `feature/home/src/test/kotlin/com/example/sample/feature/home/Home*Test.kt` | For `:feature:home`. Start the file name with `Home` |
| `feature/settings/src/test/kotlin/com/example/sample/feature/settings/Settings*Test.kt` | For `:feature:settings`. Start the file name with `Settings` |

## Constraints

- [ktFileContent](#kotlin-file-content)

## Kotlin file content

Example: `HomeViewModelTest.kt`

| Package |
|---|
| (the file's directory) |

| Import | |
|---|---|
| `org.junit.**` | optional |
| `com.example.sample.**` | optional |
| `com.example.sample.data.**.*RepositoryImpl` | not allowed |

| Declaration | |
|---|---|
| `class HomeViewModelTest` | required |

### Members of `class HomeViewModelTest`

| Member | |
|---|---|
| `@Test fun *(...)` | optional |

## Examples

- `HomeViewModelTest` ... A test that runs HomeViewModel with fakes
- `SettingsViewModelTest` ... A test that runs SettingsViewModel with fakes

## Forbidden contents

- A real Repository (`*RepositoryImpl`). Swap in the fakes from `:testing` instead
- Tests of screen rendering. If Compose tests are needed, split the role

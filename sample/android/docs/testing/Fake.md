[katachi-sample-android](../README.md) / [Testing](README.md)

# Fake

A stand-in implementation kept in :testing, used by the tests of other modules

A test implementation that satisfies the interfaces of `:data` with in-memory values
only. `FakeUserRepository` implements `UserRepository` and `FakeSettingsRepository`
implements `SettingsRepository`. Constructor arguments have defaults, so a test writes
only the values that matter to it.

The point of this role is that fakes sit in `main`, not in `src/test`. Code in
`src/test` is visible only to its own module, so handing it to another module's tests
means publishing it as production code. Users pull it in with
`testImplementation(project(":testing"))`. `:testing` depends on `:data` with `api` so
that the consumer receives the interfaces along with it.

File names are `Fake*.kt`. Only stand-in implementations may live in `:testing`; if you
want to add test helpers or custom assertions, add a role first. The tests themselves
are a different role (test code) and live in `src/test`.

Can be generated from a template. What you pass as `repository` is the name of the
interface to implement as is (`UserRepository`), and `--arg template=testing.Fake --arg
repository=UserRepository` produces `FakeUserRepository.kt`. Which domain package it
goes into is decided by which `DataDomain` the start of the name matches.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:testing` | `src/main/kotlin/**/Fake*.kt` |  |

## Examples

- `FakeUserRepository` ... The in-memory implementation of UserRepository
- `FakeSettingsRepository` ... The in-memory implementation of SettingsRepository

## Forbidden contents

Code called from production. Only the test compile path may depend on `:testing`; the
`main` of `:app` and `:feature:*` must not refer to it.

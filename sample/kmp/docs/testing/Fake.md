[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Fake

Fake implementations in the commonMain of :testing, used from the tests of other modules

The role that gathers, in `:testing`, the test doubles other modules' tests use. File
names are limited to `Fake*.kt`, so `StubUserRepository.kt` and `TestUserRepository.kt`
do not pass. Fixing on one name lets a reader of test code see "this is a fake" from the
name alone.

The point of this role is that it sits in `commonMain`, a production source set, not in
`commonTest`. A test source set cannot be referenced from other modules, so to use a fake
from the tests of `:app:android` it has to go on the production side. `:testing` is cut out
as a module that depends only on `api(project(":data"))` and that nothing in the app
itself depends on.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:testing` | `src/commonMain/kotlin/**/Fake*.kt` |  |

## Examples

- `FakeUserRepository` ... A fake implementation of UserRepository

## Allowed contents

- Fake implementations that satisfy an interface of `:data`. Keep the returned values
  replaceable through the constructor (like `FakeUserRepository(listOf("alice"))`)

## Forbidden contents

- Tests themselves. `*Spec.kt` belongs to the Test role
- Implementations called from production. The real ones are the `*Impl` in `:data`
- Dependencies on test frameworks such as kotest. This is a production source set, so
  bringing one in would leak into the build of the app itself

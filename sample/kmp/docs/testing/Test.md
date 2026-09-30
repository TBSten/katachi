[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Test code

The tests of each module. commonTest for KMP modules, src/test for pure Android / pure JVM modules

The app's own tests. Where they go depends on the kind of module: `src/test` for an
Android module and `commonTest` for a KMP module. Either way they are "the tests of that
module", so katachi treats them as one role.

What the layout currently writes is only the `testSourceSet` of `:app:android`, because
that is the only module that actually has tests; when a module with `commonTest`
appears, one line is added then. Declaring a directory that does not exist would claim
this sample has a shape it does not have.

Tests gather in `:app:android` because of KMP. The other modules are KMP with only
Android and iOS and no JVM target, so this is the only place with JVM tests that can
exercise the parts that do not use Compose (`Navigator`, `UiState`, `FakeUserRepository`).

The `@Composable` of screens is not tested. That would need the Compose test runtime,
which is outside what this sample wants to show.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:app:android` | `src/test/kotlin/com/example/kmp/app/*Spec.kt` |  |

## Examples

- `SampleModulesSpec` ... The unit tests of :app:android

## Forbidden contents

- The architecture definition. `:architecture-test` is described by the DefinitionEntry, GroupDefinition, RoleDefinition
  and related roles
- Test doubles. `Fake*` live in the commonMain of `:testing`

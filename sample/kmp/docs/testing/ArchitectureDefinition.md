[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Architecture definition

src/test of the :architecture-test module. This project's definition, written in the katachi DSL. It belongs to no layer, so it lives in a dedicated module

The code that describes the shape of this project with katachi. The file you are reading
belongs to this role too. It is kept apart from test code: the definition's job is to
describe the project, and the test that checks it against the real directories
(`ProjectArchitectureTest`) is a single line.

It sits in a dedicated `:architecture-test` module because this is KMP. katachi is a JVM
library, and the other modules of this build are KMP with only Android and iOS, so there
is no `commonTest` anywhere to hold the definition. Preparing one `kotlin("jvm")` module
is also the shape katachi recommends.

The files follow the rule of one file per declaration: the role `"UiCore"` is in
`roles/UiCoreRole.kt`, and the group `"app"` in `groups/AppGroup.kt`.
`ProjectArchitectureSpec` checks that rule itself by reading declaration sites back from
the source, so it fails if `inline` is added to a group / role function.

The layout is deliberately loose and takes one package level with `*`: adding a new
file to `roles` passes without touching the definition. A strict form that spells out
package names is in sample/android; having both shows that you can choose.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/kmp/*.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/kmp/*/*.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... The entry point of the definition
- `roles/ComponentRole.kt` ... The declaration of one role

## Allowed contents

- The definition (the `groups` / `roles` packages) and its entry point `ProjectArchitecture.kt`
- `ProjectArchitectureTest.kt`, which checks the definition against the repository (the only test a user writes)
- `*Spec.kt`, which check the definition
- Custom processors in the `processor` package (those that read custom metadata such as `owner`)

## Forbidden contents

- App code. This module belongs to no layer of the app

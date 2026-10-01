[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Processor

The ArchitectureProcessor implementations this project wrote itself. They read the definition and produce something

This project's own code that reads a katachi definition and produces something. It is the one
thing this sample wants to show; the application under `src/main/kotlin` is there only to
provide what these read.

There are three, each of a different shape.

- `RoleFileCount` - no arguments. Implements `ArchitectureProcessorNoArg` as an `object` and
  counts files from `context.roles` and `context.filesOf(role)`
- `RoleTable` - typed arguments. Has a `@Serializable data class Args` and receives
  `String` / `List<String>` / `Int` / enum from `--arg`
- `RoleDocCoverage` - a check. Its answer is its own `Report` rather than `List<Violation>`,
  and when there is a problem it returns `Result.failure` to fail `katachiRoleDocCoverage`

All three are registered in `katachi { processors { register(...) } }` of
`architecture-test/build.gradle.kts` and can be run with the `katachi<Key>` task of each
registered key (`katachiRoleTable` and so on). A processor whose registration was forgotten
cannot be called from the command line, but calling it from a test with
`projectArchitecture.process(...)` needs no registration.

`layout { }` allows the `.kt` files directly in the `processors` package. File names are not
restricted (one processor per file is a convention written in this text, not something that is
rejected mechanically).

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/processors/*.kt` |  |

## Examples

- `RoleFileCount` ... The smallest form, without arguments
- `RoleTable` ... A form that takes typed arguments
- `RoleDocCoverage` ... A check that fails the run with Result.failure

## Allowed contents

Only implementations of `ArchitectureProcessor` and the types of their arguments and results belong here.

## Forbidden contents

- Declarations of roles or groups. Those belong to the architecture definition role. A
  definition writes a shape and a processor reads it, and mixing them makes "which one is
  decided first?" unreadable
- Application code. A processor runs at build time and is never called from `main()`

[Custom processor sample](../README.md)

# Definition and processors

The katachi definition, the three processors of your own that read it, and the generated documentation

The place where this sample's subject lives. It collects the code that supports the project
rather than a layer of the application.

The roles are separate because they read in different directions. The architecture definition
describes a shape, a processor reads that shape and produces something, and the generated
documentation and the layout snapshot are what was written out. If `processors/` were included
in a definition role, what this sample wants to show would vanish from both `docs/` and the
output of `RoleFileCount`.

The three processors each take one shape: no arguments, typed arguments, and a check (failing
with `Result.failure`). All of them are `object`s; katachi has no base class to inherit from.

The two generated outputs are also separate roles. `docs/` is written by `katachiDocs` for
readers, and `snapshots/` is written by `LayoutSnapshotSpec` for katachi itself, so they differ
in how they are updated and in who is troubled when one is deleted.

| Role | Summary |
|---|---|
| [Architecture entry](./ArchitectureEntry.md) | The entry point of the katachi definition, and the section definitions shared by its groups and roles |
| [Group definition](./GroupDefinition.md) | One group of the katachi definition, declared in `groups/<Name>Group.kt` |
| [Role definition](./RoleDefinition.md) | One role of the katachi definition, declared in `roles/<Name>Role.kt` |
| [Architecture test](./ArchitectureTest.md) | The tests that check the project against the definition and exercise the processors |
| [Processor](./Processor.md) | The ArchitectureProcessor implementations this project wrote itself. They read the definition and produce something |
| [Generated documentation](./GeneratedDocumentation.md) | Markdown written out of this definition and committed to the repository |
| [Layout snapshot](./LayoutSnapshot.md) | Text recording the flattened result of `layout { }`, for katachi's own self-verification |

## Placement in this group

```
:architecture-test
  src/test/kotlin/com/example/
    ProjectArchitecture.kt      Architecture entry
    DocumentSections.kt         Architecture entry
    groups/*Group.kt            Group definition
    roles/*Role.kt              Role definition
    ProjectArchitectureTest.kt  Architecture test
    *Spec.kt                    Architecture test
    processors/*.kt             Processor

docs/
  README.md                     Generated documentation
  **/*.md                       Generated documentation
snapshots/layout.txt            Layout snapshot
```

## Forbidden contents

The main code of the application must not be placed here. `:architecture-test` has no main
source set.

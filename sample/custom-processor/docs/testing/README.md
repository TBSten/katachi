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
| [Architecture definition entry](./ArchitectureDefinitionEntry.md) | The one file whose `architecture { }` gathers every group, so the definition can be read from here |
| [Document section definition](./DocumentSectionDefinition.md) | The headings this project declares for itself, so roles and groups can write under them |
| [Group definition](./GroupDefinition.md) | One group of the katachi definition, declared in `groups/<Name>Group.kt` |
| [Role definition](./RoleDefinition.md) | One role of the katachi definition, declared in `roles/<Name>Role.kt` |
| [Architecture test](./ArchitectureTest.md) | The one test that checks the whole project against the definition |
| [Integration spec](./IntegrationSpec.md) | The tests that call the processors through the API, and katachi's own sentinel run against this real project |
| [Processor](./Processor.md) | The ArchitectureProcessor implementations this project wrote itself. They read the definition and produce something |
| [Generated documentation](./GeneratedDocumentation.md) | Markdown written out of this definition and committed to the repository |
| [Layout snapshot](./LayoutSnapshot.md) | Text recording the flattened result of `layout { }`, for katachi's own self-verification |

## Placement in this group

```
:architecture-test
  src/test/kotlin/com/example/
    ProjectArchitecture.kt  Architecture definition entry
    DocumentSections.kt     Document section definition
    groups/*Group.kt        Group definition
    roles/*Role.kt          Role definition
    *Test.kt                Architecture test
    *Spec.kt                Integration spec
    processors/*.kt         Processor

docs/
  README.md                 Generated documentation
  **/*.md                   Generated documentation
snapshots/layout.txt        Layout snapshot
```

## Forbidden contents

The main code of the application must not be placed here. `:architecture-test` has no main
source set.

[Ktor sample app](../README.md)

# Testing

Tests that check behaviour, this definition itself, and the documentation and snapshot written out from it

Not an application layer, but a place that gathers the code that supports the project:
the test code, the architecture definition itself, and the `docs/` and `snapshots/`
written out from that definition.

Tests and the definition are separate because they have different subjects. Tests check
behaviour; the architecture definition describes shape. If the definition were hidden
inside `Test`, the documentation would no longer explain why the `:architecture-test`
module exists.

What is written out is also kept apart from the definition. The definition is written by
people, while `docs/` and `snapshots/` are written by machines, so where a hand may touch
them is completely different. It is also an example of the fact that, once generated files
live outside `build/`, they need roles too. The two are not merged because their readers
differ: `docs/` is the pages opened by someone who came to read the definition, and
`snapshots/` is the text seen by someone reviewing the diff of a definition change.

`katachi-baseline.json` also belongs here, as a file the test writes out. It is the ledger
of violations that already existed when katachi was introduced, and the violations recorded
in it do not fail the test.

| Role | Summary |
|---|---|
| [Test code](./Test.md) | Tests placed in src/test/kotlin, keeping the same package structure as the main code |
| [Architecture definition entry](./ArchitectureDefinitionEntry.md) | The one file whose `architecture { }` gathers every group, so the definition can be read from here |
| [Document section definition](./DocumentSectionDefinition.md) | The headings this project declares for itself, so roles and groups can write under them |
| [Group definition](./GroupDefinition.md) | One `groups/<Name>Group.kt` per group, saying what the group is made of by calling role functions |
| [Role definition](./RoleDefinition.md) | One `roles/<Name>Role.kt` per role, holding its description, its layout and its constraints |
| [Processor definition](./ProcessorDefinition.md) | A custom processor that reads the definition, registered in `architecture-test/build.gradle.kts` |
| [Architecture test](./ArchitectureTest.md) | The one test that checks the whole project against the definition |
| [Integration spec](./IntegrationSpec.md) | katachi's own integration tests, run against this real project |
| [Generated documentation](./GeneratedDocumentation.md) | Markdown written out of this definition and committed to the repository |
| [Layout snapshot](./LayoutSnapshot.md) | A record of every line of this definition, flattened. It exists so people can review changes to the definition as a diff |
| [Baseline (ledger of held-back violations)](./BaselineFile.md) | A ledger that records violations already present when katachi was introduced and holds them back without failing the test |

## Placement in this group

```
src/test/kotlin/**/*Test.kt  Test code
architecture-test/src/test/kotlin/com/example/
  ProjectArchitecture.kt     Architecture definition entry
  DocumentSections.kt        Document section definition
  groups/*Group.kt           Group definition
  roles/*Role.kt             Role definition
  processors/*.kt            Processor definition
  *Test.kt                   Architecture test
  *Spec.kt                   Integration spec
docs/
  README.md                  Generated documentation
  **/*.md                    Generated documentation
snapshots/layout.txt         Layout snapshot
katachi-baseline.json        Baseline (ledger of held-back violations)
```

## Forbidden contents

What must not be placed here is the application's own code. `:architecture-test` is a
module that belongs to no layer of the application and has no main source set.

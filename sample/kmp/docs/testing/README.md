[katachi-sample-kmp](../README.md)

# Testing support

Test doubles, the test code itself, the katachi architecture definition, the documents and snapshot written from it, and the ledger of shelved violations

The roles around testing: Fake (commonMain of `:testing`), Test (the tests of each
module), the roles of `:architecture-test` (DefinitionEntry, DefinitionSections,
GroupDefinition, RoleDefinition, ProjectArchitectureTest, ProjectArchitectureSpec, and the
processor package's CustomProcessor, ProcessorMetadata and ProcessorSpec),
GeneratedDocumentation (`docs/` at the root), LayoutSnapshot (`snapshots/` at the root)
and BaselineFile (`katachi-baseline.json` at the root, the ledger of shelved violations).

Keeping the architecture definition apart from Test is the main point of this group. The
definition is not test code. Its job is to describe the shape of the project, and the
test that checks it against the real directories is a single line. `:architecture-test`
holds several kinds of file (the entry, groups, roles, the one test, katachi's own specs,
custom processors), so each kind is a role and none of them has to be a catch-all.

Fake living in `commonMain` instead of `commonTest` is also a shape this group shows.
A test source set cannot be referenced from other modules, so code written for tests
sometimes has to sit in a production source set. `:testing` is a module cut out for that
purpose, and nothing in the app itself depends on it.

GeneratedDocumentation and LayoutSnapshot are separate from the definition roles for the
same reason. A person writes the definition, `katachiDocs` writes `docs/`, and
`:architecture-test:test` writes `snapshots/`. The places where you may edit by hand are
opposite, so merging them into one role would make it impossible to say which one to fix.
It is also an example of generated output outside `build/` still needing a role.

The generated files are kept apart from each other too, because their readers differ.
`docs/` is the page opened by someone who came to read the definition, and `snapshots/`
is the text seen by whoever reviews the diff of a changed definition.

| Role | Summary |
|---|---|
| [Fake](./Fake.md) | Fake implementations in the commonMain of :testing, used from the tests of other modules |
| [Test code](./Test.md) | The tests of each module. commonTest for KMP modules, src/test for pure Android / pure JVM modules |
| [Definition entrypoint](./DefinitionEntry.md) | ProjectArchitecture.kt, which only calls the group functions and belongs to no layer |
| [Definition sections](./DefinitionSections.md) | DocumentSections.kt, the section headings every group and role writes through |
| [Group definition](./GroupDefinition.md) | groups/<Name>Group.kt, one group of the definition, which lists its roles by calling their functions |
| [Role definition](./RoleDefinition.md) | roles/<Name>Role.kt, one role of the definition, with where its files live |
| [Architecture test](./ProjectArchitectureTest.md) | The single JUnit test that runs the definition, kept in src/test/kotlin of :architecture-test |
| [Architecture spec](./ProjectArchitectureSpec.md) | Tests that verify the definition and katachi itself, kept in src/test/kotlin of :architecture-test |
| [Custom processor](./CustomProcessor.md) | Processors a user adds beside the check katachi ships, kept in the processor package of :architecture-test |
| [Processor metadata](./ProcessorMetadata.md) | Owner.kt, the metadata key and property a custom processor reads, kept in the processor package |
| [Processor spec](./ProcessorSpec.md) | Tests of the custom processors, kept in the processor package of :architecture-test |
| [Generated documentation](./GeneratedDocumentation.md) | Markdown written from this definition and committed to the repository |
| [Layout snapshot](./LayoutSnapshot.md) | A record of this definition, flattened and written out in full. It exists so a person can review changes to the definition as a diff |
| [Baseline (ledger of shelved violations)](./BaselineFile.md) | A ledger that records violations already present when katachi was introduced, shelving them without failing the tests |

## Placement in this group

```
:testing
  src/commonMain/kotlin/**/Fake*.kt             Fake

:app:android
  src/test/kotlin/com/example/kmp/app/*Spec.kt  Test code

:architecture-test
  src/test/kotlin/com/example/kmp/
    ProjectArchitecture.kt                      Definition entrypoint
    DocumentSections.kt                         Definition sections
    groups/*Group.kt                            Group definition
    roles/*Role.kt                              Role definition
    *Test.kt                                    Architecture test
    *Spec.kt                                    Architecture spec
    processor/
      *Processor.kt                             Custom processor
      Owner.kt                                  Processor metadata
      *Spec.kt                                  Processor spec

docs/
  README.md                                     Generated documentation
  **/*.md                                       Generated documentation
snapshots/layout.txt                            Layout snapshot
katachi-baseline.json                           Baseline (ledger of shelved violations)
```

## Forbidden contents

- The code of the app itself. What is here is code "for tests" and the description of the project

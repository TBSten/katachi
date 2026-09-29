[katachi-sample-android](../README.md)

# Testing

What exists to verify the app: shared fakes, tests, the architecture definition, generated docs, the layout snapshot and the baseline

Not a layer of the app, but what exists to verify it. Six things are gathered here.
Fakes are the replacement implementations in `:testing`; test code is the tests
themselves; the architecture definition is this very definition, written in katachi's
DSL; generated documentation is the `docs/` that `katachiDocs` writes from that
definition; the layout snapshot is the `snapshots/` that `:architecture-test:test`
writes by flattening the same definition; and the baseline is `katachi-baseline.json`,
the ledger of violations held back.

The architecture definition is not mixed into the test code because the two say
different things. The definition says "what shape it has", the tests say "how it
behaves". Both live in `:architecture-test` and are told apart by file location and
name (`ProjectArchitecture.kt` plus `groups/` and `roles/` are the definition; the
`*Spec.kt` and `*Test.kt` at the top of the package are tests). People who rewrite the
definition and people who add tests are usually doing different things, so they are
separate entries in the documentation too.

Only `:testing` is a module on the app side: it puts `Fake*` classes that satisfy the
interfaces of `:data` in the `main` source set and exposes them to other modules'
tests. Shipping test code as production code is deliberate, because `src/test` is not
visible from other modules.

`:architecture-test` is, like `:app`, a module whose package cannot be derived from the
module path. Applied as is it would be `com/example/sample/architectureTest`, so both
definition-side roles of this group write `com/example/sample` directly.

Generated documentation and the snapshot are not even inside a module: they come out in
`docs/` and `snapshots/` directly under the root. They are here as an example that
generated files outside `build/` also need roles. The two are not merged because their
readers differ: `docs/` is the page for someone who came to read the definition, and
`snapshots/` is the text for someone reviewing the diff of a definition change. The
hand-written `README.md` at the root is separate from both, and belongs to the
`Documentation` role of the `tool` group.

The baseline is the ledger of violations that already existed when katachi was adopted;
violations recorded there do not fail the test. It is also a file the test writes, so it
sits here with the generated files.

| Role | Summary |
|---|---|
| [Fake](./Fake.md) | A stand-in implementation kept in :testing, used by the tests of other modules |
| [Test code](./Test.md) | Tests that check the definition, kept in src/test/kotlin of :architecture-test |
| [Architecture definition](./ArchitectureDefinition.md) | The role definitions written in the katachi DSL. Belongs to no layer |
| [Generated documentation](./GeneratedDocumentation.md) | Markdown written out from this definition and committed to the repository |
| [Layout snapshot](./LayoutSnapshot.md) | A record of every line of this definition once flattened, so people can review changes to the definition as a diff |
| [baseline (ledger of held-back violations)](./BaselineFile.md) | A ledger of the violations that already existed when katachi was adopted, held back so the test does not fail |

## Placement in this group

```
:testing
  src/main/kotlin/**/Fake*.kt  Fake

:architecture-test
  src/test/kotlin/com/example/sample/
    *Spec.kt                   Test code
    *Test.kt                   Test code
    ProjectArchitecture.kt     Architecture definition
    DocumentSections.kt        Architecture definition
    groups/*Group.kt           Architecture definition
    roles/*Role.kt             Architecture definition

docs/
  README.md                    Generated documentation
  **/*.md                      Generated documentation
snapshots/layout.txt           Layout snapshot
katachi-baseline.json          baseline (ledger of held-back violations)
```

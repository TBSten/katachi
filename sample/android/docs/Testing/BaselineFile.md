[katachi-sample-android](../README.md) / [Testing](README.md)

# baseline (ledger of held-back violations)

A ledger of the violations that already existed when katachi was adopted, held back so the test does not fail

The file that `baseline()` in `ProjectArchitecture.kt` points to. Violations recorded
here do not fail `:architecture-test:test`; only a count such as "held back N
violations" is shown. A new violation that is not recorded fails the test as before.

This sample deliberately leaves two entries as a demo of the baseline:
`HomeFormatter.kt` in `:feature:home` (only Route / Screen / ViewModel may sit directly
in a feature package, so it is an `[UnexpectedFile]`), and `legacy/` in `:data` (a
package other than `user` and `settings`, so the whole directory is an
`[UnexpectedDirectory]`).

Not written by hand. It is updated in one of two ways.

- `./gradlew :architecture-test:test -Dkatachi.baseline.update=true` — rebuild it entirely from the violations that exist now
- `./gradlew :architecture-test:test -Dkatachi.baseline.prune=true` — remove only the entries whose violation has been fixed

Once a violation is fixed, its entry fails the test with `[StaleBaselineEntry]` as
"holding back a violation that no longer exists". It keeps failing until prune removes
the entry, so the number of held-back violations only goes down. In CI (environment
variable `CI=true`) both update and prune are refused and only the comparison runs.

## Placement

| Path | When to use |
|---|---|
| `katachi-baseline.json` |  |

## Examples

- `katachi-baseline.json` ... The list of held-back violations

## Forbidden contents

- Anything meant to be permanent. That is not for the ledger; write it in the definition's `layout { }` as a role
- Entries added by hand. The next update overwrites them

[Ktor sample app](../README.md) / [Testing](README.md)

# Baseline (ledger of held-back violations)

A ledger that records violations already present when katachi was introduced and holds them back without failing the test

The file that `baseline()` in `ProjectArchitecture.kt` points at. Violations recorded here
do not fail `:architecture-test:test`; only the count is shown, as "held back N
violations". A new violation that is not recorded fails the test as before.

This sample deliberately leaves two entries in as a demo of baseline:
`service/LegacyHealthCheck.kt` (`[UnexpectedFile]`, since it is not `*Service.kt`) and
`service/LegacyStatusService.kt` (`internal`, so it violates the `Must be public`
constraint).

Do not write it by hand. It is updated in one of two ways.

- `./gradlew :architecture-test:test -Dkatachi.baseline.update=true` — rebuild it wholesale from the violations that exist now
- `./gradlew :architecture-test:test -Dkatachi.baseline.prune=true` — remove only the entries whose violations have been fixed

Once a violation is fixed, its entry fails the test with `[StaleBaselineEntry]`, as
"holding back a violation that no longer exists". It keeps failing until prune removes the
entry, so the number of held-back violations can only go down.
On CI (environment variable `CI=true`), both update and prune are refused and only the
comparison is made.

## Placement

| Path | When to use |
|---|---|
| `katachi-baseline.json` |  |

## Examples

- `katachi-baseline.json` ... The list of held-back violations

## Forbidden contents

- Anything you want to allow permanently. That belongs not in the ledger but in the
  definition's `layout { }`, written as a role
- Entries added by hand. They are overwritten by the next update

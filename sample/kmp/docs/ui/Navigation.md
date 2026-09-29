[katachi-sample-kmp](../README.md) / [UI](README.md)

# Navigation

The definition of destinations, and the Navigator that holds the current location

The whole `:navigation` module is one role. No packages are cut, so the module package
directly under `commonMain` is the whole scope. `Destination` is the list of destinations
and `Navigator` holds the current location as a `StateFlow`.

It does not depend on Compose. The build script does not apply the Compose plugin, so a
`@Composable` written here would not compile. How to show the destinations (how the
navigation bar is laid out) is the job of `AppRoot` in `:app:android`; this role holds
only "what places exist" and "where we are now".

Keeping its own navigation instead of adding a navigation library is intentional. A
library would add one more place, the graph definition, and blur the "what goes where"
that this sample wants to show.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:navigation` | `src/commonMain/kotlin/**/*.kt` |  |

## Examples

- `Destination` ... The list of destinations
- `Navigator` ... The type that holds the current destination

## Allowed contents

- The type of destinations, their list (`Destination.topLevel`) and lookup from a route string
- Holding the current location and a way to change it

## Forbidden contents

- `@Composable` and the screens themselves
- Dependencies on feature modules. The dependency points the other way: the Route of each
  feature reads `:navigation`. If this module knew the features, it would grow with every
  screen added

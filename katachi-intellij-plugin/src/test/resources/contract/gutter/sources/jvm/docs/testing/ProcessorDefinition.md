[Ktor sample app](../README.md) / [Testing](README.md)

# Processor definition

A custom processor that reads the definition, registered in `architecture-test/build.gradle.kts`

The code behind a `katachi<Key>` task that reads the definition and produces something
from it. One processor per file in `processors/`, registered in the `katachi { processors { } }`
block of `architecture-test/build.gradle.kts`.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/processors/*.kt` |  |

## Examples

- `processors/RoleNames.kt` ... A processor that lists role names by a prefix

## Forbidden contents

- A group or a role. Those go in `groups/` and `roles/`

# sample/jvm

## What this sample is

A sample that defines, with katachi, **a small single-module HTTP server** written in Ktor.

It declares as roles what goes in each of the API, domain and data layers, and checks the placement of files with `assert()`.
One role also has a constraint written with `konsist { }`: "Must be public".
The entry of the definition itself also has a `konsist { }` constraint: it declares no group or role.
The same definition generates the documentation in [`docs/`](docs/README.md), and there is also one custom processor that reads the definition.

katachi is pulled in from the repository source with `includeBuild("../..")`, but it is written the same way a user would write it:
`testImplementation(libs.katachi)`.

## Key files

| File                                                                                                 | What it shows                                                                                                   |
|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt)         | The body of `architecture { }`. It only calls the 7 groups; the roles live in `roles/`, one role per file       |
| [`roles/ServiceRole.kt`](architecture-test/src/test/kotlin/com/example/roles/ServiceRole.kt)             | A role that writes a constraint with `konsist { }` in addition to `layout { }`. It doubles as an example of generating `*Service.kt` with the `.template { }` attached to a file declaration |
| [`roles/ControllerRole.kt`](architecture-test/src/test/kotlin/com/example/roles/ControllerRole.kt)       | An example of naming a directory's `*` with `capture("resource")` and reading it back from `.template { }` with `captureValue("resource")`. The value is used as-is in a package, so it is rejected with `require(...)` |
| [`roles/ArchitectureDefinitionEntryRole.kt`](architecture-test/src/test/kotlin/com/example/roles/ArchitectureDefinitionEntryRole.kt) | The definition split into one role per kind of file: the entry, `groups/*Group.kt` ([`GroupDefinitionRole.kt`](architecture-test/src/test/kotlin/com/example/roles/GroupDefinitionRole.kt)) and `roles/*Role.kt` ([`RoleDefinitionRole.kt`](architecture-test/src/test/kotlin/com/example/roles/RoleDefinitionRole.kt)). The entry also carries a `konsist { }` constraint |
| [`ProjectArchitectureTest.kt`](architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt) | The only test a user writes. It calls `assert(FileConstraintCheck())` so that `konsist { }` is evaluated too              |
| [`processors/RoleNames.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleNames.kt)       | A minimal example of a custom processor that receives arguments with `--arg`                                                                |
| [`katachi-baseline.json`](katachi-baseline.json) | The baseline ledger. It deliberately leaves two entries, `service/LegacyHealthCheck.kt` and `service/LegacyStatusService.kt`, held back ([`../README.md`](../README.md#baseline)) |

## How to run

No Android SDK is needed. It runs with JDK 17 alone.

```sh
cd sample/jvm

# Check file placement and the konsist { } constraints
./gradlew :architecture-test:test

# Generate the documentation into docs/ from the definition
./gradlew :architecture-test:katachiDocs

# Run the custom processor
./gradlew :architecture-test:katachiRoleNames --arg prefix=domain

# Generate src/main/kotlin/com/example/service/GreetingService.kt from the Service template
./gradlew :architecture-test:katachiTemplate --arg template=domain.Service --arg name=Greeting

# Generate src/main/kotlin/com/example/controller/greeting/GreetingController.kt from the Controller template
# (resource is the level named with capture("resource") in the layout; it selects which directory to generate into)
./gradlew :architecture-test:katachiTemplate --arg template=Controller --arg resource=greeting --arg name=Greeting
```

From the repository root, the same set as CI (including generating from templates, checking, and deleting the generated files) runs with one command.

```sh
./gradlew checkSampleJvm
```

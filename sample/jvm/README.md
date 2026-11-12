# sample/jvm

## What this sample is

A sample that defines, with katachi, **a small single-module HTTP server** written in Ktor.

It declares as roles what goes in each of the API, domain and data layers, and checks the placement of files with `assert()`.
The roles also say what their Kotlin files may hold with `ktFileContent { }`, for example that a Service is a public class.
The entry of the definition itself also has a `ktFileContent { }` constraint: it declares no group or role.
The same definition generates the documentation in [`docs/`](docs/README.md), and there is also one custom processor that reads the definition.

katachi is pulled in from the repository source with `includeBuild("../..")`, but it is written the same way a user would write it:
`testImplementation(libs.katachi)`.

## Key files

| File                                                                                                 | What it shows                                                                                                   |
|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt)         | The body of `architecture { }`. It only calls the 7 groups (the `Endpoint` template set is declared inside the Api group); each group lives in its own directory with the roles it calls, one declaration per file       |
| [`domain/ServiceRole.kt`](architecture-test/src/test/kotlin/com/example/domain/ServiceRole.kt)             | A role that writes what its file may hold with `ktFileContent { }` in addition to `layout { }`: one public class named after the file, importing models and Repositories only. It doubles as an example of generating `*Service.kt` with the `.template { }` attached to a file declaration |
| [`api/ControllerRole.kt`](architecture-test/src/test/kotlin/com/example/api/ControllerRole.kt)       | An example of naming a directory's `*` with `capture("resource")`, deriving the file name from it (`${capture("resource").pascalCase}Controller`) and reading it back from `.template { }` with `captureValue("resource")`. The value is used as-is in a package, so it is rejected with `require(...)`. In the IDE plugin, the `resource` field offers the directories that exist now (`health`) as candidates. `controller/health/LegacyStatusController.kt` deliberately breaks the derivation and is held back by the baseline as a `[CaptureMismatch]` |
| [`api/ApiGroup.kt`](architecture-test/src/test/kotlin/com/example/api/ApiGroup.kt) | `"Endpoint".templateSet { }`, declared inside `apiGroup()` next to the roles it generates: a Controller and a Service generated together from one `resource` (`--arg template=Api.Endpoint`), each member given its own value by `arg(...)` |
| [`testing/ArchitectureDefinitionEntryRole.kt`](architecture-test/src/test/kotlin/com/example/testing/ArchitectureDefinitionEntryRole.kt) | The definition split into one role per kind of file: the entry, `<group>/*Group.kt` ([`GroupDefinitionRole.kt`](architecture-test/src/test/kotlin/com/example/testing/GroupDefinitionRole.kt)) and `<group>/*Role.kt` ([`RoleDefinitionRole.kt`](architecture-test/src/test/kotlin/com/example/testing/RoleDefinitionRole.kt)). The entry also carries a `ktFileContent { }` constraint |
| [`ProjectArchitectureTest.kt`](architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt) | The only test a user writes. It calls `assert(FileConstraintCheck())` so that `ktFileContent { }` is evaluated too              |
| [`processors/RoleNames.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleNames.kt)       | A minimal example of a custom processor that receives arguments with `--arg`                                                                |
| [`katachi-baseline.json`](katachi-baseline.json) | The baseline ledger. It deliberately leaves three entries, `service/LegacyHealthCheck.kt`, `service/LegacyStatusService.kt` and `controller/health/LegacyStatusController.kt`, held back ([`../README.md`](../README.md#baseline)) |

## How to run

No Android SDK is needed. It runs with JDK 17 alone.

```sh
cd sample/jvm

# Check file placement and the ktFileContent { } constraints
./gradlew :architecture-test:test

# Generate the documentation into docs/ from the definition
./gradlew :architecture-test:katachiDocs

# Run the custom processor
./gradlew :architecture-test:katachiRoleNames --arg prefix=Domain

# Generate src/main/kotlin/com/example/service/GreetingService.kt from the Service template
./gradlew :architecture-test:katachiTemplate --arg template=Domain.Service --arg name=Greeting

# Generate src/main/kotlin/com/example/controller/greeting/GreetingController.kt from the Controller template
# (resource is the level named with capture("resource") in the layout; it selects which directory to generate into,
# and the file name GreetingController.kt is derived from it)
./gradlew :architecture-test:katachiTemplate --arg template=Controller --arg resource=greeting

# Generate controller/greeting/GreetingController.kt and service/GreetingService.kt together from the Endpoint template set
# (the set takes one `resource`; the Service gets it capitalised as its `name`)
./gradlew :architecture-test:katachiTemplate --arg template=Api.Endpoint --arg resource=greeting
```

From the repository root, the same set as CI (including generating from templates, checking, and deleting the generated files, plus checking that the baseline is up to date) runs with one command.

```sh
./gradlew checkSampleJvm
```

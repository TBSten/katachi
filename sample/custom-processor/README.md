# sample/custom-processor

## What this sample is

A sample that shows **only how to write your own ArchitectureProcessor**.

The other three samples show how to define a project with katachi. This one shows how to write a processor that reads a definition and does something with it.
The application itself is therefore just a small thing placed there for the processors to read. What is worth reading is the three files in
`architecture-test/src/test/kotlin/com/example/processors/` and their registration.

## Key files

| File | What it shows |
|------|---------------|
| [`processors/RoleFileCount.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleFileCount.kt) | The simplest processor, taking no arguments. Counts the files of each role |
| [`processors/RoleTable.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleTable.kt) | A processor that receives typed arguments (`String` / `List` / `Int` / `enum`) through `--arg` |
| [`processors/RoleDocCoverage.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleDocCoverage.kt) | A checking processor. Fails the run when a role has no `summary` or `example` |
| [`architecture-test/build.gradle.kts`](architecture-test/build.gradle.kts) | The registration of the three, and the module's default argument `arg("sortBy", "Name")` |

## How to run

No Android SDK is needed. JDK 17 is enough.

```sh
cd sample/custom-processor

# Check the file layout
./gradlew :architecture-test:test

# Run the processors of your own one at a time
./gradlew :architecture-test:katachiRoleFileCount
./gradlew :architecture-test:katachiRoleTable --arg groups=core,testing --arg sortBy=Declaration
./gradlew :architecture-test:katachiRoleDocCoverage
```

`--arg sortBy=Declaration` takes precedence over the default `Name` written in `build.gradle.kts`.

From the repository root, the same set as CI runs with one command.

```sh
./gradlew checkSampleCustomProcessor
```

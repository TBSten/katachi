# sample/kmp

## What this sample is

A sample that defines a **Kotlin Multiplatform app** (Android and iOS) with katachi.

It declares as roles what goes into each of the `:feature:*`, `:ui`, `:data`, `:navigation`, `:testing` and `:app:android` modules,
and checks the file layout with `assert()`. The highlight is how to write **things whose location differs per source set**,
such as `expect` in `commonMain` and `actual` in `androidMain` / `iosMain`. The same definition also generates the documents in [`docs/`](docs/README.md).

katachi is pulled in from the repository source with `includeBuild("../..")`, but it is written the same way a user would,
with `testImplementation(libs.katachi)`.

## Key files

| File | What it shows |
|---|---|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/kmp/ProjectArchitecture.kt) | The body of `architecture { }`. It only calls the groups; each role is in `roles/`, one role per file |
| [`roles/PlatformImplementationRole.kt`](architecture-test/src/test/kotlin/com/example/kmp/roles/PlatformImplementationRole.kt) | A role that declares `expect` / `actual` per source set |
| [`roles/RepositoryRole.kt`](architecture-test/src/test/kotlin/com/example/kmp/roles/RepositoryRole.kt) | A role whose two file declarations each carry `.template(id = ...)`, so the interface and the implementation can be generated separately (or together with `--arg template=a,b`) |
| [`roles/FeatureComponentRole.kt`](architecture-test/src/test/kotlin/com/example/kmp/roles/FeatureComponentRole.kt) | An example of `":feature:${capture("feature")}".module { }`. The same capture decides the location with `wildcard("feature")` and the template content with `captureValue("feature")` |
| [`ProjectArchitectureTest.kt`](architecture-test/src/test/kotlin/com/example/kmp/ProjectArchitectureTest.kt) | The only test a user writes. It just calls `projectArchitecture.assert()` |
| [`katachi-baseline.json`](katachi-baseline.json) | The baseline ledger. It deliberately leaves one entry, `user/` in the `androidMain` of `:data`, shelved ([`../README.md`](../README.md#baseline)) |

## How to run

**The Android SDK is required.** Set `ANDROID_HOME`, or write `sdk.dir` in `local.properties`.

```sh
# at the repository root
echo "sdk.dir=$HOME/Library/Android/sdk" > sample/kmp/local.properties
```

```sh
cd sample/kmp

# Check the file layout
./gradlew :architecture-test:test

# Generate the documents into docs/ from the definition
./gradlew :architecture-test:katachiDocs

# Generate ProfileRepository.kt and ProfileRepositoryImpl.kt into the user package of :data from the Repository template
# (give the two ids together, comma-separated; pass only data.Repository.repository for just one)
./gradlew :architecture-test:katachiTemplate --arg template=data.Repository.repository,data.Repository.repositoryImpl --arg name=Profile

# Generate SettingsToggleRow.kt into the component package of :feature:settings from the FeatureComponent template
# (feature is the name given to :feature:* in the layout; it picks the module to generate into)
./gradlew :architecture-test:katachiTemplate --arg template=feature.FeatureComponent --arg feature=settings --arg name=ToggleRow
```

**Write the task with the module path**, as in `:architecture-test:test`. Writing just `test`
also targets the tasks of the iOS modules, which needs macOS and Xcode.

From the repository root, one command runs the same set as CI (including generating from templates, checking, and deleting the generated files).

```sh
./gradlew checkSampleKmp
```

## The default tasks of `checkSampleKmp`

Unlike the other samples, the default task of `checkSampleKmp` is not `check`. `check` is not used because
the KMP modules declare iOS targets, and `check` would pull `compileKotlinIosArm64` and the download of
the Kotlin/Native toolchain into the task graph.

Instead it runs both `:architecture-test:test` (verifying katachi) and `:app:android:testDebugUnitTest` (verifying
that the sample compiles as a KMP project). `:architecture-test` is a plain JVM module that references
none of `:ui` / `:data` / `:feature:*`, so one of the two alone is not enough. These are followed by
`:architecture-test:katachiLayout` (the layout check via the processor) and
`:architecture-test:katachiDocs --arg mode=check` (whether the generated documents are up to date). The layout
snapshot (`snapshots/layout.txt`) is checked by `LayoutSnapshotSpec` inside `:architecture-test:test`. For the exact default values, see `sampleBuilds` in the root
`build.gradle.kts`.

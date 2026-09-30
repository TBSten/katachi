# sample/android

## What this sample is

A sample that defines a **multi-module Android app** written in Jetpack Compose with katachi.

What goes in each of the modules `:feature:*`, `:ui`, `:data`, `:navigation`, `:testing` and `:app` is declared as roles,
and `assert()` checks where the files are placed. The same definition also generates the documentation in [`docs/`](docs/README.md).

katachi is pulled in from the repository source with `includeBuild("../..")`, but it is written the same way a user would write it:
`testImplementation(libs.katachi)`.

## Key files

| File                                                                                                        | What it shows                                                                                              |
|-----------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/sample/ProjectArchitecture.kt)         | The body of `architecture { }`. It only calls the groups; roles are kept one per file in `roles/` |
| [`roles/ComponentRole.kt`](architecture-test/src/test/kotlin/com/example/sample/roles/ComponentRole.kt)         | How to write one role. A `.template { }` whose whole file name is `capture("name")`, generating a file with exactly the name passed |
| [`roles/FeatureComponentRole.kt`](architecture-test/src/test/kotlin/com/example/sample/roles/FeatureComponentRole.kt) | An example of `":feature:${capture("feature")}".module { }`. The same capture decides the location with `wildcard("feature")` and the template content with `captureValue("feature")` |
| [`ProjectArchitectureTest.kt`](architecture-test/src/test/kotlin/com/example/sample/ProjectArchitectureTest.kt) | The only test a user writes. It just calls `projectArchitecture.assert()` |
| [`docs/README.md`](docs/README.md)                                                                              | Documentation generated from the definition. Not written by hand |
| [`katachi-baseline.json`](katachi-baseline.json) | The baseline ledger. Two violations, `HomeFormatter.kt` and `legacy/` in `:data`, are deliberately left and held back ([`../README.md`](../README.md#baseline)) |

## How to run

**The Android SDK is required.** Set `ANDROID_HOME`, or write `sdk.dir` in `local.properties`.

```sh
# From the repository root
echo "sdk.dir=$HOME/Library/Android/sdk" > sample/android/local.properties
```

```sh
cd sample/android

# Check where files are placed
./gradlew :architecture-test:test

# Generate the documentation into docs/ from the definition
./gradlew :architecture-test:katachiDocs

# Generate AppLabel.kt in the component package of :ui from the Component template
# (The whole file name is capture("name"), so pass --arg name=AppLabel if you want "App")
./gradlew :architecture-test:katachiTemplate --arg template=Component --arg name=AppLabel

# Generate HomeUserCard.kt in the component package of :feature:home from the FeatureComponent template
# (feature is the name the layout gave :feature:*. It selects which module to generate into)
./gradlew :architecture-test:katachiTemplate --arg template=feature.FeatureComponent --arg feature=home --arg name=UserCard
```

From the repository root, one command runs the same set as CI (including generating from templates, checking, and deleting the generated files, plus checking that the baseline is up to date).

```sh
./gradlew checkSampleAndroid
```

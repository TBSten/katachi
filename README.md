# katachi

**Declare your Android/KMP project architecture in a Kotlin DSL, and get both a test and documentation out of the same definition.**

English | [日本語](./README.ja.md)

v0.1 ships the first half: **deny by default architecture testing.** You declare where each kind of file may live, in one place. Files no declaration covers come back as `Unexpected`, declarations with nothing behind them as `Missing`. Documentation generation lands in v0.2.

**Docs: https://tbsten.github.io/katachi/**

> [!NOTE]
> While the major version is 0, releases may contain breaking changes.

## Install

**The steps are the same whether your project is JVM, Android or KMP.**

1. **Create one plain JVM module**, `:architecture-test` or similar. Use `kotlin("jvm")` even in an Android or KMP project — katachi is a JVM library.
2. Add `testImplementation`.
3. Write `architecture { }`.
4. Write one test.

```kotlin
// settings.gradle.kts
include(":architecture-test")
```

```kotlin
// architecture-test/build.gradle.kts
plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

tasks.test { useJUnitPlatform() }

dependencies {
    testImplementation("me.tbsten.katachi:katachi:0.1.1")
    // Optional. Only needed if you write `konsist { }`.
    testImplementation("me.tbsten.katachi:katachi-konsist:0.1.1")

    // katachi only throws an AssertionError; it depends on no test framework,
    // so you pick the engine.
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

Requires **JDK 17 or later** and **Kotlin 2.2 or later.** The published artifacts are built with `languageVersion` 2.2 so that a 2.2 compiler can read their metadata; anything older sees every symbol as `Unresolved reference`.

> [!IMPORTANT]
> **Before Kotlin 2.4, add `-Xcontext-parameters`.** Every entry point of the DSL (`module`,
> `mainSourceSet`, `ktFile`, `konsist` …) is a contextual declaration, so without it you cannot
> write a single one.
>
> ```kotlin
> kotlin {
>     jvmToolchain(17)
>     compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
> }
> ```
>
> **From Kotlin 2.4 on, leave it out.** The feature is in the language, and passing the flag
> warns that it is redundant — which fails a build using `allWarningsAsErrors`.

> [!IMPORTANT]
> **Leave out `junit-platform-launcher` and the tests never start.** The `junit-jupiter`
> aggregate brings the API, params and engine, but not the launcher, and Gradle 9 no longer
> adds it for you. You get `Failed to load JUnit Platform`.
>
> **Leave out the engine and the test does not fail — it never runs.** `useJUnitPlatform()`
> alone leaves nothing on the classpath that picks up `@Test`, so the build goes green while
> the architecture check quietly does nothing. Check that `tests=` in
> `build/test-results/**/*.xml` is not 0.

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt
class ProjectArchitectureTest {
    @Test fun `the project matches its declaration`() = projectArchitecture.assert()
}
```

The definition describes the project as a whole, so **it belongs to no layer.** That is why it gets a module of its own instead of squatting in the root test source set or in `:app`. In a KMP project there is nowhere to squat anyway — katachi is JVM only, so it cannot live in `commonTest`.

The cost is that this module ends up on the allow list too. By katachi's own principle — a file with no role does not exist — that is exactly where it belongs.

## What it reads like

```kotlin
// in the test source set of :architecture-test
val projectArchitecture = architecture {
    "domain".group {
        title = "Domain"
        "UseCase" {
            title = "Use case"
            summary = "A single app-specific behavior that happens on a screen"
            example("GetUserUseCase", "Fetches a user")
            layout {
                "domain" / "src" / "main" / "kotlin" / "com" / "example" / "useCase" / "*UseCase".ktFile()
            }
        }
    }
}
```

- No Gradle plugin. Add a `testImplementation` and you are done.
- Works with JUnit 4, JUnit 5 or kotest — `assert()` only throws an `AssertionError`.
- When the definition grows, split it into extension functions on `ArchitectureScope`. The declaration site a violation points at is **the file that declared it**, not the call site. All three samples are written this way and prove it in a test.
  - Do not make those functions `inline`. With `inline`, the declaration site points past the end of the calling file, at a line that does not exist.

## Writing `layout`

Directly inside `layout { }` is the repository root. A string with a block is a directory; `.file()`, `.ktFile()` (appends `.kt`) and `.ktsFile()` (appends `.kts`) make it a file. Nested blocks and `/` chaining mean the same thing, and a key may span several levels like `"src/main/kotlin"`.

```kotlin
layout {
    ".gitignore".file()                  // a file at the repository root
    "app" {                              // a directory
        description = "The app entry point"   // tells roles apart when several share a package
        "src/main/kotlin/com/example" {
            "MainActivity".ktFile()      // MainActivity.kt
            "*ViewModel".ktFile()        // wildcard
        }
        "res" { ignore() }               // nothing below this is checked, however deep
        "generated" { anyFile() }        // any file directly inside; subdirectories are not allowed
    }
    "build".ignore()                     // same as "build" { ignore() }
}
```

- **An empty directory block means "nothing may go here."** A file under `"di" { }` is `Unexpected`.
- **A declaration with nothing behind it is `Missing`.** Add `.optional()` to allow that.
- **A declaration containing a wildcard is optional automatically.** Zero matches is not `Missing`.
- `anyFile()` covers **only the immediate children.** Files inside subdirectories stay `Unexpected`.
- `ignore()` exists only inside `layout { }`. The reason you decided not to check something stays in the role's `summary` and reaches the generated documentation. There is no global exclude setting.

### Globs

katachi has **exactly two wildcards, `*` and `**`.** `{a,b}`, `?` and `[abc]` are not wildcards and are an error if you write them. Escape with a backslash (`\*`) to mean the literal character.

| Syntax | Meaning |
|---|---|
| `*` | Exactly one level. `:feature:*` matches `:feature:home`, but neither `:feature` nor `:feature:home:impl` |
| `**` | Zero or more levels. `:feature:**` matches `:feature`, `:feature:home` and `:feature:home:impl` |
| `*` inside a file name | Matches part of the name and **never crosses a directory boundary.** `*UseCase.kt` matches `GetUserUseCase.kt`, but not a file of the same name in a subdirectory |

- `*` **does not match zero characters** — `*UseCase.kt` does not match `UseCase.kt`.
- Matching is **always case sensitive.**
- `*` and `**` mean the same thing in directory paths and in module paths (`:feature:home`).
- **Do not write `**` alone in a file position.** `"src/test/kotlin/**".file()` only makes `src/test/kotlin` a known directory, so `src/test/kotlin/com` becomes `[UnexpectedDirectory]`. Write `"src/test/kotlin" / "**" / "*".ktFile()`.
- **Do not start a path with `**`.** `"**/build".ignore()` registers `**` itself — any path — as a known directory, and `[UnexpectedDirectory]` stops firing entirely.

## Which files get checked

By default, **only what git calls a file of this project** (`git ls-files --cached --others --exclude-standard`: tracked, plus untracked but not ignored). You do not have to give `build/`, `.DS_Store` or `local.properties` a role.

```kotlin
architecture {
    files = gitTracked()      // the default; you do not have to write it
    // files = wholeTree()    // ignore git and walk the file tree as it is
}
```

- The library has zero runtime dependencies, but **by default it runs the `git` command** at the project root (`rev-parse --is-inside-work-tree` to decide, then one `ls-files`).
- The project root is found the same way Konsist finds it: walk up from the working directory and stop at the first directory carrying **any** of `gradlew`, `mvnw` or `.git`.
- **Whether `gitTracked()` applies is a question for git** (`git rev-parse --is-inside-work-tree`), not "is there a `.git` directly at the root". A Gradle project **inside a subdirectory of a repository** — a monorepo with `repo/.git` and `repo/app/gradlew`, a submodule, the `sample/` builds in this repository — still gets the git filter, because running `git ls-files` at the root returns that subtree's files relative to the root.
- If git says you are inside a work tree and `git ls-files` then fails, that is an error. Falling back to a full walk silently would make local and CI disagree. If the project is not under git at all, or `git` is not installed, it walks as `wholeTree()`.
- `.git/`, `.gradle/` and `.idea/` are never checked, at any depth, whatever `files` says.
- `gitTracked()` and `wholeTree()` are top-level functions in `me.tbsten.katachi.dsl` taking `ArchitectureScope` as a context parameter. They can only be written inside `architecture { }`, and you can add your own the same way.
- **`FileSelection` is yours to implement.** If something other than git owns the file list — Bazel, a generated manifest, an in-house tool — implement `FileSelection` and hand it to `files`.

## Constraining what a file declares

Placement is only half of it. `konsist { }` puts constraints on the contents of a file, in the same definition, using [Konsist](https://docs.konsist.lemonappdev.com/)'s API.

```kotlin
"UseCase" {
    layout {
        "domain" / "src/main/kotlin/com/example/useCase" / "*UseCase".ktFile()
    }

    "has exactly one invoke function".konsist {
        classes().must { it.functions().map { f -> f.name } == listOf("invoke") }
    }
}
```

The name you give the constraint is what shows up in the violation report, so write it as the thing that must hold. Both kinds of violation — placement and contents — arrive in a single message.

## Modules

| Module | What it is |
|---|---|
| `:katachi` | The library. **Zero runtime dependencies, JVM only.** `me.tbsten.katachi:katachi` |
| `:katachi-konsist` | Optional, for `konsist { }`. `me.tbsten.katachi:katachi-konsist` |
| `:architecture-test` | katachi's own architecture definition. **Not published** |

The root project exists **only to aggregate the samples** and carries no plugins and no sources, so that `./gradlew check` keeps working on a machine with neither an Android SDK nor a Kotlin/Native toolchain.

## Samples

Three of them live under `sample/`. Each is a **standalone Gradle build** with its own `settings.gradle.kts` and wrapper, pulling katachi from this repository's sources through `includeBuild("../..")`. They consume it exactly the way you would (`testImplementation(libs.katachi)`), so they double as integration tests.

| Sample | What it is | Task from the root |
|---|---|---|
| `sample/jvm` | A minimal Ktor server | `./gradlew checkSampleJvm` |
| `sample/android` | A multi-module Android app, with real Compose and AndroidX dependencies | `./gradlew checkSampleAndroid` |
| `sample/kmp` | An Android + iOS KMP project, with real Compose Multiplatform dependencies | `./gradlew checkSampleKmp` |

They are **close to the real thing, not stubs.** `Screen` is a real `@Composable`, `ViewModel` extends the real `androidx.lifecycle.ViewModel`, `@Preview` is actually written. Checking something that does not look like a real project would not tell us katachi works on one.

```bash
./gradlew check         # the library itself
./gradlew checkSamples  # all three samples, through their own wrappers
```

`checkSamples` runs the three **in sequence** — they share one katachi build, and running them in parallel corrupts katachi's `build/`.

`sample/android` and `sample/kmp` need an Android SDK: set `ANDROID_HOME`, or write `sdk.dir=...` into that sample's `local.properties`.

## Roadmap

| Version | Theme |
|---|---|
| **v0.1** | The DSL, the check, and `konsist { }` |
| **v0.2** | Gradle plugin and documentation generation from the same definition |
| **v0.3** | `declarations { }` — declare what a file may declare, deny by default |
| **v0.4+** | baseline, report output, vocabulary metrics — adopting katachi in an existing codebase |

v0.1 targets **new projects.** Dropping katachi into an existing codebase surfaces every violation at once, and the mechanism for that (baseline) is deliberately last: ship it early and you get projects where katachi is installed and says nothing.

See https://tbsten.github.io/katachi/roadmap/ for the detail.

## Development

| | Version |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10 |
| JDK / toolchain | 17 |
| kotest | 6.2.5 |
| AGP (samples) | 9.1.0 — **do not raise it** |

`gradle/libs.versions.toml` is the single source of truth for the Kotlin, katachi and kotest versions. The samples read it as `libs` and keep only their own dependencies in their own catalog.

**AGP tracks what Android Studio supports, not the newest release.** Anything newer than 9.1.0 stops Android Studio's Gradle sync with `The project is using an incompatible version (AGP x.y.z) of the Android Gradle plugin.` Raising it because the CLI build is green makes the project impossible to open in the IDE.

CI is `.github/workflows/ci.yml`: the library and each sample in their own step, on every push to `main` and every pull request.

## License

MIT. See [LICENSE](./LICENSE).

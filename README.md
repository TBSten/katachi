# katachi

**Declare where everything in your project may live, once, in a Kotlin DSL — and get an architecture test, documentation and code scaffolding out of that one definition.** The setup is the same for JVM, Android and KMP projects.

English | [日本語](./README.ja.md)

**Docs: https://tbsten.github.io/katachi/** (日本語: https://tbsten.github.io/katachi/ja/)

> [!NOTE]
> While the major version is 0, releases may contain breaking changes. To upgrade from v0.1, see v0.2.0 in the [release notes](https://github.com/TBSten/katachi/releases).

## One definition gives you a test, documentation and code

For each role (UseCase, Repository and so on), you write where it lives and what it is.

```kotlin
"UseCase" {
    title = "Use case"
    summary = "A single app-specific behavior that happens on a screen"
    layout { "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile() }
    template { /* the skeleton of a new use case */ }
}
```

Out of that definition come three things.

- **A test.** `projectArchitecture.assert()` fails on files no declaration covers (`Unexpected`) and on declarations with nothing behind them (`Missing`). You declare only what may exist — **deny by default** — so there is no list of prohibitions to keep up to date, and no new hiding place for files appears unnoticed.
- **Documentation.** `./gradlew katachiDocs` writes Markdown pages for your groups and roles. With `--arg mode=check`, CI tells you when the committed docs are out of date.
- **Code generation.** `./gradlew katachiTemplate --arg roleName=UseCase --arg name=GetUser` writes a new file where the role's `layout { }` says it belongs.

Beyond where files live, `konsist { }` lets the same definition constrain what they contain ([Konsist integration](https://tbsten.github.io/katachi/guides/konsist-integration/)).

## Install

Create one JVM module that holds only the architecture definition and its test, and add katachi to it. Make it a plain `kotlin("jvm")` module even in an Android or KMP project — katachi is a JVM library.

**Requirements:** JDK 17 or later, Kotlin 2.2 or later, and Gradle 8.0 or later if you use the Gradle plugin.

### 1. Add the module, and Maven Central as a plugin repository

katachi's Gradle plugin is published to Maven Central, not to the Gradle Plugin Portal. Without `mavenCentral()` in `pluginManagement { }`, Gradle cannot find it.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":architecture-test")
```

### 2. Add the plugin and the dependencies

```kotlin
// architecture-test/build.gradle.kts
plugins {
    // If the root build.gradle.kts already declares it with `apply false`, leave the version out here
    kotlin("jvm") version "2.4.10"
    id("me.tbsten.katachi") version "0.2.0"
}

kotlin { jvmToolchain(17) }

tasks.test { useJUnitPlatform() }

dependencies {
    testImplementation("me.tbsten.katachi:katachi:0.2.0")
    // Optional. Only needed if you write `konsist { }`.
    testImplementation("me.tbsten.katachi:katachi-konsist:0.2.0")

    // katachi only throws an AssertionError and depends on no test framework,
    // so you pick the engine (JUnit 5 here).
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

katachi {
    // The fully qualified name of the top-level val you write in step 3
    architecture = "com.example.projectArchitecture"
}
```

The test alone does not need the plugin. You need it for tasks such as `katachiDocs`.

### 3. Define your architecture

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt
package com.example

import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.kotlin.ktFile

val projectArchitecture = architecture {
    // Declares the Gradle files: the wrapper, settings, every module's build script, version catalogs
    gradle()

    "domain".group {
        title = "Domain"
        "UseCase" {
            title = "Use case"
            summary = "A single app-specific behavior that happens on a screen"
            example("GetUserUseCase", "Fetches a user")
            layout {
                "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile()
            }
            template {
                val name by stringParameter()
                file("${name}UseCase.kt") {
                    "package com.example.useCase\n\nclass ${name}UseCase\n"
                }
            }
        }
    }
}
```

### 4. Write one test and run it

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt
package com.example

import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

class ProjectArchitectureTest {
    @Test
    fun `the project matches its definition`() = projectArchitecture.assert()
}
```

```shell
./gradlew :architecture-test:test
```

**At first, every file you have not declared comes back as a violation.** katachi works from an allow list, so that is the starting point: for each path it reports, either add it to the definition or delete the file. Violations across the whole repository arrive in a single failure message, so this one test is the only one you write.

If you write `konsist { }`, `assert()` with no arguments does not evaluate the constraints, and the test fails with `[UncheckedFileConstraint]`. To check them together with the layout, write:

```kotlin
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureTest {
    @Test
    fun `the project matches its definition`() = projectArchitecture.assert(FileConstraintCheck())
}
```

### 5. Generate documentation and code

```shell
# Writes Markdown to build/katachi/docs
./gradlew :architecture-test:katachiDocs

# Creates domain/src/main/kotlin/com/example/useCase/GetUserUseCase.kt
./gradlew :architecture-test:katachiTemplate --arg roleName=UseCase --arg name=GetUser
```

How to change the output directory, check for stale docs in CI and pass template arguments is covered in [Document generation](https://tbsten.github.io/katachi/guides/document-generation/) and [Generating code from a template](https://tbsten.github.io/katachi/guides/generate-code-from-template/).

You can also hand the setup to an AI agent. See [Your first architecture definition](https://tbsten.github.io/katachi/get-started/first-architecture/).

### If the test does not run

<details>
<summary><b>Before Kotlin 2.4, you need <code>-Xcontext-parameters</code></b></summary>

Every entry point of the DSL (`module`, `mainSourceSet`, `ktFile`, `konsist` …) is declared with context parameters. Without the flag you cannot write a single one.

```kotlin
kotlin {
    jvmToolchain(17)
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}
```

From Kotlin 2.4 on, leave it out. The feature is part of the language, and passing the flag warns that it is redundant — which fails a build using `allWarningsAsErrors`.

With a Kotlin older than 2.2, every symbol is an `Unresolved reference`. The published artifacts are built with `languageVersion` 2.2.

</details>

<details>
<summary><b>Without the JUnit launcher and engine, tests do not start, or never run</b></summary>

- **Without `junit-platform-launcher`, tests do not start.** The `junit-jupiter` aggregate brings the API, params and engine, but not the launcher, and Gradle 9 does not add it for you. You get `Failed to load JUnit Platform`.
- **Without an engine, the test does not fail — it never runs.** `useJUnitPlatform()` alone leaves nothing on the classpath that picks up `@Test`, so the build goes green while the check does nothing. Make sure `tests=` in `build/test-results/**/*.xml` is not 0.
- With kotest, `kotest-runner-junit5` brings its own engine, so that is enough. But it only brings `junit-jupiter-api`, so if you mix in plain `@Test`, you also need `junit-jupiter` (engine included).

</details>

<details>
<summary><b>Why a module of its own, instead of an existing module's tests</b></summary>

The definition describes the project as a whole, so it belongs to no layer. That is why it does not squat in `:app` or in the root test source set. In a KMP project there is nowhere to squat anyway — katachi is JVM only, so it cannot live in `commonTest`.

The cost is that this module ends up on the allow list too. By katachi's own principle — a file with no role does not exist — that is where it belongs.

</details>

## Writing the definition

It works with JUnit 4, JUnit 5 or kotest, because `assert()` only throws an `AssertionError`.

When the definition grows, split it into extension functions on `DeclarationContainerScope` (callable inside both `architecture { }` and `"...".group { }`). The declaration site a violation points at is the file that wrote the declaration, not the call site. The jvm, android and kmp samples are written this way and check it in a test. Do not make those functions `inline`: with `inline`, the declaration site points past the end of the calling file, at a line that does not exist.

The details of roles, groups and `layout { }` are on the docs site: [Basic API](https://tbsten.github.io/katachi/guides/basic-api/), [Role](https://tbsten.github.io/katachi/guides/role/) and [Layout](https://tbsten.github.io/katachi/guides/layout/). What follows are the finer rules that are not on the site yet.

### Writing `layout`

Directly inside `layout { }` is the repository root. A string with a block is a directory; `.file()`, `.ktFile()` (appends `.kt`) and `.ktsFile()` (appends `.kts`) make it a file. Nested blocks and `/` chaining mean the same thing, and a key may span several levels, like `"src/main/kotlin"`.

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
- `ignore()` exists only inside `layout { }`, so that the reason you decided not to check something stays in the role's `summary` and reaches the generated documentation. There is no global exclude setting.

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

### Which files get checked

By default, **only what git calls a file of this project** (`git ls-files --cached --others --exclude-standard`: tracked, plus untracked but not ignored). You do not have to give `build/`, `.DS_Store` or `local.properties` a role.

```kotlin
architecture {
    files = gitTracked()      // the default; you do not have to write it
    // files = wholeTree()    // ignore git and walk the file tree as it is
}
```

- **By default, katachi runs the `git` command** at the project root (`rev-parse --is-inside-work-tree` to decide, then one `ls-files`).
- The project root is found the same way Konsist finds it: walk up from the working directory and stop at the first directory carrying **any** of `gradlew`, `mvnw` or `.git`.
- **Whether `gitTracked()` applies is a question for git**, not "is there a `.git` directly at the root". A Gradle project inside a subdirectory of a repository — a monorepo with `repo/.git` and `repo/app/gradlew`, a submodule, a worktree, the `sample/` builds in this repository — still gets the git filter.
- If git says you are inside a work tree and `git ls-files` then fails, that is an error: falling back to a full walk silently would make local and CI disagree. If the project is not under git at all, or `git` is not installed, it walks as `wholeTree()`.
- `.git/`, `.gradle/` and `.idea/` are never checked, at any depth, whatever `files` says.
- `gitTracked()` and `wholeTree()` are top-level functions in `me.tbsten.katachi.dsl` taking `ArchitectureScope` as a context parameter. They can only be written inside `architecture { }`, and you can add your own the same way.
- **`FileSelection` is yours to implement.** If something other than git owns the file list — Bazel, a generated manifest, an in-house tool — implement `FileSelection` and hand it to `files`.

## Samples

Four of them live under `sample/`. Each one's own README covers what it looks like and how to run it; what the samples share — design decisions and build setup — is in [`sample/README.md`](sample/README.md).

| Sample | What it is |
|---|---|
| [`sample/jvm`](sample/jvm/README.md) | A minimal Ktor server |
| [`sample/android`](sample/android/README.md) | A multi-module Android app, with real Compose and AndroidX dependencies |
| [`sample/kmp`](sample/kmp/README.md) | An Android + iOS KMP project, with real Compose Multiplatform dependencies |
| [`sample/custom-processor`](sample/custom-processor/README.md) | A hands-on tour of writing your own processor |

The jvm, android and kmp samples use the plugin, `gradle()` and `template { }`, and commit the documentation `katachiDocs` generates for them.

## What is published

| Artifact | What it is |
|---|---|
| `me.tbsten.katachi:katachi` | The library: the DSL, the check, documentation generation and templates. **JVM only.** Its only runtime dependency is `kotlinx-serialization-core` |
| `me.tbsten.katachi:katachi-konsist` | Optional, for `konsist { }` |
| Gradle plugin `me.tbsten.katachi` | Adds one task per processor (`katachiDocs`, `katachiTemplate`, `katachiTemplates` …). Use it with `:katachi` of the same version |

## Development

| | Version |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10 |
| JDK / toolchain | 17 |
| kotest | 6.2.5 |

What is in the repository:

| Where | What it is |
|---|---|
| `katachi/`, `katachi-konsist/`, `katachi-gradle-plugin/` | The three published modules |
| `architecture-test/` | katachi's own architecture definition. Not published |
| `tool/dokka/` | The Dokka plugin behind the API reference. Not published |
| `sample/` | The samples, each a standalone Gradle build |
| `docs/` | The documentation site |

```shell
./gradlew check          # the library; needs neither an Android SDK nor Kotlin/Native
./gradlew checkSamples   # every sample, one after another, through its own wrapper
./gradlew checkSampleJvm # just one (the Android and KMP samples need an Android SDK)
```

- The samples are standalone builds, not subprojects of the root, so `./gradlew check` does not run them. The root project has no sources; it only aggregates the API reference and carries the tasks that run the samples.
- `gradle/libs.versions.toml` is the single source of truth for the Kotlin, katachi and kotest versions. The samples read it as `libs` and keep their own dependencies in their own catalog, `sampleLibs` (see [`sample/README.md`](sample/README.md)).
- CI is `.github/workflows/ci.yml`: the library and each of the four samples in their own step, on every push to `main` and every pull request.

## License

MIT. See [LICENSE](./LICENSE).

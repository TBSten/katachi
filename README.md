# katachi

[![Maven Central](https://img.shields.io/maven-central/v/me.tbsten.katachi/katachi)](https://central.sonatype.com/artifact/me.tbsten.katachi/katachi)
[![CI](https://github.com/TBSten/katachi/actions/workflows/ci.yml/badge.svg)](https://github.com/TBSten/katachi/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)

**Define "which file goes where" in Kotlin, and get an architecture test, documentation and code scaffolding out of that definition.**

English | [日本語](./README.ja.md) ・ [Docs](https://tbsten.github.io/katachi/)

> [!NOTE]
> While the major version is 0, releases may contain breaking changes.

## What katachi does

```kotlin
"UseCase" {
    summary = "A single app-specific behavior that happens on a screen"
    layout { "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile() }
    template { /* the skeleton of a new UseCase */ }
}
```

From this one definition, you get the following three things.

- **A test**: reports files that live somewhere the definition doesn't cover. Since only what you declare is allowed, the set of places files can end up doesn't quietly grow
- **Documentation**: `./gradlew katachiDocs` writes out Markdown for each role
- **Code scaffolding**: `./gradlew katachiTemplate --arg roleName=UseCase --arg name=GetUser` creates a file where the definition says it belongs

You can also write rules about a file's contents — being public, for example — with `konsist { }`. How this differs from Konsist, detekt and ArchUnit is in [Comparison with other tools](https://tbsten.github.io/katachi/get-started/comparison-with-other-tools/).

**Supported**: JVM, Android and KMP projects. Put the definition and its test in a JVM module. JDK 17+, Kotlin 2.2+, Gradle 8.0+.

## Getting started

Create one JVM module for the architecture definition.

```kotlin
// settings.gradle.kts — the plugin is published to Maven Central
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
include(":architecture-test")
```

```kotlin
// architecture-test/build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.10"
    id("me.tbsten.katachi") version "0.2.0"
}

kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }

dependencies {
    testImplementation("me.tbsten.katachi:katachi:0.2.0")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

katachi { architecture = "com.example.projectArchitecture" }
```

Write the definition and a test.

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt
val projectArchitecture = architecture {
    gradle() // Gradle files: wrapper, settings, build scripts, etc.
    "domain".group {
        "UseCase" {
            layout { "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile() }
        }
    }
}

// architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt
class ProjectArchitectureTest {
    @Test
    fun `the project matches its definition`() = projectArchitecture.assert()
}
```

```shell
./gradlew :architecture-test:test           # runs the check
./gradlew :architecture-test:katachiDocs    # writes docs to build/katachi/docs
```

At first, every file you haven't declared comes back as a violation. For each path that shows up, decide whether to add it to the definition or delete the file.

The full walkthrough — importing, templates, and handing it off to an AI agent — is in [Your first architecture definition](https://tbsten.github.io/katachi/get-started/first-architecture/).

<details>
<summary><b>If it doesn't work</b></summary>

- **Below Kotlin 2.4**: you need `compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")` (don't add it from 2.4 on — it becomes a warning)
- **`Failed to load JUnit Platform`**: you're missing `junit-platform-launcher`
- **No test runs at all**: you're missing a JUnit engine (`junit-jupiter`). The build stays `BUILD SUCCESSFUL` while the check silently does nothing
- **Writing `konsist { }` fails with `[UncheckedFileConstraint]`**: write `assert(FileConstraintCheck())` (needs `@OptIn(ExperimentalKatachiApi::class)`)

</details>

## Learn more

| What you want to do | Page |
|---|---|
| Learn how to write roles and groups | [Basic API](https://tbsten.github.io/katachi/guides/basic-api/), [Role](https://tbsten.github.io/katachi/guides/role/) |
| Get the details of writing layouts | [Layout](https://tbsten.github.io/katachi/guides/layout/) |
| Check file contents too | [Konsist integration](https://tbsten.github.io/katachi/guides/konsist-integration/) |
| Generate documentation and code | [Document generation](https://tbsten.github.io/katachi/guides/document-generation/), [Generating code from a template](https://tbsten.github.io/katachi/guides/generate-code-from-template/) |
| See a real project's definition | [Samples](CONTRIBUTING.md#samples), [Recipes](https://tbsten.github.io/katachi/recipes/) |

### Finer rules not yet on the docs site

<details>
<summary><b>Writing <code>layout { }</code></b></summary>

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

</details>

<details>
<summary><b>Globs (<code>*</code> and <code>**</code>)</b></summary>

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

</details>

<details>
<summary><b>Which files get checked</b></summary>

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

</details>

## What is published

| Artifact | What it is |
|---|---|
| `me.tbsten.katachi:katachi` | The main library (JVM) |
| `me.tbsten.katachi:katachi-konsist` | Only if you use `konsist { }` |
| Gradle plugin `me.tbsten.katachi` | Tasks such as `katachiDocs` and `katachiTemplate`. Pair it with the same version of the library |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](./LICENSE)

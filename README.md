# katachi

[![Maven Central](https://img.shields.io/maven-central/v/me.tbsten.katachi/katachi)](https://central.sonatype.com/artifact/me.tbsten.katachi/katachi)
[![CI](https://github.com/TBSten/katachi/actions/workflows/ci.yml/badge.svg)](https://github.com/TBSten/katachi/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)

**Define "which file goes where" in Kotlin, and get an architecture test, documentation and code scaffolding out of that definition.**

English | [日本語](./README.ja.md) ・ [Docs](https://tbsten.github.io/katachi/)

## What katachi does

```kotlin
"UseCase" {
    summary = "A single app-specific behavior that happens on a screen"
    layout {
        "domain/src/main/kotlin/com/example/useCase" / "${capture("name")}UseCase".ktFile()
            .template { /* the skeleton of a new UseCase, filled in with captureValue("name") */ }
    }
}
```

From this one definition, you get the following three things.

- **A test**: reports files that live somewhere the definition doesn't cover. Since only what you declare is allowed, the set of places files can end up doesn't quietly grow
- **Documentation**: `./gradlew katachiDocs` writes out Markdown for each Role
- **Code scaffolding**: `./gradlew katachiTemplate --arg template=UseCase --arg name=GetUser` creates a file where the definition says it belongs. It can also be generated from an IntelliJ IDEA / Android Studio plugin (experimental) ([how to install](https://tbsten.github.io/katachi/guides/generate-code-from-template/))

You can also write rules about a file's contents — being public, for example — with `konsist { }`. How this differs from Konsist, detekt and ArchUnit is in [Comparison with other tools](https://tbsten.github.io/katachi/get-started/comparison-with-other-tools/).

**Supported**: JVM, Android and KMP projects. Put the definition and its test in a JVM module. JDK 17+, Kotlin 2.2+, Gradle 8.0+.

## Getting started

Create one JVM module for the architecture definition.

```kotlin
// settings.gradle.kts — the plugin is published to Maven Central
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } } // an existing project most likely has this already
include(":architecture-test")
```

```kotlin
// architecture-test/build.gradle.kts
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "2.4.10"
    id("me.tbsten.katachi") version "0.2.0"
}

kotlin { jvmToolchain(17) }
tasks.test {
    useJUnitPlatform()
    // The files being checked are not inputs of the Test task, so run it every time (and keep its result out of the build cache)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    // Print the list of violations to the console
    testLogging { exceptionFormat = TestExceptionFormat.FULL }
}

dependencies {
    testImplementation("me.tbsten.katachi:katachi:0.2.0")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Tells document generation and templates where to find the definition (its package + variable name)
katachi { architecture = "com.example.projectArchitecture" }
```

katachi checks the files of the whole repository, but Gradle only sees the test classpath as the Test task's input. Without `outputs.upToDateWhen { false }`, `:architecture-test:test` is skipped as `UP-TO-DATE` even after you add files, and stays green even when there are violations. `outputs.cacheIf { false }` keeps a previous result from being reused when the build cache is enabled. Without `testLogging { exceptionFormat = TestExceptionFormat.FULL }`, a failing test prints only a single line with the exception's class name to the console, and you cannot read the list of violations.

Write the definition and a test.

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt
package com.example

val projectArchitecture = architecture {
    gradle() // Gradle files: wrapper, settings, build scripts, etc.
    "domain".group {
        "UseCase" {
            layout { "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile() }
        }
    }
}

// architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt
package com.example

class ProjectArchitectureTest {
    @Test
    fun `the project matches its definition`() = projectArchitecture.assert()
}
```

```shell
./gradlew :architecture-test:test           # runs the check
./gradlew :architecture-test:katachiDocs    # writes docs to architecture-test/build/katachi/docs
```

At first, the files and directories you haven't declared come back as violations. For each path that shows up, decide whether to add it to the definition or delete the file. If there are too many to clear up right away, you can tolerate the existing violations for now with [baseline](https://tbsten.github.io/katachi/guides/baseline/) and fail only on new ones.

The full walkthrough — importing, and handing it off to an AI agent — is in [Your first architecture definition](https://tbsten.github.io/katachi/get-started/first-architecture/).

<details>
<summary><b>If it doesn't work</b></summary>

- **Below Kotlin 2.4**: you need `compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")` (don't add it from 2.4 on — it becomes a warning)
- **`Failed to load JUnit Platform`**: you're missing `junit-platform-launcher`
- **`Cannot create Launcher without at least one TestEngine` / `did not discover any tests to execute`**: you're missing a JUnit engine (`junit-jupiter`). On Gradle 8, if another engine (kotest, for example) is present, this may not fail at all: the build stays `BUILD SUCCESSFUL` while the check silently does nothing
- **Writing `konsist { }` fails with `[UncheckedFileConstraint]`**: write `assert(FileConstraintCheck())`

</details>

## Learn more

| What you want to do | Page |
|---|---|
| Learn how to write Roles and groups | [Basic API](https://tbsten.github.io/katachi/guides/basic-api/), [Role](https://tbsten.github.io/katachi/guides/role/) |
| Get the details of writing layouts | [Layout](https://tbsten.github.io/katachi/guides/layout/) |
| Check file contents too | [Konsist integration](https://tbsten.github.io/katachi/guides/konsist-integration/) |
| Generate documentation and code | [Document generation](https://tbsten.github.io/katachi/guides/document-generation/), [Generating code from a template](https://tbsten.github.io/katachi/guides/generate-code-from-template/) |
| Adopt katachi while setting existing violations aside | [Baseline](https://tbsten.github.io/katachi/guides/baseline/) |
| See a real project's definition | [Samples](CONTRIBUTING.md#samples), [Recipes](https://tbsten.github.io/katachi/recipes/) |

## Modules

| Module | What it is |
|---|---|
| `me.tbsten.katachi:katachi` | The main library (JVM) |
| `me.tbsten.katachi:katachi-konsist` | Only if you use `konsist { }` |
| Gradle plugin `me.tbsten.katachi` | Tasks such as `katachiDocs` and `katachiTemplate`. Pair it with the same version of the library |

## Contribution

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](./LICENSE)

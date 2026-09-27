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
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
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

## Modules

| Module | What it is |
|---|---|
| `me.tbsten.katachi:katachi` | The main library (JVM) |
| `me.tbsten.katachi:katachi-konsist` | Only if you use `konsist { }` |
| Gradle plugin `me.tbsten.katachi` | Tasks such as `katachiDocs` and `katachiTemplate`. Pair it with the same version of the library |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](./LICENSE)

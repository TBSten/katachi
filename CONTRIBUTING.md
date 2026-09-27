# Contributing to katachi

For people changing katachi itself. For how to use it, see the [README](README.md) and the [documentation site](https://tbsten.github.io/katachi/).

## What it is built with

| | Version |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10 (2.3.0 for the IDE plugin only, kept in its own catalog) |
| JDK / toolchain | 17 (21 for the IDE plugin only) |
| kotest | 6.2.5 |

## What is in the repository

| Where | What it is |
|---|---|
| `katachi/`, `katachi-konsist/`, `katachi-gradle-plugin/` | The three published modules |
| `architecture-test/` | katachi's own architecture definition. Not published |
| `tool/dokka/` | The Dokka plugin behind the API reference. Not published |
| `benchmark/` | The JMH benchmarks. Not published. Not part of `check`; run them with `./gradlew :benchmark:jmh` |
| `katachi-intellij-plugin/` | The plugin for IntelliJ IDEA and Android Studio, a standalone Gradle build |
| `sample/` | The samples, each a standalone Gradle build |
| `docs/` | The documentation site |

## Samples

Four of them live under `sample/`. Each one's own README covers what it looks like and how to run it; what the samples share — design decisions and build setup — is in [`sample/README.md`](sample/README.md).

| Sample | What it is |
|---|---|
| [`sample/jvm`](sample/jvm/README.md) | A minimal Ktor server |
| [`sample/android`](sample/android/README.md) | A multi-module Android app, with real Compose and AndroidX dependencies |
| [`sample/kmp`](sample/kmp/README.md) | An Android + iOS KMP project, with real Compose Multiplatform dependencies |
| [`sample/custom-processor`](sample/custom-processor/README.md) | A hands-on tour of writing your own processor |

All four use the plugin and `gradle()`, and commit the documentation `katachiDocs` generates for them. Only jvm, android and kmp use `template { }` and baseline.

## Checking

```shell
./gradlew check          # the library; needs neither an Android SDK nor Kotlin/Native
./gradlew checkSamples   # every sample, one after another, through its own wrapper
./gradlew checkSampleJvm # just one (the Android and KMP samples need an Android SDK)
./gradlew checkIdePlugin # buildPlugin test verifyPreview of the IDE plugin (needs a JDK 21)
```

- The samples are standalone builds, not subprojects of the root, so `./gradlew check` does not run them. The root project has no sources; it only aggregates the API reference and carries the tasks that run the samples.
- The IDE plugin is a standalone build too, run by neither `check` nor `checkSamples`. Its first run downloads the IntelliJ Platform SDK (a whole IDE). Its screen goldens (`verifyPreview`) were made on macOS and do not match byte for byte on other systems. The smoke test that starts a real IDE (`integrationTest`) is run by hand.
- `gradle/libs.versions.toml` is the single source of truth for the Kotlin, katachi, kotest and JUnit versions of the library and the samples (the IDE plugin is a standalone build and reads its own `katachi-intellij-plugin/gradle/libs.versions.toml`). The samples read it as `libs` and keep their own dependencies in their own catalog, `sampleLibs` (see [`sample/README.md`](sample/README.md)).
- CI is `.github/workflows/ci.yml`: on every push to `main` and every pull request, the library and each of the four samples in their own step, the documentation site build in a `docs` job, and the IDE plugin in a job of its own on macOS. The benchmark jobs (`bench-jmh`, `bench-real-project`, `bench-store`) run on the same triggers. Their comparison only goes to the job summary, and a slowdown does not fail the build. `bench-store` adds the results to the history on `gh-pages`, on `main` only.

## Writing code

- Kotlin code follows [`docs/internal/kotlin/kotlin.md`](docs/internal/kotlin/kotlin.md); exceptions and error messages follow [`docs/internal/kotlin/errors.md`](docs/internal/kotlin/errors.md).

## Writing documentation

- The documentation site is `docs/` (Astro + Starlight). First run `./gradlew generateApiDocs` at the repository root to prepare the API reference (`docs/public/api-docs/`; it's gitignored, and the link check fails without it), then `pnpm install` in `docs` and `pnpm run build` to build it, link check included.
- **Japanese is the source; English is a translation.** Write `docs/src/content/docs/ja/`, `README.ja.md` and `CONTRIBUTING.ja.md`, and translate the English side from them. Do not add anything to the English side alone (see [`docs/CLAUDE.md`](docs/CLAUDE.md)).

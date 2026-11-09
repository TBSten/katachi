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

All four use the plugin and `gradle()`, and commit the documentation `katachiDocs` generates for them. Only jvm, android and kmp use `.template { }` and baseline.

## Checking

```shell
./gradlew check          # the library; needs neither an Android SDK nor Kotlin/Native
./gradlew checkSamples   # every sample, one after another, through its own wrapper
./gradlew checkSampleJvm # just one (the Android and KMP samples need an Android SDK)
./gradlew checkIdePlugin # buildPlugin test verifyPreview of the IDE plugin (needs a JDK 21)
```

- The samples are standalone builds, not subprojects of the root, so `./gradlew check` does not run them. The root project has no sources; it only aggregates the API reference and carries the tasks that run the samples.
- The IDE plugin is a standalone build too, run by neither `check` nor `checkSamples`. Its first run downloads the IntelliJ Platform SDK (a whole IDE). Its screen goldens (`verifyPreview`) were made on macOS and do not match byte for byte on other systems. The smoke test that starts a real IDE (`integrationTest`) is run by hand. Build a zip to try in your own IDE with `./gradlew buildPlugin -Pkatachi.timestampVersion`: its version then carries the build time, as in `0.2.0-261106_1850`, so Install Plugin from Disk… does not refuse it for a version that is already installed.
- `gradle/libs.versions.toml` is the single source of truth for the Kotlin, katachi, kotest and JUnit versions of the library and the samples (the IDE plugin is a standalone build and reads its own `katachi-intellij-plugin/gradle/libs.versions.toml`; only its version comes from the root's `katachi`, and `publish.yml` attaches its zip to the GitHub Release). The samples read it as `libs` and keep their own dependencies in their own catalog, `sampleLibs` (see [`sample/README.md`](sample/README.md)).
- CI is `.github/workflows/ci.yml`: on every push to `main` and every pull request, the library and each of the four samples in their own step, the documentation site build in a `docs` job, and the IDE plugin in a job of its own on macOS. The benchmark jobs (`bench-jmh`, `bench-real-project`, `bench-store`) run on the same triggers. Their comparison only goes to the job summary, and a slowdown does not fail the build. `bench-store` adds the results to the history on `gh-pages`, on `main` only.

## Trying the katachi command locally

The `katachi` command lives in `katachi-script`. To try a change, publish it to mavenLocal and load it from a `*.local.main.kts` at the repository root. `*.local.main.kts` is gitignored, so keep as many throwaway scripts as you like.

```shell
./gradlew :katachi:publishToMavenLocal :katachi-script:publishToMavenLocal -Pkatachi.skipSigning
```

```kotlin
// try.local.main.kts
@file:DependsOn("me.tbsten.katachi:katachi-script:0.3.0") // the katachi version in gradle/libs.versions.toml

import me.tbsten.katachi.script.*

val katachi = Katachi(defaultPackage = "com.example.app", lang = ja)

println(commands(katachi.agent.init, katachi.scaffold.`test-framework`(kotest))) // only prints the commands it built
val status = katachi.command("--version").execute()                              // runs one (in this same JVM)
// main(arrayOf("agent", "guide"))                                                // the same entry point as the command line
```

```shell
kotlin try.local.main.kts
```

- Without `-Pkatachi.skipSigning`, the signing task fails on a machine with no GPG key.
- `@file:DependsOn` looks in `~/.m2/repository` first, so no `@file:Repository` is needed.
- Publish again after each change. If compilation still sees the old API, delete the script cache of `kotlin` (`~/Library/Caches/main.kts.compiled.cache` on macOS).
- `execute()` on `agent init` or `scaffold` writes files into the current directory.
- `main(...)` exits the JVM when the command ends with a non-zero status.

## Writing code

- Kotlin code follows [`docs/internal/kotlin/kotlin.md`](docs/internal/kotlin/kotlin.md); exceptions and error messages follow [`docs/internal/kotlin/errors.md`](docs/internal/kotlin/errors.md).

## Writing documentation

- The documentation site is `docs/` (Astro + Starlight). First run `./gradlew generateApiDocs` at the repository root to prepare the API reference (`docs/public/api-docs/`; it's gitignored, and the link check fails without it), then `pnpm install` in `docs` and `pnpm run build` to build it, link check included.
- **Japanese is the source; English is a translation.** Write `docs/src/content/docs/ja/`, `README.ja.md` and `CONTRIBUTING.ja.md`, and translate the English side from them. Do not add anything to the English side alone (see [`docs/CLAUDE.md`](docs/CLAUDE.md)).

## Writing the impact

In pull requests, tickets and investigation reports, write the impact of a change in the shape below. It is a summary of the whole change, so a reader can see at a glance what happened where. Which files and which Roles changed, and by how many lines, is left to the katachiDiff comment attached to every pull request (do not repeat it in the body).

```md
### Impact

#### Main changes

- CLI
  - new: `katachi <file>.katachi.kts` … runs the script
- konsist
  - update!: ktFileContent … enum entries fall under deny by default
    - Before: ``allow(enum, `class`, "Color")``
    - After: ``allow(enum, `class`, "Color") { allow(entry, "*") }``

#### Minor changes

- core
  - fix: katachiDiff › ignore() locations … not counted as no role

#### Tests and verification

- script: 13 cases in `KatachiScriptSpec` (CI)
- IDE plugin: the platform tests run for the first time on CI (not checked locally)
```

Write it in three levels: **change group › component › kind: location … what changed**. Heading depth may follow wherever it is placed (the pull request template starts at `## Impact`).

### Change groups

Keep all three headings. When a group has nothing, write "None" instead of removing it (so it is not mistaken for something forgotten).

| Group | What goes in it |
|---|---|
| Main changes | The purpose of the pull request or ticket itself; changes users notice. A change marked `!` always goes here, however small it looks |
| Minor changes | Fixes made along the way, wording, internal tidying — changes users will hardly notice |
| Tests and verification | Tests added, and how far the change was checked (locally, on CI, not checked) |

### Components

Pick names from this list. "Scope" is the word used as the scope in a ticket name `type(scope): summary`. For core, "Where" is the package under `katachi/src/main/kotlin/me/tbsten/katachi/`.

| Component | What it covers | Where | Scope |
|---|---|---|---|
| core | The katachi library itself | `katachi/` | `core` |
| └ DSL | The API for writing an architecture definition | `dsl/` | `core` |
| 　└ architecture | `architecture { }`, the entry point of a definition | `dsl/` | `core` |
| 　└ role / group | Role, group, metadata, naming, example | `dsl/` | `core` |
| 　└ layout | `layout { }`, selecting files, `gradle()`, modules | `dsl/`, `dsl/files/`, `dsl/gradle/` | `core` |
| 　└ fileConstraint | Constraints on file contents (what konsist and konture build on) | `dsl/` | `core` |
| 　└ template | `.template { }`, template sets | `dsl/` | `core` |
| └ checking | assert, validate, the shape of violations, Severity, layout warnings | `check/` | `core` |
| 　└ baseline | Reading and writing the baseline, stale entries | `check/`, `dsl/` | `core` |
| 　└ SARIF | The SARIF report | `report/` | `core` |
| └ processor API | `ArchitectureProcessor`, processor arguments, composition, running | `processor/` | `core` |
| └ IDE integration | The definition JSON the IDE plugin reads | `ide/` | `core` |
| └ built-in processors | The processors katachi ships with | | |
| 　└ katachiDocs | Documentation generation | `docs/` | `core` |
| 　└ katachiTemplate | Code generation from templates | `template/` | `core` |
| 　└ katachiDiff | Diff report and pull request comment | `diff/` | `diff` |
| konsist | `ktFileContent`, `konsist { }` | `katachi-konsist/` | `konsist` |
| konture | `konture { }` | `katachi-konture/` | `konture` |
| Gradle plugin | Tasks, `katachi { }` | `katachi-gradle-plugin/` | `gradle` |
| Actions | GitHub Actions users call with `uses:` (`diff-report`, `diff-report-comment`) | `diff-report/`, `diff-report-comment/` | `action` |
| CLI | The `katachi` command, brew | `katachi-script/cli/` | `cli` |
| script | The DSL for `*.main.kts` / `*.katachi.kts` | `katachi-script/src/` | `script` |
| IDE plugin | Gutter, tool window, MCP | `katachi-intellij-plugin/` | `ide` |
| skill | The Agent Skill users install with `gh skill install TBSten/katachi katachi` (installing, adding to and updating the definition, fixing violations) | `skills/katachi/` | `skill` |
| docs | The documentation site, README | `docs/`, `README*.md` | `docs` |
| CI / development (no user impact) | Workflows, pull request template, CONTRIBUTING, architecture-test, benchmark, `tool/` (not the actions given to users: those are Actions) | `.github/`, `architecture-test/`, `benchmark/`, `tool/` and so on | `ci` |
| sample | The samples | `sample/` | `sample` |

katachiDocs, katachiTemplate and katachiDiff are processors inside core, so as components they belong to core. Name a part inside core by its innermost name in the list, at the head of the location and joined with "›" (for example, under core: `update: katachiDiff › Importance … measured against a fixed 400 lines`). There is no need to trace it from the outside, as in `DSL › layout ›`.

### Kinds of change

| Kind | Meaning | Example |
|---|---|---|
| `new` | Something that can now be done | CLI / `new: hoge command` |
| `update` | The look, behavior or default of an existing feature changes | core / `update: katachiDiff › Importance … measured against a fixed 400 lines` |
| `fix` | A bug was fixed; behavior just returns to what is correct | core / `fix: katachiDiff › ignore() locations … not counted as no role` |
| `delete` | A feature or API was removed | core / `delete: layout › .module { } … removes the deprecated API` |

When users must rewrite their code or definitions to compile or to keep the check green, add `!` after the kind (`update!`, `delete!` and so on) and always put a Before / After right below it. That Before / After is reused as-is for the migration steps in the release notes.

### Where to write it

| Place | How |
|---|---|
| Pull request body | All three levels right after the tickets (fill in the [pull request template](.github/pull_request_template.md)). Put the detailed account of what was done after it |
| Ticket | When filing, write the planned impact in the summary. After merging, copy it from the pull request into the results |
| Investigation report | For each option, line up "the impact if this option is taken" in the same shape to compare them |
| Chat reports | Shrink it to one line (for example `Impact: CLI new / IDE plugin new×2 / konsist update!`) |

Release notes can be built by collecting the pull requests' impacts and listing them per component: `!` → breaking changes, `new` → new features, `update` → changes, `fix` → fixes.

### 2-1. Run scaffold

```sh
katachi scaffold --package com.example.app
```

Pass the app package name identified in step 1 to `--package`. It uses the version of the `katachi` command and the Kotlin version detected from the project, so normally no other arguments are needed. Where the verification module goes (`meta.moduleDir`), the test framework and how dependencies are written come from what you recorded in the checklist in 1-3. Add `--module-dir <path>` / `--test-framework junit5|kotest` / `--deps catalog|literal` only to hard-code them without recording preferences.

**Only if `doctor` printed no `Kotlin:` line in 0-1** (the version could not be detected), add `--kotlin <version>`. Omitting it will cause `scaffold` to stop. Put the version you pass into the checklist's `meta.kotlin` too.

This command does the following.

- Creating `build.gradle.kts` in the verification module (`architecture-test/` by default). It applies Kotlin JVM and the katachi Gradle plugin (`me.tbsten.katachi`), and tells the plugin where the definition lives with `katachi { architecture = "<package>.test.architecture.projectArchitecture" }`. The plugin does not add katachi as a dependency, so `testImplementation("me.tbsten.katachi:katachi:<version>")` and the rest are written alongside it. `tasks.test { }` also gets `outputs.upToDateWhen { false }` and `outputs.cacheIf { false }`. The files katachi checks (the whole repository) are not inputs of the Test task, so without these Gradle skips the test as `UP-TO-DATE` after a file is added or moved, and it stays green even with violations
- Creating `ProjectArchitecture.kt` and `ProjectArchitectureTest.kt` (in the `FunSpec` form if `testFramework` is `kotest`)
- Appending to `gradle/libs.versions.toml` (when `dependencyStyle` is `catalog`). It adds aliases for `katachi` / `katachiKonsist` / JUnit to `[versions]` / `[libraries]` / `[plugins]`, and `build.gradle.kts` references them as `libs.katachi` and so on. If an alias pointing at the same thing already exists, it is used. Only the Kotlin plugin line stays `kotlin("jvm")` (no version) even with a catalog, because `alias(libs.plugins.kotlin.jvm)` fails with "already on the classpath with an unknown version" when the Kotlin plugin is on the classpath via build-logic (includeBuild). To move it into the catalog, the form is `id(libs.plugins.kotlin.jvm.get().pluginId)`
- Adding the Kotlin JVM plugin to the root build file (does nothing if the root's `plugins { }` / `buildscript { }` or `buildSrc` already has a Kotlin plugin. If only subprojects declare it, with a version, it adds nothing to the root and writes the version in the verification module instead. Gradle then warns `The Kotlin Gradle plugin was loaded multiple times in different subprojects ...`; that is expected with this setup, so leave it. Adding it to the root or removing the version makes plugin resolution fail instead)
- Adding `include(":architecture-test")` to the settings file (`include ':architecture-test'` for a Groovy `settings.gradle`; `include(":static-analysis:katachi")` for `--module-dir static-analysis/katachi`; does nothing if it's already there)
- Adding `mavenCentral()` to `pluginManagement { repositories { } }` in the settings file, because the katachi Gradle plugin is published to Maven Central (does nothing if it's already there; creates `pluginManagement { }` if there is none)

Plugin version conflicts, the JUnit engine, the JVM toolchain (below), the compiler options the project's Kotlin version needs, and handling for Android / KMP projects are **all decided by `scaffold`.** Below Kotlin 2.4 it writes `-Xcontext-parameters`, which the DSL cannot be called without; from 2.4 on it leaves the flag out, because there it only warns that it is redundant. Below Kotlin 2.2 it stops and says so — katachi's metadata cannot be read by a compiler that old (`doctor` warns about it first; see "When Kotlin is too old" in 0-2). Even if you feel like fixing something after reading the generated files, do not fix it.

The JVM toolchain reuses the N the project already sets: `jvmToolchain(N)` or `JavaLanguageVersion.of(N)` in another module, the same forms inside buildSrc or build-logic, or a value read from the version catalog or a `val`. katachi needs JDK 17 or later, so an N below 17 is not used. When no setting is found it writes no `jvmToolchain` and simply uses the JDK that runs Gradle (so it does not fail where JDK 17 is missing and no toolchain download is configured). Which of the two happened is in the scaffold output; copy it into the checklist's `meta.jvmToolchain`. `doctor` and `scaffold` also check that the JDK Gradle uses is 17 or later, and stop with a fix if it is not.

The one exception is when the generated file has to change for the build to run at all. That takes priority over the rule above — but it means katachi has a bug, so record it in `changedFiles` giving the reason, and add it to the checklist's `questions` as well.

Add `--no-konsist` only if it has been decided not to use `ktFileContent { }` (it comes with katachi-konsist). With this setup the test passes no `FileConstraintCheck()` to `assert()`, so a `fileConstraint` written in a `gradle { }` hook is not evaluated either, and the test fails with `[UncheckedFileConstraint]` (its How to fix is `Pass FileConstraintCheck() to assert()`). If it hasn't been decided, leave the default as is.

Once it's created, mark `2-1` done.

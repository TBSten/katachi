### 3-2. Write the definition

**The goal is a definition that runs.** Writing it in one file is fine. Splitting files and cleaning up duplication happen in 3-3. When unsure about an API, read the llms.txt in "When unsure about an API" in 3-1.

**These are the imports you will need.** The documentation's snippet examples do not show them, so they are here.

```kt
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.DeclarationContainerScope   // when splitting the definition into functions
import me.tbsten.katachi.dsl.FileConstraintFailure       // when writing fileConstraint (an experimental API)
import me.tbsten.katachi.dsl.gradle.*                    // mainSourceSet / testSourceSet / kotlin / gradle()
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.konsist.declarations.ktFileContent   // when writing ktFileContent { } (an experimental API)
```

`fileConstraint` itself needs no import (it is a member of the scopes you call it in); the `FileConstraintFailure` its check returns does. Give it the reasons, the allowed entries and the fixes as plain values (`reasons = listOf("Missing" to text)`, `allowed = "Allowed here" to listOf(entry)`, `howToFix = listOf("sentence")`), so nothing else needs an import; `FileConstraintFix` from the same package is only for a fix that also shows code to paste. To read a covered file, call `subject.textOf(file)`, and `subject.lineOf(file, regex)` for the line a failure points at. For a check that looks at one file at a time and skips the ones that are fine, write `buildList { for (file in subject.files) { … continue … add(FileConstraintFailure(…)) } }` rather than `mapNotNull` with `return@mapNotNull null`.

**Star-import `me.tbsten.katachi.dsl.gradle`:** one line brings `mainSourceSet`, `testSourceSet`, `kotlin` and `gradle()`. Importing them one by one also works (`/` needs no import).

**`capture()`, `fileConstraint`, `FileConstraintFailure` and `ktFileContent { }` are experimental APIs.** A file that uses any of them starts like this:

```kt
@file:OptIn(ExperimentalKatachiApi::class)

import me.tbsten.katachi.ExperimentalKatachiApi
```

**Do not rename `val projectArchitecture` or move it to another package.** `katachi { architecture = "..." }` in the verification module's `build.gradle.kts` points at that fully qualified name. Splitting the definition into functions or files is fine (3-3).

You can write `title = "..."` and `description = "..."` at the top of `architecture { }`. They become the heading and body of the root page of the documentation generated in step 4 (they cannot be set from `build.gradle.kts`). Take their content from `overview` / `architecture` in the step 1 report.

#### One Role = one kind of file

**A Role (`"Name".role { }`) stands for one kind of file. What bundles different kinds of files is a group (`"Name".group { }`).** Pack different kinds of files into one Role and neither its `summary` nor its `ktFileContent { }` fits all of them any more, and the generated documentation shows a box that anything goes into.

**Name Roles and groups alike in UpperCamelCase** (`"ViewModel".role { }`, `"Feature".group { }`, `"BuildLogic".group { }`), as katachi's own `Gradle` group (from `gradle()`) is. Do not start a group name with a lowercase letter (`"feature".group { }`): the name shows up as it is in the generated documentation and in `katachiTemplate`'s `template=Group.Role`, next to Role names that are UpperCamelCase. Only the directories of a split definition start lowercase (3-3). `katachi lint` lists names that start lowercase.

Decide whether files belong in the same Role with these three questions. If any file gets a "no", split the Role.

- Does the Role's one-sentence `summary` describe every file?
- Does every file follow the same naming and placement rule (rather than exceptions listed one by one)?
- Does every `ktFileContent { }` constraint you would write for the Role apply to every file?

The other way round, **it is not one Role per file either.** Eighteen `*UseCase.kt` files are one Role, `UseCase`. The snippet a violation's `How to fix:` offers names the Role after the file (`"GetUserUseCase"` and so on), so do not paste it as is — rename it to the kind.

Files directly under the root also get one Role per kind (README, LICENSE, `.gitignore`, …), never one Role for all of them. Write the file directly under its Role's `layout { }` (`"README.md".file()`): no directory is needed.

**For the Gradle files (the wrapper, settings, each module's build script, `gradle.properties`, the version catalog), do not write Roles yourself: write the one line `gradle()`.** katachi declares a `Gradle` group split into one Role per kind of file (it comes with the star import of `me.tbsten.katachi.dsl.gradle.*`). Only `buildSrc` and builds added with `includeBuild`, which it does not cover, need Roles of your own. The four wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`) are required, and a missing one is `[MissingFile]`. When the project has no wrapper, or keeps only part of it, write `gradle(requireWrapper = false)` instead.

A constraint on the contents of a build file (the ones marked `constraintOnly` in the report) is not a Role either; it is written in a hook of the `gradle()` block, in 3-4. In 3-2 write only the `gradle()` line. A Role outside `gradle { }` that declares a build file claims the same file as katachi's `BuildScript` and becomes `[AmbiguousLayout]`.

<details>
<summary>Common mistakes and how to fix them</summary>

In the field tests of the install kit, these shapes turned up in 13 projects.

| Mistake (packed into one Role) | Fix |
|---|---|
| `Gradle` holding `settings.gradle.kts`, `gradle.properties`, `gradlew`, the version catalog and the wrapper — and then `.gitignore` or even `README.md` | Replace the Gradle files with the one line `gradle()`, and give `.gitignore` and `README.md` a Role each |
| `ProjectMeta` / `RootDoc` holding `README`, `LICENSE`, `CONTRIBUTING`, `CLAUDE.md` | One Role per kind — `ProjectReadme` / `License` / … — bundled in a group. A Role named `Readme` gets a documentation page whose name differs from the index `README.md` only in case, and `katachiDocs` fails |
| `AgentAndCiConfig` holding `.claude/`, `.github/`, `.run/` | One Role per tool (if you do not check their contents, `ignore()` in each is fine) |
| `AndroidApp` holding a whole module's contents (Manifest, Activity, `res/`, `build.gradle.kts`) | Make the module a group, with a Role per kind inside |
| Production code and its tests in the same Role | Put the tests in a Role of their own |

```kt
// ✗ Different kinds of files packed into one Role
"ProjectMeta".role {
  layout {
    "README.md".file()
    "LICENSE".file()
    ".gitignore".file()
  }
}

// ○ One Role per kind, bundled in a group
"Meta".group {
  "ProjectReadme".role { layout { "README.md".file() } }
  "License".role { layout { "LICENSE".file() } }
  "GitIgnore".role { layout { ".gitignore".file() } }
}
```

</details>

#### Do not stop at placement

In 3-2 you write **where the files go**. The constraints on their contents (`ktFileContent { }`, `fileConstraint`) are decided and written in 3-4, after 3-3 has tidied the definition up without changing its result. Do not write them here; they would change the check result that 3-3 compares against.

**Do not write catch-all Roles.** A layout like `commonMain/**/*.kt` — "anything, as long as it is here" — looks like a check and is not one. In a real adoption this let **a test for the same responsibility be created somewhere else entirely, and accepted.** Give a Role a pattern in the file name wherever one exists (`"*ViewModel".ktFile()`). A Role that has none (everything in one directory, say) is not left as it is: it needs a constraint on its contents, which 3-4 writes. Note the Role in the `note` of its `roles` entry in the report ("needs a constraint, 3-4") so 3-4 does not miss it.

#### The verification module needs Roles too

The verification module is checked like any other module. Its `build.gradle.kts` is covered by `gradle()`, but the definition files and the test under `<verification module>/src/test/kotlin/` need a Role, or they come back as `[UnexpectedFile]` (3-3 assumes they have one). Give them a group of their own, with `documented = false` (they are tool configuration, as in 1-2):

```kotlin
"ArchitectureTest".group {
    documented = false
    "ArchitectureDefinitionEntry".role {
        summary = "The entry of the katachi definition: the architecture { } that gathers every group"
        layout {
            "architecture-test" / testSourceSet / kotlin / "**" / "ProjectArchitecture".ktFile()
        }
    }
    "ArchitectureDefinitionTest".role {
        summary = "The test that runs the katachi definition"
        layout {
            "architecture-test" / testSourceSet / kotlin / "**" / "ProjectArchitectureTest".ktFile()
        }
    }
    "RoleDefinition".role {
        summary = "One <Name>Role.kt per Role, split out of architecture { }"
        layout {
            "architecture-test" / testSourceSet / kotlin / "**" / "*Role".ktFile()
        }
    }
    "GroupDefinition".role {
        summary = "One <Name>Group.kt per group, split out of architecture { }"
        layout {
            "architecture-test" / testSourceSet / kotlin / "**" / "*Group".ktFile()
        }
    }
}
```

Read `"architecture-test"` as `meta.moduleDir`. One Role per kind of file, as everywhere else: the entry `ProjectArchitecture.kt`, the `ProjectArchitectureTest.kt` that scaffold writes, and the `<Role>Role.kt` / `<Group>Group.kt` files that 3-3 splits the definition into (the `"**"` takes the group directories). The name patterns keep these Roles from being catch-alls ("Do not stop at placement"). A file of another shape there (a helper with shared `val`s, say) gets a Role of its own rather than a `"*"`.

If scaffold created `<verification module>/.gitignore` (it does when the project's `.gitignore` does not already ignore `build/`), that file needs a Role too, or step 4 reports it as `[UnexpectedFile]`. Declare it next to the root `.gitignore`, for example `"**/.gitignore".file()` in the `GitIgnore` Role.

Fill in the `architecture { }` in `<verification module>/src/test/kotlin/<package>/test/architecture/ProjectArchitecture.kt`.

#### Run it and save the result

Once written, run it once and **save the output as the reference log before refactoring** (not to be confused with katachi's `baseline()` in 4-2). 3-3 compares against it.

```sh
mkdir -p build/katachi
./gradlew :architecture-test:test --console=plain -Dkatachi.maxViolations=all > build/katachi/test-before-refactor.log 2>&1; echo "gradle exit status: $?"
```

([run-the-check](../../run-the-check/README.md) says how to read the log.)

What you need here is **for it to compile and for the check to run to the end.** Failing with `Katachi check failed: ...` is fine (violations are dealt with in step 4). If a compile error or a Gradle error keeps the check from running, fix it and run it again.

Record the files you created or changed in `changedFiles` (the split files can be one entry by directory or wildcard; files already recorded in 2-2 need not be recorded again), mark `3-2` done, and run:

```sh
katachi lint
```

It reads the definition as Gradle evaluates it (it runs `./gradlew :architecture-test:katachiInternalDefinitionJson`, so the first run takes a while) and lists the Roles that look like they hold different kinds of files. What it lists is dealt with in the 3-3 checklist. If the definition does not compile, `lint` checks nothing: it prints the compile errors and exits 1, so fix them and run it again.

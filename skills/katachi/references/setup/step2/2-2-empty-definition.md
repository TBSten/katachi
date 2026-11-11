### 2-2. Run once with an empty definition

```sh
katachi agent test
```

`test` runs the verification module's `test` task (`./gradlew :architecture-test:test`) and summarizes the result. The full log stays in `<workdir>/tmp/test/`, and the summary's `Log:` line shows the path. Do not start Gradle directly; throughout these steps, always use `katachi agent test` (only the CI in 6-B is written in the `./gradlew` form).

**Failing here is the expected outcome.** `architecture { }` is empty, so by the deny-by-default principle every file is reported as `Unexpected`. The summary looks like this.

```
JDK: 21 (JAVA_HOME)
Log: file:///path/to/project/tmp/install-katachi/tmp/test/2026-10-06T01-02-03Z.log
Result: Katachi check failed: 4 violations (Unexpected: 4)
```

In the log, a paragraph of the following shape follows for each violation (an example of a file and of a directory).

```
[UnexpectedFile] file:///path/to/project/.gitignore
  No role is defined for this file.

  How to fix:
    - Delete it if it is not needed
    - Add a new role for it:
        "Gitignore".role {
          summary = "TODO"
          layout {
            ".gitignore".file()
          }
        }

[UnexpectedDirectory] file:///path/to/project/.gemini
  No role is defined for this directory. Nothing below it was checked.

  How to fix:
    - Delete it if it is not needed
    - Declare what belongs in it in the layout of an existing role
    - Add a new role for it:
        "Gemini".role {
          summary = "TODO"
          layout {
            ".gemini".ignore()
          }
        }
```

katachi shows only 10 violations by default, but `katachi agent test` always writes all of them to the log (it passes `-Dkatachi.maxViolations=all` to `./gradlew`, and katachi's Gradle plugin delivers it to the test JVM). This lets the diff against the previous run and `compare-violations` in 3-3 compare the 11th violation onward too. Do not write `maxViolations` in the test code. If you do, that value wins and the log is cut off.

**Failing in this shape means the wiring is correct.**

The first Gradle run makes `.kotlin/` (Kotlin 2.x). If the project's `.gitignore` does not have it, `[UnexpectedDirectory] .kotlin` shows up. So does the `.gradle/` that Gradle creates. Treat them the same as the untracked files you looked for in 0-2: ask the user whether to add them to `.gitignore`. If git tracks files inside `.idea/` (such as `.idea/codeStyles/`), they are checked too, so declare them by writing a Role in step 3 (if you do not check their contents, `ignore()` is enough). Do not edit `.gitignore` yourself. Only an error that is not `Unexpected` is a problem — a compile error, a dependency resolution failure, something like `Could not start Gradle Test Executor` (the summary then says `Result: the check was not reached`, with the tail of the log attached).

In that case, following the same policy as step 4, **attempt to fix errors unrelated to katachi (Gradle, Java, dependency resolution).** Only if it still can't be resolved, pass the output to the user exactly as it is and ask for their judgment.

If `scaffold` says "has a buildscript { } block, so it was not edited automatically," **add the single line shown in the output to the root build file.** It will keep failing until you add it. The same goes for "could not read the shape of pluginManagement { }" and "could not find where the include statement ends": add what the output shows to the settings file.

Once you have confirmed it fails with `Unexpected` only, record the files that were created or changed in the checklist's `changedFiles`, then mark `2-2` done.

```json
"changedFiles": [
  { "path": "architecture-test/build.gradle.kts", "change": "New", "summary": "Build definition for the verification module" },
  { "path": "settings.gradle.kts", "change": "Modified", "summary": "Added include(\":architecture-test\")" },
  { "path": "gradle/libs.versions.toml", "change": "Modified", "summary": "Added the katachi and JUnit aliases" }
]
```

Enter the files listed in `scaffold`'s output. The output shows them as absolute URIs starting with `file://`, but `path` takes **the path relative to the project root** (like `architecture-test/build.gradle.kts` in the JSON above). If it appended to `pluginManagement`, say so in `summary` as well.

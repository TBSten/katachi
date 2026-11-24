### 3-4. Decide and write the constraints on contents

3-2 wrote where files go, and 3-3 tidied that up without changing the result. **This is the section where the result is allowed to change**: a constraint on contents finds code that breaks the project's conventions, and that is what it is for. A definition that only says where files go only ever tells you that a file is in the right place.

Start from the log 3-3 saved as `build/katachi/test-after-refactor.log` (the reference). Everything that is new in the check result after this section comes from a constraint you wrote here.

#### 1. Go through the rules of every Role and decide how each is checked

The candidates are already in the report: the `allowed` / `forbidden` of each entry of `roles` (1-1, 1-2), and the entries marked `constraintOnly`. Add the rules the code showed you that the documents did not say ("exactly one class per file, named like the file"). **Go through every entry of `allowed` and `forbidden` of every Role, one by one,** and give each one of these answers.

| `by` | Use it when the rule is about | Written as |
|---|---|---|
| `ktFileContent` | what a Kotlin file of the Role declares or imports (`package`, `import`, which classes and functions, their modifiers) | `ktFileContent { }` on the Role (`katachi-konsist`) |
| `fileConstraint` | the contents of any other file (XML, YAML, Markdown, properties), or something about a Kotlin file that `ktFileContent { }` cannot say | `fileConstraint("…") { subject -> … }` in the Role |
| `fileConstraint` | a build file (`build.gradle.kts`, settings, the version catalog) | `fileConstraint("…") { subject -> … }` in a hook of `gradle()`: `buildScript { }`, `settingsScript { }`, `versionCatalog { }` |
| `none` | a rule no constraint can state (judgement: "keeps a screen small"), or one an existing tool already fails the build on, with no per-file suppression (below) | not written; say why in `how` |

**Write the answer into the report, in the entry itself.** The entry changes from a string to an object, so what it says and how it is checked stay in one place:

```json
"forbidden": [
  { "text": "Using the Android framework", "by": "ktFileContent",
    "how": "imports only kotlin.**, kotlinx.coroutines.**, androidx.lifecycle.**, com.example.**; android.** is left out" },
  { "text": "Keeping a screen's state in a Composable", "by": "none",
    "how": "a matter of judgement; no constraint can say it" }
],
"allowed": [
  { "text": "Calling UseCases", "by": "ktFileContent", "how": "com.example.** (the UseCase package) is among the imports it may have" }
]
```

- `text` is the rule in the words the entry already had. Do not add a second entry for a rule that is there; turn that one into the object.
- `how` is one short line: the pattern the constraint holds the files to (what it lets in, or what it rejects). For `none`, it is the reason. The code itself does not go here; it is in the definition.
- A rule that has no entry yet (it came from reading the code now) is added to `allowed` or `forbidden` as an object.
- For a `constraintOnly` entry the rule goes in `forbidden` / `allowed` the same way, with `by: "fileConstraint"` and the hook in `how`.

**Do not drop a rule because something else covers it.** The project may already run detekt, Android Lint, ktlint or a compiler plugin of its own. In a real adoption the agent leaned so hard on avoiding duplication that it under-constrained, and **the only convention violation actually caught came from the existing checker.** Decide like this.

- **A convention that belongs to a Role goes into katachi.** "A ViewModel never calls a repository directly" *is* the definition of that Role. Another tool checking something similar does not change that — having it here keeps "what this Role is" in one place
- **The only safe thing to drop (`none`) is a rule an existing tool already fails the build on, with no per-file suppression.** If it can be suppressed file by file, katachi's copy still earns its place
- When unsure, write it. A constraint is easy to delete later

**No catch-all Roles.** A layout like `commonMain/**/*.kt` — "anything, as long as it is here" — looks like a check and is not one. In a real adoption this let **a test for the same responsibility be created somewhere else entirely, and accepted.** Every Role that has no pattern in its file name (`"*ViewModel".ktFile()`) needs at least one constraint on its contents here. If you cannot write one, say so in the warning on step 3 (0-3, "Progress").

#### 2. Write them

The file that uses a constraint starts with the lines of the "experimental APIs" paragraph of 3-2 and has the imports of its list (`ktFileContent`, `FileConstraintFailure`). Read the `kt-file-content` page (3-1) before you write a `ktFileContent { }`.

A constraint on a file's imports goes in the `import` entries of `ktFileContent { }`. Only the `import`s you write are allowed, so "do not import this" is expressed by not listing that import. Many of a project's conventions are of this form.

```kt
"ViewModel".role {
  layout { /* ... */ }

  ktFileContent {
    `package`("com.example.viewModel")

    // android.** is not listed. Importing it is a violation
    `import`("kotlin.**")
    `import`("kotlinx.coroutines.**")
    `import`("androidx.lifecycle.**")
    `import`("com.example.**")

    required(`class`, fileName) {
      allow(anythingElse)
    }
  }
}
```

For the contents of any other file, `fileConstraint` receives the files of the Role in `subject.files` (project relative) and answers with the ones it rejects. Read a file from `subject.projectRoot`. It answers with an empty list when nothing is wrong, and throws when it could not check.

```kt
import java.io.File

"StringResource".role {
  layout { /* "app/src/main/res/values/strings.xml" … */ }

  fileConstraint("no hard-coded http:// URL in strings") { subject ->
    subject.files
      .filter { File("${subject.projectRoot}/$it").readText().contains("http://") }
      .map { FileConstraintFailure(it) }
  }
}
```

A constraint on a build file goes in a hook of the `gradle()` block, as in `gradle { buildScript { fileConstraint("...") { subject -> ... } } }`. A `buildScript { }` constraint receives the `build.gradle(.kts)` of every module, the root included, in `subject.files`. To look at one module only, narrow `subject.files` by path inside the constraint (for example `subject.files.filter { it == "app/build.gradle.kts" }`). Writing a Role inside `gradle { }` with the same name as one of katachi's (`"BuildScript".role { }` and so on) raises a duplicate exception.

Constraint names follow `constraintNames` from 1-3. With `numbered`, prefix the name with the id of the question the constraint came from (`q3`, `cq2` and so on), as in `"q3: Does not import the android package".ktFileContent { }`. With `plain`, write the name without the id (`"Does not import the android package".ktFileContent { }`). Name the constraint after the rule, so a violation says which rule it broke.

A `fileConstraint` is only run when the test passes `FileConstraintCheck()` to `assert()` (2-1 says what happens with `--no-konsist`).

#### 3. Make sure each constraint really fires

A constraint that never fires looks the same as one that holds. **For every constraint you wrote, make one temporary change that breaks it** (a file with the forbidden import, a line the `fileConstraint` rejects), in one go for all of them. Run the check, and see that each constraint reports it. Then delete every temporary file and confirm with `git status` that nothing of it is left. Do this before you look at what the constraints found in the real code.

A constraint that did not fire is wrong (a layout that does not reach the file, a wildcard that is too wide): fix it and run again. Do not go on to the next part with one that is silent.

#### 4. Run it on the real code

Run the check the same way as in 3-3, keeping the log as `test-after-constraints.log`, and compare it with `test-after-refactor.log` as "Compare two runs" in [run-the-check](../../run-the-check/README.md) says.

```sh
./gradlew :architecture-test:test --console=plain -Dkatachi.maxViolations=all > build/katachi/test-after-constraints.log 2>&1; echo "gradle exit status: $?"
```

**What differs should be only new violations (`>` lines), and each of them should be a violation of a constraint you just wrote.** Anything else that changed (a `<` line, a `[UnexpectedFile]`) is a mistake in this section: fix it.

A new constraint often finds code that really breaks the rule. **Do not loosen the rule to get to 0 violations, and do not rewrite the code without the user's consent.** What to do with the violations is 4-2's job (the checklist's `violations` and `questions`, and the baseline when the user wants to tolerate what is there now), so do not open a second route here. What 3-4 has to do is tell 4-2 which is at fault:

- **A constraint that rejects most of its Role's files** is more likely a rule that is wrong or written too wide than a codebase that is wrong. Re-read the rule against the files and the documents (1-1) before you take it to 4-2. If the rule is what was wrong, fix it here and run again
- **A constraint that rejects a few files** is a finding. Leave it as it is, and write in the `questions` of 4-2 (with the files and the options "fix the code", "tolerate with a baseline", "the rule is wrong") what you found
- A constraint that rejects nothing is fine, once part 3 has shown it fires

In the report, the entry's `how` matches what ended up in the definition. Once the rules are decided, written and firing, and the check result is what you expect, mark `3-4` done.

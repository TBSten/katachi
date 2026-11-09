### 0-3. Reading and writing the checklist and report

`check-list.html` and `project-code-base-report.html` are **single HTML files that carry their data as JSON inside them.** The HTML is only a rendering shell — **the only thing you read or write is the JSON.**

```sh
# Read (only JSON is printed; there's no need to read the HTML)
katachi agent data get report
katachi agent data get check-list

# Read just part of it (keys are dot-separated; a missing key stops it)
katachi agent data get report questions
katachi agent data get check-list meta.moduleDir

# Update just part of it (deep-merged into the existing content; use this normally)
katachi agent data merge report partial.json

# Replace it wholesale
katachi agent data set report new.json
```

`report` and `check-list` are short names pointing to the files in the working directory.

**`data merge` replaces arrays wholesale. It does not append.** Merge a second item and the first one disappears the instant you do. Use `add` when adding to an array.

```sh
katachi agent add violation --violation "..." --location "..." --whyNotFixed "..." --suggestion "..."
katachi agent add question  --question "..." --observed "..." --options "A" --options "B" --recommendation "..."
katachi agent add codebase-question --question "..." --observed "..." --options "A" --options "B" --recommendation "..."
katachi agent add changed   --path "..." --change "New" --summary "..."
katachi agent add role      --importance 5 --name ViewModel --group feature.ui --layout "..." --naming "..." --allowed "..." --forbidden "..." --examples "..." --count 12
katachi agent add module    --path app --kind "Android application" --responsibility "..." --buildFile "app/build.gradle.kts" --deviation "..."
katachi agent add tool      --name ktlint --configPath ".editorconfig" --declareInKatachi "yes"
katachi agent add excluded  --path "..." --reason "..."
```

`--options` and `--allowed` / `--forbidden` / `--examples` **can be passed multiple times.** Passing an unknown field name stops it and lists the names that are available. It also stops when you pass no fields at all, and when the same `--path` or `--name` is already recorded — so a blank row and a duplicate row cannot get in. Only `add changed` ends without doing anything (exit code 0) when the path is already recorded. If you change a recorded file again later, there is no need to record it again.

`add` also stops in the following two cases.

- `add role` stops if `--layout` is a Gradle build file (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, the version catalog, and so on). Gradle files are covered by `gradle()`, so they are not written as Role candidates (except `buildSrc` and builds added with `includeBuild`). For a Role that exists only to put a constraint on a build file, record it with `--constraint-only --note <what it checks>`
- `add excluded` stops if the path is excluded by `.gitignore`. katachi never looks at `.gitignore`d files in the first place, so there is no point listing them as excluded

`add changed` takes **one entry at a time.** When the definition grows to dozens of files, record them as **a single entry using a directory or a wildcard** (for example `--path "architecture-test/src/test/kotlin/**" --change "New" --summary "the whole architecture { } definition (12 files)"`). There is no need for one entry per file.

**Do not edit the HTML directly.** Any rewrite must always go through `data merge` or `data set`. Both of them validate the JSON before writing, back up the original file to `.bak`, and automatically roll back if the result turns out broken.

#### Write down what you learn as you go

**Do not save everything up for the end.** If you get interrupted partway through, the entire investigation is lost.

`data merge` accepts partial JSON, so you can write even a single item at a time.

```sh
printf '{"overview":{"appPackage":"com.example.app"}}\n' > <KATACHI_WORKDIR>/tmp/p.json
katachi agent data merge report <KATACHI_WORKDIR>/tmp/p.json
```

As a rule of thumb, run `data merge` **once every time a section is filled in.** For step 1 that means `overview` → `architecture` → `modules` → `directoryTree` → `roles` → `tools` → `excluded`, so 7 or more times. If you split the work across subagents, write as each subagent's result comes back. **Write with one process at a time.** Subagents do not write; the main agent writes the results it receives, in order (writes are serialized by a lock, but running them side by side only waits and is no faster).

The checklist's JSON has `steps`. Progress is represented by setting the relevant item's `done` to `true`.

```json
{
  "steps": [
    {
      "id": "4",
      "title": "Verify",
      "warning": null,
      "items": [
        { "id": "4-1", "label": "...", "done": false }
      ]
    }
  ]
}
```

The item ids are `0-1` `0-2` / `1-1` `1-2` `1-3` / `2-1` `2-2` / `3-1` `3-2` `3-3` / `4-1` `4-2` `4-3` / `5-1` `5-2` — 15 of them — plus **`6-1` `6-2` `6-3` `6-4` for the optional step 6**, 19 in all. Check 6-1 to 6-4 only if you did 6-A to 6-D respectively; `verify` does not require them. Do not rewrite `label`.

**Do not rewrite the JSON yourself.** There are dedicated commands for that.

```sh
katachi agent check 1-1 1-2          # mark as done (multiple allowed)
katachi agent uncheck 1-2            # revert
katachi agent warn 4 "Left 2 architecture violations in place"   # attach a warning to that step
katachi agent warn 4 --clear         # clear the warning
```

`check` **stops if you pass an id it doesn't recognize.** A typo is never silently ignored. After marking items done it prints one line, `Next: katachi agent guide <section ID>`, so read that section next (the file for that ID in the table in `references/setup/README.md`; running the command prints the same text). It points to the next section to read in table-of-contents order, including 0-3 and the chapter introductions (`guide 3` and so on), which have no items.

`warn`'s step id is **0 through 6** (that's as far as the checklist's steps go). A step with a warning turns yellow, and stays expanded even once it's complete.

There are also arrays for `violations` (architecture violations left in place), `questions` (things to confirm with the user), and `changedFiles` (files created or changed), plus `preferences` (the writing preferences; 1-3) and `answers` (the answers to the questions; 1-2).

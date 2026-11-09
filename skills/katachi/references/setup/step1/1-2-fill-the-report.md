### 1-2. Scan the codebase and fill in the report

Answer the following four questions.

- What architecture does it use? What characteristics does the project's structure have?
- What elements exist? What kind of code can be written inside each element?
- What tools are in use?
- What does the app use for its package name?

Follow the procedure from 0-3.

```sh
# Write section by section, as you figure each one out
katachi agent data merge report <KATACHI_WORKDIR>/tmp/overview.json
katachi agent data merge report <KATACHI_WORKDIR>/tmp/roles.json
```

The report's `meta` (project name, analysis timestamp, file count, Gradle root, Git root) **has already been filled in by `init`.** There's no need to touch it.

**`null` and an empty array represent "unfilled."** If `null` or `[]` remains after you've filled things in, that's a spot you haven't investigated yet. The schema itself is shown by `data get`'s output. There is one example JSON entry inside the HTML as a comment, so check it if you're unsure of the shape.

In `modules`, write `path` / `kind` / `responsibility` / `buildFile` for each module. If a module's setup differs from the other modules of the same `kind`, write that in `--deviation` (omit it otherwise). The report draws the modules as a tree by `path`, so there is no need to write parents and children separately.

`roles` is the core of this report. The Roles in `architecture { }` are built from here. **One kind of file = one entry**, and you fill in the following. A bundle of different kinds of files — a module, or "the config files at the root" — is not one entry; write it as one entry per kind (in the definition the bundle becomes a group; see "One Role = one kind of file" in 3-2).

| Field | Content |
|---|---|
| `importance` | **An integer from 1 to 5. 5 is the most important.** The priority for inclusion in `architecture { }`. The report is sorted in this order |
| `name` | The element's name (e.g. `ViewModel`) |
| `group` | The group it belongs to. Nest with `.` (e.g. `feature.ui`) |
| `layout` | The placement pattern |
| `naming` | The naming convention |
| `allowed` / `forbidden` | What may be written / what must not be written |
| `examples` | Paths to real example files |
| `count` | The number of actual instances |

`importance` / `name` / `group` / `layout` / `naming` are **required**. `verify` will not pass unless they're filled in.

#### Guidelines for listing Role candidates

- **Write Role candidates as a tree of groups.** Nest groups in the order "module → layer → kind", and do not line up 8 or more Roles in one group (if they do, split the group)
- **`build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` / the version catalog are covered by `gradle()`.** Do not make a Role per kind of module. `add role` refuses them too (except `buildSrc` and builds added with `includeBuild`). The exception is a constraint you want to put on a build file (that the namespace matches the package, say); record it with `add role --constraint-only --note <reason>`. In the definition it does not become a Role: write the constraint in a hook such as `gradle { buildScript { } }`. A hook's constraint receives the build files of every module, so to look at one module only, narrow them inside the constraint (the Gradle paragraph under "One Role = one kind of file" in 3-2)
- **What gets `documented = false`:** (1) generated output (build output, committed generated code), (2) build and tool configuration (`.editorconfig`, CI yaml, lint configuration), (3) single-file helpers (README, LICENSE, `.gitignore`). Only Roles that are useful to a reader go into the generated documentation
- **For the Android staples (`AndroidManifest.xml`, `res/`), copy the recipe and adapt it.** Base them on the definition fetched with `katachi agent docs --topic android-three-layer`

This task can consume a large amount of context, so launch subagents where available, as appropriate (as in the table in `references/setup/README.md`: subagents read and draft, and the main agent writes).

Once everything is filled in, run `katachi agent check 1-2`.

**Put whatever you wrote into the report's `questions` in front of the user now, and get answers.** What belongs here are inconsistencies like "do screen files go directly in the package, or are subpackages allowed too" — **the answer changes how `layout` is written.** Ask after the definition is done and you will be rewriting it. **Do not leave this until after step 3.**

Ask like this. Do not re-list the questions in chat.

1. Once `questions` is written, show the report's URL (the path to `project-code-base-report.html`, starting with `file://`) and have the user answer in "7. Questions for you" in the browser and save the JSON with "Export answers"
2. Import the JSON you receive with `katachi agent answers import <path to json>`. Ids that could not be imported are printed as warnings
3. If an answer arrives in chat, import it one at a time with `katachi agent answers set <id> "<answer>"` (`--note "<supplement>"` can be added). The id decides where it goes (`cq…` is the report, `q…` the checklist)

`katachi agent answers` prints, for the checklist and the report each, how many are answered and lists the unanswered ones.

**Use the app package name identified in this step in step 2.**

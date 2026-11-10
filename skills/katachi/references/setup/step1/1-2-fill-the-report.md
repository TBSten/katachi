### 1-2. Scan the codebase and fill in the report

Answer the following four questions.

- What architecture does it use? What characteristics does the project's structure have?
- What elements exist? What kind of code can be written inside each element?
- What tools are in use?
- What does the app use for its package name?

Write the report's JSON as in 0-3, **section by section, as you figure each one out.** Its `meta` was filled in 0-2.

**`null` and an empty array represent "unfilled."** If `null` or `[]` remains after you've filled things in, that's a spot you haven't investigated yet. The template's JSON shows the shape of each section, and the HTML holds one example JSON as a comment; check it if you're unsure of the shape.

In `modules`, write `path` / `kind` / `responsibility` / `buildFile` for each module. If a module's setup differs from the other modules of the same `kind`, write that in `deviation` (omit it otherwise). The report draws the modules as a tree by `path`, so there is no need to write parents and children separately.

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

`importance` / `name` / `group` / `layout` / `naming` are **required**. Step 5 checks that they are filled in.

#### Guidelines for listing Role candidates

- **Write Role candidates as a tree of groups.** Nest groups in the order "module → layer → kind", and do not line up 8 or more Roles in one group (if they do, split the group)
- **`build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` / the version catalog are covered by `gradle()`.** Do not make a Role per kind of module, and do not write them into `roles` (except `buildSrc` and builds added with `includeBuild`). The exception is a constraint you want to put on a build file (that the namespace matches the package, say); record it as a `roles` entry with `constraintOnly: true` and the reason in `note`. In the definition it does not become a Role: write the constraint in a hook such as `gradle { buildScript { } }`. A hook's constraint receives the build files of every module, so to look at one module only, narrow them inside the constraint (the Gradle paragraph under "One Role = one kind of file" in 3-2)
- **What gets `documented = false`:** (1) generated output (build output, committed generated code), (2) build and tool configuration (`.editorconfig`, CI yaml, lint configuration), (3) single-file helpers (README, LICENSE, `.gitignore`). Only Roles that are useful to a reader go into the generated documentation
- **For the Android staples (`AndroidManifest.xml`, `res/`), copy the recipe and adapt it.** Base them on the definition in the Android recipe page (https://tbsten.github.io/katachi/recipes/android-three-layer/)

This task can consume a large amount of context, so launch subagents where available, as appropriate (as in the table in `references/setup/README.md`: subagents read and draft, and the main agent writes).

Once everything is filled in, mark `1-2` done.

**Put whatever you wrote into the report's `questions` in front of the user now, and get answers.** What belongs here are inconsistencies like "do screen files go directly in the package, or are subpackages allowed too" — **the answer changes how `layout` is written.** Ask after the definition is done and you will be rewriting it. **Do not leave this until after step 3.**

Ask like this. Do not re-list the questions in chat.

1. Once `questions` is written, show the report's URL (the path to `project-code-base-report.html`, starting with `file://`) and have the user answer in "7. Questions for you" in the browser and hand the answers back with "Copy answers" (paste into the chat) or "Save as JSON"
2. Copy the entries of the JSON you receive into the report's `answers` (0-3, "Answers")
3. If an answer arrives in chat, write it into `answers` the same way. The id decides the file (`cq…` is the report, `q…` the checklist)

Before going on, count the questions without an entry in `answers`, and tell the user which are still open.

**Use the app package name identified in this step in step 2.**

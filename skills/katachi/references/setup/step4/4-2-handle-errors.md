### 4-2. How to handle errors

- Attempt to fix errors unrelated to katachi, such as those from Gradle or Java.
- Errors from architecture violations originating in katachi: do not force a fix.
  - For minor ones, fix them yourself as the agent
    - Do not silence an `[UnexpectedFile]` by adding a line to the `layout { }` of whichever existing Role is nearest. If that Role's `summary` does not describe the file, make a new Role ("One Role = one kind of file" in 3-2)
  - For ones where the fix is unclear, leave them as is and have the user confirm in a later step
  - Record errors you leave in place in the checklist's `violations` (`violation` / `location` / `whyNotFixed` / `suggestion`).
  - Put anything you want the user to decide into **the checklist's** `questions` (ids `q1`, `q2`, …; fields in 0-3). The report also has an array with the same name, but that one is for "inconsistencies in the codebase" noticed during step 1 — its purpose is different.

Even if more than 10 violations are left, the log of `katachi agent test` holds all of them. There is no need to write `maxViolations` in the test code.

**If many violations remain and the user wants to "tolerate what is there now and fail only on new ones", you can propose the baseline.** It is an experimental feature (`@ExperimentalKatachiApi`), so ask the user in `questions` before putting it in. To use it:

- Write `baseline()` in `architecture { }` in `ProjectArchitecture.kt`, and put `@OptIn(ExperimentalKatachiApi::class)` on `val projectArchitecture`. The baseline file defaults to `katachi-baseline.json` (at the project root)
- The baseline file is checked too, so add a Role that declares it in `layout { }` (e.g. `"BaselineFile".role { layout { "katachi-baseline.json".file() } }`). Without it the baseline file itself becomes an `[UnexpectedFile]`, and that one cannot go into the baseline
- `katachi agent test -- -Dkatachi.baseline.update=true` writes the violations there now into the baseline file. From then on only violations missing from the baseline file fail the test. Once a violation is fixed, its entry fails as `[StaleBaselineEntry]`, so remove it with `katachi agent test -- -Dkatachi.baseline.prune=true` (on CI (`CI=true`) both update and prune are refused)

Write the tolerated violations in `violations` too. Putting them in the baseline file does not make them go away.

If you left violations in place, put one line about what was left in the warning on step 4.

### 0-2. Run init

Run the following **from the Gradle root directory**.

```sh
katachi agent init --lang en
```

`--lang en` specifies that the checklist and report are fetched in English, and that **`katachi`'s output (including the `summary` text) is also in English.** **If you omit it, it is English** (because the site's default is English). **Pass `--lang ja` instead when the user works in Japanese**; these steps stay in English either way. Once specified it is recorded in the working directory, so later commands do not need it.

If the user has specified where the verification module goes, add `--module-dir <path relative to the Gradle root>` (for example `--module-dir static-analysis/katachi`). Without it, it is `architecture-test`. To change it later, record it with `preferences set moduleDir=<path>` in step 1-3.

This command does all of the following. **Do not redo any of this by hand.**

- Confirming this is a Gradle project (stops if not)
- Checking whether it's under Git management (warns if not)
- Deciding on and creating the working directory (inside a `.gitignore`d location if one exists, otherwise `tmp/install-katachi/`)
- Deciding the katachi version (the same version as the `katachi` command; if the version given with `--katachi` is below 0.3.0, which this procedure assumes, it stops before creating anything)
- Detecting the Kotlin version the project uses (it warns if it is older than 2.2; see "When Kotlin is too old" below)
- Placing `check-list.html` and `project-code-base-report.html`
- Placing a copy of these setup steps (`<KATACHI_WORKDIR>/tmp/cache/index.md`, for people to open; `guide` prints from the copy bundled with `katachi`. To place it again, `katachi agent guide --refresh`)
- Initializing the checklist's JSON (working directory / version / project root / date-time / Kotlin / Git / where the verification module goes)

At the end, the result is printed in the following format (a few lines of guidance follow after it). The steps from here on use these values.

```
KATACHI_WORKDIR=/path/to/project/tmp/install-katachi
KATACHI_VERSION=0.3.0
KATACHI_PROJECT_ROOT=/path/to/project
KATACHI_KOTLIN=2.4.10
KATACHI_GIT=yes
KATACHI_SETTINGS=/path/to/project/settings.gradle.kts
KATACHI_LANG=en
KATACHI_MODULE_DIR=architecture-test
KATACHI_MODULE_PATH=:architecture-test
```

**Run every command from here on as `katachi agent <subcommand>` in the root of the project.** `katachi` finds the working directory under the current directory by itself. When running from somewhere else, prefix it with `KATACHI_WORKDIR=<the KATACHI_WORKDIR value>`. Read `:architecture-test:` in these steps as `KATACHI_MODULE_PATH`, and `architecture-test/` as `KATACHI_MODULE_DIR` (they are the same unless you changed `--module-dir`).

Once this is done, run `katachi agent check 0-1 0-2`. The last `Next:` line of `init` points to this command too.

**If it stops, handle it exactly as the output says.** Do not invent a workaround. To change the working directory, re-run with `--workdir <path>`; to pin a version, re-run with `--katachi <version>` (`scaffold` uses the version `init` recorded. Re-running without `--katachi` keeps the version recorded last time).

`init` **never breaks already-filled-in files no matter how many times you run it.** If it fails partway through, you may just re-run it as is.

**If `init` warns that the working directory is visible to git, pass that on to the user** and suggest adding it to `.gitignore`. It happens when the project has no already-ignored place to put it. katachi checks files that are untracked but not ignored, so left alone the working directory itself becomes an `[UnexpectedFile]` in step 4 (and the checklist and the report can end up in their commits). Do not edit `.gitignore` yourself.

**If it lists things that are "untracked and not ignored", the reason is the same.** Ask the user whether to declare each of them in the definition or add it to `.gitignore`.

**When Kotlin is too old.** If `init` warns something like "This project uses Kotlin 1.9.10. katachi needs Kotlin 2.2 or later", ask the user before starting the analysis in step 1 (`doctor` prints the same warning). As it is, `scaffold` in 2-1 will stop, and the analysis up to that point is wasted. Upgrading Kotlin changes the existing build, so do not do it without the user's consent. Once they agree, upgrade Kotlin to 2.2 or later. Kotlin Gradle Plugin 2.2 needs Gradle 7.6.3 or later, so if the warning says "Gradle needs upgrading too", upgrade Gradle as well. Fixing build errors caused by the upgrade is also a change to the existing build, so tell the user what you will change before fixing them. Once `./gradlew` builds, run `init` again (the files already filled in are not broken, and the Kotlin version recorded in the check list becomes the new one), and record the files you changed with `add changed` from 0-3. If the user decides not to upgrade, tell them that katachi cannot be installed in this project, and stop there.

The top level of the working directory holds only `check-list.html` and `project-code-base-report.html`, which people open, plus `tmp/`. Records and fetched files (`tmp/cache/`), and any notes or logs all go inside `<KATACHI_WORKDIR>/tmp/`. Do not add anything to the top level.

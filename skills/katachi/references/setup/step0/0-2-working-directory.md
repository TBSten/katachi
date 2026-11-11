### 0-2. Set up the working directory

The working directory holds the checklist and the report that people open, plus `tmp/` for your notes and logs. You make it yourself by copying two files from this skill. Run everything **from the Gradle root directory**.

**Pick the language of the two files.** They are for the user to read: use `ja` when the user works in Japanese, `en` otherwise. These steps stay in English either way. The `katachi` command answers in the language of the checklist you copy (it reads its `<html lang>`).

**Pick the place.** Prefer a place `.gitignore` already ignores, so that the working directory never becomes an `[UnexpectedFile]` in step 4:

```sh
git check-ignore -q .local/ && echo .local    # try .local/, .tmp/, tmp/, .scratch/ in this order. Keep the slash: `.local` misses a `.local/` line until the directory exists
```

Use `<the first ignored one>/install-katachi`. If none is ignored, use `tmp/install-katachi` and **tell the user it is visible to git**: katachi checks files that are untracked but not ignored, so as it is, the working directory itself fails step 4 (and the checklist and the report can end up in their commits). Suggest the line for `.gitignore`. Do not edit `.gitignore` yourself. If the user named a place, use it. If 0-1 already made `<workdir>/tmp/` for `katachi.main.kts` (no Homebrew), keep that place.

**Copy the two files.** `<skill>` is the directory of this skill (the one with `SKILL.md`), `<lang>` is `en` or `ja`:

```sh
mkdir -p <workdir>/tmp
cp <skill>/assets/<lang>/check-list.html <workdir>/check-list.html
cp <skill>/assets/<lang>/project-code-base-report.html <workdir>/project-code-base-report.html
```

**If the files are already there, do not copy over them.** They hold what an earlier session filled in. Read them and go on from where they stop (0-3 says how to read them).

The top level of the working directory holds only these two files and `tmp/`. Notes, logs and anything you fetch go inside `<workdir>/tmp/`.

**Fill in `meta` of both files** (0-3 says how to write the JSON). Find the values with these commands:

| Field | How to find it |
|---|---|
| checklist `meta.workdir` | The working directory, relative to the project root (`.local/install-katachi`) |
| checklist `meta.projectRoot` | The absolute path of the Gradle root (`pwd`) |
| checklist `meta.version` | The katachi version: `katachi --version` prints `katachi <version>` |
| checklist `meta.kotlin` | The Kotlin version `katachi doctor` printed in 0-1 (`Kotlin: <version>`) |
| checklist `meta.git` | `yes` when `git rev-parse --is-inside-work-tree` succeeds, otherwise `no` |
| checklist `meta.moduleDir` | Where the verification module goes, relative to the Gradle root: `architecture-test` unless the user named another place |
| checklist `meta.ranAt` | Now, as `YYYY-MM-DD HH:MM <zone>` |
| report `meta.projectName` | `rootProject.name` in `settings.gradle(.kts)` |
| report `meta.analyzedAt` | Now, as ISO 8601 |
| report `meta.fileCount` | `git ls-files \| wc -l` (a number) |
| report `meta.gradleRoot` / `meta.gitRoot` | The Gradle root / `git rev-parse --show-toplevel` |

`meta.jvmToolchain` is filled in 2-1 from `scaffold`'s output.

`katachi scaffold`, `katachi lint` and `katachi agent test` read `meta.moduleDir`, `meta.projectRoot` and `preferences` from this checklist, so keep them correct. Run those commands in the project root: they find the working directory below the current directory by themselves (from somewhere else, prefix them with `KATACHI_WORKDIR=<workdir>`; from a `git worktree` of the project, they check that worktree instead of `meta.projectRoot`). If they find two working directories, they stop and list both: pick one with `KATACHI_WORKDIR`, or ask the user to move the other one out of the project. Read `:architecture-test:` in these steps as the Gradle path of `meta.moduleDir` (`static-analysis/katachi` is `:static-analysis:katachi`), and `architecture-test/` as `meta.moduleDir`.

**If git is not there (`meta.git` is `no`), tell the user** that the changes cannot be undone if something goes wrong, and suggest committing or backing up first.

**Look for files git sees but does not track.** They become `[UnexpectedFile]` in step 4 too:

```sh
git ls-files --others --exclude-standard --directory --no-empty-directory
```

Leave out the working directory itself. Ask the user, for each of the rest, whether to declare it in the definition or add it to `.gitignore`.

**When Kotlin is too old.** If `doctor` warned something like "This project uses Kotlin 1.9.10. katachi needs Kotlin 2.2 or later", ask the user before starting the analysis in step 1. As it is, `scaffold` in 2-1 will stop, and the analysis up to that point is wasted. Upgrading Kotlin changes the existing build, so do not do it without the user's consent. Once they agree, upgrade Kotlin to 2.2 or later. Kotlin Gradle Plugin 2.2 needs Gradle 7.6.3 or later, so if the warning says "Gradle needs upgrading too", upgrade Gradle as well. Fixing build errors caused by the upgrade is also a change to the existing build, so tell the user what you will change before fixing them. Once `./gradlew` builds, run `katachi doctor` again, put the new version in `meta.kotlin`, and record the files you changed in `changedFiles` (0-3). If the user decides not to upgrade, tell them that katachi cannot be installed in this project, and stop there.

Once this is done, mark `0-1` and `0-2` done in the checklist (0-3 says how).

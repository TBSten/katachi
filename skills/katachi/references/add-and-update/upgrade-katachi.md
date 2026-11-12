# Upgrade katachi

katachi is still on 0.x, so a minor version can change the DSL. Upgrade on its own, not mixed into other changes, so a failure points at the upgrade.

## 1. Read what changed

Open the release notes of every version between the current one and the target: https://github.com/TBSten/katachi/releases . Note the breaking changes (they say what to write instead) and the experimental APIs the definition uses.

The current version is in the verification module's dependencies: `me.tbsten.katachi:katachi:<version>` and the `me.tbsten.katachi` plugin in `architecture-test/build.gradle.kts`, or the `katachi` entry in `[versions]` of `gradle/libs.versions.toml` when they are written through the catalog.

## 2. Bump every katachi artifact together

The plugin (`me.tbsten.katachi`), `katachi`, and `katachi-konsist` / `katachi-konture` if they are there, all to the same version. With the catalog that is usually one line in `[versions]`; written as literals, it is each line in the verification module's `build.gradle.kts`. Change only the version; this is the one edit to the scaffold-generated file that is expected.

Check the new version's requirements in its release notes (the Kotlin version katachi needs, JDK 17 or later). If the project's Kotlin is too old, stop and ask the user: upgrading Kotlin changes the existing build.

## 3. Run the check and fix what the upgrade broke

```sh
./gradlew :architecture-test:test -Dkatachi.maxViolations=all
```

- **A compile error in the definition**: an API changed. Find the replacement in the release notes or the API reference (https://tbsten.github.io/katachi/api-docs/llms.txt); do not guess from a similar name.
- **New violations with no change to the project**: a newer katachi checks something it did not before, or reads a layout differently. Read what the release notes say about it. Fix the definition when the project is right; when the project is wrong, list it for the user ([../fix-violations/README.md](../fix-violations/README.md)).
- **Warnings that are new** (`[AmbiguousLayout]` and the like) do not fail the check, but say what they found; fix them or tell the user.

If the project commits the generated documentation, regenerate it (`./gradlew :architecture-test:katachiDocs`): the output can change between versions.

## 4. Keep the tools in step

The `katachi` command and this skill follow the same version numbers. Tell the user how to update them, rather than doing it yourself:

- `brew upgrade katachi` (without Homebrew: change the version in `@file:DependsOn` of the `katachi.main.kts` setup 0-1 wrote)
- `gh skill update katachi` (or `npx skills update`, for a skill installed with `npx skills`)

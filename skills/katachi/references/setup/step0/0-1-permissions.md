### 0-1. Get all permissions up front

These steps repeat network access, file creation, and running Gradle many times over. Checking in every time would leave the user doing nothing but approving requests endlessly.

**Do not ask for permission in prose — actually run a harmless dummy of each.** The tool running you asks for permission per kind of command. Run each one once up front, and most of the real commands that follow are not asked about.

Run the following **from the Gradle root directory**, **one at a time, separately.** Do not join them into a single line — a single line only earns you one kind of permission.

```sh
# 1. Is the katachi command installed (the first run fetches its body from Maven Central, so it takes a few seconds)
katachi --version
```

```sh
# 2. Creating a file under the project
touch .katachi-probe && rm .katachi-probe
```

```sh
# 3. Running Gradle
./gradlew --version
```

**When the project needs a JDK other than the `java` on PATH**, prefix every `./gradlew` and `katachi` call from here on with the same `JAVA_HOME=<path>`. Shell variables do not persist across calls. `doctor` prints `Gradle's JDK: N (source)` and `katachi agent test` prints `JDK: N (source)`; if the source is `JAVA_HOME`, add it every time.

```sh
# 4. Running katachi (and checking Gradle's JDK and the project's Kotlin)
katachi doctor --lang en    # --lang ja for a user who works in Japanese
```

`doctor` in 4 checks the version of the JDK that runs Gradle, the project's Kotlin version, and whether `git` is available (recommended). **If anything falls short, it says so, with hints on how to fix it.** Pass that output on to the user, have them fix it, and run 4 again. Keep the `Kotlin:` line: 0-2 records it.

Both the working directory and the verification module (`architecture-test/` by default) are created under the project root, so 2 earns permission for both. **The probes leave no trace.** Do not write a probe that creates a directory (an empty verification module directory left behind makes step 2's `scaffold` stop with "already exists").

**If any of them is blocked, tell the user what was refused.** Without 1 you cannot start, without 2 you cannot create files, without 3 you cannot verify, and without 4 you cannot run any of the steps below.

#### When 1 cannot find `katachi`

**Do not install anything yourself.** Ask the user, with the links below, and run 1 again once they are done. Do not invent another workaround either, such as looking for a jar to put in place.

**On macOS (or wherever Homebrew is already there): ask for Homebrew.**

```sh
brew install tbsten/tap/katachi    # Kotlin comes with it
```

- Homebrew itself: https://brew.sh/
- The katachi command: https://tbsten.github.io/katachi/guides/cli-and-script/cli/

If `katachi` then prints `kotlin is not on PATH`, ask for `brew install kotlin`.

**Elsewhere (Linux or Windows without Homebrew): ask for the Kotlin command line compiler, and run katachi as a Kotlin script.** The `katachi` command is only a launcher for a Kotlin script that pulls `katachi-script` from Maven Central, so the same script run with `kotlin` does everything it does.

- The Kotlin command line compiler (SDKMAN!, the zip from GitHub releases, and so on): https://kotlinlang.org/docs/command-line.html
- katachi-script (running katachi from `*.main.kts`): https://tbsten.github.io/katachi/guides/cli-and-script/katachi-script/

Once `kotlin -version` works, pick the working directory as in 0-2 (only its place: run `mkdir -p <workdir>/tmp`, copy nothing yet) and write this script there. `<version>` is the latest release on https://github.com/TBSten/katachi/releases without its `v`.

```kotlin
// <workdir>/tmp/katachi.main.kts
@file:DependsOn("me.tbsten.katachi:katachi-script:<version>")

me.tbsten.katachi.script.main(args)
```

From here on, **read every `katachi …` in these steps as `kotlin <workdir>/tmp/katachi.main.kts …`**, with the same arguments (`kotlin <workdir>/tmp/katachi.main.kts --version` is 1). Write that line into `<workdir>/tmp/katachi-command.txt`, so that a later session runs katachi the same way. Three differences from the `katachi` command:

- `kotlin` takes every argument that starts with `-D`, `-J` or `@` for itself, even after the script. Put `katachi-escaped:` in front of such an argument (`… agent test -- katachi-escaped:-Dkatachi.baseline.update=true`); katachi removes it.
- The first run downloads katachi-script and compiles the script, so it takes a while. Later runs use the cache.
- On Windows, `katachi agent test` starts `./gradlew` through `sh`, so run these steps in WSL or Git Bash. Without either, run `gradlew.bat <module>:test --console=plain -Dkatachi.maxViolations=all` yourself and save its output as `<workdir>/tmp/test-<name>.log` (`katachi agent compare-violations` reads those).

If all four pass, the bulk of the later commands (katachi, Gradle, files under the project) will not be asked about. Fetching the documentation (curl / WebFetch), `git`, `gh` and `python3` / `jq` are other kinds of command and may still be asked about once each: tell the user so, rather than promising that nothing more will be asked.

**Existing source code is never rewritten.** The only times you touch it are when fixing a build error unrelated to katachi in step 4, and during the implementation task in step 6-A. If you are going to fix an architecture violation itself in step 4, check with the user again at that point.

At this point the working directory does not exist yet. Record the check at the end of the next step, together.

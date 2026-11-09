### 0-1. Get all permissions up front

These steps repeat network access, file creation, and running Gradle many times over. Checking in every time would leave the user doing nothing but approving requests endlessly.

**Do not ask for permission in prose — actually run a harmless dummy of each.** The tool running you asks for permission per kind of command. Run each one once up front, and the real commands that follow are never asked about.

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

> `# In a project that needs a JDK other than the java on PATH, prefix every ./gradlew and katachi call from here on with the same JAVA_HOME=<path>. Shell variables don't persist across calls. doctor and test print "JDK: N (source)"; if the source is JAVA_HOME, add it every time.`

```sh
# 4. Running katachi (and checking Gradle's JDK and the project's Kotlin)
katachi doctor --lang en    # --lang ja for a user who works in Japanese
```

`doctor` in 4 checks the version of the JDK that runs Gradle, the project's Kotlin version, and whether `git` is available (recommended). **If anything falls short, it says so, with hints on how to fix it.** Pass that output on to the user, have them fix it, and run 4 again. `init` checks Kotlin and `git` the same way.

Both the working directory and the verification module (`architecture-test/` by default) are created under the project root, so 2 earns permission for both. **The probes leave no trace.** Do not write a probe that creates a directory (an empty verification module directory left behind makes step 2's `scaffold` stop with "already exists").

**If any of them is blocked, tell the user what was refused.** Without 1 you cannot start, without 2 you cannot create files, without 3 you cannot verify, and without 4 you cannot run any of the steps below.

**If 1 cannot find `katachi`, do not try to install it yourself.** Do not invent a workaround either, such as fetching a script and running it, or looking for a jar to put in place. Ask the user to run `brew install tbsten/tap/katachi` (it needs Homebrew; Kotlin comes with it), and run 1 again once it is installed. The same goes for `kotlin is not on PATH`: ask the user to run `brew install kotlin`.

If all four pass, no permission will be asked for in any later step.

**Existing source code is never rewritten.** The only times you touch it are when fixing a build error unrelated to katachi in step 4, and during the implementation task in step 6-A. If you are going to fix an architecture violation itself in step 4, check with the user again at that point.

At this point the working directory does not exist yet. Record the check at the end of the next step, together.

### 1-3. Decide the writing preferences

Decide first the parts of the definition's style that depend on the user's preferences. `scaffold` and `lint` read this record.

```sh
katachi agent preferences
```

Paste the output to the user as is and ask once: "answer only the ones you want to change from the recommendation". Do not ask one item at a time.

| key | Question | Values | Recommended | Read by |
|---|---|---|---|---|
| `nestedGroups` | Nest groups? | `yes` / `no` | `yes` | `lint` |
| `moduleDir` | Where the verification module goes | Path relative to the Gradle root | `architecture-test` | `scaffold` and everything after |
| `testFramework` | Test framework | `junit5` / `kotest` | Detected from the project | `scaffold` |
| `dependencyStyle` | How dependencies are written | `catalog` / `literal` | `catalog` if `gradle/libs.versions.toml` exists | `scaffold` |
| `constraintNames` | Put the question's number in constraint names? | `plain` / `numbered` | `plain` | 3-2 (you read it) |
| `oneRolePerFile` | One Role per file? | `yes` / `no` | `yes` | `lint` (3-3) |

Record the answers and mark it done. If the user says the recommendations are fine, skip `set` and just run `check`.

```sh
katachi agent preferences set nestedGroups=yes testFramework=kotest
katachi agent check 1-3
```

Passing an unknown key or value stops it and lists the values that are available.

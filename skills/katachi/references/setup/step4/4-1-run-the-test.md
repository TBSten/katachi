### 4-1. Run the test

```sh
katachi agent test
```

Read the summary. Do not grep the log. Only when you need the full text, read the path on the summary's `Log:` line. The summary shows the result line, the `[MissingDescription]` count (places of a Role that may live in several places, with no `description`; a missing `summary` is not counted), and the diff against the previous run (violations added and violations removed). With 0 violations it is `Result: the check passed (0 violations)`. If the check passes with warnings (`[AmbiguousLayout]` and the like, which never fail the check), a `Warnings: N` line and the warnings' headings are printed too. Decide whether to fix or keep warnings by the policy in 4-2 as well, and if you keep them, say so in the warning on step 4 (0-3, "Progress").

### 4-1. Run the test

```sh
katachi agent test
```

Read the summary. Do not grep the log. Only when you need the full text, read the path on the summary's `Log:` line (ja: `ログ:`). The summary shows the result line (`Result:`, ja: `結果:`) and the diff against the previous run (`Difference from the previous run (vs previous.log):`, ja: `前回との差分（previous.log 比）:`; violations added and violations removed). With 0 violations it is `Result: the check passed (0 violations)` (ja: `結果: 検査は通りました（違反 0 件）`). If the check passes with warnings (`[AmbiguousLayout]` and the like, which never fail the check), a `Warnings: N` line (ja: `警告: N 件`) and the warnings' headings are printed too. Decide whether to fix or keep warnings by the policy in 4-2 as well, and if you keep them, say so in the warning on step 4 (0-3, "Progress").

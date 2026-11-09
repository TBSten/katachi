## 5. Review the checklist

```sh
katachi agent verify
```

This lists unfilled fields and unchecked items. **If nothing is printed, you're done** (exit code 0). Anything printed means you either forgot to investigate it or forgot to record it. Fill it in and run again.

**Do not read the JSON yourself and eyeball it.** Item 5-1, "reviewed the entire checklist," refers to getting `verify` to pass.

**Once it passes, `verify` automatically marks 5-1 / 5-2 as done.** There's no need to run `check` yourself.

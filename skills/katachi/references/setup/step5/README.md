## 5. Review the checklist

Go through the JSON of both blocks (checklist and report) and list what is left. **Check every point below; do not stop at the first one.**

- Checklist: every item of steps 0 to 4 is `done: true` (step 6 is optional and does not count)
- Checklist: no key of `meta` is `null` or empty
- Report: no key of `meta` or `overview` is `null` or empty
- Report: `architecture.name` and `architecture.rationale` are filled in
- Report: `modules`, `directoryTree` and `roles` are filled in
- Report: every entry of `roles` has `importance`, `name`, `layout`, `naming` and `group`
- Report: every entry of `allowed` and `forbidden` of every Role is an object with `by` (3-4). An entry left as a plain string was not gone through: decide how it is checked, or give it `by: "none"` with the reason in `how`
- Report: every Role without a pattern in its file name has at least one entry with `by` other than `none` (no catch-all Role)

Anything left means you either forgot to investigate it or forgot to record it. Fill it in and go through the list again. Also look at the steps that carry a `warning`: each says what you left as it is, and step 6 reports it.

Once nothing is left, mark `5-1` and `5-2` done.

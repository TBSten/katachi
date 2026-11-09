### 6-0. Fix the definition after review

This section is for when the user has looked at the `summary` output and the definition and sent back comments. It has no checklist item.

There is one thing to do. Check the user's comments against the questions (`katachi agent answers`) and the violations left in place (the checklist's `violations`), fix the definition, and confirm the counts with `katachi agent test`. Look at the summary's diff to see that the fixed definition has not added any `[UnexpectedFile]`.

If the user's reply answers a question, record it with `katachi agent answers set <id> "<answer>"` before fixing (no `--target` needed; the id decides where it goes). If a comment raises a new question, add it with `add question`.

> **The session may end here.** The next session runs `katachi agent summary` in the root of the project, and reads the section named in the "Resume" field of the output (its file is in the table in `references/setup/README.md`; `katachi agent guide <section ID>` prints the same text). The previous session's results are in the checklist and the report. Do not rely on the conversation's memory.

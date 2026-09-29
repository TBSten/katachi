[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Layout snapshot

Text recording the flattened result of `layout { }`, for katachi's own self-verification

Text that flattens every `layout { }` of this definition and lists "role, path, kind and
whether it is required" one entry per line. `LayoutSnapshotSpec` rebuilds it each time and
compares it against the recorded content.

It exists to show that what is checked has not changed when the way a role is written changes.
As long as this diff stays empty, rewriting to sugar (`":".module { }` or `mainSourceSet`) is
safe.

Never write this by hand; `LayoutSnapshotSpec` writes it. To update it after an intended
change, run `./gradlew :architecture-test:test -Dkatachi.snapshot.update=true`. The same
command is written in the comment at the top of the file.

A project adopting katachi does not need it. It is a device for katachi itself to see that it
has not broken the sample, and its purpose differs from that of `ProjectArchitectureTest`.

## Placement

| Module | Path | When to use |
|---|---|---|
|  | `snapshots/layout.txt` |  |

## Examples

- `snapshots/layout.txt` ... The full text of the flattened layout

## Forbidden contents

- Expected values written by hand. When a diff appears, the fix is either on the definition
  side or a regeneration of the snapshot. Editing the text directly to make things agree
  defeats the sentinel
- Other kinds of records. The current `layout { }` allows only the single file
  `snapshots/layout.txt`, so adding one starts with rewriting the role

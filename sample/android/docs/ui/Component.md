[katachi-sample-android](../README.md) / [UI (shared layer)](README.md)

# Shared component

A part in the component package of the :ui module, used across features

A Compose part called from several screens. Like `AppButton`, it is a thin layer over
Material3, so that a change of shape or emphasis reaches every screen at once.

How the look is chosen is expressed with types inside this package. `AppButton` takes an
`emphasis: AppButtonEmphasis` and handles `Filled` versus `Outlined` internally. The aim
of this role is that callers need not pick between Material3's `Button` and
`OutlinedButton` themselves, so a part, the enum for it and its `@Preview` go in the
same file.

A `@Preview` is a `private` `@Composable` at the end of the same file as its part, with
the body wrapped in `PreviewRoot { }` from the `preview` package of `:ui`, so the theme
and background are decided in one place. It passes only state. This convention is not
checked in this sample: "is private" and "is wrapped in `PreviewRoot`" cannot be
expressed by where a file sits, and would take `konsist { }` (see sample/jvm).

File names are not restricted to `*.kt`, because parts are expected to multiply.

Can be generated from a template. The whole file name is `capture("name")`, so `--arg
template=Component --arg name=AppLabel` produces `AppLabel.kt` (starting with `App` is a
convention of this role, not something the layout enforces).

## Placement

| Module | Path | When to use |
|---|---|---|
| `:ui` | `src/main/kotlin/**/component/*.kt` |  |

## Examples

- `AppButton` ... The app-wide button

## Allowed contents

Only what two or more features use, or are decided to use, belongs here. A part used by
a single screen goes in that feature module.

## Forbidden contents

- State held in the part. Pass values and callbacks (`text`, `onClick`) instead of
  keeping them with `remember`
- Dependencies on `:data` or `:feature:*`. `:ui` knows neither the layers below nor the
  features beside it
- Hard-coded colors or typography. Read them from `MaterialTheme` (see the Theme role)

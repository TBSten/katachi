[katachi-sample-kmp](../README.md) / [Data](README.md)

# Actual implementation

The :data module's platform package. The actual implementations in androidMain / iosMain

The `actual` that each platform supplies for an ExpectDeclaration: one file in
`androidMain` and one in `iosMain`, in the same package
(`com.example.kmp.data.platform`) as the `expect` for it to compile. The package is part
of the shape of this role.

The two lines of the layout differ only in the source set name. `"<name>".sourceSet`
just points at `src/<name>`, which is exactly what a KMP source set is. The `.android` /
`.ios` in the file names are a convention, not a Kotlin requirement, but once they are
written in the pattern here they become a rule of this project.

Both source sets are one role: an Android `actual` and an iOS `actual` are the same kind
of file, the platform's answer to the same `expect`. Only the location differs.

A caveat: the iOS `actual` is not compiled on CI, because none of the tasks CI runs
touches a Kotlin/Native build (the reason is in `app/ios/README.md`). And every line of
this role's layout is a wildcard, so even if the whole `iosMain` side were forgotten,
katachi would not report `[MissingFile]`. Only the compiler can check that expect and
actual match, and that compiler does not run on CI; that is the hole here.

## Placement

| Path | When to use |
|---|---|
| `data/src/androidMain/kotlin/com/example/kmp/data/platform/*.android.kt` |  |
| `data/src/iosMain/kotlin/com/example/kmp/data/platform/*.ios.kt` |  |

## Examples

- `PlatformInfo.android.kt` ... The actual for Android
- `PlatformInfo.ios.kt` ... The actual for iOS

## Allowed contents

- The `actual` of an `expect` declared in commonMain, calling the platform API

## Forbidden contents

- A declaration with no `expect` in commonMain. The common side cannot see it
- Anything about UI. This sample has no platform difference around screens at all

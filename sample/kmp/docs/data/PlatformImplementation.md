[katachi-sample-kmp](../README.md) / [Data](README.md)

# Platform implementation

The :data module's platform package. The expect declaration in commonMain and the actual implementations in androidMain / iosMain sit in the same package

A role that exists only because this sample is KMP. Write `expect` in `commonMain` and
put one `actual` each in `androidMain` and `iosMain`. The three files must be in the same
package (`com.example.kmp.data.platform`) to compile, so the package is part of the shape
of this role.

The three lines of the layout differ only in the source set name. `"<name>".sourceSet`
just points at `src/<name>`, which is exactly what a KMP source set is. The `.android` /
`.ios` in the file names are a convention, not a Kotlin requirement, but once they are
written in the pattern here they become a rule of this project.

A caveat: the iOS `actual` is not compiled on CI, because none of the tasks CI runs
touches a Kotlin/Native build (the reason is in `app/ios/README.md`). And every line of
this role's layout is a wildcard, so even if the whole `iosMain` side were forgotten,
katachi would not report `[MissingFile]`. Only the compiler can check that expect and
actual match, and that compiler does not run on CI; that is the hole here.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:data` | `src/commonMain/kotlin/**/platform/*.kt` |  |
| `:data` | `src/androidMain/kotlin/**/platform/*.android.kt` |  |
| `:data` | `src/iosMain/kotlin/**/platform/*.ios.kt` |  |

## Examples

- `PlatformInfo.kt` ... The expect declaration in commonMain
- `PlatformInfo.android.kt` ... The actual for Android
- `PlatformInfo.ios.kt` ... The actual for iOS

## Allowed contents

- A thin entry to a platform API that cannot be written from common
- The `expect` declaration that shows that entry to the common side

## Forbidden contents

- Anything that can be written in common. Every `expect`/`actual` adds two
  implementations to write, and the more shareable things you bring in, the less it pays off
- Anything about UI. This sample has no platform difference around screens at all

[katachi-sample-kmp](../README.md) / [App](README.md)

# Android manifest

AndroidManifest.xml of :app:android, which declares the app to Android

The `AndroidManifest.xml` of `:app:android`. Its shape is decided by the Android build
system, not by this project. There is only one per app, so it is named exactly and a
second one is a violation.

`:app:android` is the only module with a manifest in this sample, so this role looks
only at it. If another module needs one, widen the role then.

## Placement

| Path | When to use |
|---|---|
| `app/android/src/main/AndroidManifest.xml` |  |

## Examples

- `AndroidManifest.xml` ... The app manifest

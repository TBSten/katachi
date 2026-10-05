[katachi-sample-android](../README.md) / [Entrypoint layer](README.md)

# Android manifest

AndroidManifest.xml of :app, which declares the app to Android

The `AndroidManifest.xml` of `:app`. Its shape is decided by the Android build system,
not by this project. There is only one per app, so it is named exactly and a second one
is a violation.

`:app` is the only module with a manifest in this sample, so this role looks only at
`:app`. If another module needs one, widen the role then.

## Placement

| Path | When to use |
|---|---|
| `app/src/main/AndroidManifest.xml` |  |

## Examples

- `AndroidManifest.xml` ... The app manifest

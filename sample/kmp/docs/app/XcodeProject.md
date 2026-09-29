[katachi-sample-kmp](../README.md) / [App](README.md)

# Xcode project

Under app/ios. Outside Gradle's management and not checked

The iOS app side. `app/ios` is not a Gradle module. `settings.gradle.kts` deliberately
does not include it, so it cannot be written with a module path and cannot be required to
have a `build.gradle.kts`. It is declared as a plain directory key and `ignore()` stops
the check inside it.

Declaring it while not checking it is the showcase of this role. The only way to say
"do not look here" in katachi is to declare the directory as a role with a summary; if
nothing is written, everything under `app/ios` becomes "files no role claims".

Inside are the Swift sources and `Info.plist`, that is, only the shape of an iOS app.
`iosApp.xcodeproj/` is not committed. A handwritten `project.pbxproj` breaks in ways
Xcode cannot open, and since this sample does not build for iOS, it would be dead weight
anyway. Create one in Xcode if you want to run it.

## Placement

| Module | Path | When to use |
|---|---|---|
|  | `app/ios` |  |

## Examples

- `iosAppApp.swift` ... The SwiftUI entry point
- `ContentView.swift` ... The screen on the iOS side

## Allowed contents

- What Xcode owns: Swift sources, `Info.plist` and the asset catalog

## Forbidden contents

- Kotlin code. Put code to be shared in a KMP module such as `:data` and hand it over
  as a framework (this sample does not set that up)

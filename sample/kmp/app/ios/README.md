# app/ios

This directory is **not a Gradle module**. It is where the Xcode project lives, and it is
deliberately invisible to `settings.gradle.kts`.

That is the point: a real KMP repository mixes directories Gradle manages with directories
it does not, and katachi has to describe both. The `app/XcodeProject` role in
`app/android/src/test/kotlin/com/example/kmp/app/ProjectArchitecture.kt` is the one that
covers this directory; from implementation step 2 on it will be declared as ignored,
because Xcode — not katachi — owns what is in here.

## What is (and is not) committed

Committed: a few Swift files and an `Info.plist`, i.e. the *shape* of an iOS app.

Not committed: `iosApp.xcodeproj/`. A hand written `project.pbxproj` breaks in ways Xcode
cannot open, and since this sample never builds for iOS it would only be dead weight.
Create it with Xcode if you actually want to run the app.

```
app/ios/
  README.md
  iosApp/
    iosApp.xcodeproj/        # not committed; create it with Xcode
    iosApp/
      iosAppApp.swift
      ContentView.swift
      Info.plist
```

## Why iOS is not built in CI

katachi is a JVM library, so no katachi test can run on iOS. The Kotlin iOS targets are
still declared (`iosArm64()` / `iosSimulatorArm64()` in the KMP modules) so that the module
graph is a realistic one, but a Linux CI runner cannot compile them and a clean macOS
runner would first download the Kotlin/Native distribution.

CI therefore runs `:app:android:testDebugUnitTest`, which never reaches an iOS task.

## Connecting the shared code (later)

Add `binaries.framework { baseName = "Shared" }` to a module such as `:data` and call
`./gradlew :data:embedAndSignAppleFrameworkForXcode` from an Xcode Run Script phase. Not
set up here, since the sample does not build for iOS.

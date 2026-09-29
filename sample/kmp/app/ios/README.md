# app/ios

This directory is **not a Gradle module**. It is the place for the Xcode project, and it is
deliberately made invisible to `settings.gradle.kts`.

That is the reason this directory exists. A real KMP repository mixes directories Gradle manages with
directories it does not, and katachi has to be able to write both. The role that owns this directory is
`app/XcodeProject`
([`architecture-test/src/test/kotlin/com/example/kmp/roles/XcodeProjectRole.kt`](../../architecture-test/src/test/kotlin/com/example/kmp/roles/XcodeProjectRole.kt)),
which declares `"app/ios" { ignore() }` to stop the check inside it. What goes in it is decided by Xcode, not by katachi.

## What is committed and what is not

Committed are a few Swift files and `Info.plist`: only the **shape** of an iOS app.

`iosApp.xcodeproj/` is not committed. A handwritten `project.pbxproj` breaks in ways Xcode cannot open,
and since this sample does not build for iOS, it would be dead weight anyway. When you want to run the app,
create the project in Xcode.

```
app/ios/
  README.md
  iosApp/
    iosApp.xcodeproj/        # not committed; create it in Xcode
    iosApp/
      iosAppApp.swift
      ContentView.swift
      Info.plist
```

## Why CI does not build iOS

katachi is a JVM library, so katachi's tests do not run on iOS. That is why the definition lives in
`:architecture-test`, a plain `kotlin("jvm")` module; no other module has a JVM target that could hold it.
The KMP modules declare the Kotlin iOS targets (`iosArm64()` / `iosSimulatorArm64()`), so the module
structure is realistic. However, they cannot be compiled on a Linux CI runner, and even a clean macOS runner
would first have to download the Kotlin/Native distribution. The UI modules use Compose Multiplatform, so
compiling for iOS would also compile the Compose Kotlin/Native klibs, which is slower still.

So what CI (`./gradlew checkSampleKmp` at the root) runs is `:architecture-test:test` (the katachi check),
`:app:android:testDebugUnitTest` (the sample's own unit tests), `:architecture-test:katachiLayout` (the layout check) and
`:architecture-test:katachiDocs --arg mode=check` (whether the generated documents are up to date),
plus generating from templates and checking the baseline. None of them reaches an iOS task. Lifecycle tasks such as `check`
pull in iOS tasks, so they are not used.

## If you wire up the shared code (not done yet)

Add `binaries.framework { baseName = "Shared" }` to a module such as `:data`, and call
`./gradlew :data:embedAndSignAppleFrameworkForXcode` from the Run Script phase of Xcode. This sample does
not build for iOS, so it is not set up.

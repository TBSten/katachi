[katachi-sample-kmp](../README.md)

# App

The Android app that Gradle builds and the iOS app that Xcode builds

The two applications that actually ship. Both are "apps", but they are written so
differently that they share one group only for that reason.

`:app:android` is a Gradle module, so it is written with a module path. `app/ios` is
deliberately not included by `settings.gradle.kts`, so no module path exists for it; it is
declared as a plain directory key and `ignore()` stops the check there. A real KMP
repository mixes directories Gradle manages with directories it does not, and this group
shows that both can be written.

`:app:android` has one more trait. It is the only nested module path in this sample, and
also the only module whose package does not follow the module path (`com.example.kmp.app`,
not `com.example.kmp.app.android`). That is why Entrypoint and AndroidResource write the
package out instead of using `modulePackage`. Saying "this one is different" is kinder to
the reader than bending the rule to fit.

| Role | Summary |
|---|---|
| [Entrypoint](./Entrypoint.md) | The starting point of the Android app: a ComponentActivity and the whole-app @Composable it calls via setContent |
| [Android resources](./AndroidResource.md) | AndroidManifest.xml and the resource XML under res/. Only :app:android has them |
| [Xcode project](./XcodeProject.md) | Under app/ios. Outside Gradle's management and not checked |

## Placement in this group

```
:app:android
  src/main/
    kotlin/com/example/kmp/app/*.kt  Entrypoint
    AndroidManifest.xml              Android resources
    res/*/*.xml                      Android resources

app/ios/                             Xcode project
```

## Allowed contents

- The entry point and the assembly of the whole app (`AppRoot`)
- Resources the Android build requires

## Forbidden contents

- The screens themselves. Screens live in the feature modules; this group only calls a Route
- Logic meant to be shared. Anything written here is invisible to iOS

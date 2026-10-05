[katachi-sample-android](../README.md)

# Entrypoint layer

What :app holds: the launch entrypoints and the Android resources

The `:app` module itself. It is the only module in this app that may know every feature:
`MainActivity` builds the navigation graph and connects `:feature:home` and
`:feature:settings`.

It gathers two things that can only live in `:app`. The types Android touches at launch
(`MainActivity` / `MainApplication`), and the non-Kotlin files an application needs
(`AndroidManifest.xml` / `res/` / `proguard-rules.pro`). Both come from "being the
application", not from any feature of the app.

Screen contents are not here. `:app` sits at the top of the dependency graph and only
refers downward, so replacing `:app` as a whole does not break the modules below it. If
Composables start piling up here, they should move into a feature module.

`:app` is one of the two modules whose package cannot be derived from the module path,
so it writes `com/example/sample` directly. It is the application itself and has no
mapping like `:ui` to `com.example.sample.ui` (the other one is `:architecture-test`).

| Role | Summary |
|---|---|
| [Activity entrypoint](./ActivityEntrypoint.md) | The single Activity Android launches, kept in :app |
| [Application entrypoint](./ApplicationEntrypoint.md) | The single Application class Android creates at process start, kept in :app |
| [Android manifest](./AndroidManifest.md) | AndroidManifest.xml of :app, which declares the app to Android |
| [Proguard rules](./ProguardRules.md) | proguard-rules.pro of :app, the rules the code shrinker reads |
| [Android resources](./AndroidResource.md) | The res/ tree of :app, whose inner structure the Android resource system decides |

## Placement in this group

```
app/
  src/main/
    kotlin/com/example/sample/
      MainActivity.kt     Activity entrypoint
      MainApplication.kt  Application entrypoint
    AndroidManifest.xml   Android manifest
    res/                  Android resources
  proguard-rules.pro      Proguard rules
```

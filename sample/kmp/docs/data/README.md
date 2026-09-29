[katachi-sample-kmp](../README.md)

# Data

The :data module. The way into data, and the parts whose implementation differs per platform

One `:data` module split into packages. `user` holds the repositories and `platform` the
expect/actual pair. Like `:ui`, it is split by package, not by module.

Both roles sit in the same group because both are "a way to bring in values from outside
the app". `UserRepository` is the way into data and `platformName()` the way into
outside information about the runtime, and to the caller (the ViewModel) both look like a
single dependency on `:data`.

Confining KMP platform differences to this group is the design of this sample. Only
`:data` has `androidMain` / `iosMain`; the UI side (`:ui` and the features) has no
`expect`/`actual` at all. When something platform-specific is needed, push it down here
instead of into a screen.

| Role | Summary |
|---|---|
| [Repository](./Repository.md) | The :data module's user package. The way into data, placed as an interface and an implementation |
| [Platform implementation](./PlatformImplementation.md) | The :data module's platform package. The expect declaration in commonMain and the actual implementations in androidMain / iosMain sit in the same package |

## Placement in this group

```
:data
  src/
    commonMain/kotlin/**/
      user/
        *Repository.kt                           Repository
        *RepositoryImpl.kt                       Repository
      platform/*.kt                              Platform implementation
    androidMain/kotlin/**/platform/*.android.kt  Platform implementation
    iosMain/kotlin/**/platform/*.ios.kt          Platform implementation
```

## Forbidden contents

- UI types. `UiState` lives in the core package of `:ui`, and `:data` does not know about it
- Test doubles. `FakeUserRepository` lives in `:testing`
- `@Composable`. The `:data` build script does not apply the Compose plugin

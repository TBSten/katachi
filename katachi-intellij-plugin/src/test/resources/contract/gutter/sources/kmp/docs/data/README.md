[katachi-sample-kmp](../README.md)

# Data

The :data module. The way into data, and the parts whose implementation differs per platform

One `:data` module split into packages. `user` holds the repositories (interface and
implementation) and `platform` the expect/actual pair. Like `:ui`, it is split by package, not by module.

All four roles sit in the same group because they are "a way to bring in values from outside
the app". `UserRepository` is the way into data and `platformName()` the way into
outside information about the runtime, and to the caller (the ViewModel) both look like a
single dependency on `:data`.

Confining KMP platform differences to this group is the design of this sample. Only
`:data` has `androidMain` / `iosMain`; the UI side (`:ui` and the features) has no
`expect`/`actual` at all. When something platform-specific is needed, push it down here
instead of into a screen.

| Role | Summary |
|---|---|
| [Repository interface](./RepositoryInterface.md) | The :data module's user package. The interface through which the app reads data |
| [Repository implementation](./RepositoryImplementation.md) | The :data module's user package. The implementation of a repository interface |
| [Expect declaration](./ExpectDeclaration.md) | The :data module's platform package. The expect declaration in commonMain that the common side calls |
| [Actual implementation](./ActualImplementation.md) | The :data module's platform package. The actual implementations in androidMain / iosMain |

## Placement in this group

```
data/src/
  commonMain/kotlin/com/example/kmp/data/
    user/
      *Repository.kt                                             Repository interface
      *RepositoryImpl.kt                                         Repository implementation
    platform/*.kt                                                Expect declaration
  androidMain/kotlin/com/example/kmp/data/platform/*.android.kt  Actual implementation
  iosMain/kotlin/com/example/kmp/data/platform/*.ios.kt          Actual implementation
```

## Forbidden contents

- UI types. `UiState` lives in the core package of `:ui`, and `:data` does not know about it
- Test doubles. `FakeUserRepository` lives in `:testing`
- `@Composable`. The `:data` build script does not apply the Compose plugin

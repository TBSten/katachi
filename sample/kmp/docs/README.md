# katachi-sample-kmp documentation

## Document map

### [Feature](./feature/README.md)

One module per screen. :feature:<name> always holds a Screen / ViewModel / Route

- [Screen](./feature/Screen.md)
- [ViewModel](./feature/ViewModel.md)
- [Route](./feature/Route.md)
- [Screen component](./feature/FeatureComponent.md)

### [UI](./ui/README.md)

:ui and :navigation. Shared UI that belongs to no screen, and the definition of destinations

- [Shared component](./ui/Component.md)
- [Theme](./ui/Theme.md)
- [UI core](./ui/UiCore.md)
- [Preview](./ui/Preview.md)
- [Preview root](./ui/PreviewRoot.md)
- [Navigation](./ui/Navigation.md)

### [Data](./data/README.md)

The :data module. The way into data, and the parts whose implementation differs per platform

- [Repository](./data/Repository.md)
- [Platform implementation](./data/PlatformImplementation.md)

### [Testing support](./testing/README.md)

Test doubles, the test code itself, the katachi architecture definition, the documents and snapshot written from it, and the ledger of shelved violations

- [Fake](./testing/Fake.md)
- [Test code](./testing/Test.md)
- [Architecture definition](./testing/ArchitectureDefinition.md)
- [Generated documentation](./testing/GeneratedDocumentation.md)
- [Layout snapshot](./testing/LayoutSnapshot.md)
- [Baseline (ledger of shelved violations)](./testing/BaselineFile.md)

### [App](./app/README.md)

The Android app that Gradle builds and the iOS app that Xcode builds

- [Entrypoint](./app/Entrypoint.md)
- [Android resources](./app/AndroidResource.md)
- [Xcode project](./app/XcodeProject.md)

## Feature

One module per screen. :feature:<name> always holds a Screen / ViewModel / Route

- [Screen](./feature/Screen.md) ... The @Composable of one screen. Subscribes to the ViewModel's StateFlow and draws by combining Components
- [ViewModel](./feature/ViewModel.md) ... An androidx.lifecycle.ViewModel that holds screen state. Converts values fetched from the Repository into a UiState and exposes it as a StateFlow
- [Route](./feature/Route.md) ... Ties a screen to a navigation Destination and also takes on creating the ViewModel
- [Screen component](./feature/FeatureComponent.md) ... A @Composable used by only one screen. Placed as <Name>*.kt in the component package of the commonMain of :feature:<name>

## UI

:ui and :navigation. Shared UI that belongs to no screen, and the definition of destinations

- [Shared component](./ui/Component.md) ... The component package of the :ui module. @Composable parts used by several screens
- [Theme](./ui/Theme.md) ... The theme package of the :ui module. The MaterialTheme setup and the design tokens for color and spacing
- [UI core](./ui/UiCore.md) ... The core package of the :ui module. The screen-independent foundation of the UI, such as UiState
- [Preview](./ui/Preview.md) ... A private @Composable annotated with @Preview. Placed in <Target>Preview.kt in the same package as the target Composable, with the content wrapped in PreviewRoot
- [Preview root](./ui/PreviewRoot.md) ... The preview package of the :ui module. Wraps the content of a @Preview in AppTheme and Surface
- [Navigation](./ui/Navigation.md) ... The definition of destinations, and the Navigator that holds the current location

## Data

The :data module. The way into data, and the parts whose implementation differs per platform

- [Repository](./data/Repository.md) ... The :data module's user package. The way into data, placed as an interface and an implementation
- [Platform implementation](./data/PlatformImplementation.md) ... The :data module's platform package. The expect declaration in commonMain and the actual implementations in androidMain / iosMain sit in the same package

## Testing support

Test doubles, the test code itself, the katachi architecture definition, the documents and snapshot written from it, and the ledger of shelved violations

- [Fake](./testing/Fake.md) ... Fake implementations in the commonMain of :testing, used from the tests of other modules
- [Test code](./testing/Test.md) ... The tests of each module. commonTest for KMP modules, src/test for pure Android / pure JVM modules
- [Architecture definition](./testing/ArchitectureDefinition.md) ... src/test of the :architecture-test module. This project's definition, written in the katachi DSL. It belongs to no layer, so it lives in a dedicated module
- [Generated documentation](./testing/GeneratedDocumentation.md) ... Markdown written from this definition and committed to the repository
- [Layout snapshot](./testing/LayoutSnapshot.md) ... A record of this definition, flattened and written out in full. It exists so a person can review changes to the definition as a diff
- [Baseline (ledger of shelved violations)](./testing/BaselineFile.md) ... A ledger that records violations already present when katachi was introduced, shelving them without failing the tests

## App

The Android app that Gradle builds and the iOS app that Xcode builds

- [Entrypoint](./app/Entrypoint.md) ... The starting point of the Android app: a ComponentActivity and the whole-app @Composable it calls via setContent
- [Android resources](./app/AndroidResource.md) ... AndroidManifest.xml and the resource XML under res/. Only :app:android has them
- [Xcode project](./app/XcodeProject.md) ... Under app/ios. Outside Gradle's management and not checked

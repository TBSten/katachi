[katachi-sample-android](../README.md)

# Structure of each screen

A module per screen. Each :feature:<name> holds one Screen / ViewModel / Route, and grows with screen parts and tests

The contents of a module that holds one screen, such as `:feature:home` or
`:feature:settings`. Every module has one `<Name>Screen.kt`, `<Name>ViewModel.kt` and
`<Name>Route.kt`, and the file names are determined by the module name. A new screen
means a new module, so a feature never holds a second screen.

It is split into three because they change for different reasons. The Screen is the
look, the ViewModel is the state, and the Route is the seam to the outside; of these,
only the Route is referenced from outside the feature. `:app` knows only the Route, and
the Screen and ViewModel stay inside the module.

The shared layers (`:ui` and `:navigation`) are not in this group because they grow
differently. A feature is a place where adding is the norm, so it is written as
`":feature:*"`: adding `include(":feature:profile")` to `settings.gradle.kts` brings it
under the check without touching this definition. Adding to a shared layer is a design
decision every time, and that lives in the UI (shared layer) group.

Features do not depend on each other. Even when navigating to another screen, the
destination is decided on the `:app` side, and the feature only receives one callback.
The connection exists in a single place in `:app`, so removing a feature does not
require re-reading the others.

Screen parts and tests multiply inside one feature. Both start their file names with
the module name (`HomeUserCard.kt`, `HomeViewModelTest.kt`) and can be generated from
templates. The `*` of `":feature:*"` is named `feature` for screen parts, and
`--arg feature=home` picks the module to generate into, so adding a feature does not
touch this definition. Only tests name each module one by one, because the template
content differs per screen; when adding a feature, add one line to `FeatureModule` too.

| Role | Summary |
|---|---|
| [Screen](./Screen.md) | A @Composable that implements the UI of one screen. One <Name>Screen.kt per :feature:<name> |
| [ViewModel](./ViewModel.md) | An androidx.lifecycle.ViewModel that exposes screen state as a StateFlow and receives events |
| [Route](./Route.md) | The destination of a screen. The only entrance a feature exposes to the outside |
| [Screen part](./FeatureComponent.md) | A @Composable used by only one screen. Placed as <Name>*.kt in the component package of :feature:<name> |
| [Screen test](./FeatureTest.md) | A test in src/test of :feature:<name> that runs the ViewModel with fakes from :testing |

## Placement in this group

```
:feature:<feature>
  src/main/kotlin/**/
    <feature>Screen.kt                 Screen
    <feature>ViewModel.kt              ViewModel
    <feature>Route.kt                  Route
    component/<feature>*.kt            Screen part

:feature:home
  src/test/kotlin/**/Home*Test.kt      Screen test

:feature:settings
  src/test/kotlin/**/Settings*Test.kt  Screen test
```

package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of the resource tree Android's build system owns. */
fun DeclarationContainerScope.androidResource() = "AndroidResource" {
    title = "Android resources"
    summary = "The res/ tree of :app, whose inner structure the Android resource system decides"
    description = """
        The `res/` of `:app`. It is `ignore()`d because its inner structure (`values/`,
        `drawable-*/` and so on) follows rules set by the Android resource system, which AGP
        already validates. Writing it down again in katachi would make a second copy of the
        same rules, and one of the two would always go stale. `ignore()` is used only for a
        place another tool decides, and this is one. Right now it holds `values/strings.xml`
        and `values/themes.xml`.

        `:app` is not the only module that can hold resources (`:ui` is an Android library too),
        but no module other than `:app` has a `res/` in this sample, so this role looks only at
        `:app`. If resources are placed in another module, widen the role then.
    """.trimIndent()
    example("res/values/strings.xml", "String resources")
    layout {
        "app" {
            mainSourceSet {
                "res".ignore()
            }
        }
    }
}

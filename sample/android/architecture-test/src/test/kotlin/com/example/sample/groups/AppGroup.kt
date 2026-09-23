package com.example.sample.groups

import com.example.sample.roles.androidResource
import com.example.sample.roles.entrypoint
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the application module itself: what `:app` holds beyond wiring the features
 * together.
 *
 * `:app` is the one module of the app whose package is not derived from its module path:
 * its sources sit directly in `com.example.sample`, so the package is written out as a key
 * instead of with `modulePackage`. Everything else about the module — where it is, that it
 * has a build script, that its `build/` is not checked — still comes from `":app".module`.
 */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "エントリーポイントレイヤー"

    entrypoint()
    androidResource()
}

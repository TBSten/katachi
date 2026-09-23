package com.example.kmp.groups

import com.example.kmp.roles.platformImplementation
import com.example.kmp.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The data layer, including the parts that differ per platform.
 *
 * `:data` is one module split into packages, the same way `:ui` is: `user` holds the
 * repositories, `platform` the expect/actual pair.
 */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "データ"

    repository()
    platformImplementation()
}

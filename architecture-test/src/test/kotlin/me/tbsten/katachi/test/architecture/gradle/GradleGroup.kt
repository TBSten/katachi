package me.tbsten.katachi.test.architecture.gradle

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.gradle

fun DeclarationContainerScope.gradleGroup() = gradle {
    buildLogic()
}

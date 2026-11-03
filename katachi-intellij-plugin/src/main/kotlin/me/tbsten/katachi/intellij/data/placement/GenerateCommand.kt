package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import java.nio.file.Path

internal fun generateCommandOf(invocation: GradleTaskInvocation): String =
    GradleRunRequest(Path.of("."), listOf(invocation)).commandLine

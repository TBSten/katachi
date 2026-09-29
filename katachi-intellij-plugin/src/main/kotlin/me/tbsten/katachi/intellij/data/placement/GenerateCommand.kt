package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import java.nio.file.Path

/**
 * The shell command that runs [invocation] the way the IDE does, written into the provisional
 * content and shown when the IDE cannot: `./gradlew :arch:katachiTemplate --arg template=... --arg ...`,
 * each word quoted for a POSIX shell. It is [GradleRunRequest.commandLine], the one behind "Copy command".
 */
internal fun generateCommandOf(invocation: GradleTaskInvocation): String =
    GradleRunRequest(Path.of("."), listOf(invocation)).commandLine

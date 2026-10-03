@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import java.lang.reflect.InvocationTargetException
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.test.konsist.fixtureProject

/**
 * The pages `katachiDocs` builds for the roles [roles] declares, over a throwaway project holding
 * [sources], by page path (`Screen.md`).
 *
 * The pages are built in memory, the way `:katachi`'s own specs build them: its internal
 * `process(fileSystem) { roleReferenceDocuments(it) }`, reached by reflection. The public
 * `process(GenerateDocumentation)` would walk the project the JVM was started in -- this repository
 * -- whenever a role has no example, and write the pages to disk.
 */
internal fun rolePageOf(vararg sources: Pair<String, String>, roles: ArchitectureScope.() -> Unit): Map<String, String> =
    fixtureProject(*sources) { root ->
        val definition = architecture {
            files = wholeTree()
            roles()
        }
        val process = Class.forName("me.tbsten.katachi.processor.internal.ProcessWithFileSystemKt").methods.single { method ->
            method.name == "process" &&
                method.parameterTypes.toList() == listOf(Architecture::class.java, KatachiFileSystem::class.java, Function1::class.java)
        }
        val documents = Class.forName("me.tbsten.katachi.docs.internal.RoleReferenceKt").methods.single { it.name == "roleReferenceDocuments" }
        val build: (Any) -> Any = { context -> unwrapped { documents.invoke(null, context) } }
        @Suppress("UNCHECKED_CAST")
        unwrapped { process.invoke(null, definition, RealFileSystem(root), build) } as Map<String, String>
    }

/** [block], with the exception a reflective call threw rather than its wrapper. */
private fun <T> unwrapped(block: () -> T): T = try {
    block()
} catch (wrapped: InvocationTargetException) {
    throw wrapped.targetException
}

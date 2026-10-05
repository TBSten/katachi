package me.tbsten.katachi.intellij.data.detect

import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.model.KatachiCapability
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

/** Which internal task a definition module has (design 4.1). */
class DefinitionCapabilityDetectionTest {
    private val root = Paths.get("/work/project")

    private fun detect(vararg modules: SyncedModule) = detectDefinitionModules(SyncedProject.Synced(listOf(SyncedRoot(root, "project", modules.toList()))))

    private fun mod(path: String, vararg tasks: String) = SyncedModule(path, root.resolve(path.trim(':')), tasks.toSet(), "0.3.0")

    @Test
    fun `定義タスクとテンプレートのタスクのどちらかがあれば定義モジュールでどちらを持つかを覚える`() {
        val found = detect(
            mod(":both", "katachiInternalDefinitionJson", "katachiInternalTemplatesJson"),
            mod(":definitionOnly", "katachiInternalDefinitionJson"),
            mod(":templatesOnly", "katachiInternalTemplatesJson"),
            mod(":app", "build"),
        ) as DetectionResult.Found
        assertEquals(
            mapOf(
                ":both" to setOf(KatachiCapability.Definition, KatachiCapability.Templates),
                ":definitionOnly" to setOf(KatachiCapability.Definition),
                ":templatesOnly" to setOf(KatachiCapability.Templates),
            ),
            found.modules.associate { it.gradlePath to it.capabilities },
        )
    }

    @Test
    fun `タスク一覧の無い同期では候補はどちらのタスクを持つか分からない`() {
        val result = detect(SyncedModule(":arch", root.resolve("arch"), emptySet(), "0.3.0")) as DetectionResult.TaskListMissing
        assertEquals(emptySet<KatachiCapability>(), result.candidates.single().capabilities)
    }
}

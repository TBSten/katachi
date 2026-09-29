package me.tbsten.katachi.intellij.ide

import me.tbsten.katachi.intellij.data.placement.PlacementIndexState

/** `KatachiProjectService.placementIndex`: built off the EDT from the shared list, and the notifications follow it (issues 4, 11). */
internal class PlacementIndexStateTest : EntryServiceTestBase() {

    // covers: 論点11
    fun `test 索引が Ready になるとエディタの通知の再計算が呼ばれる`() {
        installEntryService()
        assertEquals(PlacementIndexState.NotLoaded, service.placementIndex.value)

        service.ensureLoaded()

        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        assertTrue(notificationUpdates.toString(), notificationUpdates.last() is PlacementIndexState.Ready)
    }

    // covers: 論点11
    fun `test 索引は EDT の外で作る`() {
        installEntryService()

        service.ensureLoaded()

        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        assertTrue(indexBuildsOnEdt.isNotEmpty())
        assertEquals(indexBuildsOnEdt.toString(), listOf(false), indexBuildsOnEdt.distinct())
    }

    // covers: 論点11
    fun `test 読み込んだ定義から作った索引はそのテンプレートの出力先のファイルに当たる`() {
        installEntryService()

        service.ensureLoaded()

        val ready = waitForIndex("ready") { it is PlacementIndexState.Ready } as PlacementIndexState.Ready
        assertEquals(listOf(":arch-a"), ready.index.definitions.map { it.gradlePath })
        val repository = root.resolve("data/src/main/kotlin/com/example/data/UserRepository.kt")
        assertTrue(ready.index.matchesForFile(repository).toString(), ready.index.matchesForFile(repository).any { it.id.template == "data.Repository" })
    }

    // covers: 論点11
    fun `test 本物の通知の再計算でも読み込みが通る`() {
        installService()
        val service = KatachiProjectService.getInstance(project)

        service.ensureLoaded()

        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
            { "ready: ${service.placementIndex.value}" },
            { service.placementIndex.value is PlacementIndexState.Ready },
            10,
        )
    }
}

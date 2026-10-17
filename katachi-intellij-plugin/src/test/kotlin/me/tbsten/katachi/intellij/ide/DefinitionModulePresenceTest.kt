package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import me.tbsten.katachi.intellij.AnalysisTestBase

/**
 * [DefinitionModulePresence]: the line marker provider asks at every highlight, so a read of the synced Gradle data is kept
 * until the next sync, and a sync (finished or failed), a read that fails or one that a sync overtakes keep nothing wrong.
 */
internal class DefinitionModulePresenceTest : AnalysisTestBase() {
    private val cache get() = DefinitionModulePresence.getInstance(project)
    private var reads = 0
    private var found = false
    private val read: () -> Boolean? = { reads++; found }

    override fun setUp() {
        super.setUp()
        cache.invalidate()
    }

    // The light project is shared: an answer kept here (these tests read `true`) would make every later test's real ports find a definition module.
    override fun tearDown() {
        try {
            cache.invalidate()
        } finally {
            super.tearDown()
        }
    }

    private fun syncFinishes() = project.messageBus.syncPublisher(ProjectDataImportListener.TOPIC).onImportFinished("/w/repo")

    private fun syncFails() = project.messageBus.syncPublisher(ProjectDataImportListener.TOPIC).onImportFailed("/w/repo", IllegalStateException("boom"))

    fun `test 同期が終わるまで 読んだ答えを使い回し 終わったら読み直す`() {
        repeat(5) { assertFalse(cache.hasDefinitionModule(read)) }
        assertEquals("every highlight of every file asks; the data is read once", 1, reads)

        found = true
        assertFalse("nothing changed the data, so the old answer stands", cache.hasDefinitionModule(read))
        syncFinishes()
        assertTrue(cache.hasDefinitionModule(read))
        assertEquals(2, reads)

        found = false
        syncFails()
        assertFalse(cache.hasDefinitionModule(read))
        assertEquals(3, reads)
    }

    fun `test 読むのに失敗した答えは使い回さない`() {
        var failing = true
        val flaky = { reads++; if (failing) error("the IDE could not hand over the data") else true }
        assertThrows(IllegalStateException::class.java) { cache.hasDefinitionModule(flaky) }
        failing = false
        assertTrue(cache.hasDefinitionModule(flaky))
        assertTrue(cache.hasDefinitionModule(flaky))
        assertEquals(2, reads)
    }

    fun `test 読んでいる間に同期が終わったら その答えは使い回さない`() {
        assertFalse("the answer of the data read before the sync is still given", cache.hasDefinitionModule { reads++; syncFinishes(); false })
        found = true
        assertTrue(cache.hasDefinitionModule(read))
        assertEquals(2, reads)
    }

    fun `test 同期データがまだ無い間の答えは使い回さず 戻ったら読んだ答えを使い回す`() {
        var data: Boolean? = null
        val reading = { reads++; data }
        repeat(3) { assertFalse(cache.hasDefinitionModule(reading)) }
        assertEquals("nothing is kept while there is no data, so each ask reads", 3, reads)

        data = true
        assertTrue(cache.hasDefinitionModule(reading))
        data = null
        assertTrue("the data came back, and that answer is kept", cache.hasDefinitionModule(reading))
        assertEquals(4, reads)
    }
}

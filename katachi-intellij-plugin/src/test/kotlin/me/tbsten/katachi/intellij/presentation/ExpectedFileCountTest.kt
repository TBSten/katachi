package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rowsOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** Plain JUnit, no platform: the fastest channel of the feedback loop. */
class ExpectedFileCountTest {

    @Test
    fun `チェック中のテンプレートの予想ファイル数を足し合わせる`() {
        val actual = expectedFileCountOf(listOf(FileCountSource(2), FileCountSource(1)))
        assertEquals(ExpectedFileCount(total = 3, isApproximate = false, hasUnknown = false), actual)
    }

    @Test
    fun `何もチェックしていなければ0件`() {
        assertEquals(ExpectedFileCount(0, isApproximate = false, hasUnknown = false), expectedFileCountOf(emptyList()))
    }

    @Test
    fun `プレビュー値と違う分岐が1つならその増減を足し引きして近似にしない`() {
        val actual = expectedFileCountOf(listOf(FileCountSource(2, branchDeltas = listOf(-1))))
        assertEquals(ExpectedFileCount(total = 1, isApproximate = false, hasUnknown = false), actual)
    }

    @Test
    fun `1つのテンプレートで2つ以上の分岐が違えば増減を足し合わせて近似にする`() {
        val actual = expectedFileCountOf(listOf(FileCountSource(2, branchDeltas = listOf(-1, 2))))
        assertEquals(ExpectedFileCount(total = 3, isApproximate = true, hasUnknown = false), actual)
    }

    @Test
    fun `別々のテンプレートで1つずつ違うだけなら近似にしない`() {
        val actual = expectedFileCountOf(
            listOf(FileCountSource(2, branchDeltas = listOf(1)), FileCountSource(2, branchDeltas = listOf(-1))),
        )
        assertEquals(ExpectedFileCount(total = 4, isApproximate = false, hasUnknown = false), actual)
    }

    @Test
    fun `プレビューに失敗したテンプレートは数えずに不明ありにする`() {
        val actual = expectedFileCountOf(listOf(FileCountSource(3), FileCountSource(null)))
        assertEquals(ExpectedFileCount(total = 3, isApproximate = false, hasUnknown = true), actual)
    }

    @Test
    fun `増減を引いてもテンプレート1つが負の件数にはならない`() {
        val actual = expectedFileCountOf(listOf(FileCountSource(1, branchDeltas = listOf(-1, -1)), FileCountSource(2)))
        assertEquals(2, actual.total)
    }

    private val rows = rowsOf("arch-a")

    @Test
    fun `booleanの分岐でファイルが減ると行の件数も減る`() {
        val repository = rows.row("data/Repository").template
        assertEquals(2, expectedFileCountOf(listOf(fileCountSourceOf(repository, emptyMap()))).total)
        assertEquals(1, expectedFileCountOf(listOf(fileCountSourceOf(repository, mapOf("withImpl" to "false")))).total)
    }

    @Test
    fun `booleanとenumの2つが同時にプレビュー値と違えば約にする`() {
        val useCase = rows.row("domain/UseCase").template
        val one = expectedFileCountOf(listOf(fileCountSourceOf(useCase, mapOf("withTest" to "true"))))
        assertEquals(ExpectedFileCount(total = 2, isApproximate = false, hasUnknown = false), one)
        val two = expectedFileCountOf(listOf(fileCountSourceOf(useCase, mapOf("withTest" to "true", "visibility" to "Internal"))))
        assertEquals(ExpectedFileCount(total = 3, isApproximate = true, hasUnknown = false), two)
    }

    @Test
    fun `プレビュー失敗の行を含めば件数に不明ありを付ける`() {
        val sources = listOf(fileCountSourceOf(rows.row("data/Repository").template, emptyMap()), fileCountSourceOf(rows.row("misc/Broken").template, emptyMap()))
        assertEquals(ExpectedFileCount(total = 2, isApproximate = false, hasUnknown = true), expectedFileCountOf(sources))
    }
}

package me.tbsten.katachi.intellij.uitest.pbt

import me.tbsten.katachi.intellij.testing.module
import org.junit.Test

/**
 * The counterexample [ScreenPropertyTest] found at 20 times its iterations (V1, seed 20260927),
 * played as a fixed sequence: two templates of one module share the capture `feature`, the user
 * unlinks one of them and types a different value, and both run in one `katachiTemplate` build.
 * That build carries one `feature`, the one of the row listed first (design draft section 6
 * "IDE の複数選択"); [ScreenMachine] checks each run against that rule.
 *
 * ```kotlin
 * ScreenMachine(catalog, render = true).use { it.run(ops) }
 * ```
 */
class SharedCaptureRunTest {
    private val sharesFeature = TemplateSpec(
        group = "",
        params = emptyList(),
        branch = BranchKind.None,
        files = 1,
        unresolved = false,
        previewFailed = false,
        title = TitleKind.None,
        captures = listOf(CaptureSpec("feature", module = false)),
    )
    private val catalog = Catalog(listOf(World(listOf(ModuleDef(module(":m0"), listOf(sharesFeature, sharesFeature))))))

    private val minusOne = TYPED_VALUES.indexOf("-1")
    private val user = TYPED_VALUES.indexOf("User")

    // covers: V1 指摘2（同じモジュールの同名 capture の連動を切って生成する）
    @Test
    fun `同じモジュールの2行で同名のcaptureの連動を切って別の値にしても1回の実行で先に並ぶ行の値を送り仕様の状態に留まる`() {
        ScreenMachine(catalog, render = true).use { machine ->
            machine.run(
                listOf(
                    Op.Check(0),
                    Op.Check(1),
                    // The second row's field is typed first: it drives the link and fills the first row.
                    Op.Type(pick = 1, value = minusOne),
                    // The first row then differs: only its link breaks.
                    Op.Type(pick = 0, value = user),
                    Op.Generate,
                    Op.Release,
                ),
            )
        }
    }

    // covers: V1 指摘2（同じモジュールの同名 capture の連動を切って生成する）
    @Test
    fun `後に並ぶ行の連動を切った値は先に並ぶ行の値に負けて送られない`() {
        ScreenMachine(catalog, render = false).use { machine ->
            machine.run(
                listOf(
                    Op.Check(1),
                    Op.Check(0),
                    Op.Type(pick = 0, value = user),
                    Op.Type(pick = 1, value = minusOne),
                    Op.Generate,
                    Op.Release,
                ),
            )
        }
    }
}

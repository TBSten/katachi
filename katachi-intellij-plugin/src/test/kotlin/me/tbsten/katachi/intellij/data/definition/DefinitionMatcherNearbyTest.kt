package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `nearbyOf`: katachi's `NearbyCandidates` rule (design violations-output 2.2 / 2.3), case by case, the cases of 2.4 among
 * them. The property test `DefinitionMatcherPropertyTest` compares the same rule with a reference written without the cut-offs.
 */
class DefinitionMatcherNearbyTest {
    private val dir = LayoutEntryKind.Directory
    private val any = LayoutEntryKind.AnyFile
    private val ignore = LayoutEntryKind.Ignore

    private fun nearby(vararg roles: me.tbsten.katachi.intellij.model.RoleModel, file: String) =
        DefinitionMatcher(definitionModel(*roles)).nearbyOf(file).map { it.role.qualifiedName to it.directory }

    private fun nearbyWithPattern(vararg roles: me.tbsten.katachi.intellij.model.RoleModel, file: String) =
        DefinitionMatcher(definitionModel(*roles)).nearbyOf(file).map { Triple(it.role.qualifiedName, it.directory, it.filePattern) }

    private val featureSources = "feature/*/src/main/kotlin/com/example/sample/feature/*"
    private val featureScreen = roleModel("feature.Screen", layoutEntry("$featureSources/*Screen.kt"))
    private val featureViewModel = roleModel("feature.ViewModel", layoutEntry("$featureSources/*ViewModel.kt"))
    private val featureComponent = roleModel("feature.FeatureComponent", layoutEntry("$featureSources/component/*.kt"))
    private val featureTest = roleModel(
        "feature.FeatureTest",
        layoutEntry("feature/home/src/test/kotlin/com/example/sample/feature/home/*Test.kt"),
        layoutEntry("feature/settings/src/test/kotlin/com/example/sample/feature/settings/*Test.kt"),
    )
    private val diModule = roleModel("app.DiModule", layoutEntry("app/src/main/kotlin/com/example/sample/di/module/*Module.kt"))
    private val activityEntrypoint = roleModel("app.ActivityEntrypoint", layoutEntry("app/src/main/kotlin/com/example/sample/MainActivity.kt"))
    private val domainUseCase = roleModel("domain.UseCase", layoutEntry("core/domain/src/main/kotlin/com/example/sample/useCase/*UseCase.kt"))
    private val domainRepository = roleModel("domain.Repository", layoutEntry("core/domain/src/main/kotlin/com/example/sample/repository/*Repository.kt"))
    private val buildScript = roleModel(
        "Gradle.BuildScript",
        layoutEntry("feature/home/build.gradle.kts", synthetic = true),
        layoutEntry("feature/settings/build.gradle.kts", synthetic = true),
        layoutEntry("app/build.gradle.kts", synthetic = true),
        layoutEntry("core/domain/build.gradle.kts", synthetic = true),
    )
    private val androidLike = arrayOf(featureScreen, featureViewModel, featureComponent, featureTest, diModule, activityEntrypoint, domainUseCase, domainRepository, buildScript)

    @Test
    fun `nearby はアスタリスクを含む置き場所をファイルのパスで具体化し 階層の編集距離と名前の一致の順に並べる`() {
        // Case 1 of design 2.4: `component2` is one replaced level away from FeatureComponent's `component` (name fits `*.kt`),
        // one deleted level away from Screen and ViewModel (name does not fit). BuildScript is synthetic and `.kts`.
        val home = "feature/home/src/main/kotlin/com/example/sample/feature/home"
        assertEquals(
            listOf(
                Triple("feature.FeatureComponent", "$home/component", "*.kt"),
                Triple("feature.Screen", home, "*Screen.kt"),
                Triple("feature.ViewModel", home, "*ViewModel.kt"),
            ),
            nearbyWithPattern(*androidLike, file = "$home/component2/HomeUserCard.kt"),
        )
    }

    @Test
    fun `nearby は挿入された階層と削除された階層を1ずつ数え 名前が合う方を先にする`() {
        // Case 2: DiModule is one inserted level (`module`) away and the name fits; ActivityEntrypoint one deleted level (`di`) and it does not.
        assertEquals(
            listOf("app.DiModule" to "app/src/main/kotlin/com/example/sample/di/module", "app.ActivityEntrypoint" to "app/src/main/kotlin/com/example/sample"),
            nearby(*androidLike, file = "app/src/main/kotlin/com/example/sample/di/AppModule.kt"),
        )
        // Case 3: `usecase` is one replaced level from `useCase` (name fits), one from `repository` (name does not).
        assertEquals(
            listOf("domain.UseCase" to "core/domain/src/main/kotlin/com/example/sample/useCase", "domain.Repository" to "core/domain/src/main/kotlin/com/example/sample/repository"),
            nearby(*androidLike, file = "core/domain/src/main/kotlin/com/example/sample/usecase/GetUserUseCase.kt"),
        )
    }

    @Test
    fun `nearby はディレクトリが同じで名前が合わない場所を 1階層違いで名前が合う場所より先にする`() {
        // Case 6: Screen and ViewModel are 0 levels away (name mismatch 1); FeatureComponent is 1 level away (name fits).
        val home = "feature/home/src/main/kotlin/com/example/sample/feature/home"
        assertEquals(
            listOf("feature.Screen" to home, "feature.ViewModel" to home, "feature.FeatureComponent" to "$home/component"),
            nearby(*androidLike, file = "$home/Foo.kt"),
        )
    }

    @Test
    fun `nearby はアスタリスクを埋められない浅いファイルには何も出さず synthetic な entry も出さない`() {
        // Case 7: `feature/*/...` cannot be filled from `feature`, and the build scripts are synthetic.
        assertEquals(emptyList<Any>(), nearby(featureScreen, featureViewModel, featureComponent, buildScript, file = "feature/Stray.kt"))
        // One level deeper, the first `*` is filled; a place whose second `*` still has nothing to take stays out.
        val shallowScreen = roleModel("feature.Screen", layoutEntry("feature/*/src/main/kotlin/*Screen.kt"))
        assertEquals(listOf("feature.Screen" to "feature/home/src/main/kotlin"), nearby(shallowScreen, buildScript, file = "feature/home/Stray.kt"))
        assertEquals(emptyList<Any>(), nearby(featureScreen, buildScript, file = "feature/home/Stray.kt"))
    }

    @Test
    fun `nearby は gradle が宣言した Gradle グループの Role の置き場所を候補にしない`() {
        // gradle() declares `build.gradle.kts` in every module as a plain entry (not synthetic), so the group tells it apart.
        val declared = roleModel("Gradle.BuildScript", layoutEntry("feature/home/build.gradle.kts"))
        val wrapperJar = roleModel("Gradle.GradleWrapper.WrapperJar", layoutEntry("feature/home/gradle/wrapper/*.jar"))
        val written = roleModel("build.Script", layoutEntry("feature/home/*.gradle.kts"))
        assertEquals(emptyList<Any>(), nearby(declared, wrapperJar, file = "feature/home/gradle/wrapper/Stray.jar"))
        assertEquals(listOf("build.Script" to "feature/home"), nearby(declared, written, file = "feature/home/x/other.gradle.kts"))
    }

    @Test
    fun `nearby はファイル名パターンの拡張子で受け取れない場所を外し 拡張子の無いパターンにアスタリスクがあれば何でも受ける`() {
        // Case 8: `README*` has no `.` and a `*`, so it takes every name. `LICENSE` takes a name without an extension only,
        // and a `.kts` place never takes a `.md` or `.kt` file, synthetic or not. Ties keep declaration order.
        val license = roleModel("legal.License", layoutEntry("feature/*/LICENSE"))
        val readme = roleModel("docs.FeatureReadme", layoutEntry("feature/*/README*"))
        val script = roleModel("gradle.Script", layoutEntry("feature/*/build.gradle.kts"))
        assertEquals(listOf(Triple("docs.FeatureReadme", "feature/home", "README*")), nearbyWithPattern(license, readme, script, file = "feature/home/README.md"))
        assertEquals(listOf("legal.License" to "feature/home", "docs.FeatureReadme" to "feature/home"), nearby(license, readme, script, file = "feature/home/NOTICE"))
        assertEquals(listOf("docs.FeatureReadme" to "feature/home", "gradle.Script" to "feature/home"), nearby(license, readme, script, file = "feature/home/settings.gradle.kts"))
    }

    @Test
    fun `nearby は部分的なアスタリスクの階層が当たらなければアスタリスクをファイルの階層で置き換えて具体化する`() {
        // Case 9: `ui-*` does not match `home`, so the place reads `ui-home`: one replaced level, 3 characters apart.
        val screen = roleModel("feature.Screen", layoutEntry("feature/ui-*/src/main/kotlin/com/example/sample/feature/*/*Screen.kt"))
        assertEquals(
            listOf("feature.Screen" to "feature/ui-home/src/main/kotlin/com/example/sample/feature/home"),
            nearby(screen, file = "feature/home/src/main/kotlin/com/example/sample/feature/home/HomeScreen.kt"),
        )
    }

    @Test
    fun `nearby は同点なら宣言順で3件までで 先頭の階層を共有しない場所は出さない`() {
        val r1 = roleModel("r.One", layoutEntry("a/b/c/Foo.kt"))
        val r2 = roleModel("r.Two", layoutEntry("a/b/Foo.kt"))
        val r3 = roleModel("r.Three", layoutEntry("a/x/Foo.kt"))
        val r4 = roleModel("r.Four", layoutEntry("a/b/c/d", any))
        val r5 = roleModel("r.Five", layoutEntry("a/y/Foo.kt"))
        val far = roleModel("r.Far", layoutEntry("z/b/c/Foo.kt"))
        // One: 0 levels, name mismatch. Four: 1 level (anyFile fits any name). Two: 1 level, name mismatch. Three / Five: 2 levels. Far shares nothing.
        assertEquals(
            listOf("r.One" to "a/b/c", "r.Four" to "a/b/c/d", "r.Two" to "a/b"),
            nearby(far, r1, r2, r3, r4, r5, file = "a/b/c/Stray.kt"),
        )
    }

    @Test
    fun `nearby にルートの宣言と ignore と Directory と二重アスタリスクが2つある場所は入らない`() {
        val roles = arrayOf(
            roleModel("r.Root", layoutEntry("Foo.kt")),
            roleModel("r.Ignored", layoutEntry("a/b", ignore)),
            roleModel("r.Directory", layoutEntry("a/c", dir)),
            roleModel("r.TwoStars", layoutEntry("a/**/test/**/Foo.kt")),
            roleModel("r.Near", layoutEntry("a/d/Foo.kt")),
        )
        assertEquals(listOf("r.Near" to "a/d"), nearby(*roles, file = "a/Stray.kt"))
    }

    @Test
    fun `nearby の二重アスタリスクは後ろの階層を末尾に揃えて残りを取り込む`() {
        val role = roleModel("r.Deep", layoutEntry("src/**/useCase/*UseCase.kt"))
        assertEquals(listOf("r.Deep" to "src/main/kotlin/com/example/useCase"), nearby(role, file = "src/main/kotlin/com/example/usecase/GetUserUseCase.kt"))
        assertEquals(listOf("r.Deep" to "src/useCase"), nearby(role, file = "src/Stray.kt"))
    }

    @Test
    fun `nearby は同じ役割の同じディレクトリを1回にまとめ役割が違えば別に数える`() {
        val one = roleModel("r.One", layoutEntry("a/b/X.kt"), layoutEntry("a/b/Y.kt"), layoutEntry("a/b", any))
        val two = roleModel("r.Two", layoutEntry("a/b/Z.kt"))
        assertEquals(listOf(Triple("r.One", "a/b", null), Triple("r.Two", "a/b", "Z.kt")), nearbyWithPattern(one, two, file = "a/b/Stray.kt"))
    }

    @Test
    fun `nearby は文字の編集距離を 階層の編集距離と名前の一致が同点のときだけ比べる`() {
        val near = roleModel("r.Near", layoutEntry("core/domain/useCase/*.kt"))
        val far = roleModel("r.Far", layoutEntry("core/domain/repository/*.kt"))
        // Both are one replaced level away with a fitting name; `useCase` is 1 character from `usecase`, `repository` is 10.
        assertEquals(listOf("r.Near" to "core/domain/useCase", "r.Far" to "core/domain/repository"), nearby(far, near, file = "core/domain/usecase/X.kt"))
    }

    @Test
    fun `Matched のときは nearby が空`() {
        assertEquals(emptyList<Any>(), DefinitionMatcher(definitionModel(domainUseCase)).matchFile("core/domain/src/main/kotlin/com/example/sample/useCase/GetUserUseCase.kt").nearby)
    }
}

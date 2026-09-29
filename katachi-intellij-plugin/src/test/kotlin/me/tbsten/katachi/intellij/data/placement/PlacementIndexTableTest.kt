package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.patternTemplate
import me.tbsten.katachi.intellij.testing.resolveAll
import me.tbsten.katachi.intellij.testing.snapshotOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/**
 * The table of spike S1 section 8 (`.local/idea-plugin/impl/spike-placement.md`): a path in, the
 * templates it fits with their captures and remaining path out. Row numbers are that table's.
 */
class PlacementIndexTableTest {
    /** What one template found: decided and undecided captures, the remaining path (directories only). */
    private data class Hit(
        val template: String,
        val decided: Map<String, String> = emptyMap(),
        val undecided: List<String> = emptyList(),
        val remaining: String = "",
        val targetUndecided: Boolean = false,
    )

    private fun hit(match: PlacementMatch) =
        Hit(match.template.template.template, match.decided, match.undecided, match.remainingPath, match.targetUndecided)

    private fun indexOfFixture(name: String) = indexOf(snapshotOf(module(":arch"), ContractFixtures.templates(name)))

    private fun dir(name: String, path: String, vararg expected: Hit) =
        assertEquals("$name dir '$path'", expected.toList(), indexOfFixture(name).matchesForDirectory(ROOT.resolveAll(path)).map(::hit))

    private fun file(name: String, path: String, vararg expected: Hit) =
        assertEquals("$name file '$path'", expected.toList(), indexOfFixture(name).matchesForFile(ROOT.resolve(path)).map(::hit))

    private val android = "sample-android-with-captures"
    private val jvm = "sample-jvm-with-captures"
    private val kmp = "sample-kmp-with-captures"
    private val fixedFeatureTests = listOf("home", "settings")

    private val userExample: List<TemplateModel> =
        listOf(patternTemplate("feature.Screen", "feature/src/main/com/example/feature/\${feature}/ui/\${screen}Screen.kt"))

    private fun userExampleIndex() = indexOf(snapshotOf(module(":arch"), userExample))

    // covers: 論点1
    @Test
    fun `利用者の例のパターンは深さごとに決まる値と残りのパスが変わる`() {
        val index = userExampleIndex()
        val one = index.matchesForDirectory(ROOT.resolve("feature/src/main/com/example")).single()
        assertEquals(mapOf<String, String>(), one.decided)
        assertEquals(listOf("feature", "screen"), one.undecided)
        assertEquals("feature/<feature>/ui/<screen>Screen.kt", one.remainingPath)

        val two = index.matchesForDirectory(ROOT.resolve("feature/src/main/com/example/feature/profile")).single()
        assertEquals(mapOf("feature" to "profile"), two.decided)
        assertEquals(listOf("screen"), two.undecided)
        assertEquals("ui/<screen>Screen.kt", two.remainingPath)

        val three = index.matchesForFile(ROOT.resolve("feature/src/main/com/example/feature/profile/ui/ProfileScreen.kt")).single()
        assertEquals(mapOf("feature" to "profile", "screen" to "Profile"), three.decided)
        assertEquals(emptyList<String>(), three.undecided)

        assertEquals(emptyList<PlacementMatch>(), index.matchesForDirectory(ROOT.resolve("feature/src/main/com/example/other")))
    }

    // covers: 論点1
    @Test
    fun `androidの例のパターンは合わないディレクトリに出ない`() {
        // Row 5: the user's pattern is not android's layout.
        dir(android, "feature/src/main/com/example")
        // Row 22.
        dir(android, "app")
    }

    // covers: 論点1
    @Test
    fun `androidのfeatureディレクトリではモジュールのcaptureが決まらず固定のテストは残りのパスに出る`() {
        // Row 6.
        dir(
            android,
            "feature",
            Hit(
                "feature.FeatureComponent",
                undecided = listOf("feature", "name"),
                remaining = "<feature>/src/main/kotlin/com/example/sample/feature/<feature>/component/<feature><name>.kt",
                targetUndecided = true,
            ),
            Hit(
                "feature.FeatureTest.home",
                undecided = listOf("name"),
                remaining = "home/src/test/kotlin/com/example/sample/feature/home/Home<name>Test.kt",
            ),
            Hit(
                "feature.FeatureTest.settings",
                undecided = listOf("name"),
                remaining = "settings/src/test/kotlin/com/example/sample/feature/settings/Settings<name>Test.kt",
            ),
        )
    }

    // covers: 論点1
    @Test
    fun `androidのモジュールの下ではmoduleのcaptureが決まり派生の部分もkatachiの綴りで決まる`() {
        // Row 7: <feature> in the package directory is matched with 'home'. katachi's
        // modulePlacements spells the rest of the path for :feature:home too, so nothing is left to katachi.
        dir(
            android,
            "feature/home/src/main/kotlin/com/example/sample/feature/home",
            Hit("feature.FeatureComponent", mapOf("feature" to "home"), listOf("name"), "component/Home<name>.kt"),
        )
        // Row 8: <feature> in the file name is 'Home' (pascal case).
        file(
            android,
            "feature/home/src/main/kotlin/com/example/sample/feature/home/component/HomeUserCard.kt",
            Hit("feature.FeatureComponent", mapOf("feature" to "home", "name" to "UserCard")),
        )
        // Row 9: <feature> is 'settings' where 'home' is the module.
        dir(android, "feature/home/src/main/kotlin/com/example/sample/feature/settings")
        // Row 12: not under component/.
        file(android, "feature/settings/src/main/kotlin/com/example/sample/feature/settings/SettingsScreen.kt")
    }

    // covers: 論点1
    @Test
    fun `androidの固定のテストテンプレートはそのモジュールでだけ当たる`() {
        // Row 10, 11.
        file(
            android,
            "feature/home/src/test/kotlin/com/example/sample/feature/home/HomeViewModelTest.kt",
            Hit("feature.FeatureTest.home", mapOf("name" to "ViewModel")),
        )
        dir(
            android,
            "feature/home/src/test/kotlin/com/example/sample/feature/home",
            Hit("feature.FeatureTest.home", undecided = listOf("name"), remaining = "Home<name>Test.kt"),
        )
    }

    // covers: 論点1
    @Test
    fun `androidのdataは並びがJSONの並びで名前が空のファイルには当たらない`() {
        // Row 13.
        dir(
            android,
            "data/src/main/kotlin/com/example/sample/data",
            Hit("data.Repository.user", undecided = listOf("name"), remaining = "user/User<name>Repository.kt"),
            Hit("data.Repository.settings", undecided = listOf("name"), remaining = "settings/Settings<name>Repository.kt"),
            Hit("data.Repository.userImpl", undecided = listOf("name"), remaining = "user/User<name>RepositoryImpl.kt"),
            Hit("data.Repository.settingsImpl", undecided = listOf("name"), remaining = "settings/Settings<name>RepositoryImpl.kt"),
        )
        // Row 14.
        dir(
            android,
            "data/src/main/kotlin/com/example/sample/data/user",
            Hit("data.Repository.user", undecided = listOf("name"), remaining = "User<name>Repository.kt"),
            Hit("data.Repository.userImpl", undecided = listOf("name"), remaining = "User<name>RepositoryImpl.kt"),
        )
        // Row 15: ${name} is one or more characters.
        file(android, "data/src/main/kotlin/com/example/sample/data/user/UserRepository.kt")
        // Row 16: ends in Impl.kt, so only the Impl template.
        file(
            android,
            "data/src/main/kotlin/com/example/sample/data/user/UserCacheRepositoryImpl.kt",
            Hit("data.Repository.userImpl", mapOf("name" to "Cache")),
        )
    }

    // covers: 論点1
    @Test
    fun `androidのその他のモジュールのファイルとディレクトリ`() {
        // Row 17, 18, 19.
        file(android, "ui/src/main/kotlin/com/example/sample/ui/component/AppButton.kt", Hit("ui.Component", mapOf("name" to "AppButton")))
        file(
            android,
            "testing/src/main/kotlin/com/example/sample/testing/FakeUserRepository.kt",
            Hit("testing.Fake", mapOf("repository" to "UserRepository")),
        )
        file(
            android,
            "architecture-test/src/test/kotlin/com/example/sample/groups/AppGroup.kt",
            Hit("testing.ArchitectureDefinition.group", mapOf("name" to "App")),
        )
        // Row 20.
        dir(
            android,
            "architecture-test/src/test/kotlin/com/example/sample",
            Hit("testing.ArchitectureDefinition.group", undecided = listOf("name"), remaining = "groups/<name>Group.kt"),
            Hit("testing.ArchitectureDefinition.role", undecided = listOf("name"), remaining = "roles/<name>Role.kt"),
        )
    }

    // covers: 論点1
    @Test
    fun `ルートのディレクトリではその定義の索引に入ったテンプレートがすべて出る`() {
        // Row 21.
        val hits = indexOfFixture(android).matchesForDirectory(ROOT).map { it.template.template.template }
        assertEquals(11, hits.size)
        assertEquals(hits.distinct(), hits)
    }

    // covers: 論点1
    @Test
    fun `jvmはルートのモジュールなので接頭辞が無くcaptureを1階層ずつ決める`() {
        // Row 23-25.
        dir(
            jvm,
            "src/main/kotlin/com/example",
            Hit("api.Controller", undecided = listOf("resource", "name"), remaining = "controller/<resource>/<name>Controller.kt"),
            Hit("domain.Service", undecided = listOf("name"), remaining = "service/<name>Service.kt"),
        )
        dir(
            jvm,
            "src/main/kotlin/com/example/controller",
            Hit("api.Controller", undecided = listOf("resource", "name"), remaining = "<resource>/<name>Controller.kt"),
        )
        dir(
            jvm,
            "src/main/kotlin/com/example/controller/health",
            Hit("api.Controller", mapOf("resource" to "health"), listOf("name"), "<name>Controller.kt"),
        )
        // Row 26-29.
        file(
            jvm,
            "src/main/kotlin/com/example/controller/health/HealthController.kt",
            Hit("api.Controller", mapOf("resource" to "health", "name" to "Health")),
        )
        file(jvm, "src/main/kotlin/com/example/controller/HealthController.kt")
        file(jvm, "src/main/kotlin/com/example/service/HealthService.kt", Hit("domain.Service", mapOf("name" to "Health")))
        file(jvm, "src/main/kotlin/com/example/service/LegacyHealthCheck.kt")
    }

    // covers: 論点1
    @Test
    fun `kmpのモジュールの下は今あるモジュールごとの生成先で照合され派生の部分も残らない`() {
        // Row 30, 31.
        file(
            kmp,
            "feature/home/src/commonMain/kotlin/com/example/kmp/feature/home/component/HomeUserCard.kt",
            Hit("feature.FeatureComponent", mapOf("feature" to "home", "name" to "UserCard")),
        )
        file(kmp, "feature/home/src/commonMain/kotlin/com/example/kmp/feature/home/HomeScreen.kt")
        // Row 32.
        dir(
            kmp,
            "feature/settings/src/commonMain/kotlin/com/example/kmp/feature",
            Hit("feature.FeatureComponent", mapOf("feature" to "settings"), listOf("name"), "settings/component/Settings<name>.kt"),
        )
        // Row 33, 34.
        dir(
            kmp,
            "data/src/commonMain/kotlin/com/example/kmp/data/user",
            Hit("data.Repository.repository", undecided = listOf("name"), remaining = "<name>Repository.kt"),
            Hit("data.Repository.repositoryImpl", undecided = listOf("name"), remaining = "<name>RepositoryImpl.kt"),
        )
        file(
            kmp,
            "data/src/commonMain/kotlin/com/example/kmp/data/user/UserRepositoryImpl.kt",
            Hit("data.Repository.repositoryImpl", mapOf("name" to "User")),
        )
    }

    // covers: 論点1
    @Test
    fun `previewが失敗したテンプレートは索引に入らずcaptureの無いテンプレートは固定のパスで入る`() {
        val synthetic = "synthetic-structure"
        // Row 35: a.b.Middle (conflict) is left out.
        dir(synthetic, "src/main/kotlin/a/b", Hit("a.b.c.Deep", remaining = "c/Deep.kt"))
        // Row 36.
        dir(
            synthetic,
            "src/main/kotlin",
            Hit("AllTypes", remaining = "allTypes/AllTypes.kt"),
            Hit("Counter", remaining = "counter/Counter.kt"),
            Hit("a.Shallow", remaining = "a/Shallow.kt"),
            Hit("a.b.c.Deep", remaining = "a/b/c/Deep.kt"),
        )
        // Row 37, 38.
        file(synthetic, "src/main/kotlin/counter/Counter.kt", Hit("Counter"))
        file(synthetic, "src/main/kotlin/a/b/Anything.kt")
        // Row 39.
        dir(synthetic, "feature/home/src/main/kotlin", Hit("other.Wildcard", mapOf("feature" to "home"), listOf("name"), "<name>Screen.kt"))
    }

    // covers: 論点1
    @Test
    fun `名前の無いワイルドカードと未知の種類のテンプレートは索引に入らない`() {
        // Row 40, 41.
        val roots = indexOfFixture("arch-a").matchesForDirectory(ROOT).map { it.template.template.template }
        assertEquals(listOf("data.Repository", "domain.UseCase", "misc.NoArgs", "misc.Label"), roots)
        file("arch-a", "README.md", Hit("misc.NoArgs"))
        // Row 42.
        file("capture", "app/north/home/HomeDeep.kt", Hit("feature.Deep", mapOf("area" to "north", "feature" to "home", "name" to "Home")))
    }

    // covers: 論点1
    @Test
    fun `名前を持つ値の検査を通らない値のセグメントには当たらない`() {
        val pattern = checkNotNull(PathPattern.parse("feature/\${feature}/ui/\${screen}Screen.kt"))
        for (bad in listOf("..", ".", " a", "a ", "a.", "con", "a:b", "a*b", "")) {
            assertEquals(bad, null, pattern.matchDirectory(listOf("feature", bad)))
        }
        assertEquals(null, pattern.matchFile(listOf("feature", "x", "ui", "Screen.kt")))
        assertEquals(null, pattern.matchFile(listOf("feature", "x", "ui", " .Screen.kt")))
        assertEquals(mapOf("feature" to "日本語"), pattern.matchDirectory(listOf("feature", "日本語"))?.decided)
    }

    // covers: 論点1
    @Test
    fun `ルートの外のパスと大文字小文字が違うパスには当たらない`() {
        val index = userExampleIndex()
        assertEquals(emptyList<PlacementMatch>(), index.matchesForDirectory(Paths.get("/elsewhere/feature")))
        assertEquals(emptyList<PlacementMatch>(), index.matchesForDirectory(ROOT.resolve("Feature")))
        assertEquals(emptyList<PlacementMatch>(), index.matchesForFile(ROOT))
    }

    // covers: 論点1
    @Test
    fun `区切りを挟む2つのcaptureは左を最短で取る`() {
        val index = indexOf(snapshotOf(module(":arch"), listOf(patternTemplate("misc.Pair", "misc/\${a}-\${b}.kt"))))
        val match = index.matchesForFile(ROOT.resolve("misc/x-y-z.kt")).single()
        assertEquals(mapOf("a" to "x", "b" to "y-z"), match.decided)
    }

    // covers: 論点1
    @Test
    fun `xの値が分からない派生の部分は任意の文字列に合い隣のcaptureは空になる`() {
        val index = indexOf(snapshotOf(module(":arch"), listOf(patternTemplate("misc.Any", "src/<feature>\${name}.kt", captureNames = listOf("name")))))
        val match = index.matchesForFile(ROOT.resolve("src/HomeCard.kt")).single()
        assertEquals(mapOf("name" to ""), match.decided)
    }

    // covers: 論点1
    @Test
    fun `モジュールのcaptureの派生形はハイフンを含む名前でも変換のどれかに合う`() {
        val pattern = "feature/\${feature}/src/<feature>/<feature>\${name}.kt"
        val index = indexOf(snapshotOf(module(":arch"), listOf(patternTemplate("f.C", pattern, moduleCaptures = setOf("feature")))))
        for (dirName in listOf("fuga-piyo", "fugaPiyo", "fugapiyo")) {
            val hit = index.matchesForFile(ROOT.resolve("feature/fuga-piyo/src/$dirName/FugaPiyoX.kt"))
            assertEquals(dirName, 1, hit.size)
        }
        assertTrue(index.matchesForFile(ROOT.resolve("feature/fuga-piyo/src/other/FugaPiyoX.kt")).isEmpty())
    }

    // covers: 論点1
    @Test
    fun `2つの定義は定義の並びに続けてJSONの並びで出て同じ指定でも2件になる`() {
        val a = module(":arch-a")
        val b = module(":arch-b")
        val templates = listOf(patternTemplate("x.First", "src/\${name}.kt"), patternTemplate("x.Second", "src/\${name}.kt"))
        val index = indexOf(snapshotOf(a, templates), snapshotOf(b, templates.reversed()))
        val hits = index.matchesForFile(ROOT.resolve("src/Foo.kt")).map { it.definition.gradlePath to it.template.template.template }
        assertEquals(
            listOf(":arch-a" to "x.First", ":arch-a" to "x.Second", ":arch-b" to "x.Second", ":arch-b" to "x.First"),
            hits,
        )
    }

    // covers: 論点1
    @Test
    fun `seedsForは起点が決める値を返し合わないテンプレートには空を返す`() {
        val index = userExampleIndex()
        val id = index.matchesForFile(ROOT.resolve("feature/src/main/com/example/feature/a/ui/BScreen.kt")).single().id
        val directory = me.tbsten.katachi.intellij.presentation.entry.EntryOrigin.NewMenuDirectory(ROOT.resolve("feature/src/main/com/example/feature/a"))
        val file = me.tbsten.katachi.intellij.presentation.entry.EntryOrigin.EditorFile(ROOT.resolve("feature/src/main/com/example/feature/a/ui/BScreen.kt"))
        assertEquals(mapOf("feature" to "a"), index.seedsFor(directory, id))
        assertEquals(mapOf("feature" to "a", "screen" to "B"), index.seedsFor(file, id))
        assertEquals(emptyMap<String, String>(), index.seedsFor(directory.copy(path = ROOT.resolve("elsewhere")), id))
    }

    // covers: 論点1
    @Test
    fun `seedsForは同じ起点に当たる複数のテンプレートからそれぞれ自分の決まる値を返す`() {
        // Several templates fit one directory, each deciding a capture of its own name: switching
        // templates in the dialog must seed the new template's captures, not the first match's.
        val index = indexOf(
            snapshotOf(
                module(":arch"),
                listOf(
                    patternTemplate("ui.Screen", "feature/\${feature}/ui/\${screen}Screen.kt"),
                    patternTemplate("data.Repository", "feature/\${name}/data/\${entity}Repository.kt"),
                    patternTemplate("app.Main", "app/\${main}.kt"),
                ),
            ),
        )
        val directory = me.tbsten.katachi.intellij.presentation.entry.EntryOrigin.NewMenuDirectory(ROOT.resolve("feature/profile"))
        val byRole = index.matchesForDirectory(ROOT.resolve("feature/profile")).associate { it.template.template.roleName to it.id }
        assertEquals(setOf("ui.Screen", "data.Repository"), byRole.keys)

        assertEquals(mapOf("feature" to "profile"), index.seedsFor(directory, byRole.getValue("ui.Screen")))
        assertEquals(mapOf("name" to "profile"), index.seedsFor(directory, byRole.getValue("data.Repository")))
        val main = index.matchesForFile(ROOT.resolve("app/Main.kt")).single().id
        assertEquals(emptyMap<String, String>(), index.seedsFor(directory, main))
    }

    // covers: 論点1
    @Test
    fun `パターンの起点はwrapperかgitのある最初の上のディレクトリで無ければリンクしたルート`() {
        val fs = FakeFileSystem()
        val nested = module(":arch").copy(directory = ROOT.resolve("sample/android/arch"))
        assertEquals(ROOT, placementRootOf(nested, fs))
        fs.write(ROOT.resolve(".git"), "gitdir: x")
        assertEquals(ROOT, placementRootOf(nested, fs))
        fs.write(ROOT.resolve("sample/android/gradlew"), "")
        assertEquals(ROOT.resolve("sample/android"), placementRootOf(nested, fs))
    }
}

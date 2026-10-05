package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.model.ConstraintBackend
import me.tbsten.katachi.intellij.model.ConstraintRange
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.constraintModel
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of design 4.4, one at a time, over small hand-built definitions and the definitions katachi wrote (`contract/definition`). */
class DefinitionMatcherTest {
    private val dir = LayoutEntryKind.Directory
    private val any = LayoutEntryKind.AnyFile
    private val ignore = LayoutEntryKind.Ignore

    private val useCase = roleModel(
        "domain.UseCase",
        layoutEntry("core/domain", dir),
        layoutEntry("core/domain/useCase/*UseCase.kt"),
        layoutEntry("core/domain/useCase/*.kt"),
        constraints = listOf(constraintModel("invoke", files = listOf("core/domain/useCase/*UseCase.kt"))),
    )
    private val screen = roleModel(
        "ui.Screen",
        layoutEntry("feature", dir),
        layoutEntry("feature/*", dir),
        layoutEntry("feature/*/ui", any),
        constraints = listOf(constraintModel("direct", anyFileDirectories = listOf("feature/*/ui"))),
    )
    private val docs = roleModel("ui.Docs", layoutEntry("feature/*/ui/README.md"))
    private val root = roleModel(
        "gradle.Root",
        layoutEntry("build.gradle.kts", required = true),
        layoutEntry("build", ignore),
        layoutEntry("feature/*/build", ignore),
        layoutEntry("gradle", dir),
        layoutEntry("gradle/libs.versions.toml"),
    )
    private val matcher = DefinitionMatcher(definitionModel(useCase, screen, docs, root))

    private fun DefinitionMatcher.roles(path: String) = matchFile(path).matches.map { it.role.qualifiedName to it.entry.index }

    @Test
    fun `Fileの glob に当たるファイルは役割と entry が決まり Matched になる`() {
        val match = matcher.matchFile("core/domain/useCase/GetUserUseCase.kt")
        assertEquals(MatchOutcome.Matched, match.outcome)
        assertEquals(listOf("domain.UseCase" to 1), match.matches.map { it.role.qualifiedName to it.entry.index })
        assertEquals(emptyList<Any>(), match.nearby)
    }

    @Test
    fun `役割の中では宣言順で最初に当たった entry だけが残る`() {
        // Entry 1 and entry 2 of UseCase both match; the first wins, and the role is listed once.
        assertEquals(listOf("domain.UseCase" to 1), matcher.roles("core/domain/useCase/GetUserUseCase.kt"))
        assertEquals(listOf("domain.UseCase" to 2), matcher.roles("core/domain/useCase/helper.kt"))
    }

    @Test
    fun `AnyFile は直下のファイルだけを役割のものにする`() {
        assertEquals(listOf("ui.Screen" to 2), matcher.roles("feature/home/ui/HomeScreen.kt"))
        val deeper = matcher.matchFile("feature/home/ui/sub/Deep.kt")
        assertEquals(emptyList<Any>(), deeper.matches)
        assertEquals(MatchOutcome.UnexpectedUnder("feature/home/ui/sub"), deeper.outcome)
    }

    @Test
    fun `ファイル名で当たる役割が先で ディレクトリを開けているだけの役割はその後ろに並ぶ`() {
        // Screen is declared before Docs, but Docs names the file and Screen only leaves the directory open.
        assertEquals(listOf("ui.Docs" to 0, "ui.Screen" to 2), matcher.roles("feature/home/ui/README.md"))
    }

    @Test
    fun `当たった役割の制約のうち被覆に入るものだけが付く`() {
        val covered = matcher.matchFile("core/domain/useCase/GetUserUseCase.kt")
        assertEquals(listOf("invoke"), covered.constraints.map { it.constraint.name })
        val uncovered = matcher.matchFile("core/domain/useCase/helper.kt")
        assertEquals(emptyList<String?>(), uncovered.constraints.map { it.constraint.name })
        val direct = matcher.matchFile("feature/home/ui/HomeScreen.kt")
        assertEquals(listOf("direct"), direct.constraints.map { it.constraint.name })
    }

    @Test
    fun `DirectOnly の制約は被覆に書かれた直下のファイルだけに付き 深いファイルには付かない`() {
        val role = roleModel(
            "x.Role",
            layoutEntry("x/**"),
            constraints = listOf(constraintModel("direct", range = ConstraintRange.DirectOnly, files = listOf("x/*.kt"), entryIndices = listOf(0))),
        )
        val subject = DefinitionMatcher(definitionModel(role))
        assertEquals(listOf("direct"), subject.matchFile("x/Top.kt").constraints.map { it.constraint.name })
        assertEquals(emptyList<String?>(), subject.matchFile("x/y/Deep.kt").constraints.map { it.constraint.name })
        assertEquals(listOf("x.Role"), subject.matchFile("x/y/Deep.kt").matches.map { it.role.qualifiedName })
    }

    @Test
    fun `覆う glob が壊れた制約は外して残りの答えは出す`() {
        val broken = roleModel("x.Broken", layoutEntry("x/*.kt"), constraints = listOf(constraintModel("bad", files = listOf("x/Foo?.kt")), constraintModel("good", files = listOf("x/*.kt"))))
        val subject = DefinitionMatcher(definitionModel(broken))
        assertEquals(listOf("good"), subject.matchFile("x/A.kt").constraints.map { it.constraint.name })
        assertEquals(listOf("x/Foo?.kt"), subject.skippedGlobs.map { it.pattern })
    }

    @Test
    fun `entry の glob が壊れていればその entry は照合から外れ記録される`() {
        val broken = roleModel("x.Broken", layoutEntry("x/{a,b}.kt"), layoutEntry("y/*.kt"))
        val subject = DefinitionMatcher(definitionModel(broken))
        assertEquals(listOf("x.Broken" to 1), subject.roles("y/A.kt"))
        assertEquals(emptyList<Any>(), subject.matchFile("x/a.kt").matches)
        assertEquals(listOf("x/{a,b}.kt"), subject.skippedGlobs.map { it.pattern })
    }

    @Test
    fun `ignore の下は役割の entry に当たるファイルでも Ignored で他は見ない`() {
        val match = matcher.matchFile("feature/home/build/classes/Out.kt")
        assertEquals("feature/*/build", (match.outcome as MatchOutcome.Ignored).entry.pattern)
        assertEquals(emptyList<Any>(), match.matches)
        assertEquals(emptyList<Any>(), match.constraints)
        assertEquals(emptyList<Any>(), match.nearby)
        val under = matcher.matchFile("build/out.txt")
        assertEquals("build", (under.outcome as MatchOutcome.Ignored).entry.pattern)
    }

    @Test
    fun `ignore と同じ名前のファイルは祖先ではないので Ignored にならない`() {
        // `build` is ignored as a directory; the file `build.gradle.kts` has no ancestor named build.
        assertEquals(listOf("gradle.Root" to 0), matcher.roles("build.gradle.kts"))
    }

    @Test
    fun `知られたディレクトリの中の当たらないファイルは Unexpected で近い役割が付く`() {
        val match = matcher.matchFile("core/domain/Stray.kt")
        assertEquals(MatchOutcome.Unexpected, match.outcome)
        assertEquals(listOf("domain.UseCase" to "core/domain/useCase"), match.nearby.map { it.role.qualifiedName to it.directory })
    }

    @Test
    fun `どの役割も宣言していないディレクトリの下は最初のその階層で UnexpectedUnder`() {
        val match = matcher.matchFile("core/data/impl/Repo.kt")
        assertEquals(MatchOutcome.UnexpectedUnder("core/data"), match.outcome)
        // `core` is known (on the way to core/domain), `core/data` is not.
        assertEquals(listOf("domain.UseCase" to "core/domain/useCase"), match.nearby.map { it.role.qualifiedName to it.directory })
    }

    @Test
    fun `プロジェクトルート直下の当たらないファイルは Unexpected で近い役割は無い`() {
        val match = matcher.matchFile("README.md")
        assertEquals(MatchOutcome.Unexpected, match.outcome)
        assertEquals(emptyList<Any>(), match.nearby)
        assertEquals(MatchOutcome.UnexpectedUnder("zzz"), matcher.matchFile("zzz/a/b.txt").outcome)
        assertEquals(emptyList<Any>(), matcher.matchFile("zzz/a/b.txt").nearby)
    }

    @Test
    fun `isKnown は宣言されたディレクトリとその途中を知っていて isIgnored は ignore だけ`() {
        assertTrue(matcher.isKnown("core"))
        assertTrue(matcher.isKnown("core/domain/useCase"))
        assertTrue(matcher.isKnown("feature/anything"))
        assertTrue(!matcher.isKnown("core/data"))
        assertTrue(matcher.isIgnored("feature/x/build"))
        assertTrue(!matcher.isIgnored("feature/x"))
    }

    @Test
    fun `Unknown の kind の entry は何にも当たらないが途中のディレクトリとしては知られる`() {
        val subject = DefinitionMatcher(definitionModel(roleModel("x.Future", layoutEntry("future/*/thing", LayoutEntryKind.Unknown("Hologram")))))
        assertEquals(emptyList<Any>(), subject.matchFile("future/a/thing").matches)
        assertTrue(subject.isKnown("future/a/thing"))
        assertEquals(MatchOutcome.Unexpected, subject.matchFile("future/a/other.kt").outcome)
    }

    @Test
    fun `未知の backend の制約も被覆に入れば付く`() {
        val future = roleModel("x.Future", layoutEntry("x/*.kt"), constraints = listOf(constraintModel("f", backend = ConstraintBackend.Unknown("hologram"), files = listOf("x/*.kt"))))
        assertEquals(listOf("f"), DefinitionMatcher(definitionModel(future)).matchFile("x/A.kt").constraints.map { it.constraint.name })
    }

    // --- nearby (katachi's LayoutIndex.nearbyOf) ---

    private fun nearby(vararg roles: me.tbsten.katachi.intellij.model.RoleModel, file: String) =
        DefinitionMatcher(definitionModel(*roles)).nearbyOf(file).map { it.role.qualifiedName to it.directory }

    @Test
    fun `nearby は先頭の共有階層が多い順で同点は宣言順で3件まで`() {
        val r1 = roleModel("r.One", layoutEntry("a/b/c/Foo.kt"))
        val r2 = roleModel("r.Two", layoutEntry("a/b/Foo.kt"))
        val r3 = roleModel("r.Three", layoutEntry("a/x/Foo.kt"))
        val r4 = roleModel("r.Four", layoutEntry("a/b/c/d", any))
        val r5 = roleModel("r.Five", layoutEntry("a/y/Foo.kt"))
        // The file's directory a/b/c shares 3 with One and Four (tie: declaration order), 2 with Two, 1 with Three and Five.
        assertEquals(
            listOf("r.One" to "a/b/c", "r.Four" to "a/b/c/d", "r.Two" to "a/b"),
            nearby(r1, r2, r3, r4, r5, file = "a/b/c/Stray.kt"),
        )
    }

    @Test
    fun `nearby に何も共有しない場所とルートとアスタリスクを含む場所と ignore と Directory は入らない`() {
        val roles = arrayOf(
            roleModel("r.Far", layoutEntry("z/Foo.kt")),
            roleModel("r.Root", layoutEntry("Foo.kt")),
            roleModel("r.Pattern", layoutEntry("a/*/Foo.kt")),
            roleModel("r.Ignored", layoutEntry("a/b", ignore)),
            roleModel("r.Directory", layoutEntry("a/c", dir)),
            roleModel("r.Near", layoutEntry("a/d/Foo.kt")),
        )
        assertEquals(listOf("r.Near" to "a/d"), nearby(*roles, file = "a/Stray.kt"))
    }

    @Test
    fun `nearby は同じ役割の同じディレクトリを1回にまとめ役割が違えば別に数える`() {
        val one = roleModel("r.One", layoutEntry("a/b/X.kt"), layoutEntry("a/b/Y.kt"), layoutEntry("a/b", any))
        val two = roleModel("r.Two", layoutEntry("a/b/Z.kt"))
        assertEquals(listOf("r.One" to "a/b", "r.Two" to "a/b"), nearby(one, two, file = "a/b/Stray.kt"))
    }

    @Test
    fun `Matched のときは nearby が空`() {
        assertEquals(emptyList<Any>(), matcher.matchFile("core/domain/useCase/GetUserUseCase.kt").nearby)
    }

    // --- the fixtures ---

    @Test
    fun `fixture の matchedFiles に入るファイルは照合でも同じ役割で 宣言順で最初に当たる entry に当たる`() {
        val names = DefinitionFixtures.definitionNames()
        assertTrue(names.containsAll(listOf("synthetic-definition-structure", "synthetic-definition-basic", "synthetic-definition-many")))
        for (name in names) {
            val definition = DefinitionFixtures.definition(name)
            val subject = DefinitionMatcher(definition)
            for (role in definition.roles) {
                for (entry in role.layout.filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }) {
                    for (file in entry.matchedFiles) {
                        val match = subject.matchFile(file)
                        val chosen = match.matches.firstOrNull { it.role.qualifiedName == role.qualifiedName }
                            ?: throw AssertionError("$name: $file is in ${role.qualifiedName}[${entry.index}].matchedFiles but matched ${match.matches.map { it.role.qualifiedName }} (${match.outcome})")
                        // A role is held by the first `File` entry that names the file, else by the first `anyFile()` one that leaves its directory open.
                        val named = role.layout.firstOrNull { it.kind == LayoutEntryKind.File && KatachiGlob.compile(it.pattern).matches(file) }
                        val open = role.layout.firstOrNull { it.kind == LayoutEntryKind.AnyFile && file.parentPath().isNotEmpty() && KatachiGlob.compile(it.pattern).matches(file.parentPath()) }
                        assertEquals("$name: $file in ${role.qualifiedName}", (named ?: open)?.index, chosen.entry.index)
                    }
                }
            }
            assertEquals("$name: every glob of the fixture compiles", emptyList<Any>(), subject.skippedGlobs)
        }
    }

    @Test
    fun `katachi が書いた basic の UseCase のファイルには制約が付き Model では Subtree の制約が深いファイルにも DirectOnly の制約は直下にだけ付く`() {
        val subject = DefinitionMatcher(DefinitionFixtures.definition("synthetic-definition-basic"))
        val named = subject.matchFile("core/domain/src/main/kotlin/useCase/GetUserUseCase.kt")
        assertEquals(MatchOutcome.Matched, named.outcome)
        assertEquals(listOf("does nothing"), named.constraints.map { it.constraint.name })
        assertEquals(listOf(ConstraintBackend.Custom), named.constraints.map { it.constraint.backend })
        val model = "core/domain/src/main/kotlin/model"
        assertEquals(listOf("the whole role", "only the directory itself"), subject.matchFile("$model/User.kt").constraints.map { it.constraint.name })
        assertEquals(listOf("the whole role"), subject.matchFile("$model/mapper/UserMapper.kt").constraints.map { it.constraint.name })
        assertEquals(emptyList<Any>(), subject.matchFile("settings.gradle.kts").constraints)
    }

    @Test
    fun `katachi が書いた basic の ignore の下は Ignored で 宣言の無いディレクトリは UnexpectedUnder で 知られたディレクトリの中の当たらないファイルは Unexpected`() {
        val subject = DefinitionMatcher(DefinitionFixtures.definition("synthetic-definition-basic"))
        assertEquals("core/domain/build", (subject.matchFile("core/domain/build/classes/Out.class").outcome as MatchOutcome.Ignored).entry.pattern)
        assertEquals(MatchOutcome.UnexpectedUnder("elsewhere"), subject.matchFile("elsewhere/a.kt").outcome)
        assertEquals(MatchOutcome.Unexpected, subject.matchFile("core/domain/mystery.txt").outcome)
        assertTrue(subject.matchFile("core/domain/mystery.txt").nearby.isEmpty().not())
    }

    @Test
    fun `手書きの future fixture の 知らない kind の entry は何にも当たらないが 途中のディレクトリとしては知られる`() {
        val subject = DefinitionMatcher(DefinitionFixtures.definition("handwritten-definition-future"))
        assertEquals(MatchOutcome.Unexpected, subject.matchFile("feature/home/mystery/a.kt").outcome)
        assertTrue(subject.isKnown("feature/home/mystery"))
        // The Docs `File` entry names README.md before the Screen `anyFile()` leaves its directory open.
        assertEquals(listOf("ui.Docs", "ui.Screen"), subject.matchFile("feature/home/ui/README.md").matches.map { it.role.qualifiedName })
    }

    // --- module { } のしかけ（synthetic / place）と katachi が見ないディレクトリ ---

    @Test
    fun `katachi が書いた module の定義でも build_gradle_kts は Matched で build の下は Ignored`() {
        // synthetic-definition-structure.json is what the real task wrote for `":feature:*".module { }`: the injected entries are in it.
        val subject = DefinitionMatcher(DefinitionFixtures.definition("synthetic-definition-structure"))
        for (module in listOf("feature/home", "feature/settings")) {
            val script = subject.matchFile("$module/build.gradle.kts")
            assertEquals(MatchOutcome.Matched, script.outcome)
            assertTrue(script.matches.single().entry.synthetic)
            assertEquals("$module/build", (subject.matchFile("$module/build/classes/Out.class").outcome as MatchOutcome.Ignored).entry.pattern)
            assertTrue(subject.isKnown(module))
            // `feature/home` is known (a place, and the way to its sources): a stray file there is Unexpected, not "under an unknown directory".
            assertEquals(MatchOutcome.Unexpected, subject.matchFile("$module/mystery.txt").outcome)
        }
        assertEquals(MatchOutcome.UnexpectedUnder("feature/other"), subject.matchFile("feature/other/a.txt").outcome)
    }

    @Test
    fun `synthetic と place の entry を除くと build_gradle_kts は Unexpected で build の下は UnexpectedUnder になる`() {
        // What the definition looked like when the JSON left them out: the answers the check never gives.
        val model = DefinitionFixtures.definition("synthetic-definition-structure")
        val withoutInjected = model.copy(roles = model.roles.map { role -> role.copy(layout = role.layout.filterNot { it.synthetic || it.place }) })
        val subject = DefinitionMatcher(withoutInjected)
        assertEquals(MatchOutcome.Unexpected, subject.matchFile("feature/home/build.gradle.kts").outcome)
        assertEquals(MatchOutcome.UnexpectedUnder("feature/home/build"), subject.matchFile("feature/home/build/classes/Out.class").outcome)
    }

    @Test
    fun `git と gradle と idea のディレクトリの下は どの深さでも katachi が見ないので NotWalked`() {
        assertEquals(MatchOutcome.NotWalked(".idea"), matcher.matchFile(".idea/workspace.xml").outcome)
        assertEquals(MatchOutcome.NotWalked("core/domain/.gradle"), matcher.matchFile("core/domain/.gradle/caches/x.bin").outcome)
        assertEquals(MatchOutcome.NotWalked(".git"), matcher.matchFile(".git/HEAD").outcome)
        val notWalked = matcher.matchFile("core/.gradle/8.5/x.lock")
        assertEquals(MatchOutcome.NotWalked("core/.gradle"), notWalked.outcome)
        assertEquals(emptyList<Any>(), notWalked.matches)
        assertEquals(emptyList<Any>(), notWalked.nearby)
    }

    @Test
    fun `見ない名前でも ファイルの名前なら祖先ではなく 名前が少し違うディレクトリは見る`() {
        assertEquals(MatchOutcome.Unexpected, matcher.matchFile("core/domain/.idea").outcome)
        assertEquals(MatchOutcome.UnexpectedUnder("core/domain/.ideas"), matcher.matchFile("core/domain/.ideas/a.xml").outcome)
    }

    @Test
    fun `階層ごとに見るので ignore の下の見ないディレクトリは Ignored で 見ないディレクトリの下の ignore は NotWalked`() {
        assertEquals("build", (matcher.matchFile("build/.gradle/x").outcome as MatchOutcome.Ignored).entry.pattern)
        assertEquals(MatchOutcome.NotWalked(".gradle"), matcher.matchFile(".gradle/build/x").outcome)
    }

    @Test
    fun `見ないディレクトリより上に宣言されていない階層があれば そこで止まって UnexpectedUnder`() {
        // katachi's walk reaches `zzz` first and reports it; it never gets to `.idea` below it.
        val undeclared = matcher.matchFile("zzz/.idea/workspace.xml")
        assertEquals(MatchOutcome.UnexpectedUnder("zzz"), undeclared.outcome)
        assertEquals(emptyList<Any>(), undeclared.matches)
        // Under declared directories the foreign one is the stop.
        assertEquals(MatchOutcome.NotWalked("core/domain/.idea"), matcher.matchFile("core/domain/.idea/x.xml").outcome)
    }
}

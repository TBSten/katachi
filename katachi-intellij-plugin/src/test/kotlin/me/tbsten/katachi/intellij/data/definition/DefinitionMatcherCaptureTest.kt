package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.model.CaptureRef
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.ModuleCaptureRef
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** The bound captures of an entry (design capture-check 3.8, the JSON contract of k17): which files a bound entry holds, and what `CaptureMismatch` says of the rest. */
class DefinitionMatcherCaptureTest {
    private val any = LayoutEntryKind.AnyFile

    private val boundSources = "feature/*/src/main/kotlin/com/example/sample/feature/*"
    private val boundScreen = roleModel(
        "feature.Screen",
        layoutEntry("$boundSources/*Screen.kt", captures = listOf(CaptureRef("feature", 1), CaptureRef("feature", 9), CaptureRef("feature", 10, 0, "pascalCase"))),
    )
    private val boundComponent = roleModel(
        "feature.FeatureComponent",
        layoutEntry("$boundSources/component/*.kt", captures = listOf(CaptureRef("feature", 1), CaptureRef("feature", 9), CaptureRef("name", 11))),
    )
    private val bound = DefinitionMatcher(definitionModel(boundScreen, boundComponent))

    private fun occurrences(match: me.tbsten.katachi.intellij.model.FileMatch) =
        (match.outcome as MatchOutcome.CaptureMismatch).occurrences.map { listOf(it.name, it.transform, it.level, it.value, it.expected) }

    @Test
    fun `束縛のある entry は同じ名前の階層が同じ値のファイルだけを許す`() {
        assertEquals(MatchOutcome.Matched, bound.matchFile("feature/home/src/main/kotlin/com/example/sample/feature/home/HomeScreen.kt").outcome)
        assertEquals(MatchOutcome.Matched, bound.matchFile("feature/home/src/main/kotlin/com/example/sample/feature/home/component/HomeUserCard.kt").outcome)

        val mismatch = bound.matchFile("feature/home/src/main/kotlin/com/example/sample/feature/settings/HomeScreen.kt")
        val outcome = mismatch.outcome as MatchOutcome.CaptureMismatch
        assertEquals("feature.Screen", outcome.role.qualifiedName)
        assertEquals(0, outcome.entry.index)
        assertEquals(
            listOf(
                listOf("feature", null, 1, "home", null),
                listOf("feature", null, 9, "settings", "home"),
                listOf("feature", "pascalCase", 10, "Home", "Home"),
            ),
            occurrences(mismatch),
        )
        assertEquals("feature/home/src/main/kotlin/com/example/sample/feature/home/HomeScreen.kt", outcome.expectedPath)
        assertEquals(emptyList<Any>(), mismatch.matches)
        assertEquals(emptyList<Any>(), mismatch.constraints)
        assertEquals(emptyList<Any>(), mismatch.nearby)
    }

    @Test
    fun `派生の階層は元の値の変換と一致するときだけ許す`() {
        val mismatch = bound.matchFile("feature/home/src/main/kotlin/com/example/sample/feature/home/ProfileScreen.kt")
        assertEquals(listOf(listOf("feature", null, 1, "home", null), listOf("feature", null, 9, "home", "home"), listOf("feature", "pascalCase", 10, "Profile", "Home")), occurrences(mismatch))
        assertEquals("feature/home/src/main/kotlin/com/example/sample/feature/home/HomeScreen.kt", (mismatch.outcome as MatchOutcome.CaptureMismatch).expectedPath)
        // The conversion reads katachi's words: `my-page` derives `MyPage`.
        assertEquals(MatchOutcome.Matched, bound.matchFile("feature/my-page/src/main/kotlin/com/example/sample/feature/my-page/MyPageScreen.kt").outcome)
    }

    @Test
    fun `束縛で落ちた entry があってもほかの役割が許せば Matched で 落ちた entry は役割に数えない`() {
        val anyScreen = roleModel("feature.AnyScreen", layoutEntry("$boundSources/*Screen.kt"))
        val subject = DefinitionMatcher(definitionModel(boundScreen, anyScreen))
        val match = subject.matchFile("feature/home/src/main/kotlin/com/example/sample/feature/settings/HomeScreen.kt")
        assertEquals(MatchOutcome.Matched, match.outcome)
        assertEquals(listOf("feature.AnyScreen"), match.matches.map { it.role.qualifiedName })
    }

    @Test
    fun `束縛の無い entry は captures の値を見ず アスタリスクと同じ`() {
        val plain = roleModel("feature.Screen", layoutEntry("$boundSources/*Screen.kt", captures = listOf(CaptureRef("feature", 1), CaptureRef("name", 10))))
        val subject = DefinitionMatcher(definitionModel(plain))
        assertEquals(MatchOutcome.Matched, subject.matchFile("feature/home/src/main/kotlin/com/example/sample/feature/settings/ProfileScreen.kt").outcome)
    }

    @Test
    fun `CaptureMismatch は宣言順で最初に落ちた entry で組み 宣言の無い階層の下ならそちらが先`() {
        val later = roleModel("feature.Later", layoutEntry("$boundSources/*Screen.kt", captures = listOf(CaptureRef("f", 1), CaptureRef("f", 9))))
        val subject = DefinitionMatcher(definitionModel(later, boundScreen))
        val mismatch = subject.matchFile("feature/home/src/main/kotlin/com/example/sample/feature/settings/HomeScreen.kt")
        assertEquals("feature.Later", (mismatch.outcome as MatchOutcome.CaptureMismatch).role.qualifiedName)
        assertEquals(MatchOutcome.UnexpectedUnder("feature/home/src/main/kotlin/com/example/sample/feature/settings/deep"), subject.matchFile("feature/home/src/main/kotlin/com/example/sample/feature/settings/deep/HomeScreen.kt").outcome)
    }

    @Test
    fun `anyFile の entry は親ディレクトリで束縛を比べ 移す先にはファイルの名前が残る`() {
        val docs = roleModel("docs.Feature", layoutEntry("feature/*/docs/*", any, captures = listOf(CaptureRef("f", 1), CaptureRef("f", 3))))
        val subject = DefinitionMatcher(definitionModel(docs))
        assertEquals(MatchOutcome.Matched, subject.matchFile("feature/home/docs/home/x.md").outcome)
        val mismatch = subject.matchFile("feature/home/docs/api/x.md")
        assertEquals(listOf(listOf("f", null, 1, "home", null), listOf("f", null, 3, "api", "home")), occurrences(mismatch))
        assertEquals("feature/home/docs/home/x.md", (mismatch.outcome as MatchOutcome.CaptureMismatch).expectedPath)
    }

    @Test
    fun `名前の無いアスタリスクと同じ区切りの capture は wildcardIndex で値を読み 二重アスタリスクは移す先に残る`() {
        val role = roleModel("x.Pair", layoutEntry("src/*/**/*-*.kt", captures = listOf(CaptureRef("source", 1, 0), CaptureRef("source", 3, 1))))
        val subject = DefinitionMatcher(definitionModel(role))
        assertEquals(MatchOutcome.Matched, subject.matchFile("src/home/a/b/x-home.kt").outcome)
        val mismatch = subject.matchFile("src/home/a/b/x-settings.kt")
        assertEquals(listOf(listOf("source", null, 1, "home", null), listOf("source", null, 3, "settings", "home")), occurrences(mismatch))
        assertEquals("src/home/a/b/x-home.kt", (mismatch.outcome as MatchOutcome.CaptureMismatch).expectedPath)
    }

    @Test
    fun `モジュールキーの名前とパスの同名は modulePath が実名のときだけ比べる`() {
        fun entry(modulePath: String) = layoutEntry(
            "feature/home/src/main/kotlin/com/example/feature/*/Foo.kt",
            modulePath = modulePath,
            pathInModule = "src/main/kotlin/com/example/feature/*/Foo.kt",
            moduleCapture = ModuleCaptureRef(":feature:*", listOf("feature")),
            captures = listOf(CaptureRef("feature", 8)),
        )
        val resolved = DefinitionMatcher(definitionModel(roleModel("f.Foo", entry(":feature:home"))))
        assertEquals(MatchOutcome.Matched, resolved.matchFile("feature/home/src/main/kotlin/com/example/feature/home/Foo.kt").outcome)
        val mismatch = resolved.matchFile("feature/home/src/main/kotlin/com/example/feature/settings/Foo.kt")
        assertEquals(listOf(listOf("feature", null, 1, "home", null), listOf("feature", null, 8, "settings", "home")), occurrences(mismatch))
        assertEquals("feature/home/src/main/kotlin/com/example/feature/home/Foo.kt", (mismatch.outcome as MatchOutcome.CaptureMismatch).expectedPath)

        val unresolved = DefinitionMatcher(definitionModel(roleModel("f.Foo", entry(":feature:*"))))
        assertEquals(MatchOutcome.Matched, unresolved.matchFile("feature/home/src/main/kotlin/com/example/feature/settings/Foo.kt").outcome)
    }

    @Test
    fun `知らない変換の派生は比べずに許す`() {
        val future = roleModel("f.Future", layoutEntry("feature/*/*Screen.kt", captures = listOf(CaptureRef("f", 1), CaptureRef("f", 2, 0, "hologram"))))
        assertEquals(MatchOutcome.Matched, DefinitionMatcher(definitionModel(future)).matchFile("feature/home/XScreen.kt").outcome)
    }
}

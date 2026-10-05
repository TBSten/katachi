package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/**
 * What [FileConstraintSubject.captureNames] and [FileConstraintSubject.captureValuesOf] answer: the
 * `capture("...")` names of the layout a constraint covers, and what each file's own path gives
 * them.
 *
 * Every case runs the real check over a fake tree and reads the subject a recording constraint
 * was handed, so what is pinned is what a backend sees, not a helper's own answer.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */

/** The subjects one role-wide constraint is handed when [layout] is checked against [tree]. */
private fun subjectsOf(tree: FakeFileSystem, layout: LayoutScope.() -> Unit): List<FileConstraintSubject> =
    subjectsOf(tree, listOf(layout))

/** [subjectsOf] for a role that declares each of [layouts] as a `layout { }` of its own. */
private fun subjectsOf(tree: FakeFileSystem, layouts: List<LayoutScope.() -> Unit>): List<FileConstraintSubject> {
    val recording = RecordingFileConstraint()
    val arch = architectureOf {
        "app".group {
            "Role".role {
                fileConstraint(check = recording)
                layouts.forEach { layout(it) }
            }
        }
    }
    arch.validate(tree, FileConstraintCheck())
    return recording.subjects
}

class FileConstraintCaptureSpec : FreeSpec({
    "パスの capture" - {
        "ファイル名の区切りの capture" {
            val subject = subjectsOf(repositoryOf { "data" { "UserRepositoryImpl.kt"() } }) {
                "data" / "${capture("name")}RepositoryImpl".ktFile()
            }.single()

            subject.captureNames.toList() shouldBe listOf("name")
            subject.captureValuesOf("data/UserRepositoryImpl.kt") shouldBe mapOf("name" to "User")
        }

        "ディレクトリの capture" {
            val subject = subjectsOf(repositoryOf { "feature/home" { "HomeScreen.kt"() } }) {
                "feature" / capture("feature") / "*Screen".ktFile()
            }.single()

            subject.captureValuesOf("feature/home/HomeScreen.kt") shouldBe mapOf("feature" to "home")
        }

        "** の後ろのファイル名の capture は、** が何段に当たっても取れる" {
            val tree = repositoryOf {
                "src" { "HomeScreen.kt"(); "a/b" { "SettingsScreen.kt"() } }
            }
            val subject = subjectsOf(tree) { "src" / "**" / "${capture("name")}Screen".ktFile() }.single()

            subject.captureValuesOf("src/HomeScreen.kt") shouldBe mapOf("name" to "Home")
            subject.captureValuesOf("src/a/b/SettingsScreen.kt") shouldBe mapOf("name" to "Settings")
        }

        "1 つの区切りの 2 つの capture をそれぞれ取れる" {
            val subject = subjectsOf(repositoryOf { "data" { "user-remote.kt"() } }) {
                "data" / "${capture("entity")}-${capture("source")}.kt".file()
            }.single()

            subject.captureNames.toList() shouldBe listOf("entity", "source")
            subject.captureValuesOf("data/user-remote.kt") shouldBe mapOf("entity" to "user", "source" to "remote")
        }

        "anyFile() のディレクトリの capture は、そのディレクトリの中のファイルで取れる" {
            val subject = subjectsOf(repositoryOf { "docs/home" { "notes.md"() } }) {
                "docs" { capture("feature") { anyFile() } }
            }.single()

            subject.captureValuesOf("docs/home/notes.md") shouldBe mapOf("feature" to "home")
        }

        "名前の無い * と同じ区切りの capture の値も取れる" {
            // Which `*` of `*-*.kt` the name belongs to is kept as its wildcard index.
            val subject = subjectsOf(repositoryOf { "data" { "user-remote.kt"() } }) {
                "data" / "*-${capture("source")}.kt".file()
            }.single()

            subject.captureNames.toList() shouldBe listOf("source")
            subject.captureValuesOf("data/user-remote.kt") shouldBe mapOf("source" to "remote")
        }
    }

    "モジュールの capture" - {
        val modules = repositoryOf {
            "feature/home" { "build.gradle.kts"(); "src/main/kotlin" { "HomeScreen.kt"() } }
            "feature/settings" { "build.gradle.kts"(); "src/main/kotlin" { "SettingsScreen.kt"() } }
        }

        "モジュールキーの capture は、そのファイルのモジュールの名前になる" {
            val subject = subjectsOf(modules) {
                ":feature:${capture("feature")}".module { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.single()

            subject.captureValuesOf("feature/home/src/main/kotlin/HomeScreen.kt") shouldBe mapOf("feature" to "home")
            subject.captureValuesOf("feature/settings/src/main/kotlin/SettingsScreen.kt") shouldBe
                mapOf("feature" to "settings")
        }

        "名前はモジュールのものが先、パスのものが後に並ぶ" {
            val subject = subjectsOf(modules) {
                ":feature:${capture("feature")}".module { mainSourceSet / kotlin / "${capture("name")}Screen".ktFile() }
            }.single()

            subject.captureNames.toList() shouldBe listOf("feature", "name")
            subject.captureValuesOf("feature/home/src/main/kotlin/HomeScreen.kt") shouldBe
                mapOf("feature" to "home", "name" to "Home")
        }

        "一部の * にだけ名前を付けたモジュールキーは、どの名前がどの * か分からないので値を取らない" {
            val tree = repositoryOf { "lib/core/data" { "build.gradle.kts"(); "Data.kt"() } }
            val subject = subjectsOf(tree) { ":lib:*:${capture("part")}".module { "*.kt".file() } }.single()

            subject.captureNames.toList() shouldBe listOf("part")
            subject.captureValuesOf("lib/core/data/Data.kt").shouldBeEmpty()
        }
    }

    "当たらないもの" - {
        "capture の無い宣言に当たるファイルは空" {
            val tree = repositoryOf {
                "feature/home" { "HomeScreen.kt"() }
                "misc" { "notes.md"() }
            }
            val subject = subjectsOf(tree) {
                "feature" / capture("feature") / "*Screen".ktFile()
                "misc" / "*.md".file()
            }.single()

            subject.captureNames.toList() shouldBe listOf("feature")
            subject.captureValuesOf("misc/notes.md").shouldBeEmpty()
        }

        "2 つの layout の片方だけが capture するとき、当たった側の値だけが入る" {
            val tree = repositoryOf {
                "feature/home" { "HomeScreen.kt"() }
                "shared" { "Theme.kt"() }
            }
            val subject = subjectsOf(
                tree,
                listOf<LayoutScope.() -> Unit>(
                    { "feature" / capture("feature") / "*Screen".ktFile() },
                    { "shared" / "*.kt".file() },
                ),
            ).single()

            subject.captureNames.toList() shouldBe listOf("feature")
            subject.captureValuesOf("feature/home/HomeScreen.kt") shouldBe mapOf("feature" to "home")
            subject.captureValuesOf("shared/Theme.kt").shouldBeEmpty()
        }

        "layout が何も capture しなければ、名前も値も空" {
            val subject = subjectsOf(repositoryOf { "data" { "User.kt"() } }) { "data" / "*.kt".file() }.single()

            subject.captureNames shouldBe emptySet()
            subject.captureValuesOf("data/User.kt").shouldBeEmpty()
        }

        "覆うファイルでないパスにも、layout から分かる値を返す" {
            val subject = subjectsOf(repositoryOf { "feature/home" { "HomeScreen.kt"() } }) {
                "feature" / capture("feature") / "*Screen".ktFile()
            }.single()

            subject.captureValuesOf("feature/cart/CartScreen.kt") shouldBe mapOf("feature" to "cart")
        }
    }

    "束縛と派生" - {
        val tree = repositoryOf {
            "feature/home" { "home" { "HomeScreen.kt"() }; "settings" { "HomeScreen.kt"() } }
        }

        "束縛のある名前の captureValuesOf は1つの値を返す" {
            val subject = subjectsOf(tree) {
                "feature" / capture("feature") / capture("feature") / "*Screen".ktFile()
            }.single()

            subject.captureNames.toList() shouldBe listOf("feature")
            subject.captureValuesOf("feature/home/home/HomeScreen.kt") shouldBe mapOf("feature" to "home")
        }

        "一致しないファイルは subject.files に入らない" {
            val subject = subjectsOf(tree) {
                "feature" / capture("feature") / capture("feature") / "*Screen".ktFile()
            }.single()

            subject.files shouldBe listOf("feature/home/home/HomeScreen.kt")
        }

        "派生は captureNames にも captureValuesOf にも出ない" {
            val subject = subjectsOf(tree) {
                "feature" / capture("feature") / "*" / "${capture("feature").pascalCase}Screen".ktFile()
            }.single()

            subject.captureNames.toList() shouldBe listOf("feature")
            subject.captureValuesOf("feature/home/home/HomeScreen.kt") shouldBe mapOf("feature" to "home")
        }
    }
})

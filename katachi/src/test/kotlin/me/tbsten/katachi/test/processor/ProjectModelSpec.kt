package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.FsPath
import me.tbsten.katachi.check.KatachiFileSystem
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.processor.KatachiUnknownRoleException
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.check.FileSystemTouchedException
import me.tbsten.katachi.test.check.ForbiddenFileSystem
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.layoutArchitecture
import me.tbsten.katachi.test.check.repositoryOf

class ProjectModelSpec : FreeSpec({
    "ファイルシステムに触る境界" - {
        "宣言しか見ない processor はファイルシステムに触らない" {
            val definition = architectureOf {
                "domain".group {
                    "UseCase" { layout { "useCase" / "*UseCase".ktFile() } }
                }
            }

            definition.process(ForbiddenFileSystem) { model ->
                model.groups.map { it.qualifiedName } +
                    model.roles.map { it.qualifiedName } +
                    model.declaredEntries.map { it.path }
            } shouldBe listOf("domain", "domain/UseCase", "useCase", "useCase/*UseCase.kt")
        }

        "filesOf を呼んだときに初めてファイルシステムに触る" {
            val definition = layoutArchitecture { "useCase" / "*UseCase".ktFile() }

            shouldThrow<FileSystemTouchedException> {
                definition.process(ForbiddenFileSystem) { model ->
                    model.filesOf(model.roles.single())
                }
            }
        }

        "filesOf を何度呼んでも走査は1度だけ行われる" {
            val definition = layoutArchitecture { "core" / "*.kt".file() }
            // Counted rather than asserted against a fixed number: what matters is that the
            // second and third call cost nothing, not how many directories one walk lists.
            fun listCallsWhenCalled(times: Int): Int {
                val tree = CountingFileSystem(repositoryOf { "core" { "App.kt"(); "Main.kt"() } })
                definition.process(tree) { model ->
                    val role = model.roles.single()
                    repeat(times) { model.filesOf(role) }
                }
                return tree.listCalls
            }

            listCallsWhenCalled(times = 3) shouldBe listCallsWhenCalled(times = 1)
        }
    }

    "filesOf" - {
        "glob が重なるファイルは一致した役割すべてで返る" {
            val definition = architectureOf {
                "domain".group {
                    "UseCase" { layout { "core" / "*.kt".file() } }
                    "Api" { layout { "core" / "*Api.kt".file() } }
                }
            }

            definition.process(repositoryOf { "core" { "GetUser.kt"(); "UserApi.kt"() } }) { model ->
                model.roles.associate { it.qualifiedName to model.filesOf(it) }
            } shouldBe mapOf(
                "domain/UseCase" to listOf("core/GetUser.kt", "core/UserApi.kt"),
                "domain/Api" to listOf("core/UserApi.kt"),
            )
        }

        "同じ役割が2つの宣言で同じファイルに一致しても1度だけ返る" {
            val definition = layoutArchitecture {
                "core" / "*.kt".file()
                "core" / "App.kt".file()
            }

            definition.process(repositoryOf { "core" { "App.kt"() } }) { model ->
                model.filesOf(model.roles.single())
            } shouldBe listOf("core/App.kt")
        }

        "anyFile を宣言した役割には直下のファイルが入る" {
            val definition = layoutArchitecture { "generated" { anyFile() } }

            definition.process(repositoryOf { "generated" { "a.kt"(); "b.txt"() } }) { model ->
                model.filesOf(model.roles.single())
            } shouldBe listOf("generated/a.kt", "generated/b.txt")
        }

        "実体の無い宣言しか持たない役割は空になる" {
            val definition = layoutArchitecture { "gradle" / "libs.versions.toml".file() }

            definition.process(repositoryOf { }) { model ->
                model.filesOf(model.roles.single())
            } shouldBe emptyList()
        }

        "ignore した配下のファイルは返らない" {
            val definition = layoutArchitecture { "build".ignore() }

            definition.process(repositoryOf { "build" { "output.jar"() } }) { model ->
                model.filesOf(model.roles.single())
            } shouldBe emptyList()
        }

        "宣言のないファイルはどの役割にも入らない" {
            val definition = layoutArchitecture { "core" / "App.kt".file() }

            definition.process(repositoryOf { "core" { "App.kt"(); "notes.md"() } }) { model ->
                model.filesOf(model.roles.single())
            } shouldBe listOf("core/App.kt")
        }
    }

    "別の定義の役割" - {
        // filesByRole は Role を同一性で引く。別の Architecture 由来の Role は永遠に
        // 一致しないので、素通しすると「この役割は何も持っていない」と区別が付かない
        // 空リストが返る。architecture { } を val ではなく fun で包めば毎回起きる。
        "filesOf に渡すと落ちる" {
            val definition = layoutArchitecture { "core" / "App.kt".file() }
            val another = layoutArchitecture { "core" / "App.kt".file() }

            val thrown = shouldThrow<KatachiUnknownRoleException> {
                definition.process(repositoryOf { "core" { "App.kt"() } }) { model ->
                    model.filesOf(another.allRoles.single())
                }
            }

            thrown.role.qualifiedName shouldBe "app/Role"
        }

        "落ちる前にファイルシステムには触らない" {
            val definition = layoutArchitecture { "core" / "App.kt".file() }
            val another = layoutArchitecture { "core" / "App.kt".file() }

            shouldThrow<KatachiUnknownRoleException> {
                definition.process(ForbiddenFileSystem) { model ->
                    model.filesOf(another.allRoles.single())
                }
            }
        }

        "メッセージは役割の qualifiedName と宣言位置を名指しする" {
            val definition = layoutArchitecture { "core" / "App.kt".file() }
            val another = layoutArchitecture { "core" / "App.kt".file() }

            val thrown = shouldThrow<KatachiUnknownRoleException> {
                definition.process(ForbiddenFileSystem) { model ->
                    model.filesOf(another.allRoles.single())
                }
            }

            // 例外が公開している値と文面が食い違わないことの固定。宣言位置は
            // layoutArchitecture が書いている行なので、ファイル名は決め打ちしない。
            thrown.message.shouldNotBeNull() shouldContain "\"${thrown.role.qualifiedName}\""
            thrown.message.shouldNotBeNull() shouldContain "${thrown.role.declaredAt}"
        }
    }

    "ワイルドカードの module キー" - {
        // declaredEntries は宣言だけを読む（ファイルシステムに触らない）ので、
        // ":feature:*" が何モジュールに化けるかを知りようがない。filesOf は実際に
        // 歩くので展開後が見える。この食い違いは仕様であって、KDoc の1段落だけに
        // 任せておくと次の誰かが黙って壊す。
        val definition = architectureOf {
            "feature".group { "Module" { layout { ":feature:*".module { } } } }
        }
        val tree = repositoryOf {
            "feature" {
                "home" { "build.gradle.kts"() }
                "settings" { "build.gradle.kts"() }
            }
        }

        "同じモデルの declaredEntries には現れず、filesOf には現れる" {
            // 1つの ProjectModel から両方を読む。別々に process すると「モデルが2つある
            // から答えが違う」と読めてしまう。
            val (declared, walked) = definition.process(tree) { model ->
                model.declaredEntries.map { it.path } to model.filesOf(model.roles.single())
            }

            declared shouldBe emptyList()
            walked shouldBe listOf(
                "feature/home/build.gradle.kts",
                "feature/settings/build.gradle.kts",
            )
        }

        "ワイルドカードを含まない module キーなら declaredEntries にも現れる" {
            // 食い違うのはワイルドカードのときだけ、という線引きの固定。
            val literal = architectureOf {
                "feature".group { "Module" { layout { ":feature:home".module { } } } }
            }

            literal.process(tree) { model -> model.declaredEntries.map { it.path } } shouldBe
                listOf(
                    "feature",
                    "feature/home",
                    "feature/home/build",
                    "feature/home/build.gradle.kts",
                )
        }
    }
})

/** A tree that answers normally and remembers how often it was listed. */
private class CountingFileSystem(private val delegate: KatachiFileSystem) : KatachiFileSystem {
    var listCalls: Int = 0
        private set

    override val workingDirectory: FsPath get() = delegate.workingDirectory

    override fun exists(path: FsPath): Boolean = delegate.exists(path)

    override fun isDirectory(path: FsPath): Boolean = delegate.isDirectory(path)

    override fun list(directory: FsPath): List<FsPath> {
        listCalls++
        return delegate.list(directory)
    }

    override fun toString(): String = "CountingFileSystem($delegate)"
}

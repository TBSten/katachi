package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.KatachiTemplateParameterTypeConflictException
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.internal.RealArchitectureProcessContext
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.KatachiDuplicateTemplateOutputException
import me.tbsten.katachi.template.KatachiInvalidTemplateSpecifierException
import me.tbsten.katachi.template.KatachiInvalidTemplateSpecifierException.Problem
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.template.OnExisting
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

/**
 * `--arg template=a,b`: two or more templates generated together, as one set -- design draft
 * section 2, "複数指定のとき".
 */
class TemplateMultipleSpec : FreeSpec({
    val repositoryPair = architectureOf {
        "data".group {
            "Repository" {
                layout {
                    "repository" / "${capture("name")}Repository.kt".file().template(id = "repository") {
                        "interface ${captureValue("name")}Repository"
                    }
                    "repository" / "${capture("name")}RepositoryImpl.kt".file().template(id = "repositoryImpl") {
                        "class ${captureValue("name")}RepositoryImpl : ${captureValue("name")}Repository"
                    }
                }
            }
        }
    }

    "capture は名前ごとに1回渡せば、選んだテンプレート全部に効く" {
        repositoryPair.generated(
            listOf("data.Repository.repository", "data.Repository.repositoryImpl"),
            mapOf("name" to "User"),
        ) shouldContainExactly mapOf(
            "repository/UserRepository.kt" to "interface UserRepository",
            "repository/UserRepositoryImpl.kt" to "class UserRepositoryImpl : UserRepository",
        )
    }

    "役割をまたいでよい" {
        val arch = architectureOf {
            "data".group {
                "Repository" {
                    layout { "repository" / "${capture("name")}Repository.kt".file().template { "interface ${captureValue("name")}Repository" } }
                }
            }
            "testing".group {
                "Fake" {
                    layout { "testing" / "Fake${capture("name")}.kt".file().template { "class Fake${captureValue("name")}" } }
                }
            }
        }

        arch.generated(listOf("data.Repository", "testing.Fake"), mapOf("name" to "User")) shouldContainExactly mapOf(
            "repository/UserRepository.kt" to "interface UserRepository",
            "testing/FakeUser.kt" to "class FakeUser",
        )
    }

    "型が食い違う同名パラメータは入口で KatachiTemplateParameterTypeConflictException" {
        val arch = architectureOf {
            "a".group {
                "A" { layout { "a" / "A.kt".file().template { val name by stringParameter(); "// $name" } } }
            }
            "b".group {
                "B" { layout { "b" / "B.kt".file().template { val name by intParameter(); "// $name" } } }
            }
        }

        val thrown = shouldThrow<KatachiTemplateParameterTypeConflictException> {
            arch.generated(listOf("a.A", "b.B"), mapOf("name" to "1"))
        }
        thrown.name shouldBe "name"
        thrown.label shouldBe "Int"
        thrown.conflictsWithLabel shouldBe "String"
    }

    "既定値が食い違う同名パラメータも入口で KatachiTemplateParameterTypeConflictException（型が同じでも）" {
        // レビューで見つかったバグの再現: declaredWith（型のラベルだけ）しか比較していなかったので、
        // このケースは通り、a/A.kt と b/B.kt に既定値のまま x / y という別々の値が書かれていた。
        // 仕様の節2は「型か既定値が食い違ったら落とす」で、名前ごとに1つの値という前提と矛盾する。
        val arch = architectureOf {
            "a".group {
                "A" { layout { "a" / "A.kt".file().template { val name by stringParameter(default = "x"); "// $name" } } }
            }
            "b".group {
                "B" { layout { "b" / "B.kt".file().template { val name by stringParameter(default = "y"); "// $name" } } }
            }
        }

        val thrown = shouldThrow<KatachiTemplateParameterTypeConflictException> {
            arch.generated(listOf("a.A", "b.B"))
        }
        thrown.name shouldBe "name"
        thrown.default shouldBe "y"
        thrown.conflictsWithDefault shouldBe "x"
    }

    "既定値も型も同じ同名パラメータは1つの値として通る" {
        val arch = architectureOf {
            "a".group {
                "A" { layout { "a" / "A.kt".file().template { val name by stringParameter(default = "x"); "// $name" } } }
            }
            "b".group {
                "B" { layout { "b" / "B.kt".file().template { val name by stringParameter(default = "x"); "// $name" } } }
            }
        }

        arch.generated(listOf("a.A", "b.B")) shouldContainExactly mapOf(
            "a/A.kt" to "// x",
            "b/B.kt" to "// x",
        )
    }

    "異なる enum 型どうしは declaredWith（enumParameter()）が同じでも型の食い違いとして落ちる" {
        val arch = architectureOf {
            "a".group {
                "A" { layout { "a" / "A.kt".file().template { val color by enumParameter(Color.entries); "// $color" } } }
            }
            "b".group {
                "B" { layout { "b" / "B.kt".file().template { val color by enumParameter(Size.entries); "// $color" } } }
            }
        }

        val thrown = shouldThrow<KatachiTemplateParameterTypeConflictException> {
            arch.generated(listOf("a.A", "b.B"), mapOf("color" to "Red"))
        }
        thrown.name shouldBe "color"
        thrown.label shouldBe "Size"
        thrown.conflictsWithLabel shouldBe "Color"
    }

    "途中で値が足りずに失敗したら、何も書かない" {
        val arch = architectureOf {
            "data".group {
                "Repository" {
                    layout {
                        "repository" / "${capture("name")}Repository.kt".file().template(id = "repository") { captureValue("name") }
                        "repository" / "${capture("other")}RepositoryImpl.kt".file().template(id = "repositoryImpl") { captureValue("other") }
                    }
                }
            }
        }

        shouldThrow<KatachiMissingTemplateCaptureException> {
            arch.generated(listOf("data.Repository.repository", "data.Repository.repositoryImpl"), mapOf("name" to "User"))
        }
    }

    "onExisting は集合ごとに効く（GenerateCodeFromTemplate の Args 経由）" {
        // onExisting はファイル書き込み側（writeTemplateFiles）の話で TemplateOutputSpec が
        // 検査済み。ここでは Args 自体が集合共通の1つのフィールドであることだけを確かめる。
        GenerateCodeFromTemplate.Args(
            template = listOf("data.Repository.repository", "data.Repository.repositoryImpl"),
            onExisting = OnExisting.Skip,
        ).onExisting shouldBe OnExisting.Skip
    }

    "同じパスに2つのテンプレートが書こうとすると KatachiDuplicateTemplateOutputException" {
        val arch = architectureOf {
            "a".group {
                "A" { layout { "shared" / "File.kt".file().template(id = "a") { "// a" } } }
            }
            "b".group {
                "B" { layout { "shared" / "File.kt".file().template(id = "b") { "// b" } } }
            }
        }

        val thrown = shouldThrow<KatachiDuplicateTemplateOutputException> {
            arch.generated(listOf("a.A.a", "b.B.b"))
        }
        thrown.path shouldBe "shared/File.kt"
        thrown.templates shouldContainExactly listOf("a.A.a", "b.B.b")
    }

    "空・空の要素・重複はどれも KatachiInvalidTemplateSpecifierException" {
        shouldThrow<KatachiInvalidTemplateSpecifierException> { repositoryPair.generated(emptyList()) }
            .problem shouldBe Problem.Empty
        shouldThrow<KatachiInvalidTemplateSpecifierException> {
            repositoryPair.generated(listOf("data.Repository.repository", "", "data.Repository.repositoryImpl"))
        }.problem shouldBe Problem.EmptyElement
        shouldThrow<KatachiInvalidTemplateSpecifierException> {
            repositoryPair.generated(listOf("data.Repository.repository", "data.Repository.repository"))
        }.problem shouldBe Problem.Duplicate
    }

    "undeclaredArgNames は選んだ全テンプレートの名前を返す" {
        val context = RealArchitectureProcessContext(
            architecture = repositoryPair,
            args = Unit,
            fileSystem = ForbiddenFileSystem,
            rawArgs = mapOf("template" to "data.Repository.repository,data.Repository.repositoryImpl", "name" to "User"),
        )
        // "name" は両方のテンプレートで共有される唯一の capture。
        GenerateCodeFromTemplate.undeclaredArgNames(context) shouldContainExactly setOf("name")
    }
})

private enum class Color { Red, Blue }
private enum class Size { Small, Large }

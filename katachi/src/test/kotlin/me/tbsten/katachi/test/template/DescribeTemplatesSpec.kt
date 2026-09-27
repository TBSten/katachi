package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.internal.runProcessors
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.KatachiNoTemplateException
import me.tbsten.katachi.template.KatachiUnknownTemplateRoleException
import me.tbsten.katachi.template.PreviewValueSource
import me.tbsten.katachi.template.TemplateDescription
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.template.TemplateParameterKind
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

private enum class Visibility { Public, Internal }

/** Two roles with a template, one of them branching on a Boolean, and one role without. */
private fun describedArchitecture(): Architecture = architecture {
    "domain".group {
        "UseCase" {
            title = "ユースケース"
            summary = "1つの操作"
            layout { "useCase" / "*UseCase.kt".file() }
            template {
                val name by stringParameter()
                file("${name}UseCase.kt") { "interface ${name}UseCase" }
            }
        }
    }
    "data".group {
        "Repository" {
            layout {
                "repository" / "*Repository.kt".file()
                "repository" / "*RepositoryImpl.kt".file()
            }
            template {
                val name by stringParameter()
                val item by stringParameter(default = "String")
                val withImpl by booleanParameter(default = true)
                val pageSize by intParameter()
                val visibility by enumParameter(Visibility.entries)
                val modifier = visibility.name.lowercase()

                file("${name}Repository.kt") {
                    "$modifier interface ${name}Repository { fun items(): List<$item>; val size: Int get() = $pageSize }"
                }
                if (withImpl) {
                    file("${name}RepositoryImpl.kt") { "class ${name}RepositoryImpl : ${name}Repository" }
                }
            }
        }
        "Readme" {
            layout { "README.md".file() }
        }
    }
}

/**
 * Runs the processor against a tree that refuses to be read: describing a template needs the
 * declarations and nothing else.
 */
private fun Architecture.describe(roleName: String? = null): Pair<TemplateDescription, List<String>> {
    val context = FakeArchitectureProcessContext(
        architecture = this,
        args = DescribeTemplates.Args(roleName = roleName),
        fileSystem = ForbiddenFileSystem,
    )
    return DescribeTemplates.process(context).getOrThrow() to context.logs
}

private fun Architecture.detail(roleName: String): Pair<TemplateDetail, List<String>> {
    val (description, logs) = describe(roleName)
    return description.shouldBeInstanceOf<TemplateDetail>() to logs
}

/**
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.template` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class DescribeTemplatesSpec : FreeSpec({
    "roleName なしでは、テンプレートを持つ役割の一覧を出す" - {
        "テンプレートの無い役割は入らず、宣言順に並ぶ" {
            val (description, _) = describedArchitecture().describe()

            val list = description.shouldBeInstanceOf<TemplateList>()
            list.templates.map { it.roleName } shouldContainExactly listOf("domain/UseCase", "data/Repository")
        }

        "title / summary、パラメータ名、ファイル数を持つ" {
            val (description, _) = describedArchitecture().describe()

            val list = description.shouldBeInstanceOf<TemplateList>()
            val useCase = list.templates.first()
            useCase.title shouldBe "ユースケース"
            useCase.summary shouldBe "1つの操作"
            useCase.fileCount shouldBe 1
            val repository = list.templates.last()
            repository.title.shouldBeNull()
            repository.parameterNames shouldContainExactly listOf("name", "item", "withImpl", "pageSize", "visibility")
            repository.fileCount shouldBe 2
        }

        "ログに一覧と、詳細と生成の案内を出す" {
            val (_, logs) = describedArchitecture().describe()

            logs.first() shouldBe "2 templates:"
            logs shouldContain "- domain/UseCase (ユースケース)"
            logs shouldContain "    parameters: name, item, withImpl, pageSize, visibility"
            logs.last() shouldContain "katachiTemplates --arg roleName=<role>"
            logs.last() shouldContain "katachiTemplate --arg roleName=<role>"
        }

        "テンプレートが1つも無ければそう言う" {
            val arch = architecture { "Readme" { layout { "README.md".file() } } }

            val (description, logs) = arch.describe()

            description.shouldBeInstanceOf<TemplateList>().templates.shouldBeEmpty()
            logs shouldContainExactly listOf("No role declares a template { }.")
        }
    }

    "roleName を渡すと、そのテンプレートの詳細を出す" - {
        "パラメータの型・既定値・受け付ける値・必須かどうか" {
            val (detail, _) = describedArchitecture().detail("Repository")

            detail.parameters.map { it.name } shouldContainExactly
                listOf("name", "item", "withImpl", "pageSize", "visibility")
            val (name, item, withImpl, pageSize, visibility) = detail.parameters
            name.kind shouldBe TemplateParameterKind.StringParameter
            name.isRequired shouldBe true
            item.default shouldBe "String"
            item.isRequired shouldBe false
            withImpl.kind shouldBe TemplateParameterKind.BooleanParameter
            withImpl.default shouldBe "true"
            withImpl.acceptedValues shouldContainExactly listOf("true", "false")
            pageSize.typeName shouldBe "Int"
            pageSize.isRequired shouldBe true
            visibility.typeName shouldBe "Visibility"
            visibility.acceptedValues shouldContainExactly listOf("Public", "Internal")
        }

        "String は \${名前} のプレースホルダで埋め、既定値があっても置き換えない" {
            val (detail, _) = describedArchitecture().detail("Repository")

            val file = detail.files.first()
            file.fileName shouldBe "\${name}Repository.kt"
            file.content shouldContain "interface \${name}Repository"
            file.content shouldContain "List<\${item}>"
            detail.parameters.first().previewValueSource shouldBe PreviewValueSource.Placeholder
        }

        "Boolean / Int / enum は既定値か仮の値で埋め、どれを使ったかを持つ" {
            val (detail, _) = describedArchitecture().detail("Repository")

            val byName = detail.parameters.associateBy { it.name }
            byName.getValue("withImpl").previewValue shouldBe "true"
            byName.getValue("withImpl").previewValueSource shouldBe PreviewValueSource.Default
            byName.getValue("pageSize").previewValue shouldBe "0"
            byName.getValue("pageSize").previewValueSource shouldBe PreviewValueSource.StandIn
            byName.getValue("visibility").previewValue shouldBe "Public"
            byName.getValue("visibility").previewValueSource shouldBe PreviewValueSource.StandIn
            detail.files.first().content shouldBe
                "public interface \${name}Repository { fun items(): List<\${item}>; val size: Int get() = 0 }"
        }

        "ログに使った値とその理由を明記する" {
            val (_, logs) = describedArchitecture().detail("Repository")

            logs shouldContain "  withImpl: Boolean, default true, accepts true | false"
            logs shouldContain "  pageSize: Int, required"
            logs.single { it.startsWith("Previewed with: ") } shouldContain
                "withImpl=true (default), pageSize=0 (stand-in, no default), visibility=Public (stand-in, no default)"
        }

        "生成先のパスは layout から決まり、ファイル名はプレースホルダのまま" {
            val (detail, logs) = describedArchitecture().detail("Repository")

            detail.files.map { it.path } shouldContainExactly listOf(
                "repository/\${name}Repository.kt",
                "repository/\${name}RepositoryImpl.kt",
            )
            logs shouldContain "  repository/\${name}Repository.kt"
            logs shouldContain "    | class \${name}RepositoryImpl : \${name}Repository"
        }

        "分岐でファイルが変わる値を、仮の値でたどった分岐とあわせて出す" {
            val (detail, logs) = describedArchitecture().detail("Repository")

            val branch = detail.branches.single()
            branch.parameterName shouldBe "withImpl"
            branch.value shouldBe "false"
            branch.removedFiles shouldContainExactly listOf("\${name}RepositoryImpl.kt")
            branch.addedFiles.shouldBeEmpty()
            logs shouldContain "  --arg withImpl=false: leaves out \${name}RepositoryImpl.kt"
        }

        "分岐の中でだけ宣言されるパラメータにも値を埋める" {
            val arch = architecture {
                "Service" {
                    layout { "*.kt".file() }
                    template {
                        val withImpl by booleanParameter()
                        if (withImpl) {
                            val implName by stringParameter()
                            file("$implName.kt") { "class $implName" }
                        }
                        file("Service.kt") { "interface Service" }
                    }
                }
            }

            val (detail, _) = arch.detail("Service")

            detail.parameters.map { it.name } shouldContainExactly listOf("withImpl", "implName")
            detail.files.map { it.fileName } shouldContainExactly listOf("\${implName}.kt", "Service.kt")
            detail.branches.single().removedFiles shouldContainExactly listOf("\${implName}.kt")
        }

        "モジュールがワイルドカードで決まらないファイルは、パスの代わりにパターンを持つ" {
            val arch = architecture {
                "Screen" {
                    layout { ":feature:*".module { "*Screen.kt".file() } }
                    template {
                        val name by stringParameter()
                        file("${name}Screen.kt") { "// a screen" }
                    }
                }
            }

            val (detail, logs) = arch.detail("Screen")

            val file = detail.files.single()
            file.path.shouldBeNull()
            file.unresolvedPatterns.single() shouldContain "*Screen.kt"
            logs.single { it.startsWith("  \${name}Screen.kt") } shouldContain "no single directory"
        }

        "必須のパラメータを埋めた、そのまま貼れる katachiTemplate のコマンドを出す" {
            val (detail, logs) = describedArchitecture().detail("Repository")

            detail.exampleCommand shouldBe "./gradlew katachiTemplate --arg roleName=data/Repository " +
                "--arg name=Name --arg pageSize=0 --arg visibility=Public"
            logs.last() shouldBe "  ${detail.exampleCommand}"
        }
    }

    "失敗" - {
        "知らない役割は KatachiUnknownTemplateRoleException で失敗する" {
            val failure = shouldThrow<KatachiUnknownTemplateRoleException> {
                describedArchitecture().describe("Servce")
            }
            failure.roleName shouldBe "Servce"
        }

        "テンプレートの無い役割は KatachiNoTemplateException で失敗する" {
            shouldThrow<KatachiNoTemplateException> {
                describedArchitecture().describe("Readme")
            }
        }
    }

    "コマンドラインから" - {
        "--arg roleName を受け取り、何も書かずに成功する" {
            val out = mutableListOf<String>()

            val summary = runProcessors(
                architecture = describedArchitecture(),
                registry = mapOf("templates" to DescribeTemplates::class.java),
                processorKeys = listOf("templates"),
                rawArgs = mapOf("roleName" to "UseCase"),
                fileSystem = ForbiddenFileSystem,
                out = out::add,
            )

            summary.failed shouldBe 0
            out shouldContain "  [templates] Template of domain/UseCase (ユースケース)"
            out shouldContain "Template domain/UseCase: 1 parameters, 1 files"
        }
    }
})

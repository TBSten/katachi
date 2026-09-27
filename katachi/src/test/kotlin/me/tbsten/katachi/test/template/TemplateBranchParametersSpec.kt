package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateParameterKind
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

/** A service whose implementation file, and the parameter naming it, exist only while `withImpl` is true. */
private fun serviceArchitecture(withImplDefault: Boolean): Architecture = architecture {
    "Service" {
        layout { "*.kt".file() }
        template {
            val withImpl by booleanParameter(default = withImplDefault)
            if (withImpl) {
                val implName by stringParameter(default = "ServiceImpl")
                file("$implName.kt") { "class $implName" }
            }
            file("Service.kt") { "interface Service" }
        }
    }
}

private fun Architecture.detailOf(roleName: String): Pair<TemplateDetail, List<String>> {
    val context = FakeArchitectureProcessContext(
        architecture = this,
        args = DescribeTemplates.Args(roleName = roleName),
        fileSystem = ForbiddenFileSystem,
    )
    return DescribeTemplates.process(context).getOrThrow().shouldBeInstanceOf<TemplateDetail>() to context.logs
}

/**
 * [me.tbsten.katachi.template.TemplateBranch.addedParameters] and `removedParameters`: which
 * parameters another value brings in or takes away, so a form can show and hide them.
 */
class TemplateBranchParametersSpec : FreeSpec({
    "既定が true の Boolean を false にすると、if の中のパラメータが removedParameters に出る" {
        val (detail, logs) = serviceArchitecture(withImplDefault = true).detailOf("Service")

        val branch = detail.branches.single()
        branch.value shouldBe "false"
        branch.removedParameters shouldContainExactly listOf("implName")
        branch.addedParameters.shouldBeEmpty()
        logs shouldContain "  --arg withImpl=false: leaves out \${implName}.kt; drops parameter implName"
    }

    "既定が false の Boolean を true にすると、if の中のパラメータが型と既定値つきで addedParameters に出る" {
        val (detail, logs) = serviceArchitecture(withImplDefault = false).detailOf("Service")

        val branch = detail.branches.single()
        branch.value shouldBe "true"
        branch.removedParameters.shouldBeEmpty()
        val added = branch.addedParameters.single()
        added.name shouldBe "implName"
        added.kind shouldBe TemplateParameterKind.StringParameter
        added.default shouldBe "ServiceImpl"
        added.previewValue shouldBe "\${implName}"
        logs shouldContain "  --arg withImpl=true: adds \${implName}.kt; adds parameter implName"
    }

    "ファイルが変わらずパラメータだけが増える値も、分岐として残る" {
        val arch = architecture {
            "Screen" {
                layout { "*.kt".file() }
                template {
                    val name by stringParameter()
                    val withTitle by booleanParameter(default = false)
                    val title = if (withTitle) {
                        val titleText by stringParameter()
                        titleText
                    } else {
                        name
                    }
                    file("${name}Screen.kt") { "// $title" }
                }
            }
        }

        val (detail, logs) = arch.detailOf("Screen")

        val branch = detail.branches.single()
        branch.parameterName shouldBe "withTitle"
        branch.addedFiles.shouldBeEmpty()
        branch.removedFiles.shouldBeEmpty()
        branch.addedParameters.map { it.name } shouldContainExactly listOf("titleText")
        logs shouldContain "  --arg withTitle=true: adds parameter titleText"
    }

    "ファイルもパラメータも変わらない値は分岐にならない" {
        val arch = architecture {
            "Model" {
                layout { "*.kt".file() }
                template {
                    val name by stringParameter()
                    val isData by booleanParameter(default = true)
                    file("$name.kt") { if (isData) "data class $name(val id: Int)" else "class $name" }
                }
            }
        }

        val (detail, _) = arch.detailOf("Model")

        detail.branches.shouldBeEmpty()
    }
})

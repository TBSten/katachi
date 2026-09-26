package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.processor.KatachiDuplicateEntryPointOptionException
import me.tbsten.katachi.processor.KatachiDuplicateProcessorArgException
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgForOptionException
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgOptionException
import me.tbsten.katachi.processor.KatachiMissingEntryPointOptionException
import me.tbsten.katachi.processor.KatachiMissingProcessorSelectionException
import me.tbsten.katachi.processor.KatachiUnknownProcessorOptionException
import me.tbsten.katachi.processor.internal.ProcessorCommandLine
import me.tbsten.katachi.processor.internal.parseProcessorCommandLine

class ProcessorCommandLineSpec : FreeSpec({
    "正常な argv" - {
        "--entry-point / --processor / --arg が揃った argv が正しく分解される" {
            parseProcessorCommandLine(
                arrayOf(
                    "--entry-point=com.example.MyEntryPoint",
                    "--processor=layout",
                    "--arg=roleName=GetUser",
                ),
            ) shouldBe ProcessorCommandLine(
                entryPointClassName = "com.example.MyEntryPoint",
                processorKeys = listOf("layout"),
                args = mapOf("roleName" to "GetUser"),
            )
        }

        "--arg の値に = が入っていても最初の = だけで割られる" {
            parseProcessorCommandLine(
                arrayOf(
                    "--entry-point=com.example.MyEntryPoint",
                    "--processor=layout",
                    "--arg=invokeImpl=TODO()",
                ),
            ).args shouldBe mapOf("invokeImpl" to "TODO()")
        }

        "--processor=a,b と --processor=a --processor=b は同じ結果になる" {
            val commaSeparated = parseProcessorCommandLine(
                arrayOf("--entry-point=com.example.MyEntryPoint", "--processor=a,b"),
            )
            val repeated = parseProcessorCommandLine(
                arrayOf("--entry-point=com.example.MyEntryPoint", "--processor=a", "--processor=b"),
            )

            commaSeparated.processorKeys shouldBe listOf("a", "b")
            repeated.processorKeys shouldBe listOf("a", "b")
        }

        "--processor=a,a は重複除去され、最初に出た位置の順序が保たれる" {
            parseProcessorCommandLine(
                arrayOf("--entry-point=com.example.MyEntryPoint", "--processor=b,a,b,a"),
            ).processorKeys shouldBe listOf("b", "a")
        }
    }

    "壊れた argv" - {
        "--entry-point が無ければ落ちる" {
            shouldThrow<KatachiMissingEntryPointOptionException> {
                parseProcessorCommandLine(arrayOf("--processor=layout"))
            }
        }

        "--entry-point が2回あれば落ちる" {
            shouldThrow<KatachiDuplicateEntryPointOptionException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.A",
                        "--entry-point=com.example.B",
                        "--processor=layout",
                    ),
                )
            }
        }

        "--processor が1つも無ければ落ちる" {
            shouldThrow<KatachiMissingProcessorSelectionException> {
                parseProcessorCommandLine(arrayOf("--entry-point=com.example.MyEntryPoint"))
            }
        }

        "--arg に = が無ければ落ちる" {
            shouldThrow<KatachiInvalidProcessorArgOptionException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.MyEntryPoint",
                        "--processor=layout",
                        "--arg=noEquals",
                    ),
                )
            }
        }

        "--arg のキーが空なら落ちる" {
            shouldThrow<KatachiInvalidProcessorArgOptionException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.MyEntryPoint",
                        "--processor=layout",
                        "--arg==v",
                    ),
                )
            }
        }

        "同じ --arg キーが2回あれば落ちる" {
            val thrown = shouldThrow<KatachiDuplicateProcessorArgException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.MyEntryPoint",
                        "--processor=layout",
                        "--arg=roleName=A",
                        "--arg=roleName=B",
                    ),
                )
            }
            thrown.key shouldBe "roleName"
        }

        "未知のオプションは落ちる" {
            val thrown = shouldThrow<KatachiUnknownProcessorOptionException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.MyEntryPoint",
                        "--processor=layout",
                        "--unknown=x",
                    ),
                )
            }
            thrown.argument shouldBe "--unknown=x"
        }

        "-- で始まらないトークンは落ちる" {
            val thrown = shouldThrow<KatachiUnknownProcessorOptionException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.MyEntryPoint",
                        "--processor=layout",
                        "stray",
                    ),
                )
            }
            thrown.argument shouldBe "stray"
        }
    }
    "--arg-for（1つの processor にだけ渡す引数）" - {
        "--arg-for=<key>:<name>=<value> は key ごとの引数として分解され、--arg とは混ざらない" {
            val parsed = parseProcessorCommandLine(
                arrayOf(
                    "--entry-point=com.example.MyEntryPoint",
                    "--processor=docs,layout",
                    "--arg-for=docs:outputDir=docs/architecture",
                    "--arg=roleName=GetUser",
                ),
            )

            parsed.args shouldBe mapOf("roleName" to "GetUser")
            parsed.argsFor shouldBe mapOf("docs" to mapOf("outputDir" to "docs/architecture"))
        }

        "値に : や = が入っていても、最初の : と その後の最初の = だけで割られる" {
            parseProcessorCommandLine(
                arrayOf(
                    "--entry-point=com.example.MyEntryPoint",
                    "--processor=template",
                    "--arg-for=template:invokeImpl=a:b=c",
                ),
            ).argsFor shouldBe mapOf("template" to mapOf("invokeImpl" to "a:b=c"))
        }

        "--arg-for が無ければ argsFor は空" {
            parseProcessorCommandLine(
                arrayOf("--entry-point=com.example.MyEntryPoint", "--processor=layout"),
            ).argsFor shouldBe emptyMap()
        }

        "形が <key>:<name>=<value> でなければ落ちる" {
            listOf(
                "--arg-for=docs",
                "--arg-for=docs:outputDir",
                "--arg-for=:outputDir=x",
                "--arg-for=docs:=x",
            ).forEach { token ->
                val thrown = shouldThrow<KatachiInvalidProcessorArgForOptionException> {
                    parseProcessorCommandLine(
                        arrayOf("--entry-point=com.example.MyEntryPoint", "--processor=docs", token),
                    )
                }
                thrown.argument shouldBe token
                thrown.processorKey shouldBe null
            }
        }

        "--processor で選ばれていない key 宛ての --arg-for は落ちる" {
            val thrown = shouldThrow<KatachiInvalidProcessorArgForOptionException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.MyEntryPoint",
                        "--processor=layout",
                        "--arg-for=docs:outputDir=x",
                    ),
                )
            }
            thrown.processorKey shouldBe "docs"
            thrown.selectedKeys shouldBe listOf("layout")
        }

        "同じ key と name の --arg-for が2回あれば落ちる" {
            val thrown = shouldThrow<KatachiDuplicateProcessorArgException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.MyEntryPoint",
                        "--processor=docs",
                        "--arg-for=docs:outputDir=a",
                        "--arg-for=docs:outputDir=b",
                    ),
                )
            }
            thrown.key shouldBe "docs:outputDir"
        }
    }
})

package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.processor.KatachiDuplicateEntryPointOptionException
import me.tbsten.katachi.processor.KatachiDuplicateProcessorArgException
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgOptionException
import me.tbsten.katachi.processor.KatachiMissingEntryPointOptionException
import me.tbsten.katachi.processor.KatachiMissingProcessorSelectionException
import me.tbsten.katachi.processor.KatachiUnknownProcessorOptionException
import me.tbsten.katachi.processor.ProcessorCommandLine
import me.tbsten.katachi.processor.parseProcessorCommandLine

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
})

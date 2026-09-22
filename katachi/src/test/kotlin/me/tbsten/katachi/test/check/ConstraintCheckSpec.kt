package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import me.tbsten.katachi.check.ConstraintCheck
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.ConstraintFailure
import me.tbsten.katachi.dsl.FileSetConstraint
import me.tbsten.katachi.dsl.KatachiConstraintMemoTypeException
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.scan.UncheckedConstraintReason

/**
 * What [ConstraintCheck] evaluates, what it refuses to leave unevaluated, and what one run
 * shares between its constraints.
 *
 * A block that throws, and a backend answering about files it was never handed, are
 * [ConstraintFailureSpec]'s; the exact report text is [ConstraintReportSpec]'s. The three are
 * split on size alone — one file covering step 5 would be well past what this repository asks
 * a file to be.
 *
 * Konsist appears nowhere here on purpose: the evaluation, the guard and the report are
 * `:katachi`'s own and have to hold for a `constraint { }` written by hand.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class ConstraintCheckSpec : FreeSpec({
    "覆う範囲" - {
        "役割直下の制約が全 layout の和集合を覆う" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        constraint("role wide", check = rejectsEverything())
                        layout { "alpha" / "*.kt".file() }
                        layout { "beta" / "*.kt".file() }
                    }
                }
            }

            arch.validate(
                repositoryOf {
                    "alpha" { "A.kt"() }
                    "beta" { "B.kt"() }
                },
                ConstraintCheck(),
            ).labels() shouldBe listOf(
                "[UnsatisfiedConstraint] alpha/A.kt",
                "[UnsatisfiedConstraint] beta/B.kt",
            )
        }

        "layout の中に書いた制約はその場所のファイルだけを覆う" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint("alpha only", check = rejectsEverything())
                                "*.kt".file()
                            }
                            "beta" / "*.kt".file()
                        }
                    }
                }
            }

            arch.validate(
                repositoryOf {
                    "alpha" { "A.kt"() }
                    "beta" { "B.kt"() }
                },
                ConstraintCheck(),
            ).labels() shouldBe listOf("[UnsatisfiedConstraint] alpha/A.kt")
        }

        "同じファイルを役割直下と layout 内の制約が両方拒んだら2ブロック出る" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        constraint("role wide", check = rejectsEverything())
                        layout {
                            "alpha" {
                                constraint("alpha only", check = rejectsEverything())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val violations = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, ConstraintCheck())

            violations.labels() shouldBe listOf(
                "[UnsatisfiedConstraint] alpha/A.kt",
                "[UnsatisfiedConstraint] alpha/A.kt",
            )
            // 役割直下のほうが先。定義の中でも layout { } より上に書かれている。
            violations.unsatisfied().map { it.constraintName } shouldBe
                listOf("role wide", "alpha only")
        }

        "制約をブロックの前に書いても後ろに書いても覆う範囲は変わらない" {
            val before = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint("x", check = rejectsEverything())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }
            val after = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                "*.kt".file()
                                constraint("x", check = rejectsEverything())
                            }
                        }
                    }
                }
            }
            val tree = { repositoryOf { "alpha" { "A.kt"(); "B.kt"() } } }

            before.validate(tree(), ConstraintCheck()).labels() shouldBe
                after.validate(tree(), ConstraintCheck()).labels()
            before.validate(tree(), ConstraintCheck()).labels() shouldBe listOf(
                "[UnsatisfiedConstraint] alpha/A.kt",
                "[UnsatisfiedConstraint] alpha/B.kt",
            )
        }

        "subject は覆ったファイルだけを walk 順で受け取る" {
            val recording = RecordingConstraint()
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint("alpha only", check = recording)
                                "*.kt".file()
                            }
                            "beta" / "*.kt".file()
                        }
                    }
                }
            }

            arch.validate(
                repositoryOf {
                    "alpha" { "B.kt"(); "A.kt"() }
                    "beta" { "C.kt"() }
                },
                ConstraintCheck(),
            ).shouldBeEmpty()

            val subject = recording.subjects.single()
            subject.files shouldBe listOf("alpha/A.kt", "alpha/B.kt")
            subject.role.qualifiedName shouldBe "domain/UseCase"
            subject.name shouldBe "alpha only"
            subject.paths shouldBe listOf("alpha")
            subject.projectRoot shouldBe "/repo"
        }
    }

    "違反の組み立て" - {
        "同じ宣言を2回返しても1件、別の宣言なら2件" {
            val twice = FileSetConstraint { subject ->
                subject.files.flatMap {
                    listOf(
                        ConstraintFailure(it, declaration = "Helper", line = 12),
                        ConstraintFailure(it, declaration = "Helper", line = 12),
                        ConstraintFailure(it, declaration = "Other", line = 20),
                    )
                }
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "alpha" { constraint("dup", check = twice); "*.kt".file() } }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, ConstraintCheck())
                .unsatisfied().map { "${it.declaration}:${it.line}" } shouldBe
                listOf("Helper:12", "Other:20")
        }

        "バックエンドが逆順で答えても walk 順に並ぶ" {
            val reversed = FileSetConstraint { subject ->
                subject.files.reversed().map { ConstraintFailure(it) }
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "alpha" { constraint("rev", check = reversed); "*.kt".file() } }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"(); "B.kt"() } }, ConstraintCheck())
                .labels() shouldBe listOf(
                "[UnsatisfiedConstraint] alpha/A.kt",
                "[UnsatisfiedConstraint] alpha/B.kt",
            )
        }

        "配置違反と制約違反が1回の validate の1レポートに種別順で並ぶ" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint("no helper", check = rejecting("alpha/Helper.kt"))
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val violations = arch.validate(
                repositoryOf {
                    "alpha" { "Helper.kt"() }
                    "notes.md"()
                },
                ConstraintCheck(),
            )

            violations.labels() shouldBe listOf(
                "[UnexpectedFile] notes.md",
                "[UnsatisfiedConstraint] alpha/Helper.kt",
            )
            violations.report().lines().first() shouldBe
                "Katachi check failed: 2 violations (Unexpected: 1, Constraint: 1)"
        }

        "ConstraintCheck を2つ渡しても違反は倍にならない" {
            val recording = RecordingConstraint { subject ->
                subject.files.map { ConstraintFailure(it) }
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "alpha" { constraint("once", check = recording); "*.kt".file() } }
                    }
                }
            }

            arch.validate(
                repositoryOf { "alpha" { "A.kt"() } },
                ConstraintCheck(),
                ConstraintCheck(),
            ).labels() shouldBe listOf("[UnsatisfiedConstraint] alpha/A.kt")
            recording.subjects.size shouldBe 1
        }
    }

    "未評価ガード" - {
        "ConstraintCheck を渡さずに validate すると NotEvaluated になる" {
            val recording = RecordingConstraint()
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "alpha" { constraint("invoke", check = recording); "*.kt".file() } }
                    }
                }
            }

            val violations = arch.validate(repositoryOf { "alpha" { "A.kt"() } })

            violations.labels() shouldBe listOf("[UncheckedConstraint] alpha")
            violations.unchecked().single().reason shouldBe UncheckedConstraintReason.NotEvaluated
            violations.unchecked().single().cause shouldBe null
            // 評価されていないのだから、ブロックは一度も走っていない。
            recording.subjects.shouldBeEmpty()
            violations.report().lines().last() shouldBe "1 constraint could not be evaluated."
        }

        "ConstraintCheck を渡せば出ない" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "alpha" { constraint("invoke", check = silentCheck()); "*.kt".file() } }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, ConstraintCheck()).shouldBeEmpty()
            // 利用者が実際に書くのは assert のほう。ガードが緑を保つのはこちらの綴りでもある。
            shouldNotThrowAny {
                arch.assert(repositoryOf { "alpha" { "A.kt"() } }, ConstraintCheck())
            }
            shouldThrow<KatachiArchitectureAssertionError> {
                arch.assert(repositoryOf { "alpha" { "A.kt"() } })
            }.violations.unchecked().single().reason shouldBe UncheckedConstraintReason.NotEvaluated
        }

        "制約を1つも書いていない定義では偽陽性が出ない" {
            val arch = layoutArchitecture { "alpha" / "*.kt".file() }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }).shouldBeEmpty()
            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, ConstraintCheck()).shouldBeEmpty()
        }

        "process(ConstraintCheck()) 単体では未評価違反が出ない" {
            // ガードは validateWith が足すもの。processor を直接走らせた結果に
            // 「自分が評価しなかった制約」が混ざったら、返り値の意味が変わってしまう。
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        constraint("role wide", check = silentCheck())
                        layout {
                            "alpha" { constraint("alpha only", check = silentCheck()); "*.kt".file() }
                        }
                    }
                }
            }

            arch.process(ConstraintCheck(), repositoryOf { "alpha" { "A.kt"() } }).shouldBeEmpty()
        }

        "ディレクトリしか宣言していない役割でも1行目がドットにならない" {
            val arch = architectureOf {
                "build".group {
                    "Generated" {
                        constraint("role wide", check = silentCheck())
                        layout { "generated" { } }
                    }
                }
            }

            arch.validate(repositoryOf { "generated" { } })
                .unchecked().single().path shouldBe "generated"
        }
    }

    "対象0件" - {
        "違反を1件も出さず、NotEvaluated にもならず、ブロックも呼ばれない" {
            val recording = RecordingConstraint()
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" { constraint("nothing yet", check = recording); "*.kt".file() }
                        }
                    }
                }
            }

            // `alpha/` はあるが `.kt` が 1 つも無い。ワイルドカードの宣言が 0 件マッチなのは
            // layout 検査が既に「正常」と決めているので、制約側だけが赤くなってはいけない。
            arch.validate(repositoryOf { "alpha" { } }, ConstraintCheck()).shouldBeEmpty()
            recording.subjects.shouldBeEmpty()
        }
    }

    "memo" - {
        "同じ run の2つの制約が同じキーで同じインスタンスを受け取る" {
            val handed = mutableListOf<ScratchValue>()
            var created = 0
            val memoizing = FileSetConstraint { subject ->
                handed += subject.memo("scope", ScratchValue::class) {
                    created++
                    ScratchValue("once")
                }
                emptyList()
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        constraint("first", check = memoizing)
                        layout { "alpha" { constraint("second", check = memoizing); "*.kt".file() } }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, ConstraintCheck()).shouldBeEmpty()

            created shouldBe 1
            handed.size shouldBe 2
            handed[0] shouldBeSameInstanceAs handed[1]
        }

        "別の型で同じキーを使うと KatachiConstraintMemoTypeException になる" {
            val asValue = FileSetConstraint { subject ->
                subject.memo("scope", ScratchValue::class) { ScratchValue("once") }
                emptyList()
            }
            val asText = FileSetConstraint { subject ->
                subject.memo("scope", StringBuilder::class) { StringBuilder("other") }
                emptyList()
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        constraint("value", check = asValue)
                        layout { "alpha" { constraint("text", check = asText); "*.kt".file() } }
                    }
                }
            }

            val unchecked = arch
                .validate(repositoryOf { "alpha" { "A.kt"() } }, ConstraintCheck())
                .unchecked()
                .single()

            unchecked.constraintName shouldBe "text"
            unchecked.reason shouldBe UncheckedConstraintReason.Failed
            val cause = unchecked.cause.shouldBeInstanceOf<KatachiConstraintMemoTypeException>()
            cause.key shouldBe "scope"
            cause.expected shouldBe StringBuilder::class
            cause.actual shouldBe ScratchValue::class
        }
    }
})

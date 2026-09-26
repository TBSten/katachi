package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.KatachiInvalidFileConstraintParallelismException
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.processor.internal.process
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * `FileConstraintCheck(parallelism = n)`: constraints evaluated on several threads, answered in
 * declaration order.
 *
 * What must hold is that turning it on changes nothing but the wall clock: the same violations
 * in the same order, the same `memo` sharing, the same treatment of a constraint that throws.
 * And that it stays off unless asked for, because a user's lambda need not be thread safe.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */
class FileConstraintParallelSpec : FreeSpec({
    "既定は逐次" - {
        "引数なしで作ると parallelism は 1 で、制約は呼び出したスレッドで1つずつ走る" {
            val caller = Thread.currentThread()
            val threads = Collections.synchronizedList(mutableListOf<Thread>())
            val running = AtomicInteger()
            val overlapped = AtomicInteger()
            val observing = {
                FileConstraint {
                    if (running.incrementAndGet() > 1) overlapped.incrementAndGet()
                    threads += Thread.currentThread()
                    Thread.sleep(5)
                    running.decrementAndGet()
                    emptyList()
                }
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("first", check = observing())
                                fileConstraint("second", check = observing())
                                fileConstraint("third", check = observing())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val check = FileConstraintCheck()
            check.parallelism shouldBe 1
            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, check).shouldBeEmpty()

            threads.toList() shouldBe listOf(caller, caller, caller)
            overlapped.get() shouldBe 0
        }

        "Gradle から名前で登録したときと同じく、引数なしのコンストラクタで作れる" {
            val made = FileConstraintCheck::class.java.getDeclaredConstructor().newInstance()

            made.parallelism shouldBe 1
        }

        "1 未満を渡すと、書いた行を指す KatachiInvalidFileConstraintParallelismException になる" {
            val cause = shouldThrow<KatachiInvalidFileConstraintParallelismException> {
                FileConstraintCheck(parallelism = 0)
            }

            cause.parallelism shouldBe 0
            cause.declaredAt.fileName shouldBe "FileConstraintParallelSpec.kt"
            cause.message shouldContain "FileConstraintParallelSpec.kt"
        }
    }

    "並列にしても答えは変わらない" - {
        "違反の中身と並び順が逐次と同じ" {
            val arch = manyConstraints()
            val tree = repositoryOf {
                "alpha" { "A.kt"(); "B.kt"(); "C.kt"() }
                "beta" { "D.kt"(); "E.kt"() }
                "gamma" { "F.kt"(); "G.kt"() }
            }

            val serial = arch.validate(tree, FileConstraintCheck())
            val parallel = arch.validate(tree, FileConstraintCheck(parallelism = 4))

            serial.size shouldBe 15
            parallel.described() shouldBe serial.described()
        }

        "先に宣言した制約が後で終わっても、宣言順で返す" {
            val lastDone = CountDownLatch(1)
            val first = FileConstraint { subject ->
                // Only finishes once the last one has, so completion order is the reverse of
                // declaration order.
                lastDone.await(10, TimeUnit.SECONDS) shouldBe true
                subject.files.map { FileConstraintFailure(it) }
            }
            val last = FileConstraint { subject ->
                subject.files.map { FileConstraintFailure(it) }.also { lastDone.countDown() }
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("first", check = first)
                                fileConstraint("last", check = last)
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val violations = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck(parallelism = 2))

            violations.unsatisfied().map { it.constraintName } shouldBe listOf("first", "last")
        }

        "投げた制約だけが Unchecked(Failed) になり、ほかは答える" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("broken", check = throwing { IllegalStateException("boom") })
                                fileConstraint("fine", check = rejectsEverything())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }
            val tree = repositoryOf { "alpha" { "A.kt"() } }

            val parallel = arch.validate(tree, FileConstraintCheck(parallelism = 2))

            parallel.described() shouldBe arch.validate(tree, FileConstraintCheck()).described()
            val unchecked = parallel.unchecked().single()
            unchecked.constraintName shouldBe "broken"
            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            parallel.unsatisfied().map { it.constraintName } shouldBe listOf("fine")
        }

        "制約が投げた AssertionError は逐次と同じく failure として返す" {
            val assertion = AssertionError("the caller's own answer")
            val arch = twoConstraints(throwing { assertion }, silentCheck())

            val result = shouldNotThrowAny {
                arch.process(FileConstraintCheck(parallelism = 2), repositoryOf { "alpha" { "A.kt"() } })
            }

            result.exceptionOrNull() shouldBeSameInstanceAs assertion
        }

        "致命的な例外は逐次と同じく投げる" {
            val arch = twoConstraints(silentCheck(), throwing { StackOverflowError() })

            shouldThrow<StackOverflowError> {
                arch.process(FileConstraintCheck(parallelism = 2), repositoryOf { "alpha" { "A.kt"() } })
            }
        }
    }

    "本当に並列に走る" - {
        "parallelism 個の制約が同時に走り、呼び出し元のコンテキストクラスローダを引き継ぐ" {
            val barrier = CyclicBarrier(3)
            val loaders = Collections.synchronizedList(mutableListOf<ClassLoader?>())
            val threads = Collections.synchronizedSet(mutableSetOf<Thread>())
            val meeting = {
                FileConstraint {
                    threads += Thread.currentThread()
                    loaders += Thread.currentThread().contextClassLoader
                    // 逐次ならここで3者が揃わず、タイムアウトして Unchecked になる。
                    barrier.await(10, TimeUnit.SECONDS)
                    emptyList()
                }
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("one", check = meeting())
                                fileConstraint("two", check = meeting())
                                fileConstraint("three", check = meeting())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }
            val callerLoader = ClassLoader.getSystemClassLoader().parent
            val caller = Thread.currentThread()
            val previous = caller.contextClassLoader
            caller.contextClassLoader = callerLoader
            val violations = try {
                arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck(parallelism = 3))
            } finally {
                caller.contextClassLoader = previous
            }

            violations.shouldBeEmpty()
            threads.size shouldBe 3
            threads.contains(caller) shouldBe false
            loaders.toList() shouldContainExactly listOf(callerLoader, callerLoader, callerLoader)
        }

        "走り終えたらワーカースレッドを残さない" {
            val workers = Collections.synchronizedSet(mutableSetOf<Thread>())
            val recording = { FileConstraint { workers += Thread.currentThread(); emptyList() } }
            val arch = twoConstraints(recording(), recording())

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck(parallelism = 2))

            workers.forEach { it.join(5_000) }
            workers.none { it.isAlive } shouldBe true
        }
    }

    "memo はスレッドをまたいでも1回だけ作る" - {
        "同時に同じキーを引いても create は1回で、全員が同じインスタンスを受け取る" {
            val barrier = CyclicBarrier(4)
            val created = AtomicInteger()
            val handed = Collections.synchronizedList(mutableListOf<ScratchValue>())
            val memoizing = {
                FileConstraint { subject ->
                    barrier.await(10, TimeUnit.SECONDS)
                    handed += subject.memo("scope", ScratchValue::class) {
                        created.incrementAndGet()
                        // Long enough for the other three to arrive while this one is creating.
                        Thread.sleep(50)
                        ScratchValue("once")
                    }
                    emptyList()
                }
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("a", check = memoizing())
                                fileConstraint("b", check = memoizing())
                                fileConstraint("c", check = memoizing())
                                fileConstraint("d", check = memoizing())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck(parallelism = 4)).shouldBeEmpty()

            created.get() shouldBe 1
            handed.size shouldBe 4
            handed.distinct().size shouldBe 1
        }

        "create が投げたら何も残さず、次に引いた制約は自分の create で作る" {
            val handed = mutableListOf<ScratchValue>()
            val failing = FileConstraint { subject ->
                subject.memo("scope", ScratchValue::class) { throw IllegalStateException("not now") }
                emptyList()
            }
            val succeeding = FileConstraint { subject ->
                handed += subject.memo("scope", ScratchValue::class) { ScratchValue("second") }
                emptyList()
            }
            val arch = twoConstraints(failing, succeeding)

            val violations = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck())

            violations.unchecked().single().constraintName shouldBe "first"
            handed.single().label shouldBe "second"
        }

        "別の run とは memo を共有しない" {
            val handed = mutableListOf<ScratchValue>()
            val memoizing = FileConstraint { subject ->
                handed += subject.memo("scope", ScratchValue::class) { ScratchValue("run") }
                emptyList()
            }
            val arch = twoConstraints(memoizing, silentCheck())
            val tree = repositoryOf { "alpha" { "A.kt"() } }

            arch.validate(tree, FileConstraintCheck(parallelism = 2))
            arch.validate(tree, FileConstraintCheck(parallelism = 2))

            handed.size shouldBe 2
            handed[0] shouldNotBe handed[1]
        }
    }
})

/** Everything a spec compares about a violation, since violations compare by identity. */
private fun List<Violation>.described(): List<String> = map { violation ->
    when (violation) {
        is UnsatisfiedFileConstraint -> "unsatisfied ${violation.constraintName} ${violation.path}"
        is UncheckedFileConstraint -> "unchecked ${violation.constraintName} ${violation.reason}"
        else -> "${violation.label} ${violation.path}"
    }
}

/** Two constraints, named `first` and `second`, over the files under `alpha`. */
private fun twoConstraints(first: FileConstraint, second: FileConstraint): Architecture = architectureOf {
    "domain".group {
        "UseCase" {
            layout {
                "alpha" {
                    fileConstraint("first", check = first)
                    fileConstraint("second", check = second)
                    "*.kt".file()
                }
            }
        }
    }
}

/**
 * Constraints spread over three roles and several blocks, each rejecting a different subset,
 * so that an answer assembled in the wrong order cannot look right by accident.
 */
private fun manyConstraints(): Architecture = architectureOf {
    "domain".group {
        "UseCase" {
            fileConstraint("role wide", check = rejectsEverything())
            layout {
                "alpha" {
                    fileConstraint("alpha all", check = rejectsEverything())
                    fileConstraint("alpha B", check = rejecting("alpha/B.kt"))
                    fileConstraint("alpha none", check = silentCheck())
                    "*.kt".file()
                }
            }
        }
        "Repository" {
            layout {
                "beta" {
                    fileConstraint("beta E", check = rejecting("beta/E.kt"))
                    fileConstraint("beta all", check = rejectsEverything())
                    "*.kt".file()
                }
            }
        }
    }
    "data".group {
        "Source" {
            layout {
                fileConstraint("gamma all", check = rejectsEverything())
                "gamma" {
                    fileConstraint("gamma F", check = rejecting("gamma/F.kt"))
                    fileConstraint("gamma all again", check = rejectsEverything())
                    "*.kt".file()
                }
            }
        }
    }
}

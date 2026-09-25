package me.tbsten.katachi.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.concurrent.CancellationException
import me.tbsten.katachi.catching
import me.tbsten.katachi.runProcessorCatching

/**
 * The two ways katachi keeps what a block threw, and the one place they part: an
 * [AssertionError] ends a unit of the walk, but is the answer of a processor.
 */
class CatchingSpec : FreeSpec({
    "runProcessorCatching" - {
        "投げなければ success になる" {
            runProcessorCatching { 42 }.getOrNull() shouldBe 42
        }

        "ふつうの例外は failure になる" {
            val cause = IllegalStateException("boom")

            runProcessorCatching { throw cause }.exceptionOrNull() shouldBeSameInstanceAs cause
        }

        "AssertionError は failure になる" {
            val cause = AssertionError("the answer")

            runProcessorCatching { throw cause }.exceptionOrNull() shouldBeSameInstanceAs cause
        }

        "致命的な5種は Result にならず外へ出る" {
            val fatals = listOf<() -> Throwable>(
                { StackOverflowError() },
                { OutOfMemoryError() },
                { NoClassDefFoundError("SomeClass") },
                { InterruptedException("stop") },
                { CancellationException("cancelled") },
            )

            for (fatal in fatals) {
                val thrown = shouldThrow<Throwable> { runProcessorCatching<Unit> { throw fatal() } }
                thrown::class shouldBe fatal()::class
            }
        }
    }

    "catching との違い" - {
        "catching は AssertionError を外へ出す" {
            shouldThrow<AssertionError> { catching<Unit> { throw AssertionError("the answer") } }
        }
    }
})

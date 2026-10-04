package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.NamePattern
import me.tbsten.katachi.dsl.internal.GlobProblem

/**
 * The glob of a layout key, opened up as [NamePattern] for names, imports and packages.
 *
 * What is pinned here is that it is the *same* glob: the rules a reader learned from `layout { }`
 * (`*` is one level of one character or more, `**` crosses levels, `?{}[]` are refused, `\`
 * escapes) hold unchanged once the separator is `.` or none.
 */
class NamePatternSpec : FreeSpec({
    "区切りなし（名前・型）" - {
        "* は 1 文字以上に当たり、空には当たらない" {
            val impl = NamePattern.compile("*Impl")

            impl.matches("UserRepositoryImpl") shouldBe true
            impl.matches("Impl") shouldBe false
        }

        "区切りが無いので * は . や < も含めて何にでも当たる" {
            NamePattern.compile("*").matches("a.b.C") shouldBe true
            NamePattern.compile("StateFlow<*>").matches("StateFlow<HomeUiState>") shouldBe true
            NamePattern.compile("StateFlow<*>").matches("StateFlow<Map<K,V>>") shouldBe true
        }

        "** だけの名前は * と同じく何にでも当たる" {
            NamePattern.compile("**").matches("HomeViewModel") shouldBe true
            NamePattern.compile("**").matches("a.b") shouldBe true
        }

        "大文字と小文字を区別する" {
            NamePattern.compile("homeViewModel").matches("HomeViewModel") shouldBe false
            NamePattern.compile("*viewmodel").matches("HomeViewModel") shouldBe false
        }

        "区切りは null と読める" {
            NamePattern.compile("*Impl").separator shouldBe null
        }
    }

    "区切り '.'（import・package）" - {
        "* は 1 区切りだけに当たる" {
            val coroutines = NamePattern.compile("kotlinx.coroutines.*", separator = '.')

            coroutines.matches("kotlinx.coroutines.launch") shouldBe true
            coroutines.matches("kotlinx.coroutines.flow.Flow") shouldBe false
        }

        "** は区切りをまたぐ" {
            val kotlinx = NamePattern.compile("kotlinx.**", separator = '.')

            kotlinx.matches("kotlinx.coroutines.flow.Flow") shouldBe true
            kotlinx.matches("kotlinx.coroutines") shouldBe true
            kotlinx.matches("kotlin.collections.List") shouldBe false
        }

        "途中の ** は 0 区切りにも当たる" {
            val pattern = NamePattern.compile("com.example.**.domain.*", separator = '.')

            pattern.matches("com.example.domain.User") shouldBe true
            pattern.matches("com.example.feature.home.domain.User") shouldBe true
        }

        "star import は a.b.* という文字列として当たる" {
            NamePattern.compile("""kotlinx.coroutines.\*""", separator = '.').matches("kotlinx.coroutines.*") shouldBe true
            NamePattern.compile("kotlinx.**", separator = '.').matches("kotlinx.coroutines.*") shouldBe true
        }

        "区切りは '.' と読める" {
            NamePattern.compile("kotlin.**", separator = '.').separator shouldBe '.'
        }
    }

    "hasWildcard" - {
        "* か ** があれば true" {
            NamePattern.compile("*ViewModel").hasWildcard shouldBe true
            NamePattern.compile("com.example.**", separator = '.').hasWildcard shouldBe true
        }

        "ワイルドカードが無ければ false" {
            NamePattern.compile("HomeViewModel").hasWildcard shouldBe false
        }

        """\* は文字として読むので false""" {
            NamePattern.compile("""Star\*""").hasWildcard shouldBe false
        }
    }

    "エスケープ" - {
        """\? は ? という文字に当たる""" {
            val nullable = NamePattern.compile("""String\?""")

            nullable.matches("String?") shouldBe true
            nullable.matches("String") shouldBe false
        }

        """\* は * という文字だけに当たる""" {
            val star = NamePattern.compile("""Foo\*""")

            star.matches("Foo*") shouldBe true
            star.matches("FooBar") shouldBe false
        }

        "書いたとおりのパターンをエスケープごと読める" {
            NamePattern.compile("""Foo\*""").pattern shouldBe """Foo\*"""
        }
    }

    "読めないパターン" - {
        "? { } [ ] は KatachiGlobSyntaxException" {
            listOf("Foo?", "on{Click,Tap}", "Foo[0]").forEach { pattern ->
                shouldThrow<KatachiGlobSyntaxException> { NamePattern.compile(pattern) }.pattern shouldBe pattern
            }
        }

        "空のパターンは KatachiGlobSyntaxException" {
            shouldThrow<KatachiGlobSyntaxException> { NamePattern.compile("") }
                .problem shouldBe GlobProblem.EmptyPattern
        }

        "空の区切り a..b は KatachiGlobSyntaxException" {
            shouldThrow<KatachiGlobSyntaxException> { NamePattern.compile("a..b", separator = '.') }
                .problem.shouldBeInstanceOf<GlobProblem.EmptySegment>().separator shouldBe '.'
        }

        "区切りの中の ** は KatachiGlobSyntaxException" {
            shouldThrow<KatachiGlobSyntaxException> { NamePattern.compile("com.**Impl", separator = '.') }
                .problem.shouldBeInstanceOf<GlobProblem.DoubleStarInsideSegment>()
        }

        "glob 自身の文字を区切りにすると KatachiGlobSyntaxException" {
            listOf('*', '\\', '{', '}', '?', '[', ']', ',').forEach { separator ->
                shouldThrow<KatachiGlobSyntaxException> { NamePattern.compile("a*b", separator = separator) }
                    .problem.shouldBeInstanceOf<GlobProblem.UnusableSeparator>().separator shouldBe separator
            }
        }
    }

    "等しさと表示" - {
        "パターンと区切りが同じなら等しい" {
            NamePattern.compile("*Impl") shouldBe NamePattern.compile("*Impl")
            NamePattern.compile("*Impl").hashCode() shouldBe NamePattern.compile("*Impl").hashCode()
        }

        "区切りが違えば等しくない" {
            NamePattern.compile("a.*") shouldNotBe NamePattern.compile("a.*", separator = '.')
        }

        "toString は NamePattern(<pattern>)" {
            NamePattern.compile("*Impl").toString() shouldBe "NamePattern(*Impl)"
        }
    }
})

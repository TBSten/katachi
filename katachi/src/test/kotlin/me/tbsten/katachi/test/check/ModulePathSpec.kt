package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.KatachiGlobSyntaxException
import me.tbsten.katachi.check.ModulePath
import me.tbsten.katachi.check.ModulePattern

class ModulePathSpec : FreeSpec({
    "モジュールパスの読み取り" - {
        "先頭の : はあってもなくても同じモジュールになる" {
            ModulePath.of(":core:data") shouldBe ModulePath.of("core:data")
        }

        "segments に階層が順に入る" {
            ModulePath.of(":core:data:remoteApi").segments shouldContainExactly
                listOf("core", "data", "remoteApi")
        }

        "value は先頭に : を付けて出力する" {
            ModulePath.of("core:data").value shouldBe ":core:data"
        }

        "name は一番内側の名前になる" {
            ModulePath.of(":core:data").name shouldBe "data"
        }

        "parent は1つ上のモジュールを返す" {
            ModulePath.of(":core:data").parent shouldBe ModulePath.of(":core")
        }

        "大文字小文字は区別される" {
            (ModulePath.of(":core:Data") == ModulePath.of(":core:data")) shouldBe false
        }
    }

    "ルートプロジェクト" - {
        "\":\" はルートプロジェクトになる" {
            ModulePath.of(":") shouldBe ModulePath.ROOT
        }

        "ルートプロジェクトは階層を持たず : と表示される" {
            ModulePath.ROOT.segments shouldContainExactly emptyList()
            ModulePath.ROOT.value shouldBe ":"
            ModulePath.ROOT.isRoot shouldBe true
        }

        "ルートプロジェクトより上は無い" {
            ModulePath.ROOT.parent.shouldBeNull()
        }
    }

    "読めないモジュールパスは DSL 評価時に落ちる" - {
        "空文字列" {
            shouldThrow<KatachiGlobSyntaxException> { ModulePath.of("") }
                .message.shouldNotBeNull() shouldContain "must not be empty"
        }

        ": が2つ続く" {
            shouldThrow<KatachiGlobSyntaxException> { ModulePath.of(":core::data") }
                .message.shouldNotBeNull() shouldContain "empty module name"
        }

        ": で終わる" {
            shouldThrow<KatachiGlobSyntaxException> { ModulePath.of(":core:") }
                .message.shouldNotBeNull() shouldContain "empty module name"
        }

        "ワイルドカードは1つのモジュールを名指ししていない" {
            shouldThrow<KatachiGlobSyntaxException> { ModulePath.of(":feature:*") }
                .message.shouldNotBeNull() shouldContain "uses `*`"
        }
    }

    "パターンの捕捉" - {
        "\":feature:*\" は :feature:home にマッチし home を捕捉する" {
            ModulePattern.compile(":feature:*").match(ModulePath.of(":feature:home"))
                .shouldNotBeNull() shouldContainExactly listOf("home")
        }

        "\":feature:*\" は :feature 自体にも :feature:home:impl にもマッチしない" {
            val pattern = ModulePattern.compile(":feature:*")
            pattern.matches(ModulePath.of(":feature")) shouldBe false
            pattern.matches(ModulePath.of(":feature:home:impl")) shouldBe false
        }

        "\":core:*:*\" は2階層をそれぞれ捕捉する" {
            ModulePattern.compile(":core:*:*").match(ModulePath.of(":core:data:remote"))
                .shouldNotBeNull() shouldContainExactly listOf("data", "remote")
        }

        "\":feature:**\" は :feature:hoge:fuga を階層ごとに平坦化して捕捉する" {
            ModulePattern.compile(":feature:**").match(ModulePath.of(":feature:hoge:fuga"))
                .shouldNotBeNull() shouldContainExactly listOf("hoge", "fuga")
        }

        "\":feature:**\" が :feature 自体にマッチしたときの捕捉は空になる" {
            ModulePattern.compile(":feature:**").match(ModulePath.of(":feature"))
                .shouldNotBeNull() shouldContainExactly emptyList()
        }

        "\":core:*:**\" は手前の * の添字を固定したまま ** を末尾に並べる" {
            ModulePattern.compile(":core:*:**").match(ModulePath.of(":core:x:a:b"))
                .shouldNotBeNull() shouldContainExactly listOf("x", "a", "b")
        }

        "マッチしなければ null を返す" {
            ModulePattern.compile(":feature:*").match(ModulePath.of(":core:data")).shouldBeNull()
        }
    }

    "パターンの書き方" - {
        "先頭の : は省略できる" {
            ModulePattern.compile("feature:*").pattern shouldBe ":feature:*"
            ModulePattern.compile("feature:*").matches(ModulePath.of(":feature:home")) shouldBe true
        }

        "ワイルドカードの有無を判別できる" {
            ModulePattern.compile(":core:data").hasWildcard shouldBe false
            ModulePattern.compile(":feature:*").hasWildcard shouldBe true
            ModulePattern.compile(":feature:**").hasWildcard shouldBe true
        }

        "ワイルドカードが無いパターンは1つのモジュールを名指しする" {
            ModulePattern.compile("core:data").literalPath shouldBe ModulePath.of(":core:data")
        }

        "ワイルドカードを含むパターンは1つのモジュールを名指ししない" {
            ModulePattern.compile(":feature:*").literalPath.shouldBeNull()
        }

        "\":\" はルートプロジェクトだけにマッチする" {
            val pattern = ModulePattern.compile(":")
            pattern.match(ModulePath.ROOT).shouldNotBeNull() shouldContainExactly emptyList()
            pattern.matches(ModulePath.of(":core")) shouldBe false
            pattern.hasWildcard shouldBe false
            pattern.literalPath shouldBe ModulePath.ROOT
        }

        "\":**\" はルートプロジェクトを含むすべてのモジュールにマッチする" {
            val pattern = ModulePattern.compile(":**")
            pattern.match(ModulePath.ROOT).shouldNotBeNull() shouldContainExactly emptyList()
            pattern.match(ModulePath.of(":feature:home"))
                .shouldNotBeNull() shouldContainExactly listOf("feature", "home")
        }

        "階層の一部に対する * も書ける" {
            ModulePattern.compile(":feature:debug-*").match(ModulePath.of(":feature:debug-menu"))
                .shouldNotBeNull() shouldContainExactly listOf("menu")
        }
    }

    "** の位置は DSL 評価時に検査される" - {
        "末尾以外に書くと落ちる" {
            shouldThrow<KatachiGlobSyntaxException> { ModulePattern.compile(":feature:**:impl") }
                .message.shouldNotBeNull() shouldContain "before its last segment"
        }

        "2つ以上書くと落ちる" {
            shouldThrow<KatachiGlobSyntaxException> { ModulePattern.compile(":**:feature:**") }
                .message.shouldNotBeNull() shouldContain "at most once"
        }

        "空文字列は読めない" {
            shouldThrow<KatachiGlobSyntaxException> { ModulePattern.compile("") }
                .message.shouldNotBeNull() shouldContain "must not be empty"
        }

        "katachi の glob に無いメタ文字は落ちる" {
            shouldThrow<KatachiGlobSyntaxException> { ModulePattern.compile(":feature:{home,settings}") }
        }
    }
})

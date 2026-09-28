package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.internal.featured.FeaturedCollector
import me.tbsten.katachi.dokka.internal.featured.FeaturedDeclarationKind
import me.tbsten.katachi.dokka.internal.featured.FeaturedEntry
import me.tbsten.katachi.dokka.internal.fragment.toSummarySegments

class FeaturedCollectorSpec : FreeSpec({
    val entries by lazy { FeaturedCollector.collect(DokkaRunner.run(FeaturedSources.ALL_KINDS).module) }

    "表示名と並び順" - {
        "メンバは Owner.member、トップレベルは名前だけで、パッケージ → 名前の順に並ぶ" {
            entries.map { it.name } shouldContainExactly FeaturedSources.ALL_KINDS_NAMES
        }

        "同じページに載るオーバーロードは1件にまとまる" {
            entries.count { it.name == "Architecture.assert" } shouldBe 1
        }

        "2回集めても同じ順番になる" {
            val again = FeaturedCollector.collect(DokkaRunner.run(FeaturedSources.ALL_KINDS).module)
            again.map { it.name } shouldContainExactly entries.map { it.name }
        }

        "宣言の種類が分かる" {
            entries.associate { it.name to it.kind } shouldBe mapOf(
                "Architecture" to FeaturedDeclarationKind.Class,
                "Architecture.assert" to FeaturedDeclarationKind.Function,
                "Definition" to FeaturedDeclarationKind.TypeAlias,
                "Strictness.STRICT" to FeaturedDeclarationKind.EnumEntry,
                "architecture" to FeaturedDeclarationKind.Function,
                "formatVersion" to FeaturedDeclarationKind.Property,
                "DeclarationScope" to FeaturedDeclarationKind.Interface,
                "DeclarationScope.Defaults.name" to FeaturedDeclarationKind.Property,
            )
        }
    }

    "概要の選び方" - {
        "タグの後ろに文があれば、KDoc の本文より優先する" {
            entries.summaryTextOf("Architecture.assert") shouldBe
                "Runs every check and throws AssertionError on violations."
        }

        "タグだけなら KDoc の本文の最初の段落を使う" {
            entries.summaryTextOf("architecture") shouldBe "Declares the architecture of a project."
        }

        "本文もタグの文も無ければ概要は無い" {
            entries.first { it.name == "Definition" }.summary.shouldBeNull()
        }
    }

    "@featured がひとつも無いモジュールでは何も集まらない" {
        FeaturedCollector.collect(DokkaRunner.run(FeaturedSources.NONE).module).shouldBeEmpty()
    }
})

private fun List<FeaturedEntry>.summaryTextOf(name: String): String? =
    first { it.name == name }.summary?.toSummarySegments()?.joinToString("") { it.text }

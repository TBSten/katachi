package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.KatachiConflictingLayoutMetadataException
import me.tbsten.katachi.dsl.KatachiMetadataWithoutEntryException
import me.tbsten.katachi.dsl.LayoutDeclarationScope
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.metadata

/** The two lines a processor author writes, exactly as [MetadataScope]'s own KDoc does. */
private val Obsolete: MetadataKey<Boolean> = metadata()
private var MetadataScope.obsolete: Boolean? by Obsolete

/** A second key, to confirm two keys on one declaration are told apart by identity. */
private val Owner: MetadataKey<String> = metadata()
private var LayoutDeclarationScope.owner: String? by Owner

class LayoutMetadataSpec : FreeSpec({
    "宣言に付けて読める" - {
        "ファイルの宣言に付けた値を LayoutEntry[key] で読める" {
            val entries = layoutOf { "A".file().metadata { obsolete = true } }

            entries.single { it.path == "A" }[Obsolete] shouldBe true
        }

        "ディレクトリの宣言に付けた値も読める" {
            val entries = layoutOf { "dir" { "A".file() }.metadata { obsolete = true } }

            entries.single { it.path == "dir" }[Obsolete] shouldBe true
        }

        "/ の末尾に付けた値は葉のファイルに付く" {
            val entries = layoutOf { "dir" / "A".file().metadata { obsolete = true } }

            entries.single { it.path == "dir/A" }[Obsolete] shouldBe true
            entries.single { it.path == "dir" }[Obsolete].shouldBeNull()
        }

        "モジュールの宣言に付けた値は展開したディレクトリに付く" {
            val entries = layoutOf {
                ":feature:*".module { "A".file() }.metadata { obsolete = true }
            }

            // プロジェクトを見ていないインデックスでは、キーはパターンのまま1つのディレクトリ
            // （"feature/*"）に展開される。それがモジュールの開いたディレクトリそのもの。
            entries.single { it.path == "feature/*" }[Obsolete] shouldBe true
        }

        "何も付けなければキーは null" {
            layoutOf { "A".file() }.single { it.path == "A" }[Obsolete] shouldBe null
        }

        "同じ宣言に2回書くと後勝ち（役割の metadata と同じ規則）" {
            val entries = layoutOf {
                "A".file().metadata {
                    owner = "team-a"
                    owner = "team-b"
                }
            }

            entries.single { it.path == "A" }[Owner] shouldBe "team-b"
        }

        "同じ器にキーごとの値を持てる" {
            val entries = layoutOf { "A".file().metadata { obsolete = true; owner = "team-a" } }
            val entry = entries.single { it.path == "A" }

            entry[Obsolete] shouldBe true
            entry[Owner] shouldBe "team-a"
        }
    }

    "継承しない" - {
        "ディレクトリに付けた値は下のエントリに継承されない" {
            val entries = layoutOf {
                "dir" { "A".file() }.metadata { obsolete = true }
            }

            entries.single { it.path == "dir" }[Obsolete] shouldBe true
            entries.single { it.path == "dir/A" }[Obsolete].shouldBeNull()
        }
    }

    ":\".module { }.metadata { }" - {
        "ルートプロジェクトの module には付ける先が無く KatachiMetadataWithoutEntryException" {
            val thrown = shouldThrow<KatachiMetadataWithoutEntryException> {
                layoutOf { ":".module { "README.md".file() }.metadata { obsolete = true } }
            }

            thrown.modulePath shouldBe ":"
        }
    }

    "2つの宣言が同じパスに合流するとき" - {
        "同じ値なら1つにまとまる" {
            val entries = layoutOf {
                "dir" / "A".file().metadata { obsolete = true }
                "dir" / "A".file().metadata { obsolete = true }
            }

            entries.count { it.path == "dir/A" } shouldBe 1
            entries.single { it.path == "dir/A" }[Obsolete] shouldBe true
        }

        "違う値なら KatachiConflictingLayoutMetadataException" {
            val thrown = shouldThrow<KatachiConflictingLayoutMetadataException> {
                layoutOf {
                    "dir" / "A".file().metadata { owner = "team-a" }
                    "dir" / "A".file().metadata { owner = "team-b" }
                }
            }

            thrown.path shouldBe "dir/A"
            thrown.role shouldBe "group.Role"
        }
    }

    "平坦化の結果にも検査にも影響しない" - {
        "metadata を付けた定義と外した定義で path・kind・required・description が同じ" {
            val withMetadata = layoutOf {
                "useCase" / "*UseCase".ktFile().metadata { obsolete = true }
            }
            val plain = layoutOf {
                "useCase" / "*UseCase".ktFile()
            }

            withMetadata.shape() shouldBe plain.shape()
        }
    }
})

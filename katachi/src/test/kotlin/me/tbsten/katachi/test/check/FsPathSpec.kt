package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.FsPath
import me.tbsten.katachi.dsl.InternalKatachiApi

@OptIn(InternalKatachiApi::class)
class FsPathSpec : FreeSpec({
    "区切り文字を / に正規化する" - {
        "Windows の区切り文字を受け付ける" {
            FsPath.of("""C:\repo\app""").value shouldBe "C:/repo/app"
        }

        "区切り文字が続いても1つにまとめる" {
            FsPath.of("/repo//app///src").value shouldBe "/repo/app/src"
        }

        "末尾の区切り文字を落とす" {
            FsPath.of("/repo/app/").value shouldBe "/repo/app"
        }

        "\".\" と \"..\" を解決する" {
            FsPath.of("/repo/./app/../lib").value shouldBe "/repo/lib"
        }
    }

    "絶対パスと相対パスを区別する" {
        FsPath.of("/repo").isAbsolute shouldBe true
        FsPath.of("C:/repo").isAbsolute shouldBe true
        FsPath.of("app/src").isAbsolute shouldBe false
    }

    "親を辿れる" - {
        "parent は1つ上のディレクトリを返す" {
            FsPath.of("/repo/app/src").parent?.value shouldBe "/repo/app"
        }

        "ルートの parent は null" {
            FsPath.of("/").parent.shouldBeNull()
        }

        "ルートまで辿るとすべての祖先が並ぶ" {
            val ancestors = generateSequence(FsPath.of("/repo/app/src")) { it.parent }
            ancestors.map { it.value }.toList() shouldContainExactly
                listOf("/repo/app/src", "/repo/app", "/repo", "/")
        }
    }

    "div で子を足せる" - {
        "1階層ずつ足せる" {
            (FsPath.of("/repo") / "app").value shouldBe "/repo/app"
        }

        "複数階層をまとめて足せる" {
            (FsPath.of("/repo") / "gradle/wrapper/gradle-wrapper.jar").value shouldBe
                "/repo/gradle/wrapper/gradle-wrapper.jar"
        }
    }

    "name は末尾のセグメントを返す" {
        FsPath.of("/repo/app/build.gradle.kts").name shouldBe "build.gradle.kts"
        FsPath.of("/").name shouldBe ""
    }

    "startsWith と relativeTo でルートからの相対パスを取れる" - {
        val root = FsPath.of("/repo")

        "配下のパスは相対パスになる" {
            (FsPath.of("/repo/app/src").relativeTo(root)) shouldBe "app/src"
        }

        "ルート自身は空文字列になる" {
            root.relativeTo(root) shouldBe ""
        }

        "配下でないパスは null になる" {
            FsPath.of("/other/app").relativeTo(root).shouldBeNull()
            FsPath.of("/other/app").startsWith(root) shouldBe false
        }

        "セグメント単位で比較するので、名前の前方一致では配下にならない" {
            FsPath.of("/repository/app").startsWith(root) shouldBe false
        }
    }

    "value が同じなら等しい" {
        FsPath.of("/repo/app") shouldBe FsPath.of("""\repo\app\""")
        FsPath.of("/repo/app").hashCode() shouldBe FsPath.of("/repo/app").hashCode()
    }

    "大文字小文字は区別する" {
        (FsPath.of("/repo/App") == FsPath.of("/repo/app")) shouldBe false
    }
})

package me.tbsten.katachi.test

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.displayExistingPath
import me.tbsten.katachi.internal.displayPath
import me.tbsten.katachi.internal.fileUri

class FileUrisSpec : FreeSpec({
    val root = "/repo"

    "fileUri" - {
        "絶対パスをスラッシュ3つの file URI にする" {
            fileUri("/repo/app/Main.kt") shouldBe "file:///repo/app/Main.kt"
        }

        "空白と日本語をパーセントエンコードして ASCII だけにする" {
            fileUri("/repo/a b/日本.kt") shouldBe "file:///repo/a%20b/%E6%97%A5%E6%9C%AC.kt"
        }

        "# と % をエンコードする" {
            fileUri("/repo/a#b%c") shouldBe "file:///repo/a%23b%25c"
        }

        "ドライブレターのパスに先頭のスラッシュを足す" {
            fileUri("C:/repo/x.kt") shouldBe "file:///C:/repo/x.kt"
        }

        "OS のパスを絶対パスにして . と .. を畳む" {
            absolutePathOf("/repo/./app/../docs") shouldBe "/repo/docs"
        }

        "バックスラッシュをスラッシュに直す" {
            fileUri("""C:\repo\x.kt""") shouldBe "file:///C:/repo/x.kt"
        }

        "ルートからの相対パスをルートにつなぐ" {
            fileUri(root, "app/Main.kt") shouldBe "file:///repo/app/Main.kt"
        }

        "ドットはルート自身になる" {
            fileUri(root, ".") shouldBe "file:///repo"
        }

        "ファイルシステムのルートの下でもスラッシュが重ならない" {
            fileUri("/", "a.kt") shouldBe "file:///a.kt"
        }
    }

    "displayPath" - {
        "相対パスはルートの下の file URI になる" {
            displayPath(root, "core/domain/UseCase.kt") shouldBe "file:///repo/core/domain/UseCase.kt"
        }

        "ドットはルートの file URI になる" {
            displayPath(root, ".") shouldBe "file:///repo"
        }

        "パターンはそのまま返す" {
            displayPath(root, "feature/*/src") shouldBe "feature/*/src"
            displayPath(root, "src/{main,test}") shouldBe "src/{main,test}"
            displayPath(root, "a?.kt") shouldBe "a?.kt"
            displayPath(root, "[ab].kt") shouldBe "[ab].kt"
        }

        "絶対パスはルートに関係なく file URI にする" {
            displayPath(root, "/elsewhere/x.kt") shouldBe "file:///elsewhere/x.kt"
            displayPath(root, "D:/work/x.kt") shouldBe "file:///D:/work/x.kt"
        }

        "すでに file URI のものはそのまま返す" {
            displayPath(root, "file:///repo/x.kt") shouldBe "file:///repo/x.kt"
        }

        "ルートが無ければそのまま返す" {
            displayPath(null, "core/domain/UseCase.kt") shouldBe "core/domain/UseCase.kt"
        }
    }

    "displayExistingPath" - {
        "グロブの文字を含む実在ファイルも file URI にしてエンコードする" {
            displayExistingPath(root, "[ab].kt") shouldBe "file:///repo/%5Bab%5D.kt"
            displayExistingPath(root, "a{b}.kt") shouldBe "file:///repo/a%7Bb%7D.kt"
            displayExistingPath(root, "a?.kt") shouldBe "file:///repo/a%3F.kt"
        }

        "ドットはルートの file URI になる" {
            displayExistingPath(root, ".") shouldBe "file:///repo"
        }

        "ルートが無ければそのまま返す" {
            displayExistingPath(null, "[ab].kt") shouldBe "[ab].kt"
        }
    }
})

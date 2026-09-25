package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of starting the process and assembling the application. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "エントリポイント"
    summary = "プロセスの起動。`main()` を持つ唯一のファイル"
    description = """
        `main()` が書かれる場所です。このサンプルでは `NoteStore` を作って `all()` を呼び、
        結果を標準出力に並べるだけで、分岐も設定もありません。

        置いてよいのは、プロセスを起動して層を組み立てるところまでです。

        置いてはいけないもの:

        - 値そのもの。ノートの中身は保管庫の役割が持ちます。`main()` に直接書くと
          「データがどこから来るのか」の答えがエントリポイントに移ってしまいます
        - 値の形。`Note` の定義はモデルの役割です

        本体をわざわざ3つの役割に割っているのは、このサンプルの本題が processor だからです。
        役割が1つしか無いと、`RoleFileCount` が数える対象も `RoleTable` が並べる行も1件に
        なってしまい、processor の出力から何も読み取れません。アプリの規模から必要になった
        分割ではありません。

        この役割の `layout { }` にはワイルドカードが無いので、`Main.kt` が1つあることを
        要求します。消せば `[MissingFile]` が出ます。
    """.trimIndent()
    example("Main.kt", "プロセスの起動点")
    layout {
        // The application is the root project, so `":"` resolves to the repository root and
        // `modulePackage` derives `com/example` from the base package alone. No wildcard, so
        // this one is required.
        ":".module {
            mainSourceSet / kotlin / modulePackage / "Main".ktFile()
        }
    }
}

package me.tbsten.katachi.test.architecture.backend

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.backendGroup() = "backend".group {
    title = "バックエンド"
    description = """
        Konsist・Konture は重いパーサなので、`:katachi` だけを使う利用者に背負わせないよう、`api` 依存に持つバックエンドを別モジュールにしています。
        この分離がライブラリの目に見える約束なので、1 層ではなく境界として独立した group にしています。
    """.trimIndent()

    konsistBackend()
    kontureBackend()
}

# api

公開範囲・API の形・非推奨の時期への判断（手順 3・4）。形は [README.md](README.md)。

## このまま: .module の削除が予告より 1 版早い・ModuleResolver が渡す口の無い安定 API として残る
- decision: このまま
- file: katachi/src/main/kotlin/me/tbsten/katachi/dsl/ModuleResolver.kt
- quote: public fun interface ModuleResolver
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（TODO.html）

手順 3 の p6。`.module` 系の非推奨 API を v0.4.0 で消した（KATACHI-234）のが、予告より 1 版早い。`ModuleResolver` / `ModulePath` は渡す口が無くなったのに注釈なしの public で残る。
利用者の一言:「使ってる方が悪いしほぼいないはず」。リリースノートで触れていれば、次の版でも同じ指摘は出さない。

## このまま: 安定 API の例外が FsPath（Experimental）を返す
- decision: このまま
- file: katachi/src/main/kotlin/me/tbsten/katachi/dsl/FileSelectionExceptions.kt
- quote: public val root: FsPath,
- decided: 2026-11-10 261109-01 KATACHI-327
- by: 利用者（TODO.html、「ギリ許容」）

手順 3 の p4。注釈なしの public な例外（`FileSelectionExceptions.kt`・`check/FileConstraintExceptions.kt` など）のプロパティが `@ExperimentalKatachiApi` の `FsPath` を返す。

## このまま: gitTracked() と FileSelection.GitTracked の別名
- decision: このまま
- file: katachi/src/main/kotlin/me/tbsten/katachi/dsl/FileSelections.kt
- quote: public fun gitTracked(): FileSelection = FileSelection.GitTracked
- decided: 2026-11-10 261109-01 KATACHI-327
- by: 利用者（TODO.html）

手順 3 の p2。同じものを指す名前が 2 つある。

## このまま: Experimental の機能だけが出す違反・例外が、注釈なしの public になっている
- decision: このまま
- file: katachi/src/main/kotlin/me/tbsten/katachi/check/CaptureViolations.kt
- quote: public class CaptureMismatch internal constructor(
- decided: 2026-11-10 261109-01 KATACHI-327
- by: 利用者（TODO.html、02:58 JST「現状維持」）

手順 3 の p3。`@ExperimentalKatachiApi` の機能だけが投げる・報告する違反と例外のクラスが、注釈なしの public になっている。261109-01 では `CaptureMismatch` など 24 件（一覧は Mac の `.local/release-v0.4.0/tmp/visibility-*-judged.md`）。
次のチェックでは、この 24 件に該当する宣言は要検討にしない。同じ種類の新しいクラスが出たら、この判断に該当しそうと添えて要検討に載せる（まとめて方針にするかは利用者に聞く）。

## このまま: v0.3.0 のプロパティの行（名前, 型）が、エラーにならずに「レシーバー, 名前」に読み替わる
- decision: このまま
- file: katachi-konsist/src/main/kotlin/me/tbsten/katachi/konsist/declarations/PropertyAllow.kt
- quote: receiver: String,
- decided: 2026-11-12 261112-01 KATACHI-368
- by: 利用者（スレッド、08:27 JST「ならやらないでいいや」）

手順 3 の p5。v0.3.0 の ``allow(`val`, "uiState", "StateFlow<*>")`` は v0.4.0 でもコンパイルが通り、拡張プロパティの行として読まれる。`not.allow` の行は何も禁止しなくなり、黙って通る。
型を書く方法は `.type(...)` だけで、困るのは古い行を残して上げた人だけ。型にしか見えない名前を止める手当ては入れない。リリースノートの移行表の注意書き（古い `not.allow` の行は黙って通る）だけにする。
次のチェックでは、この読み替えを要検討にしない。

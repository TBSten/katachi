---
title: v0.4.0 リリース前チェック 5 回目 手順 11
base: 66e20980
---

## [p2] Kotlin の例が 4 スペースの字下げ（D2-3）
- file: ja/get-started/manual-install.mdx
- line: 16
- quote:     repositories {
- impact: あり: 写すと 4 スペースの字下げが広まる
- recommend: 直さない 6, 直してもらう 4

- 該当: settings.gradle.kts の例が 4 スペースの字下げ
- 根拠: D2-3（Kotlin のコードブロックは 2 スペース。`docs/AGENTS.md`）
- 直し方の案: 2 スペースにする（`katachi-script` と README は過去に「直さない」の判断）

## [p2] Kotlin の例が 4 スペースの字下げ（D2-3）
- file: ja/get-started/manual-install.mdx
- line: 43
- quote:     kotlin("jvm") version "<Kotlin のバージョン>"
- impact: あり: 写すと 4 スペースの字下げが広まる
- recommend: 直さない 6, 直してもらう 4

- 該当: architecture-test/build.gradle.kts の例が 4 スペースの字下げ
- 根拠: D2-3
- 直し方の案: 2 スペースにする

## [p2] Kotlin の例が 4 スペースの字下げ（D2-3）
- file: ja/get-started/manual-install.mdx
- line: 89
- quote:     gradle()
- impact: あり: 写すと 4 スペースの字下げが広まる
- recommend: 直さない 6, 直してもらう 4

- 該当: ProjectArchitecture.kt の例が 4 スペースの字下げ
- 根拠: D2-3
- 直し方の案: 2 スペースにする

## [p2] Kotlin の例が 4 スペースの字下げ（D2-3）
- file: ja/get-started/manual-install.mdx
- line: 116
- quote:     @Test
- impact: あり: 写すと 4 スペースの字下げが広まる
- recommend: 直さない 6, 直してもらう 4

- 該当: ProjectArchitectureTest.kt の例が 4 スペースの字下げ
- 根拠: D2-3
- 直し方の案: 2 スペースにする

## [p1] シェルのコマンドが kt のコードブロックに入っている
- file: ja/get-started/manual-install.mdx
- line: 128
- quote: ```kt
- impact: あり: コマンドが Kotlin として色付けされる
- recommend: 直してもらう 7, 直さない 3

- 該当: `./gradlew :architecture-test:test` を kt のフェンスに入れている
- 直し方の案: `shell` にする

## [p1] ハイライトが閉じかっこまで含んでいる（D2-4）
- file: ja/guides/basic-api.mdx
- line: 201
- quote: {7-12}
- impact: あり: ハイライトの末尾が閉じかっこを指す
- recommend: 直さない 7, 直してもらう 3

- 該当: `{7-12}` の 12 行目は `ktFileContent { }` を閉じる `}`
- 直し方の案: `7-11` にする。ブロック全体を指す意図なら範囲のままでよい（要確認）。kt-file-content.mdx の `{6,8-13,15-19}` は過去に「直さない」

## [p2] 指示語「これは」が前の文を指している（D3-3）
- file: ja/guides/diff-report.mdx
- line: 18
- quote: これは量が多く
- impact: あり: 指すものが前の文にしか無い
- recommend: 直さない 6, 直してもらう 4

- 該当: 「単純な git diff では…列挙されます。これは量が多く…」
- 直し方の案: 「これは」を「その一覧は」にする

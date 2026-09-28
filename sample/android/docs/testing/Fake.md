[katachi-sample-android](../README.md) / [テスト](README.md)

# フェイク

:testing に置く、他モジュールのテストから使う偽の実装

`:data` のインターフェースを、メモリ上の値だけで満たすテスト用の実装。
`FakeUserRepository` は `UserRepository` を、`FakeSettingsRepository` は
`SettingsRepository` を実装する。コンストラクタ引数に既定値を持たせてあるので、
テスト側はその回に関係のある値だけを書けばよい。

`src/test` ではなく `main` に置いてあるのがこの役割の肝。`src/test` のコードは
同じモジュールからしか見えないので、他モジュールのテストへ渡すには
製品コードとして公開するしかない。利用側は `testImplementation(project(":testing"))` で
取り込む。`:testing` が `:data` に `api` で依存しているのも、取り込んだ側が
インターフェースごと受け取れるようにするため。

ファイル名は `Fake*.kt`。`:testing` に置けるのは差し替え用の実装だけで、
テストのヘルパーやカスタムアサーションを足したくなったら、まず役割を増やす。
テストそのものは別の役割（テストコード）で、`src/test` にある。

テンプレートから生成できる。`repository` に渡すのは実装したいインターフェースの名前
そのもの（`UserRepository`）で、`--arg template=testing.Fake --arg repository=UserRepository`
で `FakeUserRepository.kt` ができる。どの領域の package に置くかは、名前の頭が
`DataDomain` のどれと一致するかで決める。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:testing` | `src/main/kotlin/**/Fake*.kt` |  |

## Examples

- `FakeUserRepository` ... UserRepository のメモリ実装
- `FakeSettingsRepository` ... SettingsRepository のメモリ実装

## 置いてはいけないもの

本番から呼ばれるコード。`:testing` に依存してよいのは
テストのコンパイル経路だけで、`:app` や `:feature:*` の `main` からは参照しない。

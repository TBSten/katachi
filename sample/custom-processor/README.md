# sample/custom-processor

katachi のサンプルのうち、**利用者が自分の processor を書く方法**だけを見せるもの。
`sample/jvm` `sample/android` `sample/kmp` と同じく、`settings.gradle.kts` と gradle wrapper を自前で持つ
**独立した Gradle ビルド**で、`includeBuild("../..")` によって katachi をリポジトリのソースから取り込む。

ほかの3サンプルは「**プロジェクト**を katachi でどう書くか」を見せる。こちらは「**プロセッサ**をどう書くか」だけを見せる。
そのため **アプリ本体は 3 ファイルしかない**。`src/main/kotlin/com/example/` にノートを読み出して並べるだけのものがあり、
これは processor が読む対象を用意するためだけに置いてある。読む価値があるのは
`architecture-test/src/test/kotlin/com/example/processors/` の 3 本。

## 実行

```sh
./gradlew check                 # ProjectArchitectureTest と CustomProcessorSpec
./gradlew :architecture-test:runKatachiProcessor --processor=roleFileCount
```

Android SDK は要らない。JDK 17 だけで動く。

## 3 本の processor

`ArchitectureProcessor` の実装は `object` 1つで済む。**継承すべき基底クラスは無く**、
渡されるのは `ArchitectureProcessContext` ただ 1 つ。3 本はそれぞれ違う形を受け持つ。

### 1. `RoleFileCount` — 引数なし

[`processors/RoleFileCount.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleFileCount.kt)

`ArchitectureProcessorNoArg<List<String>>` を `object` で実装したいちばん単純な形。
`argsSerializer` を書かないのがこのインタフェースを使う理由のすべて。

`context.roles` は宣言を読むだけで IO を起こさない。`context.filesOf(role)` がもう半分で、
こちらはプロジェクトを歩く。**走査は最初の 1 回だけ**で、役割の数だけ呼んでも歩き直さない。

```sh
./gradlew :architecture-test:runKatachiProcessor --processor=roleFileCount
```

```
  [roleFileCount] 10 個の役割が覆うファイルを数えます

[OK] roleFileCount
core/Entrypoint: 2 件
core/Model: 2 件
...
```

<details>
<summary><code>core/Model</code> がなぜ 2 件なのか</summary>

`Model` 役割の `layout { }` は `":".module { mainSourceSet / kotlin / modulePackage / "model" / "*".ktFile() }`。
`.module { }` は**それ自体がディレクトリ + `build.gradle.kts` + `"build".ignore()`** を意味するので、
`Note.kt` に加えてルートの `build.gradle.kts` もこの役割が覆っている。

`filesOf(role)` は**重なりを含めて全部**返す。1 つのファイルを複数の役割が覆ってよい、というのが
katachi のチェックの規則そのもので、「本当の持ち主」を 1 つに決めるのは processor 側の解釈になる。
`tool/Documentation` は `.module { }` を使わず `README.md` だけを覆うので、常に 1 件。

</details>

### 2. `RoleTable` — 型付きの引数

[`processors/RoleTable.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleTable.kt)

`ArchitectureProcessor<Args, R>` を `object` で実装し、`@Serializable data class Args` と
その `Args.serializer()` を渡す。`--arg` から型どおりの値が届く。
**このモジュールが `kotlin.plugin.serialization` を適用しているのは、この `Args` 1 つのため。**
引数なしの processor しか登録しないモジュール（`sample/android` と `sample/kmp` がそれ）には要らない。

`Args` は種類の違うフィールドを 1 つずつ持っている。並べて見ないと分からない規則があるため。

| フィールド | 型 | `--arg` の書き方 | 届く値 |
|---|---|---|---|
| `title` | `String` | `--arg title=役割,一覧` | `"役割,一覧"` — **分割されない** |
| `groups` | `List<String>` | `--arg groups=core,testing` | `["core", "testing"]` |
| `minExamples` | `Int` | `--arg minExamples=2` | `2`（`x` なら実行が失敗する） |
| `sortBy` | `enum` | `--arg sortBy=Name` | `SortBy.Name`（entry 名そのまま） |

**カンマが分割されるのは、受け取る側が `List` か `Set` のときだけ。** エスケープ構文は無いので、
カンマを含む値を渡したいときは `String` のフィールドで受けるのが答えになる。

```sh
./gradlew :architecture-test:runKatachiProcessor --processor=roleTable --arg groups=core
```

### 3. `RoleDocCoverage` — 検査する processor

[`processors/RoleDocCoverage.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleDocCoverage.kt)

ドキュメントに出る役割が `summary` と `example` を持っているかを見る。**3 本のうちここがいちばん読む価値がある。**

返すのは `List<Violation>` ではなく**自前の `Report`**。見つけたのはファイルのパスの一覧ではなく
「何件見て、何が足りないか」だからで、結果の型は processor が決めてよい。

そのうえで **`isFailure(result)` を override している**。

```kotlin
override fun isFailure(result: Report): Boolean = result.missing.isNotEmpty()
```

`runKatachiProcessor` は結果の形を知らない。中身の入った `List<Violation>` と中身の入った `List<String>` は
そこからは区別がつかないので、**合否を答えられるのは processor だけ**。既定は `false`（「結果を作れば仕事は終わり」）で、
生成する processor にはそれが正しく、検査する processor には間違っている。
これを書かないと、問題を見つけたまま `[OK]` を出して exit 0 で終わる — 決して落ちない検査ができあがる。

```sh
./gradlew :architecture-test:runKatachiProcessor --processor=roleDocCoverage
```

```
[OK] roleDocCoverage
7 件すべてに summary と example がある
```

`summary` を 1 つ消すと `[FAILED] roleDocCoverage` になり、タスクが赤くなる。

<details>
<summary>どの役割を見て、どれを見ないか</summary>

読む人が出会う役割だけを見る。`documented = false` を書いた役割と、それを書いた group の中の役割は対象外
（このサンプルでは `build` と `tool` の 3 役割）。

**metadata は継承されない。** `role[Documented]` は書かれたとおりの値を返すだけなので、
group をたどって「上で false なら子も false」と決めるのは processor 側の仕事になる。
`Role.groupPath` を 1 段ずつ辿っているのはそのため。

</details>

## 登録と実行

`architecture-test/build.gradle.kts` の `katachi { processors { } }` に 3 本とも登録してある。

```kotlin
register("roleFileCount", "com.example.processors.RoleFileCount")
register("roleTable", "com.example.processors.RoleTable") {
    arg("sortBy", "Name")          // このモジュールの既定値
}
register("roleDocCoverage", "com.example.processors.RoleDocCoverage")
```

`register` の第 2 引数は**完全修飾名の文字列**。`object` でも引数なしのクラスでも同じように動く。

`arg(name, value)` は**そのモジュールが常に渡したい値**で、ロックではない。
**同じ名前の `--arg` がコマンドラインにあればそちらが勝つ。** リポジトリルートの `checkSampleCustomProcessor` は
`--arg sortBy=Declaration` を付けて回しているので、`build.gradle.kts` を書き換えずに並び順だけを変えている。

`layout` は**わざと登録していない**。`ProjectArchitectureTest` の `assert()` が同じ検査をしていて、
`check` がそのテストを回すので、登録しても同じ答えを別名でもう一度出すだけになる。
`docs` は逆に**登録が要らない**（plugin が既定で登録している）。`docs { }` ブロックは出力先を動かしているだけ。

3 本まとめて 1 回の走査で回すこともできる。

```sh
./gradlew :architecture-test:runKatachiProcessor \
  --processor=roleFileCount,roleTable,roleDocCoverage,docs \
  --arg groups=core,testing --arg sortBy=Declaration
```

`--arg` の名前は**選んだ processor 全部の引数の和集合**に対して検査される。
`groups` は `roleTable` しか読まないが、`roleFileCount` が「知らない引数だ」と言って落とすことはない。

## テストから呼ぶ

コマンドラインは 2 つある入口のうち遅いほう（JVM を起動し、レジストリを引き、コマンドラインを解釈する）。
processor を書いている最中に使うのはもう一方、`Architecture.process(...)`。
**結果が型のまま返る**ので、印字されたレポートを読み直さずにそのまま assert できる。

```kotlin
val report = projectArchitecture.process(RoleDocCoverage)
report.missing shouldBe emptyList()
RoleDocCoverage.isFailure(report) shouldBe false
```

`build.gradle.kts` への登録は `--processor=<key>` を効かせるためのもので、
この呼び方には要らない。全部は
[`CustomProcessorSpec.kt`](architecture-test/src/test/kotlin/com/example/CustomProcessorSpec.kt) にある。

## katachi の定義

`architecture-test/src/test/kotlin/com/example/` に置いてある。katachi の推奨導入形そのもので、4 サンプルとも同じ形。

- `ProjectArchitecture.kt` — `architecture { }` 本体。group の関数を呼ぶだけ
- `ProjectArchitectureTest.kt` — **利用者が書くのはこれだけ。** `projectArchitecture.assert()` を呼ぶ JUnit のテスト 1 個
- `CustomProcessorSpec.kt` — 3 本の processor を API から呼ぶ kotest の `FreeSpec`
- `LayoutSnapshotSpec.kt` — `layout { }` を平坦化した結果を `snapshots/layout.txt` に記録する番兵。
  katachi 自身の自己検証で、導入するプロジェクトには要らない
- `groups/<Name>Group.kt` / `roles/<Name>Role.kt` — 1 宣言 1 ファイル
- `processors/` — このサンプルの本題

拡張関数は **`inline` にしない**。宣言位置はスタックトレースから取るので、inline すると
呼び出し元ファイルの存在しない行を指すようになる。

役割は 10 個、group は 4 つ。`processors/` は `ArchitectureDefinition` ではなく
**`Processor` という独立した役割**が覆っている。定義は形を書くもの、processor はその形を読むもので、
混ぜると `docs/` からも `RoleFileCount` の出力からもこのサンプルの主題が消えてしまうため。

生成物も役割を持つ。`docs/`（`--processor=docs` が書く）と `snapshots/`（`LayoutSnapshotSpec` が書く）は
どちらも `build/` の外にあり、既定の `files = gitTracked()` の検査対象に入る。
役割を消すと `[UnexpectedDirectory]` で `:architecture-test:test` が落ちる。

## 生成ドキュメント

[`docs/`](docs/README.md) は `--processor=docs` がこの定義から書き出したもの。**手では書かない。**
直す場所は常に定義側で、役割や group の `title` `summary` `description` `example` がそのままページになる。

```sh
./gradlew :architecture-test:runKatachiProcessor --processor=docs               # 書き出す
./gradlew :architecture-test:runKatachiProcessor --processor=docs --arg mode=check  # 突き合わせるだけ
```

CI（リポジトリルートの `checkSampleCustomProcessor`）は後者で回している。定義を変えて生成し忘れると赤くなる。

## バージョンの制約

Kotlin / katachi / kotest はリポジトリルートの `gradle/libs.versions.toml`（`libs`）が SSoT。
このサンプル専用の `gradle/sample.versions.toml`（`sampleLibs`）には JUnit の 1 件しか無い。
`ProjectArchitectureTest` が素の JUnit テストで、kotest の runner が Jupiter の**エンジン**までは
持ってこないため（エンジンが無いと `@Test` は黙って 1 度も走らない）。

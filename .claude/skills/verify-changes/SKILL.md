---
name: verify-changes
description: >-
  Use when code under katachi/ or sample/ has changed and needs verifying — which Gradle
  tasks to run, in what order, how to read the layout snapshots, and which parts of the
  repository (app/ios, the Compose UI, the docs site) are deliberately not verified.
  katachi の :katachi / sample/ を変更したあとの検証手順。「何をテストしたいか」と
  「何はテストしなくて良いか」を分けて書いてある。
---

# 変更後の検証

このリポジトリは **4 つの独立した Gradle ビルド**（ルート + `sample/{jvm,android,kmp}`）で
できている。何を変えたかで回す範囲が変わるので、まず対応表で当たりをつけてから読む。

## どこまで回すか

| 変更した場所 | 回すもの |
| --- | --- |
| `:katachi` の内部実装だけ（リファクタ・リネーム） | `./gradlew check` → `./gradlew checkSamples` → **スナップショット無差分** |
| DSL の語彙を足した / 変えた | 上 + サンプルのどれかで**実際に書いてみる** + 空振り検出 |
| サンプルの構成（モジュール・ファイル配置）を変えた | そのサンプルの `checkSample<Name>` + **スナップショット更新して差分を読む** |
| サンプルのアーキテクチャ定義だけを書き換えた | 同上。リファクタのつもりなら無差分が受け入れ基準 |
| Kotlin / AGP のバージョン、サンプルのルート `build.gradle.kts` | 上 + `.github/scripts/check-kotlin-versions.sh` |
| `docs/`（Astro サイト）だけ | `cd docs && pnpm build`。Gradle は不要 |
| `.md` / コメントだけ | ビルド不要 |

CI（`.github/workflows/ci.yml`、`pull_request` と `main` への push で走る）が実際に回すのは
次の 5 つだけ。ローカルでこれが緑なら CI も緑になる。

```
.github/scripts/check-kotlin-versions.sh
./gradlew check
./gradlew checkSampleJvm
./gradlew checkSampleAndroid
./gradlew checkSampleKmp
```

## 何をテストしたいか

### 1. `:katachi` の単体テスト — `./gradlew check`

**何が壊れたときに落ちるか**: DSL の語彙、glob の構文解析、モジュールパスの解決、レイアウトの
平坦化、例外の型と文面。kotest（`FreeSpec`、JUnit Platform 経由）。

ルートプロジェクトは意図的に空で、ビルドに入っているのは `:katachi` ただ 1 つ。だから
`./gradlew check` と `./gradlew :katachi:check` は同じものを指す。Android SDK も
Kotlin/Native も要らないのはこの空っぽさのおかげなので、ルートにプラグインやソースを足さない。

`check` にぶら下がっているのは **compile と test だけ**。ktlint はいまも入っていない。
フォーマット違反は誰も検出しないので、自分で揃える。

**`./gradlew check` は `:katachi-konsist:test` も拾う。** ルートプロジェクトが
`:katachi` に加えて `:katachi-konsist`（Konsist バックエンド。v0.1 ステップ4 で追加）も
含んでいるため。`:katachi-konsist` のテストは実ファイルシステムを使う fixture 方式で、
一時ディレクトリは `.local/tmp/katachi-konsist-fixtures/<連番>/` に書き出して `finally` で消す
（Konsist は実ファイルのパスを要求するので、偽のファイルシステムでは動かない）。

### 2. サンプル 3 本の結合テスト — `./gradlew checkSamples`

**何が壊れたときに落ちるか**: 利用者と同じ経路。サンプルは `includeBuild("../..")` で
`me.tbsten.katachi:katachi` を差し替える**別ビルド**なので、公開 API の形・`@InternalKatachiApi`
のオプトイン壁・宣言位置のスタックトレース取得が、単体テストでは見えない形で壊れるとここで落ちる。

個別に回すなら `checkSampleJvm` / `checkSampleAndroid` / `checkSampleKmp`。
それぞれサンプル自身の wrapper を叩く `Exec` タスクで、既定で走るのは:

| サンプル | 既定のタスク | 補足 |
| --- | --- | --- |
| jvm | `check` | `:architecture-test:test` を含む |
| android | `check` | 9 モジュールの Android Lint 込みで warm 14 秒程度 |
| kmp | `:architecture-test:test` と `:app:android:testDebugUnitTest` | `check` にすると Apple ターゲットを引きずる |

絞りたいときは `-Pkatachi.sample.kmp.task=":architecture-test:test"` のようにサンプル単位で、
`-Pkatachi.sample.task=...` で全体に上書きできる。

各サンプルの `architecture-test` モジュールの中身は 2 種類に分かれていて、読み分けないと
混乱する。

- **`ProjectArchitectureTest` / `ProjectLayoutSpec`** — `projectArchitecture.assert()` 1 行。
  **利用者が書く唯一のテスト**。導入手順の最後のステップそのもの
- **`*Spec`（`ProjectArchitectureSpec` ほか）** — katachi 自身の結合テスト。利用者のプロジェクト
  には存在しないもの。ここを読んで「利用者はこんなに書くのか」と誤解しない

`sample/android` と `sample/kmp` は Android SDK が要る。`ANDROID_HOME`（または
`ANDROID_SDK_ROOT`）が通っていれば何もしなくてよい。無ければ
`sample/<name>/local.properties` に `sdk.dir` を書く（**このファイルはコミットしない**。
`.gitignore` 済み）。作った直後の 1 回だけ `--no-configuration-cache` を付ける
— 存在チェックが configuration cache の入力になっていないため。

### 3. スナップショットの差分が意図どおりか

`sample/layout-snapshots/{jvm,android,kmp}.txt` に、平坦化後のレイアウトが
`<役割の qualifiedName> TAB <パス> TAB <種別> TAB <required|optional>` 1 行ずつで記録して
ある。`LayoutSnapshotSpec` が毎回これと比較する。

**何が壊れたときに落ちるか**: 「書き換えたが意味は変えていないはず」が実は変わっていたとき。
糖衣構文（`.module { }`、`mainSourceSet`、`modulePackage`）が素のディレクトリ宣言と同じものを
言っているかは、これでしか押さえられない。

判断のしかた:

- **リファクタなら `git diff --stat sample/layout-snapshots` が空であることが受け入れ基準**。
  1 行でも動いたらリファクタではない
- **機能追加・構成変更なら差分を 1 行ずつ読む**。「増えた行が意図したパスか」「消えた行が
  消えてよいものか」。まとめて目を通して納得する、で済ませない

更新は自動ではない。サンプルのディレクトリに降りて:

```bash
cd sample/jvm && ./gradlew :architecture-test:test --rerun -Dkatachi.snapshot.update=true
```

**`--rerun` を省かない。** システムプロパティは `test` タスクの入力に配線してあるが、
他の入力が変わっていなければ Gradle はタスクごと飛ばす。更新したつもりで何も起きない。

### 4. 空振り検出 — テストが「落ちること」を確かめる

このリポジトリで実際にあった事故: `LayoutSnapshotSpec` が `Validate.kt` の配線が壊れたまま
**緑のままだった**。Spec が自前で `ModuleIndex` を組んでいて、本番の経路を通っていなかったため。
`flattenLayout()` が引数なしで呼ばれていたのに誰も気付かなかった。

通ったことは、見ていることの証明にならない。**新しいテストを足したときは必ず 1 度、実装を
わざと壊して落ちることを見る。** 壊し方は「そのテストが守っているはずの 1 行を削る」でよい。

サンプル側にはこれが常設で組み込んである。触ったら意味を確かめること。

- `sample/jvm` の `ProjectArchitectureSpec`「役割を 1 つ欠いた定義では、その役割が覆っていた
  ファイルが Unexpected になる」— 走査が本当にファイルに届いているかを見ている
- `sample/kmp` の `OmittedRoleSelfCheckSpec` — わざと不完全な定義を組んで、違反が出ることを見る

### 5. 公開 API の壁が生きているか

`:katachi` は `explicitApi()` が有効。`public` を省いた新しい宣言はコンパイルが通らない。

`@InternalKatachiApi` の壁は、`:katachi` 自身がモジュール全体で opt-in しているので**内側では
検証できない**。壁が生きている証拠は、**サンプル側に `@OptIn(InternalKatachiApi::class)` が
書いてあること**。内部 API を公開面に出してしまうと、ここが「不要な opt-in」警告に変わる。
逆にサンプルから `@OptIn` が消えたら、それは壁が緩んだサインなので疑う。

## 何はテストしなくて良いか

毎回ここで時間を溶かさないための章。**理由つきで覚える**。

- **`sample/kmp/app/ios`** — `settings.gradle.kts` に `include` されていない。Xcode プロジェクトと
  README が置いてあるだけで、Gradle のビルド対象ではない。iOS のビルドも結合テストもしない
- **KMP の Apple ターゲット全般** — `sample/kmp` で `check` / `build` / `assemble` を打たない。
  モジュールが `iosArm64` / `iosSimulatorArm64` を宣言しているので、ライフサイクルタスクが
  `compileKotlinIosArm64` と Kotlin/Native ディストリビューションのダウンロードを引きずる。
  既定の 2 タスク（`:architecture-test:test` と `:app:android:testDebugUnitTest`）は
  どちらも Apple タスクに到達しない。ここを広げない
- **サンプル UI の実行・描画** — Compose / androidx の実依存を入れてあるのは「依存が解決して
  **コンパイルが通る**」ことに意味があるのであって、画面を出すためではない。instrumented test は
  無く、エミュレータも実機も要らない。`PreviewRoot { }` も同じで、プレビューが描けることは
  検証対象ではない
- **ドキュメントサイト（`docs/`、Astro Starlight）** — `:katachi` やサンプルのコードを変えても
  触らなくてよい。`docs/` 自体を直したときだけ `cd docs && pnpm build`（pnpm。`pnpm-lock.yaml` がある）。
  Gradle ビルドからは完全に独立している
- **Gradle plugin 経由の導入** — v0.3 以降の話で、まだ存在しない。導入形は `:architecture-test`
  という素の `kotlin("jvm")` モジュール 1 つだけ
- **ルートプロジェクト自体** — 意図的に空。ソースもプラグインも無いので、検証する中身が無い
- **`check-kotlin-versions.sh`** — Kotlin / AGP のバージョンやサンプルのルート `build.gradle.kts`
  を触っていないなら回さなくてよい。3 サンプルで `buildEnvironment` を解決するので遅い

## 実行の作法

### 一時成果物は `.local/tmp/` の下

```bash
mkdir -p .local/tmp/gradle-cache
./gradlew check -q --console=plain --no-daemon \
  --project-cache-dir .local/tmp/gradle-cache/<名前> \
  > .local/tmp/$(date '+%m%d-%H%M%S')-check.log 2>&1; echo "exit=$?"
```

- リポジトリ直下やサンプル直下に `.gradle-agent-*` を撒かない（`.gitignore` にあるのは
  古い規約の後始末用の保険であって、推奨ではない）
- 出力は `.local/tmp/<時刻>-<task>.log` に落としてから読む。`grep` で雑に切って
  肝心の行を捨てない
- 複数の agent が同時に走るならキャッシュディレクトリ名を分ける

### ただし、サンプルは並列に回してはいけない

キャッシュディレクトリを分けても解決しない。**3 つのサンプルはどれも
`includeBuild("../..")` で同じ `katachi/build/` に書き込む**ので、同時に走らせると壊れる。
`includeBuild("../..")` は `:katachi-konsist` も含んでいるので、**`katachi-konsist/build/` も
同じ取り合いに加わる**（konsist { } を使わないサンプルでも、composite build 自体が
`:katachi-konsist` を configure するため対象になる）。ルートの `build.gradle.kts` がタスク間に
`mustRunAfter` を張っているのはこのため（`./gradlew check checkSamples` の同時実行も含めて
順序を保証している。`:katachi:*` 4 タスクと `:katachi-konsist:*` 4 タスクの両方に張ってある）。

subagent に分担させるときも、**サンプルを触るものは 1 体だけ**にする。

### 差分の確認

```bash
git status --short
git diff --stat sample/layout-snapshots
```

**新規ファイルは `git diff` に出ない**（untracked のため）。スナップショットを新規作成した
ときに「差分なし」と読んでしまう事故があるので、`git status --short` と必ず併用する。

`.local/` と `local.properties` はコミットしない。

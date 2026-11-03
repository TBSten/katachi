# 10. IDE プラグインの nightly 相当のチェック・E2E・配る zip・文言の日英

[SKILL.md](../SKILL.md) の手順 10 の詳細。10-1（nightly 相当の PBT）と 10-2（E2E）は、どちらも IDE プラグインが自動では
めったに回らないチェックを、リリース前に一度は必ず通す手順。

## 10-1. nightly 相当のチェック（uiTest の PBT を20倍の系列数で）

IDE プラグインの property-based tests（`uiTest`）は、8 の `checkIdePlugin`（`test` 経由）でも固定シード・少ない系列数で
走る。`.github/workflows/ide-plugin-nightly.yml` の `pbt` ジョブは、コミットのたびではなく毎晩、20 倍の系列数を毎回
違うシードで走らせている（ある回帰は 380 系列目で初めて出た）。nightly は落ちても利用者が気づきにくいので、リリース前に
一度は同じ広さで手元でも通す。

```shell
python3 .claude/skills/prerelease/scripts/run-ide-plugin-nightly.py --list                                # 走らせるコマンドを見る
python3 .claude/skills/prerelease/scripts/run-ide-plugin-nightly.py --release-dir .local/release-v<版>    # 既定 scale 20 で走らせる
python3 .claude/skills/prerelease/scripts/run-ide-plugin-nightly.py --release-dir .local/release-v<版> --scale 5   # 短く済ませたい時
```

- コマンドはスクリプトに書き写さず、毎回 `ide-plugin-nightly.yml` の `pbt` ジョブ「Run uiTest」ステップの `run:` から読む
- シードは既定で実行時刻（nightly の既定は実行 id）。`--seed` / `--scale` で上書きできる
- 落ちたら `ide-plugin-nightly.md` に、同じシードで再現するコマンドがそのまま書かれる。ログの末尾から shrink された
  系列とシードを拾い、原因を1〜2行で書く
- 既定 scale 20 は Apple silicon で約30〜35分かかる（v0.2.0 の prerelease の実測で 1965 秒）
- 前提: 8 と同じ（IDE プラグイン用の JDK 21、macOS。`verifyPreview` 同様 Skia のレンダリングが乗るため）
- 結果は `ide-plugin-nightly.md`、ログは `tmp/ide-plugin-nightly/`

## 10-2. E2E（channel D の Driver smoke。`integrationTest`）

`integrationTest`（Starter + Driver で実 IDE を起動し、sample/jvm の写しを Gradle import してからツールウィンドウで
生成する `GenerationIdeErrorsTest`・`ToolWindowSmokeTest`）は ci.yml にも ide-plugin-nightly.yml にも無く、どちらの
ワークフローでも自動では回っていない。katachi-intellij-plugin/build.gradle.kts のコメント（`Run on demand with
./gradlew integrationTest`）と ci.yml のコメント（`it stays a check to run by hand before a release`）の両方が、
「リリース前に手で回す」前提を明言している。手で回すとされたまま忘れられないよう、ここで必ず一度は通す。

```shell
python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --list                              # 走らせるコマンドを見る
python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --release-dir .local/release-v<版>  # 走らせる
```

- コマンドは `./gradlew integrationTest` の1つだけで、ci.yml の `run:` のような置き場が無い（どのワークフローにも
  出てこないため）。書き写す先は `katachi-intellij-plugin/build.gradle.kts` の
  `intellijPlatformTesting.testIdeUi.register("integrationTest")`（タスク名が変わったらスクリプトも直す）
- 前提: 8 / 10-1 と同じ JDK 21・macOS に加えて、**ディスプレイのあるセッション**が要る（実 IDE のウィンドウを Starter が
  開く。SSH だけのヘッドレス環境では動かない）。IDE 本体は 8 / 10-1 と同じ `katachi-intellij-plugin/.intellijPlatform`
  のキャッシュを使う（テストへ `intellijPlatform.platformPath` をそのまま渡すので追加ダウンロードは無い。8 / 10-1 を
  一度も走らせていない初回はその場でダウンロードが乗る）
- 所要: テスト2本（`GenerationIdeErrorsTest`・`ToolWindowSmokeTest`）、1本あたり IDE 起動込みで2分半〜3分が目安。
  sample/jvm の写しを IDE がさらに Gradle import する分、初回はもっとかかる。テスト側のタイムアウトは安全側に
  40分まで見ている（典型的な所要時間ではない）
- 実 IDE のウィンドウを開くぶん、8・10-1 以上に他の Gradle 作業や画面操作と重ねない。動かしている間は他の
  subagent にも IDE や画面を触らせない
- Starter は `<git root>/out/ide-tests` に IDE の複製・ログを置く。スクリプトは消さないので、容量が気になれば
  手で消す
- 結果は `ide-plugin-e2e.md`、ログは `tmp/ide-plugin-e2e/`

## 10-3. 配る zip（Release に添付するもの）

v0.2.0 から、IDE プラグインは実験的として katachi のリリースに同梱して配る。**プラグインの版は katachi とは別**
（ルートの `gradle/libs.versions.toml` の `katachiIntellij`。以下 `<IDE の版>`）。JetBrains Marketplace には出さず、publish.yml が
Release の published で `buildPlugin` し、`gh release upload` で `katachi-intellij-plugin-<IDE の版>.zip` を添付する。利用者は
Settings > Plugins > ⚙ > Install Plugin from Disk… で入れる。その zip の中身を、リリースの前に確かめる。

```shell
cd katachi-intellij-plugin
./gradlew --no-daemon --console=plain --project-cache-dir ../.local/tmp/gradle-cache/prerelease-zip buildPlugin
unzip -l build/distributions/katachi-intellij-plugin-<IDE の版>.zip
unzip -p build/distributions/katachi-intellij-plugin-<IDE の版>.zip 'katachi-intellij-plugin/lib/katachi-intellij-plugin-<IDE の版>.jar' > ../.local/release-v<版>/tmp/ide-plugin.jar
unzip -p ../.local/release-v<版>/tmp/ide-plugin.jar META-INF/plugin.xml
```

確かめること:

- zip のファイル名が `katachi-intellij-plugin-<IDE の版>.zip` で、publish.yml が添付しようとする名前（`zip=` の行）と一致する。
  リリースノートの IDE プラグインの badge も、この名前とこの版を指す
- `plugin.xml` の `<version>` が `<IDE の版>`（`katachiIntellij`）
- 説明文（`<description>`）の先頭が Experimental で、要る katachi の版（今回の katachi の版）と、zip で配ることが書かれている。
  `<change-notes>` の見出しが `<IDE の版>` で、中身が今回の変更になっている。**どちらも版を直書きしているので、版を上げたときに古いまま残りやすい**
- `katachiIntellij` を上げ忘れていないか（プラグインに変更があるのに前のリリースと同じ版なら警告）
- `<idea-version since-build="261">` のとおりで、`until-build` が無い（上限なしは利用者の決定）
- 手で入れて動かす確認（Install Plugin from Disk… からサンプルの写しで生成まで）は、10-2 の E2E が build のプラグインで
  同じ画面を通すので必須にはしない。zip の作り方（publish.yml・`buildPlugin` の設定）を変えた版では、手で一度入れる

食い違いは priority 8 の警告（利用者が入れたプラグインの版や説明が違う。エラーからは気づけるので 9 ではない）。
結果は `ide-plugin-zip.md` に書く。

## 10-4. 文言の日英（英語・日本語の直書きが残っていないか）

IDE プラグインの画面の文言は `messages/KatachiBundle.properties`（英語）と `KatachiBundle_ja.properties`（日本語）に置き、
IDE の言語で出し分ける（`src/shared` は bundle を読めないので `GutterStrings` などの interface 経由。ツールウィンドウは
`EnglishKatachiStrings` / `JapaneseKatachiStrings`）。鍵の突き合わせ（`KatachiBundleParityTest`）と日本語の文面の完全一致
テストは 8 の `checkIdePlugin` で走るが、**bundle を通っていない直書きはテストでは拾えない**。v0.3 まではガター・
Code Vision・quick fix 名が英語の直書きのまま残り、利用者が日本語の IDE で見て気づいた。リリース前に機械的に拾う。

```shell
python3 .claude/skills/prerelease/scripts/list-ide-plugin-texts.py --base <基準> --out .local/release-v<版>/tmp/ide-plugin-texts.md
```

Gradle を使わず数秒で終わり、ファイルを書き換えないので、どの手順とも重ねてよい（1 が通ったらすぐ始められる）。
スクリプトは候補を出すだけで判定しない。出力の A・B・C・E を subagent（sonnet）に1件ずつ判定させる（直させない）。

| 区分 | 出どころ | 判定 |
|---|---|---|
| A | `src/main/kotlin`・`src/shared/kotlin` の文字列リテラル（コメント・ログ・例外・`sdkCall(…)` の説明・`Regex(…)`・bundle 経由・開発用の `ide/debug/` は除いてある）。`data/`・`model/` 層はファイルごとに `<details>` に畳んで後ろに回す | 画面に出るか。出るなら直し忘れ。先に表の側（画面に近い層）を見る |
| E | `JapaneseKatachiStrings`（ツールウィンドウ）で値に日本語が無いメンバー | 訳し忘れか、意図した英語か |
| B | bundle の片方にしか無い鍵・ja が en と同じ値の鍵 | ja だけの鍵は英語の IDE で落ちる。同じ値は訳し忘れか、意図した英語か |
| C | plugin.xml の直書きの `text=` などと、bundle に `action.<id>.text` が無い `<action>` | アクション名・設定名が片方の言語に固定されていないか |
| D | `--base` から文言の置き場（`messages/`・`*Strings*.kt`）が変わったか | 判定は要らない。変わっていれば日本語の IDE での目視を `TODO.html` に載せる |

意図した英語として扱うもの（直し忘れにしない）:

- katachi 本体が書いた本文（定義の誤り・SARIF の違反）。katachi の CLI・Gradle の出力と同じ英語で、利用者が
  検索するときに一致する
- `data/` 層のパース失敗の理由。画面に出ないもの（例外を捨てる・`reason` を読まない）は対象外。出るものも、見出しが
  bundle で日英になっていて詳細の行だけ英語なら、意図した英語（katachi の出力を読んだ結果の詳細）
- 宣言元を探せなかった理由（`DeclarationSiteResolver`・`NoRoleDecider` などの `no test source root in …` の類）。
  ガターのメニュー（`gutter.menu.declarationNotFound`）・no-role の popup（`popup.rules.unresolved`）の `{0}` に入る。
  2026-11-02 の日英化で「探す側の英語のまま」残した（bundle の ja にもその旨のコメントがある）。同じ欄に流れる
  例外の `message` も同じ扱い
- DSL の語・型名（`capture`・`Role`・`layout`・`ktFileContent`・`Allowed` / `Prohibited` などの判定名）。コードに書く
  名前と同じ綴りで見せる
- `katachi` という名前だけの値（通知グループ名・設定名・quick fix の family）
- Gradle・IntelliJ の出力や画面に合わせる文字列（`Gradle sync finished`・`Sync All Gradle Projects`・Gradle の警告を
  読み飛ばす正規表現など）。照合に使うもので、利用者には見せない
- Search Everywhere の英語の別名（plugin.xml の `<synonym>`。日本語の IDE でも英語名で見つかるようにするためのもの）
- ファイル名・パス・識別子・`${…}` だけの組み立て。`katachi: ` のような固定の接頭辞に名前をつないだだけのもの

材料が拾わないもの（知った上で判定する）: リテラルでない英語（`it.message` など例外の文面。上の「宣言元を探せなかった
理由」と同じ扱いでよい）、`throw`・`error(…)` などの行に UI の文言を渡す書き方、plugin.xml の `<description>`・
`<change-notes>`（Marketplace・zip 向けの英語。10-3 で見る）。

判定に迷うもの（画面に出るかが呼び出し側で決まるもの）は、呼び出し元を1段たどって確かめる。それでも決まらなければ
「要確認」として残す。bundle の ja に「英語のまま」と理由のコメントがある鍵は意図した英語。

- 直し忘れ（画面に出る英語・日本語の直書き・訳し忘れの値）は priority 5（ボタン・メニュー・通知の文）か 3（区切り
  文字・記号など見た目だけの混入）。片方の言語で混ざるだけで、使えなくはならない。**ja だけの鍵は priority 9**
  （英語の IDE で鍵が無く、例外か鍵の名前がそのまま出る）
- 上の「意図した英語」のどれにも当てはまらず、画面に出るのに英語のままと決めた形跡（bundle やコードのコメント）が
  あるものは「要確認」として残し、判断が付いたらこの一覧に足す
- D で置き場が変わっていれば、「日本語の IDE で ◆ のメニュー・違反の注釈・Code Vision・生成ダイアログを開いて、
  英語が混ざっていないか・文が切れていないかを目で見る」を `TODO.html` の「操作」に載せる。テストは文面を比べるが、
  実際の IDE の幅で切れていないか・言語パックで切り替わるかは見ていない
- 結果は `ide-plugin-texts.md`（区分ごとの件数・直し忘れの一覧・意図した英語にした理由のまとめ）。材料は `tmp/ide-plugin-texts.md`

## 共通

- 10-1・10-2 は1つでも落ちたら priority 10 の警告にする（8・9-1 と同じ扱い。リリースしてはいけない状態）。10-3・10-4 は各節の priority
- prerelease-check-list.md には、10-1 は通った/落ちたとシード・scale を、10-2 は通った/落ちたを、10-3 は zip の名前と plugin.xml の版を、10-4 は区分ごとの直し忘れの件数を書く

# 10. IDE プラグインの nightly 相当のチェックと E2E

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
- 既定 scale 20 は Apple silicon で約18分かかる（ワークフロー自身のコメントより）
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

## 共通

- 1つでも落ちたら priority 10 の警告にする（8・9-1 と同じ扱い。リリースしてはいけない状態）
- prerelease-check-list.md には、10-1 は通った/落ちたとシード・scale を、10-2 は通った/落ちたを書く

# 8. CI と同等のチェック

[SKILL.md](../SKILL.md) の手順 8 の詳細。

リリースするコミットで、CI（`.github/workflows/ci.yml`）と同じチェックを手元で通す。CI が緑でも、未 push のコミットや
CI に上がっていない差分があれば、それは誰も確かめていない。

```shell
python3 .claude/skills/prerelease/scripts/run-ci-checks.py --list                                  # 走らせるコマンドを見る
python3 .claude/skills/prerelease/scripts/run-ci-checks.py --release-dir .local/release-v<版>      # 走らせる
```

- コマンドはスクリプトに書き写さず、毎回 ci.yml の `run:` から読む。走らせるのはジョブ `check` と `ide-plugin`
  （IDE プラグインの画面の golden は macOS で作ったものなので、macOS で走らせる）。ベンチマークのジョブは既定で飛ばす
  （失敗で落ちない計測。JMH も見たいときは `--with-bench`）
- 1つ落ちても最後まで走り、`.local/release-v<版>/ci-checks.md` に結果の表を、`.local/release-v<版>/tmp/ci-checks/`
  にコマンドごとのログを書く
- **オーケストレータが自分で走らせてよい**（判断の要らない作業。background で起動し、終わったら結果の表だけ読む）
- 前提: Android SDK（ルートの `local.properties` か `ANDROID_HOME`）と、IDE プラグイン用の JDK 21
- 1つでも落ちたら priority 10 の警告にする（リリースしてはいけない状態）。落ちたステップのログの末尾から原因を1〜2行で書く
- prerelease-check-list.md には、通った数 / 全体と、落ちたステップだけを書く

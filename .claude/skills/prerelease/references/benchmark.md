# 12. benchmark の棚卸し

[SKILL.md](../SKILL.md) の手順 12 の詳細。

benchmark は `check` に入らず、CI の bench ジョブ（`bench-jmh`・`bench-real-project`）は遅くなっても落ちない。8 の `run-ci-checks.py` も
bench ジョブを飛ばす。機能を足してベンチを足し忘れても、履歴の保存が止まっても、どこにも出ない（v0.4 では、v0.3.0 から 510 コミットの間に
ベンチが 1 本も増えていなかった）。リリースの前に 1 回、ここで見る。

```shell
python3 .claude/skills/prerelease/scripts/check-benchmark.py --release-dir .local/release-v<版>   # 材料を tmp/benchmark-material.md に書く
python3 .claude/skills/prerelease/scripts/check-benchmark.py --base <基準>                          # 標準出力に出すだけ。基準を指定する
```

- **オーケストレータが自分で走らせてよい**。Gradle を使わず数秒で終わるので、どの手順とも重ねてよい
- JMH は手元で走らせない。手元（Mac・クラウド）の数値は CI の runner の履歴と比べられない。数値は、CI の bench-store が main の push ごとに
  gh-pages の `dev/bench/data.js` に足している履歴から読む
- 材料は `.local/release-v<版>/tmp/benchmark-material.md`。それを読んで、結果を `.local/release-v<版>/benchmark.md` に書く（下の「結果の形」）
- 始める前に [decisions/benchmark.md](../decisions/benchmark.md) を読み、同じ判断は出し直さない

## 見ること

### 範囲: 測るべきものを測っているか

材料の表は、[`benchmark/README.md`](../../../../benchmark/README.md) の対応表（コンポーネント → 入口 → ベンチ、または測らない理由）の行ごとに、
基準からそのコンポーネントが変わったかと「確認した版」を並べたもの。

| 判定 | すること |
|---|---|
| **要判断**（未確認） | まだ人が決めていない行。ベンチを足すか・測らないかを `TODO.html` の「2. 判断してほしい」で聞く（推奨と理由を添える） |
| **要確認**（変更あり） | 基準からの変更（`git log <基準>..HEAD -- <場所>` の feat・perf のコミット）を読み、新しい入口・通り道が増えていないかを見る。増えていなければ「確認した版」を今回の版に上げる。増えていて今のベンチが通らないなら、足すか・測らないかを聞く |
| 変更なし | 「確認した版」を今回の版に上げるだけ |
| 確認済み | 何もしない |

- 「入口が増えた」とは: 新しい DSL の語・検査の経路・processor・サブコマンド・タスクが足されて、今あるベンチの定義（`benchmark/src/` の
  合成プロジェクトの定義・nowinandroid の定義・gradle-profiler のシナリオ）がそこを通らないこと。ベンチの定義を実際に読んで確かめる
- 聞くのは priority 5。人の答え（足す・測らない・後で）は、対応表の行（理由・確認した版）と decisions/benchmark.md の両方に書く
- 「確認した版」を上げる・理由を直すのは対応表の書き換えなので、修正 PR に入れる（無ければこの回の記録の PR）
- 対応表と実物の食い違い（行が無い・載っていないベンチ）は priority 8。CI の `check` も同じ検査（`.github/scripts/check-benchmark-coverage.py`）で落ちている
- 「コードに残っている benchmark 待ちの TODO」が出たら、その TODO が待っているベンチがあるかを見る。あれば結果を読んで TODO を片付けるチケットに、
  無ければ「測らないまま TODO だけ残っている」として priority 3

### 数値: 基準の版より遅くなっていないか

材料は、基準のタグ以前の直近 5 回の中央値 → HEAD までの直近 7 回の中央値。倍率は 今 ÷ 基準。

- time 1.5 倍・alloc/op 1.2 倍以上は priority 8 の警告（ci.yml の `alert-threshold` と同じ値）。スクリプトが `### [WARN …]` の形で出す
- CI の runner の速さは回ごとに 0.6〜1.35 倍ぶれる。time は「runner 補正」（katachi のコードを通らない `KonsistScopeBench.parse` の値で割ったもの）も
  閾値を超えたときだけ警告にしてある。「注意」（1.2 倍以上）は、系列ごとの表でその系列の動きを見て、段差なのかぶれなのかを 1 行書く
- 警告が出たら、どのコミットで段差ができたかを `data.js` の系列から探し（その系列の値が変わった最初の回の `commit.id`）、警告に書く
- 「新規」は基準の時点に無かった系列（比べる相手が無い）。「消えた」は、意図して消したベンチかを確かめる
- 基準の時点の計測が無い（履歴より古いタグ）ときは、比べられなかったと書く。警告にはしない
- 倍率の表（まとまりごと）は、リリースノートの材料にもなる。利用者に効く速さの変化（半分以下になった、など）があれば 4 の担当に渡す

### 鮮度: 計測そのものが動いているか

- **履歴の最後**: main の先頭より 2 日以上古ければ priority 8（bench-store が止まっている）。GitHub の Actions で main の CI の `bench-store` を見て、原因を 1〜2 行で書く
- HEAD がまだ測られていない（HEAD まで n コミット）だけなら警告にしない。main に入って CI が終われば足される
- **固定しているコミット・版**（nowinandroid のコミット・gradle-profiler・JMH）: 180 日以上そのままなら priority 3（上げるか・このままかを聞く）
- **週 1 の gradle-profiler**: 最新の実行が成功しているか。`gh` が使えない環境では材料に「未確認」と出るので、GitHub の Actions で見て書く。
  失敗が続いていれば priority 5。結果は artifact に置くだけで比べる仕組みが無いので、数値はここでは見ない

## 結果の形

`.local/release-v<版>/benchmark.md` に書く。材料をそのまま写さず、判断したことを書く。

```md
# benchmark の棚卸し

- 範囲: 見直した行 n 行（上げただけ n / 聞いた n）
- 数値: 警告 n 件 / 注意 n 件（<基準> → HEAD、n 系列）
- 鮮度: 履歴の最後 <sha>（<日付>）/ gradle-profiler <日付> <結果>

## 警告

### [WARN / priority 8/10] …

## 範囲

（行ごとに: 何が変わっていて、どう決めたか。聞いたものは TODO.html の番号）

## 数値

（まとまりごとの表。注意の系列には 1 行の読み）

## 過去の判断どおり（n 件）
```

- prerelease-check-list.md の 12 には件数だけを書く
- 人が答えたら、その回のうちに対応表と decisions/benchmark.md に書く

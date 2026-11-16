# benchmark の対応表

新しい入口（DSL の語・検査の経路・processor・CLI のサブコマンド・Gradle のタスク）を足す PR や、検査の通り道を作り変える PR では、[`benchmark/README.md`](../../benchmark/README.md) の対応表を見る。

- その入口を見ているベンチがあるか確かめる。無ければ、ベンチを足すか、表に「測らない理由」を書く（どちらにするか決めきれないときは、理由を書いて「確認した版」を `未確認` にする）
- ベンチを足した・名前を変えた・消したら、表の「ベンチ」の列も合わせる
- コンポーネントを増やしたら（`.github/labels.json` に `影響:〜` を足したら）、表にも行を足す
- 「benchmark で分かったら直す」のような TODO をコードに残さない。分かるベンチを足すか、チケットにする

表と実物の食い違い（行が無い・載っていないベンチ・理由の無い `なし`）は、CI の `check` の `.github/scripts/check-benchmark-coverage.py` が落とす。手元では `python3 .github/scripts/check-benchmark-coverage.py`。
行の中身が今のコードに合っているかは、リリース前チェック（prerelease の手順 12）で見直す。

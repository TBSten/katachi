# benchmark

benchmark の棚卸し（手順 12）で人が下した判断。何を測らないと決めたか、固定しているコミット・版をこのままにするか、など。形は [README.md](README.md)。

測らないと決めたコンポーネントは、ここに書いたうえで [`benchmark/README.md`](../../../../benchmark/README.md) の対応表の行（測らない理由・確認した版）にも書く。
`file` は `benchmark/README.md`、`quote` はその行の測らない理由の一部にする（行が書き換わったら見直しの候補になる）。

## このまま: core/katachiDiff はベンチを足さない（測らない）
- decision: このまま
- file: benchmark/README.md
- quote: 1 PR に 1 回の実行で、時間の大半は git の呼び出し
- decided: 2026-11-20 261119-01 KATACHI-391
- by: 利用者（チャット、TODO.html の問いへの答え）

手順 12 の p5。対応表の core/katachiDiff の行は KATACHI-379 で「次の prerelease で聞く」とした未確認の行で、測らない理由は KATACHI-380 の提案のままだった。
1 PR に 1 回の実行で、時間の大半は git の呼び出し。role への割り当ては `WalkBench`・`RoleMatchBench` が見ている。
利用者の一言:「計らないで確定」。新しい通り道（git の呼び出しの仕方・role への割り当てとは別の索引）が増えたら見直す。

## このまま: CLI・script はベンチを足さない（測らない）
- decision: このまま
- file: benchmark/README.md
- quote: 起動は `kotlin` コマンドのスクリプトのコンパイルと依存の解決が主で、JMH には載らない
- decided: 2026-11-20 261119-01 KATACHI-391
- by: 利用者（チャット、TODO.html の問いへの答え）

手順 12 の p5。対応表の CLI・script の行は KATACHI-379 で「次の prerelease で聞く」とした未確認の行で、測らない理由は KATACHI-380 の提案のままだった。
利用者の一言:「計らないで確定」。

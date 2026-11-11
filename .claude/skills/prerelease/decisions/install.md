# install

インストールキット・katachi コマンドの振る舞いへの判断（手順 9）。形は [README.md](README.md)。

## このまま: gh skill install が非対話だと Claude Code が読まない場所に入る
- decision: このまま
- file: docs/public/install/*/index.md
- quote: gh skill install TBSten/katachi katachi
- rule: 手順 9-2 の手順書のレビュー（非対話の既定は scope=project・agent=github-copilot）
- decided: 2026-11-11 261110-01 KATACHI-354
- by: 利用者（チャット）

手順 9-2 の p6。AI エージェントが `gh skill install` を非対話で走らせると、`.agents/skills/` に入って Claude Code が読まない。
利用者「そもそも gh skill install は人間にやってもらう想定をしてる」。人が対話で agent と scope を選ぶ前提なので、
`--agent` などの指定は足さない。

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

## このまま: .claude/・.github/・.run/ は中身を検査しなければ ignore() でよいと勧める
- decision: このまま
- file: skills/katachi/references/setup/step3/3-2-write-the-definition.md
- quote: if you do not check their contents, `ignore()` in each is fine
- rule: 手順 9-2 の手順書のレビュー（ツールの設定ディレクトリを Role にせず ignore() で済ませてよいか）
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（v0.3.0 のチェックで「そのまま」。Mac の memory にしか無かったものを写した）

AI エージェントや CI の設定ディレクトリ（`.claude/`・`.github/`・`.run/`）は、ツールごとに Role を分けたうえで、
中身を検査しないなら layout を `ignore()` にしてよい。v0.3.0 のチェックで利用者が「そのまま」と決めた。
2 回目（261110-01）のレビュー担当が、decisions/ に無いので次回また聞かれると指摘したため写した。

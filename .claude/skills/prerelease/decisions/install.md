# install

インストールキット・katachi コマンドの振る舞いへの判断（手順 9）。形は [README.md](README.md)。

## 後で: 6-0 で直した違反が summary に残り続ける
- decision: 後で
- revisit: install script と katachi CLI の agent 周りを刷新したあと（KATACHI-318・329 / PR #65 のマージ後）。刷新後のキットで同じことが起きるなら見直し候補にする
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（TODO.html、01:51 JST）

手順 9-2 の p5。導入の途中（6-0）で直した違反が、あとで出す summary に残ったままになる。agent 周りを作り直す予定なので、今の形では直さない。

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

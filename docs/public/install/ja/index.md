# katachi のインストール

AI エージェントが [katachi](https://tbsten.github.io/katachi) を Gradle プロジェクトに導入する手順です。手順そのものは **katachi skill** にあり、セクションごとに 1 ファイルです。このページではなく、そちらから読んでください。

1. **katachi skill を入れる。** 次のどちらかの実行をユーザに頼みます（ユーザのエージェントの skill に追加される）。ユーザが同意すれば自分で実行してかまいません。

   ```sh
   gh skill install TBSten/katachi katachi
   npx skills add TBSten/katachi --skill katachi
   ```

2. **skill に従う。** `SKILL.md` が、初めての導入を `references/setup/README.md` に案内します。そこにセクションが順に並んでいます。1 セクションずつ読んでください。

手順は `katachi` コマンド（`brew install tbsten/tap/katachi`）で進めます。入っているかは setup の最初のセクションで確かめます。

**skill を使えないエージェントでは**、同じファイルをリポジトリから読みます。入口は https://raw.githubusercontent.com/TBSten/katachi/main/skills/katachi/SKILL.md です。中のリンクは `skills/katachi/` からの相対パスです。手順は英語ですが、手順が作るチェックリストとレポートは日本語にできます（手順 0-2）。

導入のあとの定義の追加・更新、katachi の更新、違反の直し方も、同じ skill にあります。

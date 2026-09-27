# 4. リリースノート作成

[SKILL.md](../SKILL.md) の手順 4 の詳細。

- 本バージョンではいるユーザ向けの影響を git, PR の履歴などから調査し、列挙する。
    - 材料はスクリプトで出す。直前のタグからのコミットを、BREAKING CHANGE（件名の `!` と本文の `BREAKING CHANGE:`。本文の説明つき）・
      Conventional Commits の type ごと・scope ごとの件数に並べ、未コミットの変更の件数も出す。
      材料は作業場所の `tmp/` に書く（人が読む成果物ではない）。

      ```shell
      mkdir -p .local/release-v<版>/tmp
      python3 .claude/skills/prerelease/scripts/release-note-material.py >.local/release-v<版>/tmp/release-note-material.txt   # --files でコミットごとのファイルも
      ```

    - どれが利用者に効くか、どうまとめるかは判断する（`refactor!` でも利用者に見えないもの、`docs` でも利用者に効くものがある）。
      未コミットの変更があれば、リリースに入らないことを警告する。
- .local/release-v0.0.0/release-note.md (0.0.0 の部分は該当リリースバージョン) にリリースノートを作成する。
    - ユーザ影響を中心に作成する。
    - 重要でない変更は Also セクションに簡略的に列挙するに留める。
    - **機能の詳しい説明はドキュメントに任せる。**新機能は1〜3行で「何ができるようになったか」だけを書き、
      「詳しくは: [ページ名](https://tbsten.github.io/katachi/ja/...)」とドキュメントのページへリンクする。
      使い方・オプションの一覧・コード例をノートに書き写さない（ドキュメントと2箇所になり、片方だけ古くなる）。
    - ドキュメントに載らない**移行方法**（破壊的変更の書き換え方）だけは、ノートに具体的に書く。

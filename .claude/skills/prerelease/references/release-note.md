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

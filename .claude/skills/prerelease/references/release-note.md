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
    - **英語で書く。**GitHub Releases にそのまま貼るもので、読み手は日本語を読むとは限らない。
      リンクは英語のドキュメント（`https://tbsten.github.io/katachi/...`。`/ja/` を付けない）へ張る。
      日本語で下書きした場合は `tmp/release-note.ja.md` に置く（人が読む成果物は英語の1本だけにする）。
      手順 7 の報告のリリースノート概要は、これまでどおり日本語で書く
    - **タイトルの直下に badge を並べる。**版はそのリリースの版で固定する（`img.shields.io/badge/...` の静的な badge。
      `maven-central/v/...` は最新版を指すので、古いリリースのノートでも新しい版が出てしまう）。並べるもの:
      katachi（Central の artifact の版のページへ）・Gradle プラグイン（plugin marker の版のページへ）・
      IDE プラグイン（experimental。版は catalog の `katachiIntellij` で katachi と別。Release に添付する `katachi-intellij-plugin-<その版>.zip` へ）・Docs・
      動作に要る JDK / Kotlin / Gradle の下限（README の「対応」の行と同じ値）。
      「利用できるもの」（katachi・Gradle プラグイン・IDE プラグイン・Docs）と「利用できる環境」（JDK・Kotlin・Gradle）の
      2段に分け、間に空行を1つ置く（空行が無いと1行に続けて並ぶ）
    - **節の中身は `<details>` で畳む。**最初に見えるのは冒頭の説明と、新機能の名前を1行に並べたものだけにする。
      新機能の詳細・移行表・Also は、それぞれ `<details><summary>… (n)</summary>` に入れる。
      GitHub の Markdown が中を描画するように、`<summary>` の行の後と `</details>` の前に空行を置く
    - ユーザ影響を中心に作成する。
    - 重要でない変更は Also セクションに簡略的に列挙するに留める。
    - **機能の詳しい説明はドキュメントに任せる。**新機能は1〜3行で「何ができるようになったか」だけを書き、
      「See: [page title](https://tbsten.github.io/katachi/...)」とドキュメントのページへリンクする。
      使い方・オプションの一覧・コード例をノートに書き写さない（ドキュメントと2箇所になり、片方だけ古くなる）。
    - ドキュメントに載らない**移行方法**（破壊的変更の書き換え方）だけは、ノートに具体的に書く。

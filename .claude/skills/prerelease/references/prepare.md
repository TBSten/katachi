# 1. 前提チェック

[SKILL.md](../SKILL.md) の手順 1 の詳細。

スクリプトは `.claude/skills/prerelease/scripts/` にある。リポジトリの直下で、python3（標準ライブラリだけ）で実行する。

```shell
python3 .claude/skills/prerelease/scripts/prepare.py
```

- 今回の版（`gradle/libs.versions.toml` の `katachi`）と、公開済みの直前の版（git の `vX.Y.Z` タグと Maven Central の
  maven-metadata.xml。Maven Central に届かなければタグだけ）を出す。
- 両者が同じ（または Maven Central に公開済み）なら `STOP:` を出して終了コード 1 で止まり、作業場所は作らない。
  作業を中断し、ユーザに本当にこのバージョンで publish するのか尋ねる。
    - バージョンの更新忘れである可能性が高い。
    - ユーザが「この版で進める」と言ったら `--force` を付けて作り直す。
- 止まらなければ `.local/release-v<版>/` を作り、チェックリストを写して 1 の版の欄と、作業場所の中のファイルのパスの欄（3〜6・8・9）を埋める。
  出力の `WARN:`（タグと Maven Central の食い違い）は memo に書く。
- 以降の手順の `<基準>` は、出力の「基準（直前のタグ）」。
- 出力の最後の「過去の判断」は [decisions/](../decisions/README.md) の件数・問題・見直す条件。チェックリストの 1 の欄は
  新しく作ったときに埋まる。問題（`GONE` = ファイルが無い、`LOST` = `quote` が見つからない）と `REVISIT` は memo に写し、
  各手順で見直す（`GONE` で判断の対象ごと消えたなら、その判断は消してよい）。

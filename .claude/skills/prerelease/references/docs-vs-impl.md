# 5. 実際の実装・挙動 と ドキュメント に不整合がないかチェック

[SKILL.md](../SKILL.md) の手順 5 の詳細。

- check-docs-against-impl skill を実行する。
    - 基準には直前のリリースのタグを渡す（その SKILL の `list-api-changes.py <基準>` が材料を出す）。
    - 結果は `.local/release-v0.0.0/docs-vs-impl.md` にまとめさせる。材料・下書き・サンプルを動かしたログは
      `.local/release-v0.0.0/tmp/docs-vs-impl/` に置かせる。
    - サンプルを動かす（Gradle を使う）作業は、8・9・6-1 と重ねない。走らせる前に `pgrep -fl GradleWrapperMain` で確かめさせる。
- prerelease-check-list.md には、件数と priority 7 以上の警告だけを書く。


## [p4] diff-report の JSON のキーの表に、PR #123 で足す baselined・noRoleDeleted が無く、noRole の説明も広すぎる
- file: ja/guides/diff-report.mdx
- line: 324
- quote: | `noRole` | どの Role にも該当しない変更ファイル |
- impact: あり: PR #123（KATACHI-419）のマージ後、JSON を読むスクリプトを書く人が、消したファイルと baseline で許しているファイルは `noRole` に入らず別のキーにあることを知らずに、数を取り違える
- recommend: 直してもらう 8, 直さない 2

- 該当: PR #123 で `noRole` は「アーキテクチャテストが違反にするファイル」だけになる。どの Role にも該当しないファイルのうち、baseline で許しているものは `baselined`、この変更で消したものは `noRoleDeleted` に分かれ、summary に `baselinedFileCount`・`noRoleDeletedFileCount` が増える
- 直し方の案: この行を「どの Role にも該当せず、アーキテクチャテストが違反にする変更ファイル」にし、「| `baselined` | どの Role にも該当しないが、baseline で許している変更ファイル |」「| `noRoleDeleted` | どの Role にも該当しないファイルのうち、この変更で消したもの |」の 2 行を足す。en（guides/diff-report.mdx の 324〜325 行目）も
- 出どころ: KATACHI-419 の直し（PR #123）。マージ前は今の説明のままで合っている

## [p3] files-in-no-role.md の説明が、PR #123 の後は消したファイルと baseline で許しているファイルも載るように読める
- file: ja/guides/diff-report.mdx
- line: 40
- quote: どの Role にも当てはまらない変更ファイルの一覧。アーキテクチャテストでも違反になるファイルです
- impact: あり: PR #123 のマージ後、消したファイルしか no role に無い変更では files-in-no-role.md が書かれない。説明を読んだ人が出力の漏れと思う
- recommend: 直してもらう 7, 直さない 3

- 該当: PR #123 の後、files-in-no-role.md に載るのはアーキテクチャテストが違反にするファイルだけ。消したファイルと baseline で許しているファイルは、要約（diff-report.md・HTML）の畳んだ段にだけ出る。194 行目の「どの Role にも当てはまらないファイルがあるときは、diff-report.files-in-no-role.md も同じディレクトリに書きます」も同じ（消したファイルだけなら書かれない）
- 直し方の案: 40 行目を「アーキテクチャテストで違反になる、どの Role にも当てはまらない変更ファイルの一覧（消したファイルと baseline で許しているファイルは要約にだけ出ます）」、194 行目を「アーキテクチャテストで違反になる、どの Role にも当てはまらないファイルがあるときは」に。en の 40・194 行目も
- 出どころ: KATACHI-419 の直し（PR #123）。マージ前は今の説明のままで合っている

## [p3] CI の例の jq のコメントが、PR #123 の後の noRoleFileCount の意味より広い
- file: ja/guides/diff-report.mdx
- line: 311
- quote: # どの Role にも該当しないファイルがあれば失敗させる
- impact: なし: jq の式（`.summary.noRoleFileCount == 0`）はそのまま動き、アーキテクチャテストと同じ結果になる。コメントだけが広い
- recommend: 直してもらう 6, 直さない 4

- 該当: PR #123 の後、`noRoleFileCount` は消したファイルと baseline で許しているファイルを数えない
- 直し方の案: 「# アーキテクチャテストが違反にするファイル（どの Role にも該当しないもの。消したファイルと baseline で許しているファイルは除く）があれば失敗させる」。en の 311 行目（`# Fail if any file matches no Role`）も
- 出どころ: KATACHI-419 の直し（PR #123）。マージ前は今の説明のままで合っている

## [p4] シンボリックリンクにしたディレクトリが 1 つのファイルとして扱われることが書かれない
- file: ja/guides/layout.mdx
- line: 148
- quote: どちらでも `.git/` だけは対象になりません。
- impact: あり: 共有のコードをシンボリックリンクで入れたディレクトリ（`src/main/kotlin/com/example/service` が `../../shared/service` を指すなど）は、katachi がリンクを辿らないので 1 つのファイルとして扱われ、`[UnexpectedFile] … No role is defined for this file.` になる。ディレクトリなのに「ファイル」と言われ、How to fix の案も `"service".file()` なので、何が起きているか分からない
- recommend: 直してもらう 6, 直さない 4

- 該当: 検査の対象になるファイルの説明（折りたたみの中）に、シンボリックリンクの扱いが無い。リンクを辿らないのは意図どおり（循環やプロジェクトの外の取り込みを避ける）で、困るのは書かれていないことと違反の文面
- 直し方の案: この段落の後に 1 文足す。「シンボリックリンクは辿りません。ディレクトリを指すリンクも 1 つのファイルとして扱うので、`"service".file()` のようにファイルとして宣言します。リンク先のファイルは、実体のある場所で検査されます。」en（guides/layout.mdx の同じ段落）: "Symbolic links are not followed. A link to a directory is treated as a single file, so declare it as a file, like `"service".file()`. The files it points to are checked where they actually are."
- 出どころ: Mac の探索（macOS でしか起きないこと、担当 a。Linux でも同じ）。違反の文面のほうは p6 以下のため件数だけ（exploratory-mac-only.md）

## [p3] doctor の出力例の Kotlin の行に、バージョンを読んだファイルが無い（PR #132 のマージ後）
- file: ja/guides/cli-and-script/cli.mdx
- line: 45
- quote: Kotlin: 2.4.10
- impact: なし: 出力例が実際の出力と少し違うだけで、読み手がすることは変わらない
- recommend: 直してもらう 6, 直さない 4

- 該当: PR #132（KATACHI-431）のマージ後、doctor の Kotlin の行の後ろに、バージョンを読んだファイルが出る
- 直し方の案: 出力例の行を `     Kotlin: 2.4.10（file:///Users/you/demo-app/gradle/libs.versions.toml から）` にする。en（guides/cli-and-script/cli.mdx の 45 行目）: `     Kotlin: 2.4.10 (from file:///Users/you/demo-app/gradle/libs.versions.toml )`
- マージ前は今の出力例で合っているので、直すのは PR #132 のマージ後
- 出どころ: 探索的検証 2 周目 実在の OSS（KATACHI-431 / PR #132）

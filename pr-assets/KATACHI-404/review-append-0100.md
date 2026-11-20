
## [p4] --module-dir の説明に、PR #124 の後は lint・agent test・agent compare-violations にも付けることが書かれない
- file: ja/guides/cli-and-script/cli.mdx
- line: 166
- quote: 検査用モジュールを置く場所。Gradle のルートからの相対パス
- impact: あり: PR #124（KATACHI-420・KATACHI-413）のマージ後、scaffold --module-dir で既定以外に置いた人がチェックリスト無しで lint・agent test を実行すると「定義のディレクトリがありません」で止まり、同じ --module-dir を付ければよいことにページからたどり着けない
- recommend: 直してもらう 8, 直さない 2

- 該当: PR #124 で lint・agent test・agent compare-violations が --module-dir を受ける（優先は --module-dir > チェックリストの meta.moduleDir > architecture-test）。agent test では `--` より前に書く。このページで --module-dir が出てくるのは scaffold のオプションの表だけ
- 直し方の案: この説明の末尾に「既定以外に置いたら、katachi lint・katachi agent test・katachi agent compare-violations にも同じ --module-dir を付ける（チェックリストに meta.moduleDir があれば不要）」を足す。lint の節が無い件（同じページの別の指摘）を直すときに、lint の --module-dir も書く。en の同じ行も
- 出どころ: KATACHI-420 の直し（PR #124）。マージ前は今の説明のままで合っている

## [p4] 「検査の対象になるファイル」に、git が答えられないときに止まることが書かれない（PR #125 のマージ後）
- file: ja/guides/layout.mdx
- line: 140
- quote: Layout API と照らし合わせるファイルは、`architecture { }` の `files` で決まります。デフォルトは `gitTracked()` です。
- impact: あり: PR #125（KATACHI-422）のマージ後、ルートかその上に .git があるのに git が答えられない環境（dubious ownership・PATH に git が無い・gitdir の切れた worktree）では、検査が KatachiGitUnavailableException で止まる。ページには gitTracked() の中身しか無く、止まる条件と wholeTree() に切り替える逃げ道にページからたどり着けない
- recommend: 直してもらう 8, 直さない 2

- 該当: 「`gitTracked()` と `wholeTree()` の違い」の details に、git が使えないときの動きが無い。PR #125 の前は、黙って全ファイル（wholeTree() と同じ）に切り替わっていた
- 直し方の案: details の `gitTracked()` の項目の後に 1 文足す。「プロジェクトルートかその上に `.git` があるのに git が答えられないとき（dubious ownership・PATH に git が無い・worktree の gitdir が切れている）は、全ファイルに切り替えずに `KatachiGitUnavailableException` で止まります。その環境で全ファイルを見るなら `files = wholeTree()` にします。`.git` がどこにも無ければ全ファイルを見ます。」en（guides/layout.mdx の同じ details）にも訳す
- マージ前は今の説明で合っているので、直すのは PR #125 のマージ後
- 出どころ: 探索的検証 2 周目 core（KATACHI-422 / PR #125）

## [p3] `ignore()` の節に、ignore したディレクトリの下に Role の layout を書くと警告になることが書かれない（PR #125 のマージ後）
- file: ja/guides/layout.mdx
- line: 97
- quote: そのディレクトリより下を、どの深さでも検査しなくなります。たとえばビルド出力のディレクトリに使います。
- impact: あり: PR #125（KATACHI-422）のマージ後、ignore() したディレクトリの下に別の Role の layout を書くと `[IgnoredLayout]` の警告が出る（その Role のファイルは検査されない）。ページからは警告の意味にたどり着けない
- recommend: 直してもらう 7, 直さない 3

- 該当: 「`ignore()` — 以下すべての検査をやめる」は、ignore() の中を検査しないことだけを書いている。PR #125 の前は、ignore() の下に書いた Role の検査が黙って止まっていた
- 直し方の案: `"build" { ignore() }` の段落の後に 1 文足す。「`ignore()` したディレクトリの下に別の Role の layout を書くと、その Role のファイルは検査されないので `[IgnoredLayout]` の警告が出ます。その Role を検査したいなら `ignore()` の範囲を狭め、要らないならその Role から layout の行を消します。」en も同じ位置に訳す
- マージ前は今の説明で合っているので、直すのは PR #125 のマージ後
- 出どころ: 探索的検証 2 周目 core（KATACHI-422 / PR #125）

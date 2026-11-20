## [p4] 識別子の大文字小文字だけを変えたとき、Git でも名前を変える必要があることが書かれない（PR #130 のマージ後）
- file: ja/guides/document-generation.mdx
- line: 205
- quote: ファイル名とディレクトリ名は、`title` ではなく**識別子**（`"UseCase".role { }` の `UseCase`、`"Domain".group { }` の `Domain`）で決まります。
- impact: あり: PR #130（KATACHI-428）のマージ後、macOS・Windows で Role・group の識別子の大文字小文字だけを変えると、生成したファイルは新しい綴りになる。ところが Git は既定（core.ignorecase=true）で大文字小文字だけの名前の変更を記録しないので、コミットした repo は古い綴りのまま残り、Linux の CI の mode=check が食い違いを出す
- recommend: 直してもらう 6, 直さない 4

- 該当: 識別子でファイル名が決まることは書いてあるが、大文字小文字だけを変えたときの Git の扱いが無い。PR #130 の前は、macOS ではファイル名が古い綴りのまま残っていた（mode=check も通っていた）
- 直し方の案: この段落の後に 1 文足す。「識別子の大文字小文字だけを変えたとき（Api から API へ、など）も、ファイル名とディレクトリ名は新しい綴りで書き直されます。macOS と Windows の Git は、既定では大文字小文字だけの名前の変更を記録しません。`git mv Api.md API.md` のように Git の上でも名前を変えてからコミットしてください。」en（guides/document-generation.mdx の同じ段落）: "Changing only the letter case of an identifier (from Api to API, for example) also rewrites the file and directory names in the new spelling. Git on macOS and Windows does not record a rename that changes only letter case by default, so rename the file in Git as well, with `git mv Api.md API.md`, before you commit."
- マージ前は macOS でファイル名が書き直されないので、直すのは PR #130 のマージ後
- 出どころ: Codex 1 回目（KATACHI-428 / PR #130）

## [p4] baseline のページに、konsist { } と konture { } の違反も宣言ごと・制約の名前ごとに記録されることが書かれない（PR #134 のマージ後）
- file: ja/guides/baseline.mdx
- line: 95
- quote: の違反は、宣言ごとに記録されます。
- impact: あり: PR #134（KATACHI-427）のマージ後、konsist { } / konture { } の baseline は外側のクラスで修飾した宣言名（`User.id`）と制約の名前（名前の無いブロックは `konsist`・`konture`）で記録され、1 つの Role に同じ名前の制約は書けなくなる。baseline の JSON を読む人や、名前の無いブロックを 2 つ書いていて定義が通らなくなった人が、このページから理由を知れない
- recommend: 直してもらう 7, 直さない 3

- 該当: 宣言ごとに記録する説明が ktFileContent { } だけで、konsist { } / konture { }、クラスの中の宣言の名前、制約の名前の扱いが無い
- 直し方の案: この段落を次にする。「[`ktFileContent { }`](/katachi/ja/guides/kt-file-content/)・`konsist { }`・`konture { }` の違反は、宣言ごとに記録されます。たとえば `GetUserUseCaseImpl`・`HomeViewModel.isLoading`・`import android.content.Context`・`package com.example.home` のような単位で、クラスの中の宣言にはパッケージを除いて外側のクラスの名前を付けます（`User.id`・`HomeScreen.State`）。制約の名前でも分けて記録し、名前を付けずに書いたブロックは `ktFileContent`・`konsist`・`konture` という名前になるので、1 つの Role に同じ名前の制約は 1 つしか書けません。行番号は記録しないので、…（以下は今のまま）」en（guides/baseline.mdx の同じ段落）: "Violations of [`ktFileContent { }`](/katachi/guides/kt-file-content/), `konsist { }` and `konture { }` are recorded per declaration. Examples of the unit are `GetUserUseCaseImpl`, `HomeViewModel.isLoading`, `import android.content.Context`, or `package com.example.home`; a declaration inside a class is named after the classes around it, without the package (`User.id`, `HomeScreen.State`). They are also recorded under the constraint's name, and a block written without a name is named `ktFileContent`, `konsist` or `konture`, so a Role holds one constraint of each name. Line numbers are not recorded, …"
- マージ前は konsist { } / konture { } の宣言名が修飾されないので、直すのは PR #134 のマージ後
- 出どころ: 探索的検証 2 周目 konsist/konture（KATACHI-427 / PR #134）

## [p4] OS が作るファイル（.DS_Store など）は Role を書かずに .gitignore に足す、ということが書かれない
- file: ja/guides/layout.mdx
- line: 150
- quote: `.idea/` の中に追跡しているファイルがあれば、それも宣言します。
- impact: あり: `.gitignore` に `.DS_Store` の無い Mac では、Finder が作る `.DS_Store` が `[UnexpectedFile]` になる。Role を書くと Mac では通るが、`.DS_Store` の無い CI のチェックアウトで `[MissingFile]` になる。PR #136（KATACHI-436）で違反の How to fix は `.gitignore` を勧めるようになるが、このページを読んで Role を書く人には届かない
- recommend: 直してもらう 7, 直さない 3

- 該当: `gitTracked()` と `.gitignore` の関係を説明する段落に、OS が勝手に作るファイルの扱いが無い
- 直し方の案: この段落の後に足す。「OS が勝手に作るファイル（Finder の `.DS_Store`、Windows の `Thumbs.db`・`desktop.ini`）は、Role を書かずに `.gitignore`（または git の global の excludes）に足すのがおすすめです。ignore していないと、未追跡のファイルとして検査の対象になり、`[UnexpectedFile]` になります。Role を書くと、そのファイルが無い CI のチェックアウトで `[MissingFile]` になります。`wholeTree()` は `.gitignore` を読まないので、`.gitignore` に足しても検査の対象になります。」en（guides/layout.mdx の同じ段落の後）: "We recommend adding files the OS writes on its own (Finder's `.DS_Store`, Windows' `Thumbs.db` and `desktop.ini`) to `.gitignore` (or git's global excludes) instead of giving them a Role. If they are not ignored, they are untracked files like any other and become `[UnexpectedFile]`. With a Role, a checkout without them, such as on CI, gets `[MissingFile]`. `wholeTree()` does not read `.gitignore`, so it checks them even when they are listed there."
- 出どころ: Mac の探索（macOS でしか起きないこと、担当 b の p7。KATACHI-436 / PR #136）

## [p3] doctor が Gradle の JDK をどこから探すかが書かれない（PR #137 のマージ後）
- file: ja/guides/cli-and-script/cli.mdx
- line: 52
- quote: Gradle が利用する JDK のバージョン
- impact: なし: PR #137（KATACHI-434）のマージ後、doctor はシェルの JAVA_HOME、無ければ PATH の java（macOS では `/usr/libexec/java_home` が選ぶ JDK）を見る。どこで見つけたかは doctor の出力の行に出るので、ページに無くても利用者がすることは変わらない
- recommend: 直してもらう 4, 直さない 6

- 該当: チェックする項目の 1 行目に、Gradle の JDK を何から決めるかが無い。Homebrew の katachi は openjdk で動くので、「katachi が動く JDK」と「Gradle の JDK」が違いうる
- 直し方の案: この行を「Gradle が利用する JDK のバージョン（`gradle.properties` の `org.gradle.java.home`、シェルの JAVA_HOME、PATH の java の順に見ます。macOS では `/usr/libexec/java_home` が選ぶ JDK）」にする。en（guides/cli-and-script/cli.mdx の同じ行）: "The version of the JDK Gradle uses (from `org.gradle.java.home` in `gradle.properties`, then `JAVA_HOME` in your shell, then `java` on `PATH`; on macOS, the JDK `/usr/libexec/java_home` picks)"
- docs の決まり「利用者が最初に知らなくてよい細部は書かない」「エラーメッセージの読み方はドキュメント化しない」に寄せるなら、直さないでよい
- 出どころ: Mac の探索（macOS でしか起きないこと、担当 b の p7。KATACHI-434 / PR #137）

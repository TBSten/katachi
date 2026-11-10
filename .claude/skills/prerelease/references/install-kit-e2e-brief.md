# インストールキットの統合テスト — subagent に渡す共通の前提

[install-kit.md](install-kit.md) の「9-2. 統合テスト」で、各 subagent にこのファイルを読ませる。`<配信元>` は 6-1 で配信している URL（例: `http://127.0.0.1:4394/katachi`）に、
`<katachi>` は 9-1 の `build-katachi-cli.sh` が組んだ katachi コマンドの絶対パス（`<repo>/katachi-script/build/cli/katachi-cli-<版>/bin/katachi`）に置き換えて渡す。

目的: katachi の導入手順書（katachi skill の skills/katachi/references/setup/。英語だけ）と katachi コマンド（`katachi agent …` / `katachi scaffold` など）を、
**実際の AI エージェントが実在のプロジェクトに導入する**のと同じように、最初から最後まで通して確かめる。
コマンドが動くかだけでなく、**手順書が適切か**（読んで迷わないか、順番、抜け、曖昧さ、冗長さ、
コマンドの出力やチェックリストと食い違わないか、エージェントが誤解しやすい箇所）を評価する。

## katachi コマンド（リリース前なので brew の版ではなく今回の版）
- 手順書の `katachi` は `<katachi>` に読み替える。呼ぶたびに `PATH=<katachi のディレクトリ>:$PATH` を前置するか、絶対パスで呼ぶ
  （シェルの変数は呼び出しをまたいで残らない）。0-1 の `katachi --version` が今回の版を出すことを確かめる。
  brew で入れた別の版が動いていたら、そこまでの結果を捨てて `<katachi>` でやり直し、逸脱として記録する
- 0-1 の「`katachi` が見つからなければ `brew install tbsten/tap/katachi`」は、リリース前なので飛ばす（手順書の問題には数えない）。
  `kotlin` が無いと `<katachi>` は動かない。そのときは止まってオーケストレータに伝える

## 配信元（リリース前なので本番ではなくローカル）
- 手順書とテンプレートは katachi skill（`<repo>/skills/katachi/`）にある。手順書が指すドキュメントのページ
  （`https://tbsten.github.io/katachi/...`）は、リリース前なのでビルド済みのサイト（6-1 で配信している URL か、`file://<repo>/docs/dist`）に読み替える。
  localhost への curl がサンドボックスで空になるときは、サンドボックスを外して実行してよい（このホストに対してだけ）。
  本番のページを読んでしまったら、それは所見（priority 6 以上）として書く。
- **4321 番は利用者の astro dev なので使わない。**
- 今回の版の katachi は Maven Central に未公開。9-1 の `build-katachi-cli.sh` が mavenLocal に publish している。
  `katachi scaffold` に `KATACHI_MAVEN_LOCAL=1` を付けると、scaffold が mavenLocal() を足す。
  これと、依存の解決のために mavenLocal() を足すのは「リリース前だから要る逸脱」として許す（手順書の問題には数えない。ただし足した場所と方法を記録する）。
- Android のモジュールがあるプロジェクトは local.properties（sdk.dir）が要る。環境の都合なので問題に数えない
  （手順書が触れていないなら、それは所見として書いてよい）。

## やってはいけないこと
- 本物の手順書（docs/）と katachi コマンドのコード（katachi-script/）を直さない。見つけたものは報告する。
- .local/sample-app/ の原本に触らない（写しを使う）。
- katachi リポジトリで git の add/commit/push/stash/checkout/restore/clean をしない。写しの中の git は自由。
- Gradle は `--no-daemon`（手順書が別の指定をするならそれに従い、その旨を記録）。

## 記録
- 進み具合を自分の作業ディレクトリの progress.md に、手順書の節ごとに1行追記する（再開できるように）。
- 所見は1件ごとに: 手順書の場所（ファイル:行 か 節の見出し）/ 何が起きたか・何に迷ったか / 根拠（出力やログ）/ 直し方の案 / priority。
- priority の基準（check-docs-against-impl と同じ）:
  9〜10 書いたとおりにすると失敗し原因にたどり着けない、または検査が黙って効かない / 7〜8 失敗するがメッセージから直せる /
  5〜6 失敗しないが誤った理解・余計な作業が広まる / 3〜4 迷う・遠回り / 1〜2 言い回し

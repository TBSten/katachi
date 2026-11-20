# process

チェックの進め方そのものの決まり（オーケストレータが読む）。形は [README.md](README.md)。

## 方針: クラウドで先に回し、Android サンプルと IDE プラグインと 9-2 だけ Mac で回す
- decision: 方針
- decided: 2026-11-09 261109-01 KATACHI-322
- by: 利用者（322 のスレッド）

クラウドで回せる手順を先に回し、届かないもの（`checkSampleAndroid`・10-1 nightly 相当・10-2 E2E・`checkIdePlugin`）と、外部のリポジトリで Gradle を走らせる 9-2 は Mac で回す（9-2 はクラウドの自動承認が「外部のコード」として止める）。Mac に回す分は最初に利用者へ伝える。

## 方針: publish はリリース前チェックの場でしない
- decision: 方針
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（TODO.html、02:58 JST）

チェックは publish の手順と zip の change-notes を確かめるところまで。publish そのものは、ドキュメントの更新が終わってから別の場で行う。チェックの中で publish を進めたり、「publish してよいか」を聞いたりしない（TODO.html の「4. 操作してほしい」に利用者の作業として載せるのはよい）。

## 方針: 手順 11 の p4 以下は、表ではなく Review の popup で人に選んでもらう
- decision: 方針
- decided: 2026-11-09 261109-01 KATACHI-330
- by: 利用者（322 のスレッド「popup 的に出す viewer」）

細かな docs の指摘は、TODO.html や .md の表に並べず、`<置き場>/review/prerelease-<回>-step11.md` に登録して dev サーバーの Review で見てもらう（形は `docs/src/integrations/feedback/review/README.md`、1 件 1 か所）。登録の前に `decisions.py match --drop` で過去の判断と同じものを外す。

## 方針: Review に登録する指摘には、ユーザ影響の札と選択肢ごとの推奨度を付ける
- decision: 方針
- decided: 2026-11-10 KATACHI-351
- by: 利用者（「review ツールは ユーザ影響の有無をタグ的につける / サジェストされた各選択肢は推奨度を小さく薄い字で出す」）

prerelease check などで Review に指摘を登録するときは、1 件ごとに `- impact: あり|なし`（理由を添える）と `- recommend: <ボタンの名前> 0〜10, …` を必ず書く。
推奨度は popup のボタン（`解決済みにする`・`直さない`・`直してもらう`・人が足した選択肢）のうち、意味のあるものだけに付ける。基準は [references/docs-rules.md](../references/docs-rules.md) の「Review に登録する」。
登録したら `cli.mjs review check` を回し、問題 0 件・「注意」0 件にする。

## 方針: skills/katachi/ への指摘は聞かずに直す
- decision: 方針
- decided: 2026-11-13 261112-01 KATACHI-368
- by: 利用者（368 のスレッド、00:49 JST）

利用者の一言: 「skills/katachi に対する指摘は一律直して」

`skills/katachi/`（SKILL.md・references・assets のチェックリスト）宛ての指摘は、TODO.html や Review で利用者に選ばせず、その回の修正 PR で直す。Review には登録しない（登録済みのものは直したら remove）。docs の本文は人が書く決まりのままで、この方針は skill だけに当てはまる。ガイドと skill のどちらに決まりを置くかという指摘（ルールの置き場）は、skill を直す話ではないのでこの方針に入れない。

## 方針: 探索的検証（手順 13）を prerelease と週替わりの nightly の両方で回す
- decision: 方針
- decided: 2026-11-20 KATACHI-402
- by: 利用者（決定カード「探索的検証をどこから入れる？」で「A と B を同時に始める」）

prerelease の手順 13 として、コンポーネント別のチャーターで利用者目線の探索的検証を回す（クラウド先、IDE plugin は Mac で人が 15 分）。
あわせて、クラウドの定期実行で週に 1 コンポーネントずつ回す（[exploratory-nightly.md](../references/exploratory-nightly.md)。修正 PR は作らず、p7 以上は `fix:` チケットにする）。
提案は A（prerelease だけ・推奨度 9/10）だったが、利用者が費用とノイズの様子見も同時に進めることを選んだ。
一巡（6 週）の集計で続けるか・頻度を変えるかを利用者に聞く。

## 方針: 9-2 で Mac の Notion チケットの hook が書き込みを止めたら、Mac 側でその回のチケットを掴んでよい
- decision: 方針
- decided: 2026-11-20 261120-01 KATACHI-404
- by: 利用者（判断カード「Mac の 9-2 で止まった書き込みをどう通しますか?」で「Mac 側で掴んでよい」）

Mac の `~/dev/katachi` には、いま掴まれている Notion チケット（`.local/notion-ticket/` の状態）を見て Write・Edit を止める hook がある。9-2（手順書どおりの導入）の subagent が定義を書くところでこれに止められたら、`ticket.py set <その回の prerelease チケット> --status AI:実装中` でその回のチケットを掴んで続け、9-2 が終わったら元のチケットに戻す（元のチケットのステータスは変えない）。聞き直さない。
Mac の auto mode は `ticket.py set` の書き込みも止めるので、そのときは利用者にターミナルで実行してもらう（コマンドをそのまま渡す）。9-2 を始める前に掴んでおけば、途中で止まらない。

## このまま: `katachi <file>.katachi.kts` が、中のコマンドの失敗を終了コードで伝えない
- decision: このまま
- file: exploratory:cli-script
- quote: 終了コードは 0
- decided: 2026-11-20 261120-01 KATACHI-404
- by: 利用者（スレッド、22:35 JST、TODO.html の問いへの答え）

手順 13（探索的検証 1 周目 CLI/script）の p7。スクリプトの中の `execute()`・`executeCommands()` は終了コードを返すだけで、`exitProcess` を書かないと `katachi ci.katachi.kts` は 0 で終わる（CI に書くと、検査が失敗しても緑になる）。
(a) 最後の式が数値ならそれを katachi の終了コードにする / (b) docs の katachi-script のページに `exitProcess(executeCommands(…))` の書き方を足す / (c) このまま、と並べて聞き、(b) になった。katachi の挙動は変えない。docs は人が書くので、書き方の案を Review（prerelease-5-docs.md）に載せた。
次のチェックでは、この挙動を発見として出さない。docs に書き方が無いままなら、docs の指摘としてだけ出す。

## このまま: Kotlin 2.2 のプロジェクトに katachi-konture を入れるとコンパイルが通らない
- decision: このまま
- file: exploratory:konsist-konture
- quote: The binary version of its metadata is 2.4.0, expected version is 2.2.0.
- decided: 2026-11-20 261120-01 KATACHI-404
- by: 利用者（スレッド、22:35 JST「これはまあこのままでいいや」）
- revisit: katachi-konture が持ち込む Konture の版を変えたとき

手順 13（探索的検証 1 周目 konsist/konture）の p7。katachi-konture が持ち込む Konture 1.0.1 は Kotlin 2.4 のメタデータで作られていて、Kotlin 2.2 では読めない（2.3 では通る）。README とリリースノートは「Kotlin 2.2+」のままで、konture の下限は Module.md にだけある。
(a) README・`konture { }` の KDoc・リリースノートにも「Kotlin 2.3 以上」と書く / (b) Konture の版を下げる / (c) このまま、と並べて聞き、(c) になった。理由の一言なし。次のチェックでは出さない。

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

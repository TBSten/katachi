# environment

既知の環境要因。チェックを回した場所のせいで落ちるもので、製品の不具合ではないと利用者と確かめたもの（手順 8・9・10）。
形は [README.md](README.md)。ここに該当する失敗は priority 10 の警告にせず、結果に「環境要因（decisions/environment.md）」と書く。
**確かめずに環境要因にしない**: 同じテストが別の場所（Mac の手元・CI）で通ることを見てから環境要因とする。

## このまま: root で走らせると konsist / konture の spec が 5 件落ちる
- decision: このまま
- decided: 2026-11-09 261109-01 KATACHI-322
- by: 利用者（322 のスレッド）

クラウドのコンテナは root で Gradle を走らせるので、ファイルの権限を前提にした konsist / konture の spec（ParseTarget 4 件・KontureFailure 1 件）が落ちる。Mac と CI では通る。手順 8 をクラウドで回したときに出る。

## このまま: Remote Control から起動した 10-2 の SpikeDriverTest で右クリックのメニューが出ない
- decision: このまま
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（2026-11-10 02:00 JST 確認）

手順 10-2 を Remote Control から起動すると、SpikeDriverTest の右クリックのメニューが出ずに落ちる。手元のターミナルから起動すると通る。

## このまま: クラウドでは Android SDK と JetBrains に届かない
- decision: このまま
- decided: 2026-11-09 261109-01 KATACHI-322
- by: 利用者（2026-11-09、分担の判断）

クラウドは dl.google.com（Android SDK → `checkSampleAndroid`）と `*.jetbrains.com`（IDE プラグイン）に届かない。この 2 つが落ちるのは環境要因で、Mac で回す（process.md の分担）。

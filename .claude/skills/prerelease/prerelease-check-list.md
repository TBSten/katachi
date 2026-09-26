# リリース前チェックリスト

## 1. 前提チェック

- [ ] 今回のリリースバージョン: {TODO}
- [ ] publish 済の直前のリリースバージョン: {TODO}

<details>
<summary>memo</summary>

{TODO}

</details>

## 2. 日本語 -> 英語へのドキュメント更新

- [ ] translate-ja-en を実行した: {TODO}
- [ ] 訳し直しが要る組が残っていない（`list-targets.py` の結果）: {TODO}

<details>
<summary>memo</summary>

{TODO}

</details>

## 3. 公開範囲（可視性）のチェック

- [ ] public（注釈なし）: {TODO 件数} 件 / 要検討 {TODO} 件
- [ ] public + `@InternalKatachiApi`: {TODO 件数} 件 / 要検討 {TODO} 件
- [ ] public + `@ExperimentalKatachiApi`: {TODO 件数} 件 / 要検討 {TODO} 件
- [ ] 一覧: {TODO file://.local/release-v0.0.0/visibility-check.md の絶対パス}

<details>
<summary>memo</summary>

{TODO}

</details>

## 4. リリースノート

- [ ] リリースノート: {TODO file://.local/release-v0.0.0/release-note.md の絶対パス}
- [ ] BREAKING CHANGE: {TODO} 件
- [ ] 未コミットの変更がリリースに入らないことを確かめた: {TODO}

<details>
<summary>memo</summary>

{TODO}

</details>

## 5. 実装・挙動とドキュメントの不整合

- [ ] check-docs-against-impl の結果: {TODO file://.local/release-v0.0.0/docs-vs-impl.md の絶対パス}
- [ ] 食い違い: {TODO} 件（priority 7 以上: {TODO} 件）

<details>
<summary>memo</summary>

{TODO}

</details>

## 6. ドキュメントサイトと API リファレンスの巡回

- [ ] `generateApiDocs` と `pnpm run build` が通った: {TODO}
- [ ] en: {TODO} ページ / 問題 {TODO} 件
- [ ] ja: {TODO} ページ / 問題 {TODO} 件
- [ ] api-docs: {TODO} ページ / 問題 {TODO} 件
- [ ] スクリーンショット: {TODO file://.local/release-v0.0.0/screenshots/ の絶対パス}

<details>
<summary>memo</summary>

{TODO}

</details>

## そのほか

<details>
<summary>memo</summary>

{TODO}

</details>

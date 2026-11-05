# assets/katachi-220

PR 用の証拠ファイルだけを置く orphan ブランチ（ソースとは切り離してある。main には merge しない）。

- `tool-calls.md`: MCP tool の呼び出し記録（コマンド・結果・要点）
- `tool-calls.png`: 呼び出しの様子（記録 JSON から描いたターミナル風の画像）
- `demo.mp4` / `demo.gif`: 呼び出し 15 回を順に流した 50 秒の動画（同じく記録から描いたもの。画面収録ではない）
- `settings-katachi.png`: Settings | Tools | katachi（新しいチェックボックス付き。headless で描画）
- `settings-mcp-server.png`: Settings | Tools | MCP Server（headless で描画）
- `raw/`: 各呼び出しの JSON-RPC のリクエスト・レスポンス、使ったクライアント

対応チケット: KATACHI-220（実装は KATACHI-234 / 238 / 239、設計は KATACHI-222）

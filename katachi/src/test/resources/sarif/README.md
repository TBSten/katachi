# SARIF のテスト用リソース

- `sarif-schema-2.1.0.json`: SARIF 2.1.0 の JSON Schema（draft-07）。
  https://json.schemastore.org/sarif-2.1.0.json から 2026-10-04 に取得し、そのまま置いている。
  `SarifSchemaSpec` が `toSarif` の出力をこれで検証する。
- `golden/*.sarif`: `SarifDocumentSpec` が比べる決定的な出力。
  書き直すときは `./gradlew :katachi:test -Dkatachi.golden.update=true`。

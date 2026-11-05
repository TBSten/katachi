# KATACHI-220 MCP tool の呼び出し記録（実機）

IntelliJ IDEA 2026.1（`./gradlew -p katachi-intellij-plugin runIde` の sandbox）で `sample/jvm` を開き、Settings | Tools | MCP Server を有効にして、外から MCP の tool を呼んだ記録。2026-11-06 に取得。

| 項目 | 値 |
|---|---|
| MCP Server | `IntelliJ IDEA MCP Server` 2026.1（IDE 内蔵の `com.intellij.mcpServer`） |
| 接続先 | `http://127.0.0.1:64342/sse`（SSE。`POST /mcp` の Streamable HTTP は 404） |
| クライアント | `mcp_client.py`（標準ライブラリだけの最小 MCP クライアント。`mcp list` / `mcp call <tool> <json>` と書いているのはこれ） |
| プロジェクト | `.claude/worktrees/katachi-220-mcp/sample/jvm`（定義モジュール `:architecture-test`） |
| 呼び方 | 全部の tool に `projectPath` を渡している（下の表示では省いている） |

## 要点

- tools/list に katachi の tool 7 本が出る: roles_of_file / list_roles / list_templates / violations / refresh_definition / run_check / generate
- 読み取り 4 本は定義が読めていれば即答（0.0 秒）。初回だけ定義の読み込みを待つ（list_roles 54 秒）
- run_check は Gradle のテストを回して結果を返す。役割のないファイルを置くと failed と違反 1 件、message は katachi のテスト出力そのもの
- generate は Settings | Tools | katachi の「Allow AI agents to generate files from templates over MCP」が無いと断られる。許可を入れると生成し、既存ファイルは上書きせず止まる（fail）か飛ばす（skip）

## 呼び出しと結果

### tools/list

JetBrains MCP Server の tool 一覧に katachi の 7 本が出る。

```sh
mcp list
```

結果:

```text
34 tools, 7 of them from katachi:
  katachi_generate(module, templates, arguments, onExisting)
  katachi_list_roles(module)
  katachi_list_templates(module)
  katachi_refresh_definition(module)
  katachi_roles_of_file(path)
  katachi_run_check(module)
  katachi_violations(path, module)
```

### katachi_list_roles

定義が宣言する役割（初回は定義の読み込みを待つので 54 秒）。 所要 54.0 秒。

```sh
mcp call katachi_list_roles '{}'
```

<details><summary>結果（長いので折りたたみ）</summary>

```text
module :architecture-test: 30 roles
  api.Controller   src/main/kotlin/com/example/controller/*/*Controller.kt  (ControllerRole.kt:21)
  api.KtorPlugin   src/main/kotlin/com/example/plugin/*.kt  (KtorPluginRole.kt:13)
  domain.Service   src/main/kotlin/com/example/service/*Service.kt  (ServiceRole.kt:18)
  domain.Model     src/main/kotlin/com/example/model/[A-Z]*.kt  (ModelRole.kt:14)
  data.Repository  src/main/kotlin/com/example/repository/*Repository.kt  (RepositoryRole.kt:10)
  app.Entrypoint   src/main/kotlin/com/example/Application.kt  (EntrypointRole.kt:8)
  app.ApplicationConfig src/main/resources/application.conf  (ApplicationConfigRole.kt:7)
  app.LoggingConfig src/main/resources/logback.xml  (LoggingConfigRole.kt:7)
  testing.Test     src/test/kotlin/**/*Test.kt  (TestRole.kt:7)
  testing.ArchitectureDefinitionEntry architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt  (ArchitectureDefinitionEntryRole.kt:13)
  testing.DocumentSectionDefinition architecture-test/src/test/kotlin/com/example/DocumentSections.kt  (DocumentSectionDefinitionRole.kt:13)
  testing.GroupDefinition architecture-test/src/test/kotlin/com/example/**/*Group.kt  (GroupDefinitionRole.kt:12)
  testing.RoleDefinition architecture-test/src/test/kotlin/com/example/**/*Role.kt  (RoleDefinitionRole.kt:12)
  testing.ProcessorDefinition architecture-test/src/test/kotlin/com/example/processors/*.kt  (ProcessorDefinitionRole.kt:8)
  testing.ArchitectureTest architecture-test/src/test/kotlin/com/example/*Test.kt  (ArchitectureTestRole.kt:9)
  testing.IntegrationSpec architecture-test/src/test/kotlin/com/example/*Spec.kt  (IntegrationSpecRole.kt:8)
  testing.GeneratedDocumentation docs/**/*.md  (GeneratedDocumentationRole.kt:8)
  testing.LayoutSnapshot snapshots/layout.txt  (LayoutSnapshotRole.kt:8)
  testing.BaselineFile katachi-baseline.json  (BaselineFileRole.kt:8)
  Gradle.SettingsScript settings.gradle  (GradleGroup.kt:16)
  Gradle.BuildScript architecture-test/build.gradle  (GradleGroup.kt:16)
  Gradle.GradleProperties gradle.properties  (GradleGroup.kt:16)
  Gradle.VersionCatalog gradle/*.versions.toml  (GradleGroup.kt:16)
  Gradle.DaemonJvmProperties gradle/gradle-daemon-jvm.properties  (GradleGroup.kt:16)
  Gradle.GradleWrapper.LauncherScript gradlew.bat  (GradleGroup.kt:16)
  Gradle.GradleWrapper.WrapperJar gradle/wrapper/gradle-wrapper.jar  (GradleGroup.kt:16)
  Gradle.GradleWrapper.WrapperProperties gradle/wrapper/gradle-wrapper.properties  (GradleGroup.kt:16)
  tool.Documentation README.md  (DocumentationRole.kt:6)
  tool.Git         .gitignore  (GitRole.kt:6)
  tool.IdeCodeStyle .idea/codeStyles/codeStyleConfig.xml  (IdeCodeStyleRole.kt:7)
stale: False
```

</details>

### katachi_roles_of_file（役割のあるファイル）

HealthService.kt は domain.Service。 所要 0.0 秒。

```sh
mcp call katachi_roles_of_file '{"path": "src/main/kotlin/com/example/service/HealthService.kt"}'
```

結果:

```json
{
  "module": ":architecture-test",
  "relativePath": "src/main/kotlin/com/example/service/HealthService.kt",
  "outcome": "matched",
  "roles": [
    {
      "qualifiedName": "domain.Service",
      "title": "Service",
      "summary": "Owns one application-specific behaviour, realized by combining Repositories",
      "pattern": "src/main/kotlin/com/example/service/*Service.kt",
      "template": {
        "specifier": "domain.Service"
      }
    }
  ],
  "nearby": [],
  "stale": false
}
```

### katachi_roles_of_file（役割のないパス）

存在しないパスでも答える。outcome=unexpected と移動先の候補 nearby。 所要 0.0 秒。

```sh
mcp call katachi_roles_of_file '{"path": "src/main/kotlin/com/example/Stray.kt"}'
```

結果:

```json
{
  "module": ":architecture-test",
  "relativePath": "src/main/kotlin/com/example/Stray.kt",
  "outcome": "unexpected",
  "roles": [],
  "nearby": [
    {
      "role": "app.Entrypoint",
      "directory": "src/main/kotlin/com/example",
      "filePattern": "Application.kt"
    },
    {
      "role": "domain.Model",
      "directory": "src/main/kotlin/com/example/model",
      "filePattern": "[A-Z]*.kt"
    },
    {
      "role": "api.KtorPlugin",
      "directory": "src/main/kotlin/com/example/plugin",
      "filePattern": "*.kt"
    }
  ],
  "stale": false
}
```

### katachi_list_templates

テンプレートの引数・書くファイル。 所要 0.0 秒。

```sh
mcp call katachi_list_templates '{"module": ":architecture-test"}'
```

結果:

```text
module :architecture-test: 3 templates
  api.Controller  params(resource:capture)
      -> src/main/kotlin/com/example/controller/${resource}/<resource.pascalCase>Controller.kt
  api.Endpoint    params(resource:string)
      -> src/main/kotlin/com/example/controller/${resource}/<resource.pascalCase>Controller.kt
      -> src/main/kotlin/com/example/service/${resource}Service.kt
  domain.Service  params(name:capture, kdoc:string?)
      -> src/main/kotlin/com/example/service/${name}Service.kt
stale: False
```

### katachi_violations（チェック前）

まだ走っていないので state=notRun。 所要 0.0 秒。

```sh
mcp call katachi_violations '{}'
```

結果:

```json
{
  "state": "notRun",
  "stale": false,
  "violations": []
}
```

### katachi_refresh_definition

Gradle で定義を読み直して ready。 所要 3.6 秒。

```sh
mcp call katachi_refresh_definition '{"module": ":architecture-test"}'
```

結果:

```json
{
  "state": "ready",
  "modules": [
    ":architecture-test"
  ],
  "stale": false,
  "definitionErrors": []
}
```

### katachi_run_check（違反なし）

テストを Gradle で回して succeeded。 所要 4.1 秒。

```sh
mcp call katachi_run_check '{"module": ":architecture-test"}'
```

結果:

```json
{
  "module": ":architecture-test",
  "outcome": "succeeded",
  "violationCount": 0,
  "violations": [],
  "truncated": false
}
```

### katachi_run_check（役割のない Stray.kt を置いた後）

failed と違反 1 件。message は katachi のテスト出力そのもの。 所要 2.5 秒。

```sh
mcp call katachi_run_check '{"module": ":architecture-test"}'
```

結果:

```json
{
  "module": ":architecture-test",
  "outcome": "failed",
  "violationCount": 1,
  "violations": [
    {
      "module": ":architecture-test",
      "ruleId": "katachi.UnexpectedFile",
      "label": "UnexpectedFile",
      "level": "error",
      "message": "No role is defined for this file.\n\nNearby locations:\n  app.Entrypoint src/main/kotlin/com/example/ (Application.kt)\n  domain.Model   src/main/kotlin/com/example/model/ ([A-Z]*.kt)\n  api.KtorPlugin src/main/kotlin/com/example/plugin/ (*.kt)\n\nHow to fix:\n  - Move it to one of the locations above, with a name matching the pattern in parentheses\n  - Delete it if it is not needed\n  - Add a new role for it:\n      \"Stray\".role {\n        summary = \"TODO\"\n        layout {\n          \"src/main/kotlin/com/example\" / \"Stray\".ktFile()\n        }\n      }",
      "path": "<sample/jvm>/src/main/kotlin/com/example/Stray.kt"
    }
  ],
  "truncated": false
}
```

### katachi_violations（path を指定）

SARIF から読んだ同じ違反。 所要 0.0 秒。

```sh
mcp call katachi_violations '{"path": "src/main/kotlin/com/example/Stray.kt"}'
```

結果:

```json
{
  "state": "ready",
  "readAt": "2026-11-05T19:10:26.433633680Z",
  "stale": false,
  "violations": [
    {
      "module": ":architecture-test",
      "ruleId": "katachi.UnexpectedFile",
      "label": "UnexpectedFile",
      "level": "error",
      "message": "No role is defined for this file.\n\nNearby locations:\n  app.Entrypoint src/main/kotlin/com/example/ (Application.kt)\n  domain.Model   src/main/kotlin/com/example/model/ ([A-Z]*.kt)\n  api.KtorPlugin src/main/kotlin/com/example/plugin/ (*.kt)\n\nHow to fix:\n  - Move it to one of the locations above, with a name matching the pattern in parentheses\n  - Delete it if it is not needed\n  - Add a new role for it:\n      \"Stray\".role {\n        summary = \"TODO\"\n        layout {\n          \"src/main/kotlin/com/example\" / \"Stray\".ktFile()\n        }\n      }",
      "path": "<sample/jvm>/src/main/kotlin/com/example/Stray.kt"
    }
  ]
}
```

### katachi_generate（設定オフ）

Settings | Tools | katachi の許可が無いと断られる。 所要 0.0 秒。

```sh
mcp call katachi_generate '{"module": ":architecture-test", "templates": ["domain.Service"], "arguments": {"name": "evidence"}}'
```

結果（isError=true）:

```text
katachi: generating files over MCP is turned off. Turn on 'Allow AI agents to generate files from templates over MCP' in Settings | Tools | katachi.
```

### katachi_generate（許可を入れた後）

domain.Service を name=evidence で生成。 所要 4.6 秒。

```sh
mcp call katachi_generate '{"module": ":architecture-test", "templates": ["domain.Service"], "arguments": {"name": "evidence"}}'
```

結果:

```json
{
  "module": ":architecture-test",
  "items": [
    {
      "templates": [
        "domain.Service"
      ],
      "result": "generated",
      "files": [
        {
          "path": "<sample/jvm>/src/main/kotlin/com/example/service/evidenceService.kt",
          "kind": "new"
        }
      ]
    }
  ],
  "writtenFiles": [
    "<sample/jvm>/src/main/kotlin/com/example/service/evidenceService.kt"
  ],
  "timedOut": false
}
```

### katachi_generate（同じ引数をもう一度、onExisting=fail）

既存ファイルで止まる stoppedAtConflict。上書きしない。 所要 2.8 秒。

```sh
mcp call katachi_generate '{"module": ":architecture-test", "templates": ["domain.Service"], "arguments": {"name": "evidence"}, "onExisting": "fail"}'
```

結果:

```json
{
  "module": ":architecture-test",
  "items": [
    {
      "templates": [
        "domain.Service"
      ],
      "result": "stoppedAtConflict",
      "existingPaths": [
        "<sample/jvm>/src/main/kotlin/com/example/service/evidenceService.kt"
      ]
    }
  ],
  "writtenFiles": [],
  "timedOut": false
}
```

### katachi_generate（onExisting=skip）

既存ファイルを飛ばして skipped。 所要 2.4 秒。

```sh
mcp call katachi_generate '{"module": ":architecture-test", "templates": ["domain.Service"], "arguments": {"name": "evidence"}, "onExisting": "skip"}'
```

結果:

```json
{
  "module": ":architecture-test",
  "items": [
    {
      "templates": [
        "domain.Service"
      ],
      "result": "skipped",
      "existingPaths": [
        "<sample/jvm>/src/main/kotlin/com/example/service/evidenceService.kt"
      ]
    }
  ],
  "writtenFiles": [],
  "timedOut": false
}
```

### katachi_generate（未知のテンプレート）

知っている id を並べて断る。 所要 1.0 秒。

```sh
mcp call katachi_generate '{"module": ":architecture-test", "templates": ["no.SuchTemplate"], "arguments": {}}'
```

結果（isError=true）:

```text
katachi: no.SuchTemplate is not a template of this module. Pass ids as katachi_list_templates gives them: api.Controller, api.Endpoint, domain.Service.
```

## 生の記録

`work/*.json` に、各呼び出しの JSON-RPC のリクエストとレスポンス（`tools/list` の全文を含む）がそのまま残っている。

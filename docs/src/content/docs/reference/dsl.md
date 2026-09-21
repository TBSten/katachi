---
title: DSL リファレンス
description: architecture / group / 役割 / layout の書き方。
---

:::caution[実装中]
`layout { }` の中身はまだ実装されていません。このページは v0.1 の設計に基づく予定です。
:::

## `architecture { }`

アーキテクチャ定義全体を囲むエントリポイント。戻り値は `Architecture` 型です。

```kotlin
val projectArchitecture = architecture {
    domainRoles()
    uiRoles()
}
```

- ブロック内で宣言した group・役割は、**書いた時点で登録済み**になります
- ブロックは**遅延実行**されます。値を作っただけでは検査は走りません
- **役割は group の中にも、ルート直下にも書けます。** ルート直下に書いた役割はどの group にも属さず、
  `qualifiedName` は役割名そのものになります
- **ルート直下では group と役割が同じ名前を持てません。** `"domain".group { }` と `"domain" { }` は
  どちらも `"domain"` として参照されることになるため、DSL の評価時に落ちます

## `"name".group { }`

役割をまとめる単位で、**ドキュメントの出力ディレクトリ**に対応します。
モジュール構成とは独立していて、ネストできます。

```kotlin
"domain".group {
    title = "ドメイン"
    "UseCase" { /* ... */ }
}

// ドキュメントに出さない
"build".group {
    documented = false
    "GradleModule" { /* ... */ }
}
```

`documented = false` でも**検査の対象からは外れません**。allow list としては有効で、
文書に出ないだけです。

同じ親の下に同名の group を2回宣言すると、DSL の評価時にエラーになります。

## `"RoleName" { }`

「この役割は何で、どこに置けるか」を1箇所に書きます。

```kotlin
"UseCase" {
    title = "ユースケース"           // 省略すると役割名がそのまま使われる
    summary = "..."
    documented = true               // 省略時 true
    example("GetUserUseCase", "ユーザーを取得する")
    layout { /* ... */ }
}
```

役割名と group 名は `[A-Za-z][A-Za-z0-9_-]*` に制限されます。生成ファイル名と
参照キーになるためです。読みやすい表示名は `title` に分けます。違反は DSL の評価時に落ちます。

## `layout { }`

その役割のファイルをどこに置けるか。

```kotlin
layout {
    ":core:domain".module {                    // Gradle module
        description = "複数 feature から使われるもの"
        mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
    }
    ":feature:*".module { /* ... */ }          // ワイルドカード
    "app/ios" { ignore() }                     // ただのディレクトリ
    ".gitignore".file()                        // layout 直下 = リポジトリルート
}
```

| 書き方 | 指すもの |
|---|---|
| `"...".module { }` | Gradle module |
| `"..." { }` | ディレクトリ |
| `layout` 直下 | リポジトリルート |

`*` はちょうど1階層、`**` は0階層以上にマッチします。
ワイルドカードを含む宣言は自動で optional になります（0件でも違反になりません）。

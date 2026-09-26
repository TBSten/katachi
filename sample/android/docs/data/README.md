[katachi-sample-android](../README.md)

# データレイヤー

:data が持つもの。データの取得と保存

`:data` モジュール。いまは Repository 役割1つだけで、扱う対象ごとの package
（`user` / `settings`）にインターフェースと実装を並べる。package は `DataDomain` の
1項目ずつで、対象を増やすときはそこに1行足す。

他のどのモジュールにも依存しない、このアプリで一番下の層。Android にも Compose にも
触らないので、`:ui` や `:feature:*` を持ち出さずに読める。ViewModel はここの
インターフェースをコンストラクタで受け取り、テストでは `:testing` のフェイクに差し替える。

役割が1つしか無くても group にしてあるのは、増える場所だから。データソースや DTO を
分けたくなったらここに足す。いまは Repository 以外のファイルを `:data` に置くと違反になるので、
「置いてから考える」ができないようになっている。

| Role | Summary |
|---|---|
| [リポジトリ](./Repository.md) | データの取得と保存。インターフェースと実装を :data の、扱う対象ごとの package （user / settings）に並べて置く |

## Placement in this group

```
:data
  src/main/kotlin/**/
    user/
      UserRepository.kt           リポジトリ
      User*Repository.kt          リポジトリ
      UserRepositoryImpl.kt       リポジトリ
      User*RepositoryImpl.kt      リポジトリ
    settings/
      SettingsRepository.kt       リポジトリ
      Settings*Repository.kt      リポジトリ
      SettingsRepositoryImpl.kt   リポジトリ
      Settings*RepositoryImpl.kt  リポジトリ
```

# 例外の扱い

## 基本的なルール

- 命名は `Katachi<例外を端的に表す名前>Exception` にして prefix, suffix を遵守する。
- 直接または間接的に `KatachiDeclarationException`、`KatachiCheckException`、
  `KatachiInternalException`、`KatachiArchitectureAssertionError` のいずれかを継承すること。

利用者のテストが落ちたとき、スタックトレースにはライブラリのクラス名がそのまま出る。
`GlobSyntaxException` のような一般名だと、どのライブラリが投げたのかが出力から分からない。
prefix はそのためにある。

## 3つの基底

katachi が投げるものは、**利用者が取るべき行動**で3つに分かれる。

| 基底 | いつ起きるか | 利用者が直す先 |
|---|---|---|
| `KatachiDeclarationException` | `architecture { }` の評価中。ファイルを1つも読む前 | **定義そのもの**（役割名、モジュールパス、重複） |
| `KatachiCheckException` | 検査の実行中。定義は正しいが走らせられない | **環境**（git が無い、プロジェクトルートが見つからない） |
| `KatachiInternalException` | katachi 自身の前提が崩れた | **何もない。katachi のバグ**なので issue を立ててもらう |
| `KatachiArchitectureAssertionError` | 検査が完了し、違反が見つかった | **ファイルの配置**、または定義 |

- `KatachiDeclarationException` は `IllegalArgumentException` を継承する。
  弾いているのは常に利用者が DSL に渡した値だから。
- `KatachiCheckException` は `IllegalStateException` を継承する。
  渡された値ではなく、実行環境が期待どおりでない。
- `KatachiInternalException` も `IllegalStateException` を継承する。
  利用者には直しようがないので、**メッセージには「これは katachi のバグである」と書く**。
- `KatachiArchitectureAssertionError` だけ `Error` 側で、`AssertionError` を継承する。
  テストフレームワークが「アサーション失敗」として扱うため。**suffix も `Error` にする。**

新しい例外を足すときは、まずこの表のどれに当たるかを決める。
どれにも当たらないと感じたら、**基底を増やす前に分類を疑う**。

## コンストラクタ引数

```kotlin
class KatachiSomeException(
    val someParams: Long,
) : KatachiDeclarationException(
    message = """
        TODO
    """.trimIndent()
)
```

- 雑に message のような引数を作らない。

**理由。** `message` を引数にすると、同じ例外に対して呼び出し側ごとに文面を書けてしまう。
実際 `KatachiDuplicateDeclarationException` は group 用と role 用で別々の文面を渡されていて、
**同じ例外なのに2通りの言い回しが存在していた**。

文面を例外の中で組み立てると次が揃う。

- 例外が公開しているプロパティ（`name` / `declaredAt` など）と、文面に出る値が必ず一致する
- 文面を直すとき、探す場所が例外クラス1箇所に決まる
- 呼び出し側は**値を渡すだけ**になり、何を渡すべきかがシグネチャから分かる

文面が呼び出し側ごとに変わるなら、それは**別の例外**か、**分岐の材料をプロパティとして受け取るべき**もの。
後者の例:

```kotlin
class KatachiDuplicateDeclarationException(
    val kind: DeclarationKind,          // Group か Role か。文面の分岐もこれで閉じる
    val name: String,
    val scope: String,
    val firstDeclaredAt: DeclarationSite,
    val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Duplicate ${kind.label} \"$name\" declared at $declaredAt.")
        appendLine("It was already declared at $firstDeclaredAt, in $scope.")
        append(kind.uniquenessRule)
    },
)
```

`kind` をプロパティにすると、**利用者が `catch` したときにプログラムから判別できる**という利点も付く。

## メッセージの書き方

エラーメッセージの仕様は利用者向けの検査結果（`[UnexpectedFile]` など）と同じ方針に従う。

- **英語**で書く。利用者が書いた識別子（役割名、制約名）が引用として混ざるのは許容する
- **ASCII のみ。** `→` や `✗` は環境によって化ける。`->` で足りる
- ANSI エスケープを使わない
- **宣言位置（`ファイル名:行番号`）を含める。** DSL 評価時のエラーは、どこを直せばよいかが
  行番号で分かるかどうかで直しやすさが変わる
- **何が起きたか → なぜだめか → どう直すか**の順で書く。1行目は事実だけにする

```
Invalid role name "ユースケース" declared at ProjectArchitecture.kt:42.
Names must match [A-Za-z][A-Za-z0-9_-]*: start with an ASCII letter, then ASCII letters, digits, '_' or '-'.
Use `title` for a human readable display name.
```

最後の1行が効く。**利用者がやりたかったこと（読みやすい名前を付けたい）に対して、正しい場所を示している。**

## 内部の想定違反

利用者の入力ではなく、katachi 自身の前提が崩れた場合。**ここにも専用の例外を定義する。**

- **`check` / `require` / `checkNotNull` / `requireNotNull` / `error` を使わない。**
  これらが投げるのは `IllegalStateException` と `IllegalArgumentException` で、
  スタックトレースに katachi の名前が出ない。利用者は「自分のコードが悪いのか、
  katachi が悪いのか」を切り分けられないまま放り出される
- 代わりに `KatachiInternalException` を継承した専用の例外を定義する。
  名前が出どころを語り、`catch` して報告できる
- **キャストは `as` ではなく `as?` + `?:` で落とす**（`kotlin.md` の `as` 禁止）。
  素の `ClassCastException` は何が起きたのか読み手に伝わらない
- メッセージには**これが katachi のバグであること**を書く。利用者に直せる話ではないので、
  「どう直すか」の代わりに「報告してほしい」を置く

```kotlin
public class KatachiUnsupportedLayoutScopeException internal constructor(
    public val actualType: String,
) : KatachiInternalException(
    message = """
        The layout scope here is a $actualType, which does not implement ModuleAwareLayoutScope.
        Every scope katachi hands to a `layout { }` block implements it, so a layout cannot
        cause this. Please report it at https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)

private fun LayoutScope.moduleAware(): ModuleAwareLayoutScope =
    this as? ModuleAwareLayoutScope
        ?: throw KatachiUnsupportedLayoutScopeException(this::class.simpleName ?: "unknown scope")
```

「起きないはずだから雑でいい」は逆で、**起きないはずのものが起きた瞬間ほど、出どころが
名前で分かることに意味がある**。

## 検査は途中で死なない

`assert()` の値打ちは「1 回走らせれば全部わかる」ことにある。エージェントに 1 回読ませれば
直す材料が揃う、という一点で設計している。**1 ファイルで想定外の例外が出た瞬間に全部が
消えると、利用者は違反を 1 つも見られないまま、katachi の内部エラーだけを見せられる。**
しかもそのファイルは、たいてい直したい違反とは関係のないファイルである。

だから**走査は最小でもファイル単位で握る**。1 ファイルの失敗はそのファイルの結果になるだけで、
残りの走査は続く。

```kotlin
private inline fun <T> catchingPerFile(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it.isFatal) throw it }
```

### 握ったものを捨てない

握った失敗は **`Violation` として報告に載せ、検査を失敗させる**。黙って緑にするのは最悪で、
「動いているつもりの穴」を作る。`ViolationKind.Failed` と、`UncheckedFile` /
`UncheckedDirectory`（既存の `UnexpectedFile` / `UnexpectedDirectory` と同じ対）を用意し、
`[UncheckedFile]` として他のブロックと並べる。握った `Throwable` も持たせ、その型名と
メッセージを報告に出す — 利用者がそのまま issue に貼れる程度に。スタックトレース全体は出さない。

報告の末尾に **件数の行**を出す。`maxViolations` による打ち切りでこの行が消えないこと。
これが「部分的な結果である」と伝える唯一の保証になる。

`ViolationKind.Failed` は enum の**最後**に置く。`Scan` が `sortedBy { it.kind.ordinal }` で
ブロックを並べているので、先頭に足すと既存の報告の並びが全部ずれる。

### 握ってはいけないもの

**`runCatching` は `Throwable` を全部飲む。** そのままでは使えない。飲んだあとに
致命的なものを投げ直すこと。

| 投げ直す | なぜ |
|---|---|
| `VirtualMachineError`（`OutOfMemoryError` / `StackOverflowError`） | JVM がもう続けられない。握っても次のファイルで同じことが起きるだけで、原因だけが消える |
| `LinkageError` | classpath が壊れている。全ファイルで失敗するので、報告が N 件の同じノイズで埋まる |
| `InterruptedException` | 呼び出し側がやめろと言っている。握るのは無視すること |
| `KatachiArchitectureAssertionError` | これは**結果**であって失敗ではない |

`KatachiInternalException` は**握ってよい**。katachi のバグだが、そのファイル 1 つを
`[UncheckedFile]` にして残りを検査できるなら、その方が利用者に渡せるものが多い。

### 粒度

最小がファイル 1 つ、というだけで、上位の単位に同じ考え方を広げてよい。役割 1 つ、
モジュール 1 つ、サンプル 1 本。**「ここで失敗しても、隣は独立して答えを出せるか」**が判断の軸。
独立していないなら握っても意味がないので、素通しする。

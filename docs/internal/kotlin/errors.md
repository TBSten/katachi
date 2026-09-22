# 例外の扱い

この文書は2層になっている。

- **[規約](#規約)** — 例外を1つ足す・直すときに守るもの。ここだけ読めば足りる
- **[なぜそうなっているか](#なぜそうなっているか)** — その規約に至った経緯。規約を変えたくなったときに読む

## 例外を1つ足すときにやること

1. 名前を `Katachi<何が起きたかを端的に>Exception` にする → [命名](#命名)
2. 4つの基底から1つ選ぶ。増やす前に分類を疑う → [基底を選ぶ](#基底を選ぶ)
3. `message` を引数に取らない。値をプロパティで受け取り、文面はクラスの中で組む →
   [コンストラクタ引数](#コンストラクタ引数)
4. 文面は英語・ASCII のみ・宣言位置つき・「何が起きたか → なぜだめか → どう直すか」の順 →
   [メッセージの書き方](#メッセージの書き方)
5. 投げる側で `check` / `require` / `error` を使わない。キャストも `as` ではなく `as?` + `?:` →
   [内部の想定違反](#内部の想定違反)
6. `KatachiInternalException` 系なら、文面に「これはそのライブラリのバグ」と報告先を書く →
   [内部の想定違反](#内部の想定違反)
7. public 宣言なので KDoc と利用例を書く（形式は `kotlin.md` の「public 宣言」）

## 規約

### 命名

- `Katachi<例外を端的に表す名前>Exception` にして prefix, suffix を遵守する
- `AssertionError` を継承するものだけ、suffix も `Error` にする
  （現状 `KatachiArchitectureAssertionError` の1本）

→ [prefix を強制する理由](#prefix-を強制する理由)

### 基底を選ぶ

直接または間接的に、次の4つのいずれかを継承すること。分かれ目は**利用者が取るべき行動**。

| 基底 | いつ起きるか | 利用者が直す先 |
|---|---|---|
| `KatachiDeclarationException` | 定義に書かれた値が悪い。多くは `architecture { }` の評価中、ファイルを1つも読む前 | **定義そのもの**（役割名、モジュールパス、重複） |
| `KatachiCheckException` | 検査の実行中。定義は正しいが走らせられない | **環境**（git が無い、プロジェクトルートが見つからない） |
| `KatachiInternalException` | katachi 自身の前提が崩れた | **何も直せない。バグとして報告してもらう** |
| `KatachiArchitectureAssertionError` | 検査が完了し、違反が見つかった | **ファイルの配置**、または定義 |

継承元は行動の分類から決まる。

- `KatachiDeclarationException` は `IllegalArgumentException` を継承する。
  弾いているのは常に利用者が DSL に渡した値だから
- `KatachiCheckException` は `IllegalStateException` を継承する。
  渡された値ではなく、実行環境が期待どおりでない
- `KatachiInternalException` も `IllegalStateException` を継承する
- `KatachiArchitectureAssertionError` だけ `Error` 側で、`AssertionError` を継承する。
  テストフレームワークが「アサーション失敗」として扱うため

`KatachiDeclarationException` が評価中より遅れて出ることはある。`layout { }` は遅延評価なので
読めないキーは検査が平坦化するまで気づけないし、あとから渡し直された宣言が別の定義のものだったと
分かることもある。どちらも「定義に書かれた値が悪い」という同じ間違いを、katachi が最初に見られた
瞬間に見ているだけなので、基底は変えない。

新しい例外を足すときは、まずこの表のどれに当たるかを決める。
どれにも当たらないと感じたら、**基底を増やす前に分類を疑う**。
→ [基底が4つである理由](#基底が4つである理由)

**第三者の artifact が名乗ってよいのは `KatachiDeclarationException` と `KatachiInternalException`
の2つだけ。** この2つだけコンストラクタが `@ExperimentalKatachiApi public` で開いていて、
`cause: Throwable?` も取れる。残り2つは `internal constructor` のまま。
→ [第三者の artifact に2つの基底だけ開いている理由](#第三者の-artifact-に2つの基底だけ開いている理由)

### コンストラクタ引数

**`message` のような引数を雑に作らない。** 値をプロパティで受け取り、文面はクラスの中で組む。

文面が呼び出し側ごとに変わるなら、それは**別の例外**か、**分岐の材料をプロパティとして
受け取るべき**もの。後者の例
（`katachi/src/main/kotlin/me/tbsten/katachi/dsl/DeclarationExceptions.kt`）:

```kotlin
public class KatachiDuplicateDeclarationException internal constructor(
    public val kind: DeclarationKind,
    public val firstKind: DeclarationKind,
    public val name: String,
    public val scope: String,
    public val firstDeclaredAt: DeclarationSite,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Duplicate ${kind.label} \"$name\" declared at $declaredAt.")
        if (kind == firstKind) {
            appendLine("It was already declared at $firstDeclaredAt, in $scope.")
            append(kind.uniquenessRule)
        } else {
            appendLine(
                "A ${firstKind.label} of that name was already declared at $firstDeclaredAt, " +
                    "in $scope.",
            )
            append(
                "A group and a role in $scope would both be referred to as \"$name\". " +
                    "Rename one of them, or move the role into a group.",
            )
        }
    },
)
```

`kind` が group か role か、`firstKind` が先に名前を取った側。文面の分岐はこの2つで閉じていて、
呼び出し側は値を渡すだけになる。プロパティにしてあるので、**利用者が `catch` したときに
プログラムから判別できる**という利点も付く。

→ [message を引数にしない理由](#message-を引数にしない理由)

### メッセージの書き方

エラーメッセージの仕様は利用者向けの検査結果（`[UnexpectedFile]` など）と同じ方針に従う。

- **英語**で書く。利用者が書いた識別子（役割名、制約名）が引用として混ざるのは許容する
- **ASCII のみ。** `→` や `✗` は環境によって化ける。`->` で足りる
- ANSI エスケープを使わない
- **宣言位置（`ファイル名:行番号`）を含める。** DSL 評価時のエラーは、どこを直せばよいかが
  行番号で分かるかどうかで直しやすさが変わる
- **何が起きたか → なぜだめか → どう直すか**の順で書く。1行目は事実だけにする

`KatachiInvalidIdentifierException` の実際の出力:

```
Invalid role name "ユースケース" declared at ProjectArchitecture.kt:42.
Names must match [A-Za-z][A-Za-z0-9_-]*: start with an ASCII letter, then ASCII letters, digits, '_' or '-'.
Use `title` for a human readable display name.
```

最後の1行が効く。**利用者がやりたかったこと（読みやすい名前を付けたい）に対して、正しい場所を示している。**

### 内部の想定違反

利用者の入力ではなく、katachi 自身の前提が崩れた場合。**ここにも専用の例外を定義する。**

- **`check` / `require` / `checkNotNull` / `requireNotNull` / `error` を使わない。**
  代わりに `KatachiInternalException` を継承した専用の例外を定義する
- **キャストは `as` ではなく `as?` + `?:` で落とす**（`kotlin.md` の `as` 禁止）
- メッセージには**これが（それを投げた）そのライブラリのバグであること**と、**報告先**を書く。
  利用者に直せる話ではないので、「どう直すか」の代わりに「報告してほしい」を置く。
  読み手がどこに issue を立てればよいかを、メッセージだけで判断できるようにするため

`katachi` が投げるものは「a bug in katachi」と、報告先に
`https://github.com/TBSten/katachi/issues` を書く。`katachi-konsist` の
`KatachiKonsistScopeIncompleteException` も現状これと同じ文面で、同じリポジトリから出る
artifact なので報告先も katachi の issue のまま。`KatachiInternalException` を名乗る
**外部の**第三者の artifact なら、そのライブラリ名と、そのライブラリの報告先を書く。

→ [標準の precondition を使わない理由](#標準の-precondition-を使わない理由)

実物（`katachi/src/main/kotlin/me/tbsten/katachi/dsl/InternalExceptions.kt` と
`katachi/src/main/kotlin/me/tbsten/katachi/dsl/gradle/LayoutScopeInternals.kt`）:

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

### 検査は途中で死なない

**走査は最小でもファイル単位で握る。** 1 ファイルの失敗はそのファイルの結果になるだけで、
残りの走査は続く。握りは `katachi/src/main/kotlin/me/tbsten/katachi/scan/Catching.kt` の
1箇所に置く。

```kotlin
internal inline fun <T> catching(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it.isFatal) throw it }
```

握った失敗は **`Violation` として報告に載せ、検査を失敗させる**。黙って緑にするのは最悪で、
「動いているつもりの穴」を作る。

- 失敗した単位ごとに `ViolationKind.Failed` の violation を作る。`UncheckedFile` /
  `UncheckedDirectory`（既存の `UnexpectedFile` / `UnexpectedDirectory` と同じ対）、
  `UncheckedConstraint`、`UncheckedCheck` がそれ
- 握った `Throwable` も持たせ、`[UncheckedFile]` として他のブロックと並べる。ブロックには
  **型名と message の1行目**だけを出す（`ViolationReport.causeLine`）。スタックトレース全体は出さない —
  利用者がそのまま issue に貼れる程度に
- 報告の**先頭**に件数の行（`Katachi check failed: N violations (...)`）を出す。長い出力は
  末尾から切られるため
- **「部分的な結果である」と言う行**（`3 files could not be checked.` など）は、`maxViolations`
  による打ち切りの行より**後ろ**に、打ち切りに巻き込まれない場所へ置く。これが部分性を伝える
  唯一の保証になる
- `ViolationKind.Failed` は enum の**最後**に置く。`Scan` と `validate` が
  `sortedBy { it.kind.ordinal }` でブロックを並べているので、先頭に足すと既存の報告の並びが全部ずれる

→ [失敗を握って走査を続ける理由](#失敗を握って走査を続ける理由) /
[握る粒度をどこまで広げるか](#握る粒度をどこまで広げるか)

### 握ってはいけないもの

**`runCatching` は `Throwable` を全部飲む。** そのままでは使えない。飲んだあとに
致命的なものを投げ直すこと（`Throwable.isFatal`）。

| 投げ直す | なぜ |
|---|---|
| `VirtualMachineError`（`OutOfMemoryError` / `StackOverflowError`） | JVM がもう続けられない。握っても次のファイルで同じことが起きるだけで、原因だけが消える |
| `LinkageError` | classpath が壊れている。全ファイルで失敗するので、報告が N 件の同じノイズで埋まる |
| `InterruptedException` | 呼び出し側がやめろと言っている。握るのは無視すること |
| `AssertionError`（`KatachiArchitectureAssertionError` を含む） | これは**結果**であって失敗ではない |

`KatachiInternalException` は**握ってよい**。katachi のバグだが、そのファイル 1 つを
`[UncheckedFile]` にして残りを検査できるなら、その方が利用者に渡せるものが多い。

投げ直す側を `KatachiArchitectureAssertionError` ではなく `AssertionError` にしてあるのは、
走査が `check` より下の層に居て、上の層のクラス名を書けないため。範囲が広がる方向で、
**利用者のテストハーネスが偽のファイルシステムの中から投げたアサーションも、結果であって
走査の失敗ではない**、という理屈も立つ。

## なぜそうなっているか

### prefix を強制する理由

利用者のテストが落ちたとき、スタックトレースにはライブラリのクラス名がそのまま出る。
`GlobSyntaxException` のような一般名だと、どのライブラリが投げたのかが出力から分からない。
prefix はそのためにある。

### 基底が4つである理由

katachi が投げるものは、**利用者が取るべき行動**で分かれる。定義を直す／環境を直す／何もできず
報告する／ファイルの配置を直す、の4つしかない。基底の数はこの4つに対応しているので、
「どれにも当たらない」と感じたときに増やすべきなのはたいてい基底ではなく、行動の読み違いの方。

### 第三者の artifact に2つの基底だけ開いている理由

konsist 連携（`katachi-konsist`）を別 artifact として足したときに決めた。

```kotlin
public abstract class KatachiDeclarationException
@ExperimentalKatachiApi public constructor(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

public abstract class KatachiInternalException
@ExperimentalKatachiApi public constructor(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
```

- **第三者のアーキテクチャ検査バックエンド（`katachi-konsist` のような別 artifact）が、
  この2つの基底を名乗ってよい。** `katachi-konsist` の `KatachiKonsist*Exception` 5本はこの経路で
  作られている — もし `katachi` の外の第三者が同じことをしたければ、同じ2つの基底を継承すればよい
- **どちらも `cause: Throwable?` を新たに取れる。** 既存のサブクラスは全部 `message =` を
  名前付きで渡しているので無傷（コンストラクタのシグネチャが増えても呼び出し側は変わらない）。
  `IllegalArgumentException(message, cause)` / `IllegalStateException(message, cause)` に
  そのまま渡す
- **`KatachiCheckException` と `KatachiArchitectureAssertionError` は `internal constructor` のまま。**
  前者は「定義は正しいが**環境**が走らせられない」（git が無い、ルートが見つからない）という
  katachi 自身の走査の話で、第三者バックエンドが名乗る理由がない。後者は検査の**結果**を表す型で、
  `assert()` という一番外側の薄い皮だけが投げるべきもの
- **`ExperimentalKatachiApi` は `AnnotationTarget.CONSTRUCTOR` を持つ**ので、コンストラクタへの
  付与は構文として通る
- `KatachiInternalException` の「メッセージにはこれが katachi のバグであると書く」という規約は、
  この2つが開いたことで**「これが（それを投げた）そのライブラリのバグであると書く」**に一般化した。
  これが今の規約（[内部の想定違反](#内部の想定違反)）。`katachi` 自身が投げるものは結果として
  今までどおり「katachi のバグ」になるが、規約の文としては katachi 固定で書き直さないこと —
  読み手（利用者）がどこに issue を立てればよいかを、メッセージだけで判断できるようにするため

### message を引数にしない理由

`message` を引数にすると、同じ例外に対して呼び出し側ごとに文面を書けてしまう。
実際 `KatachiDuplicateDeclarationException` は group 用と role 用で別々の文面を渡されていて、
**同じ例外なのに2通りの言い回しが存在していた**。

文面を例外の中で組み立てると次が揃う。

- 例外が公開しているプロパティ（`name` / `declaredAt` など）と、文面に出る値が必ず一致する
- 文面を直すとき、探す場所が例外クラス1箇所に決まる
- 呼び出し側は**値を渡すだけ**になり、何を渡すべきかがシグネチャから分かる

### 標準の precondition を使わない理由

`check` / `require` / `checkNotNull` / `requireNotNull` / `error` が投げるのは
`IllegalStateException` と `IllegalArgumentException` で、スタックトレースに katachi の名前が
出ない。利用者は「自分のコードが悪いのか、katachi が悪いのか」を切り分けられないまま
放り出される。専用の例外なら名前が出どころを語り、`catch` して報告できる。

素の `ClassCastException` も同じで、何が起きたのか読み手に伝わらない。`as` を禁じているのは
このため。

「起きないはずだから雑でいい」は逆で、**起きないはずのものが起きた瞬間ほど、出どころが
名前で分かることに意味がある**。

### 失敗を握って走査を続ける理由

`assert()` の値打ちは「1 回走らせれば全部わかる」ことにある。エージェントに 1 回読ませれば
直す材料が揃う、という一点で設計している。**1 ファイルで想定外の例外が出た瞬間に全部が
消えると、利用者は違反を 1 つも見られないまま、katachi の内部エラーだけを見せられる。**
しかもそのファイルは、たいてい直したい違反とは関係のないファイルである。

### 握る粒度をどこまで広げるか

最小がファイル 1 つ、というだけで、上位の単位に同じ考え方を広げてよい。役割 1 つ、
モジュール 1 つ、サンプル 1 本。**「ここで失敗しても、隣は独立して答えを出せるか」**が判断の軸。
独立していないなら握っても意味がないので、素通しする。

いま広げてあるもの:

- **走査の前段のモジュール探索。** ディレクトリ 1 つが読めなくても隣のサブツリーは独立して
  モジュールを答えられるので、子 1 つ単位で握り、`UncheckedDirectory`
  （`reason = ModulesNotDiscovered`）として報告に載せる
- **`validate` が受け取った check 1 つ、constraint 1 つ。** 片方が投げても残りは答えを出せるので、
  `UncheckedCheck` / `UncheckedConstraint` にして走査を続ける

一方**ルート探索は握らない** — ルートが決まらなければ検査するものが 1 つも残らず、
「隣」が存在しないため。

/*
 * トップページ（Home.astro）の文言の辞書。
 *
 * **日本語（ja）が原本、英語（en）は訳。** 文言を直すときはこのファイルだけを直す。
 * ページの構造（section / div / クラス）は Home.astro の1か所にしかない。
 *
 * - ja を先に直し、同じキーの en をその場で訳す。en だけにキーを足さない（型が ja から決まる）
 * - `Html` で終わるキーは HTML として出す（`<br />`・`<code>`・`<strong>` などを含められる）
 * - サイト内リンクは base と locale を付けない形（`get-started/motivation/`）で書く。
 *   Home.astro が `/katachi/` や `/katachi/ja/` を前に付ける
 * - コード例の中の日本語も訳す（訳さないと英語ページに日本語が出る）
 */

const ja = {
	title: 'katachi',
	description:
		'Kotlin DSL でアーキテクチャを定義し、プロジェクト全体の構造を deny by default で検査するライブラリ。',

	hero: {
		eyebrow: 'Kotlin architecture test',
		leadHtml: 'katachi で<br />動くアーキテクチャドキュメントを',
		intro:
			'宣言していないファイルは、存在を許されない。プロジェクト全体の「どこに何を置けるか」を Kotlin DSL で書き、テストとして検証します。',
		actionsLabel: 'はじめる',
		getStarted: '導入する →',
		github: 'GitHub ↗',
		codeTitle: 'ProjectArchitecture.kt',
		code: `val projectArchitecture = architecture {
  "domain".group {
    "UseCase" {
      layout {
        "core/domain" / mainSourceSet / kotlin /
          "com/example/core/domain/useCase" / "*UseCase".ktFile()
      }
    }
  }
}`,
	},

	problem: {
		titleHtml: '崩れるのは<br />いつも1ファイルから。',
		body: '置き場所の逸脱は、レビューで見逃しがちです。AI Agent がファイルを作るときも、手本にするのは文書ではなく既存の配置。1つずれれば、そこから複製されていきます。',
		emphasis: 'ドキュメントには、強制力がない。',
		treeLabel: '検査されていないディレクトリ構造の例',
		treeCaption: 'src/main/kotlin/com/example/',
		tree: `├─ useCase/
│  ├─ GetUserUseCase.kt
│  └─ UpdateUserUseCase.kt
├─ DeleteUserUseCase.kt  ← 置き場所が違う
└─ misc/
   └─ memo.md            ← 役割がない`,
		treeNote: 'この2つを、誰も検査していない',
	},

	solution: {
		titleHtml: '宣言した場所だけが<br />存在を許される',
		body: 'katachi では、存在してよいものを宣言します。禁止を数え上げるのではありません。',
		emphasis: 'Deny by default — 宣言にないものは、存在できない。',
		treeLabel: 'katachi が許可したディレクトリ構造の例',
		treeCaption: 'ProjectArchitecture.kt が許可した範囲',
		tree: `src/main/kotlin/com/example/
├─ useCase/*UseCase.kt       ✓ declared
├─ repository/*Repository.kt ✓ declared
├─ DeleteUserUseCase.kt      ✕ reported
└─ misc/memo.md              ✕ reported`,
		treeLegendHtml:
			'<span aria-hidden="true">●</span> 宣言済み　<span aria-hidden="true">●</span> 違反として報告される',
	},

	pillars: {
		label: 'katachi の三つの柱',
		deny: {
			titleHtml: '宣言にないファイルは<br />アーキテクチャ違反',
			bodyHtml:
				'禁止を数え上げる運用から、存在してよいものを宣言する運用へ。「いつの間にか増えた置き場所」が起きません。',
			code: `"UseCase" { layout { "domain/useCase" / "*UseCase".ktFile() } }
// domain/useCase/Helper.kt → [UnexpectedFile]`,
		},
		role: {
			titleHtml: 'Role 駆動による<br />アーキテクチャ定義',
			bodyHtml:
				'1つの Role が2つのモジュールに住んでいても、説明は1箇所のまま。置き場所は <code dir="auto">layout { }</code> を並べて宣言します。',
			code: `"UseCase" {
  summary = "各画面で発生するアプリ固有の1つの振る舞い"
  layout { "core/domain/useCase" / "*UseCase".ktFile() }
  layout { "feature/home/useCase" / "*UseCase".ktFile() }
}`,
		},
		docs: {
			titleHtml: '検査した定義が、<br />そのままドキュメントへ',
			// リンクの前後で分ける。リンク先は `link`（locale なし）。
			bodyBeforeLinkHtml:
				'<code dir="auto">title</code> / <code dir="auto">summary</code> / <code dir="auto">example</code> を Role の中に書きます。v0.2 からは <code dir="auto">./gradlew katachiDocs</code> で、同じ定義から',
			linkText: 'ドキュメントを生成',
			link: 'guides/document-generation/',
			bodyAfterLinkHtml: 'できます。',
			code: `"UseCase" {
  title = "ユースケース"
  summary = "各画面で発生するアプリ固有の1つの振る舞い"
  example("GetUserUseCase", "ユーザーを取得する")
}`,
		},
	},

	agents: {
		titleHtml: 'Built for AI agents.<br /><em>Good for humans too.</em>',
		bodyHtml:
			'エージェントにとって有効なのは、文章ではなく落ちるテストです。',
	},

	cta: {
		title: 'あなたのプロジェクトの形を、宣言してみる。',
		body: 'プロジェクト全体の構成を定義し、テストを実行しましょう。宣言にないファイルは違反として報告されます。',
		getStarted: { title: '導入する', note: '定義を作り、テストを通すまでの手順。' },
		motivation: {
			title: '設計思想を読む',
			note: 'なぜ deny by default なのか、Konsist との住み分け。',
		},
		github: { title: 'GitHub', note: '実装とロードマップ。' },
	},
};

export type HomeStrings = typeof ja;

const en: HomeStrings = {
	title: 'katachi',
	description:
		'A library that defines your architecture in a Kotlin DSL and checks the structure of the whole project, deny by default.',

	hero: {
		eyebrow: 'Kotlin architecture test',
		leadHtml: 'Architecture docs that run,<br />with katachi',
		intro:
			'A file you never declared is not allowed to exist. Write what may go where across the whole project in a Kotlin DSL, and verify it as a test.',
		actionsLabel: 'Get started',
		getStarted: 'Get started →',
		github: 'GitHub ↗',
		codeTitle: 'ProjectArchitecture.kt',
		code: ja.hero.code,
	},

	problem: {
		titleHtml: 'It always starts<br />with a single file.',
		body: 'A file in the wrong place is easy to miss in review. And when an AI agent writes one, what it imitates is not your documentation — it is the placement already in the tree. One file off, and it gets copied from there.',
		emphasis: 'Documentation has no way to enforce itself.',
		treeLabel: 'Example of a directory structure nobody checks',
		treeCaption: 'src/main/kotlin/com/example/',
		tree: `├─ useCase/
│  ├─ GetUserUseCase.kt
│  └─ UpdateUserUseCase.kt
├─ DeleteUserUseCase.kt  ← in the wrong place
└─ misc/
   └─ memo.md            ← has no role`,
		treeNote: 'Nobody is checking these two',
	},

	solution: {
		titleHtml: 'Only what you declared<br />is allowed to exist',
		body: 'In katachi you declare what may exist. You do not enumerate what is forbidden.',
		emphasis: 'Deny by default — anything not in the declaration cannot exist.',
		treeLabel: 'Example of a directory structure allowed by katachi',
		treeCaption: 'What ProjectArchitecture.kt allows',
		tree: ja.solution.tree,
		treeLegendHtml:
			'<span aria-hidden="true">●</span> Declared&nbsp;&nbsp;<span aria-hidden="true">●</span> Reported as a violation',
	},

	pillars: {
		label: 'The three pillars of katachi',
		deny: {
			titleHtml: 'A file you never declared<br />is an architecture violation',
			bodyHtml:
				'Stop enumerating what is forbidden; declare what may exist instead. Locations no longer appear out of nowhere.',
			code: ja.pillars.deny.code,
		},
		role: {
			titleHtml: 'Architecture definitions<br />driven by roles',
			bodyHtml:
				'Even when one role lives in two modules, the explanation stays in one place. Declare its placements by listing <code dir="auto">layout { }</code> blocks.',
			code: `"UseCase" {
  summary = "A single app-specific behavior that occurs on a screen"
  layout { "core/domain/useCase" / "*UseCase".ktFile() }
  layout { "feature/home/useCase" / "*UseCase".ktFile() }
}`,
		},
		docs: {
			titleHtml: 'The definition you check<br />becomes the documentation',
			bodyBeforeLinkHtml:
				'Write <code dir="auto">title</code>, <code dir="auto">summary</code>, and <code dir="auto">example</code> inside the role. From v0.2, <code dir="auto">./gradlew katachiDocs</code> ',
			linkText: 'generates documentation',
			link: 'guides/document-generation/',
			bodyAfterLinkHtml: ' from that same definition.',
			code: `"UseCase" {
  title = "Use case"
  summary = "A single app-specific behavior that occurs on a screen"
  example("GetUserUseCase", "Gets a user")
}`,
		},
	},

	agents: {
		titleHtml: ja.agents.titleHtml,
		bodyHtml:
			'What gets through to an agent is a failing test, not prose.',
	},

	cta: {
		title: 'Declare the shape of your project.',
		body: 'Define the structure of the whole project and run the test. Any file that is not declared is reported as a violation.',
		getStarted: { title: 'Get started', note: 'From writing a definition to a passing test.' },
		motivation: {
			title: 'Read the design philosophy',
			note: 'Why deny by default, and where Konsist fits alongside it.',
		},
		github: { title: 'GitHub', note: 'The implementation and the roadmap.' },
	},
};

export const homeStrings = { ja, en } as const;

export type HomeLang = keyof typeof homeStrings;

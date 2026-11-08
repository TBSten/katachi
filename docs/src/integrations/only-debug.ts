import type { AstroIntegration } from 'astro'

/**
 * `<OnlyDebug> … </OnlyDebug>` で囲んだ MDX の本文を、`astro dev` のときだけ出す統合。
 *
 * 書きかけの参考資料（CLI の実装から書き出した一覧など）を本文のそばに置いておきたいが、
 * 公開サイトには出したくない、という用途。本番ビルド（リリース版も `/katachi/dev/` の写しも
 * `astro build`）では mdast の段階で要素ごと落とすので、本文・目次・リンク検査のどれにも残らない。
 * `astro dev` では中身をそのまま出し、開発用だと分かる枠と見出しを付ける（見た目は styles/only-debug.css）。
 *
 * Sätteri の mdast プラグインとして差すので、ページ側に import は要らない。囲む要素はブロック
 * （`<OnlyDebug>` を行頭に、前後に空行）で書く。インライン（文中）の `<OnlyDebug>` は対象にしない。
 */
export default function onlyDebug(): AstroIntegration {
	let isDev = false
	return {
		name: 'katachi-only-debug',
		hooks: {
			'astro:config:setup': ({ command }) => {
				isDev = command === 'dev'
			},
			'astro:config:done': ({ config, logger }) => {
				const processor = config.markdown?.processor as { name?: string; options?: { mdastPlugins?: unknown[] } } | undefined
				const list = processor?.name === 'satteri' ? processor.options?.mdastPlugins : undefined
				if (!list) return logger.warn('markdown.processor が Sätteri ではないので、<OnlyDebug> は処理しません')
				list.unshift(createOnlyDebugPlugin(isDev))
			},
		},
	}
}

const TAG = 'OnlyDebug'

type JsxNode = { type: string; name?: string | null; children: unknown[] }
type Ctx = {
	removeNode(node: unknown): void
	replaceNode(node: unknown, replacement: unknown[]): void
}

/** dev なら中身を枠で囲んで残し、それ以外なら要素ごと落とす mdast プラグイン。 */
export function createOnlyDebugPlugin(isDev: boolean) {
	return {
		name: 'katachi-only-debug',
		mdxJsxFlowElement(node: JsxNode, ctx: Ctx) {
			if (node.name !== TAG) return
			if (!isDev) return ctx.removeNode(node)
			ctx.replaceNode(node, [
				{
					type: 'html',
					value:
						'<aside class="only-debug" aria-label="開発用の下書き">' +
						'<p class="only-debug__label">開発サーバーでだけ表示（ビルドには含まれない）</p>',
				},
				...node.children,
				{ type: 'html', value: '</aside>' },
			])
		},
	}
}

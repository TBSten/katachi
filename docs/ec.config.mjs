// @ts-check
// Expressive Code（コードブロック）の設定。astro.config.mjs からここへ出してある。
// astro.config.mjs の中にプラグイン（関数）を書くと、トップページの `<Code>` コンポーネントが
// 「設定を JSON にできない」と言って描画できないため。Expressive Code がこのファイルを自動で読む。
import { defineEcConfig } from '@astrojs/starlight/expressive-code';
import { pluginCollapsible } from 'expressive-code-collapsible';

export default defineEcConfig({
	plugins: [
		// 長いコードブロックを畳む。手触りの検証中で、しきい値は暫定。
		//
		// ボタンの文言は指定しない。プラグインは i18n に対応しておらず
		// 全ロケールに同じ文字列が出るため、日本語を入れると英語版
		// （既定ロケール）にも日本語のボタンが出る。llms-full.txt にも
		// そのまま混入していた（英語版に43箇所）。既定は Show more / Show less。
		pluginCollapsible({
			lineThreshold: 15,
			previewLines: 8,
			defaultCollapsed: true,
		}),
	],
});

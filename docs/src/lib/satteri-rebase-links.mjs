// 本文（MDX）の絶対リンクを、ビルドの `base` に合わせて付け替える Sätteri（Astro の Markdown 処理系）の
// mdast プラグイン。
//
// 本文のリンクは公開 URL の `/katachi/...` で書く決まり（docs/AGENTS.md）。開発版のビルド
// （docs.yml の main 側、`KATACHI_DOCS_BASE=/katachi/dev`）では base が違うので、そのままだと
// `/katachi/guides/role/` が開発版の外（リリース版）を指し、リンク検査も落ちる。
// ここで `/katachi/...` → `/katachi/dev/...` に付け替える。base が `/katachi` のときは何もしない。
//
// 対象: Markdown のリンク・画像・定義の url、MDX の JSX（`<LinkCard href="…">` など）の
// href / src / url 属性の文字列。mdast の段階で直すので、hast を見る starlight-links-validator は
// 付け替えたあとのリンクを検査する。
import { base, writtenBase } from './versions.mjs';

const ATTRIBUTES = new Set(['href', 'src', 'url']);

/** `writtenBase` で始まる絶対パスなら `base` に付け替える。それ以外（相対・外部・別の base）はそのまま。 */
export function rebase(url) {
  if (typeof url !== 'string') return url;
  if (url === writtenBase || url === `${writtenBase}/`) return `${base}/`;
  if (!url.startsWith(`${writtenBase}/`)) return url;
  return `${base}${url.slice(writtenBase.length)}`;
}

/** @returns Sätteri の mdast プラグイン。base が `writtenBase` のままなら `false`（パイプラインに入れない）。 */
export function satteriRebaseLinks() {
  if (base === writtenBase) return false;

  const rebaseUrl = (node, ctx) => {
    const url = rebase(node.url);
    if (url !== node.url) ctx.setProperty(node, 'url', url);
  };
  const rebaseAttributes = (node, ctx) => {
    let changed = false;
    const attributes = node.attributes.map((attribute) => {
      if (attribute.type !== 'mdxJsxAttribute' || !ATTRIBUTES.has(attribute.name) || typeof attribute.value !== 'string') {
        return attribute;
      }
      const value = rebase(attribute.value);
      if (value === attribute.value) return attribute;
      changed = true;
      return { ...attribute, value };
    });
    // `attributes` は setProperty で書けない（Sätteri 0.10 の制限）ので、同じ子を持つ節に置き換える。
    if (changed) ctx.replaceNode(node, { type: node.type, name: node.name, attributes, children: node.children });
  };

  return {
    name: 'katachi-rebase-links',
    link: rebaseUrl,
    image: rebaseUrl,
    definition: rebaseUrl,
    mdxJsxFlowElement: rebaseAttributes,
    mdxJsxTextElement: rebaseAttributes,
  };
}

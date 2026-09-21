import Root from './CodeComparison.astro';
import Panel from './CodeComparisonPanel.astro';

/**
 * `<CodeComparison>` と `<CodeComparison.Panel>` をひとつの import で使えるようにする。
 *
 * Astro には React のような複合コンポーネントの仕組みが無いので、
 * コンポーネント（実体はただのオブジェクト）にプロパティを生やして
 * ドット記法を作っている。MDX は `<Foo.Bar>` を `Foo.Bar` の
 * プロパティアクセスにコンパイルするだけなので、これで解決する。
 */
export default Object.assign(Root, { Panel });

/**
 * `@astrojs/starlight/components` の代わりに読まれる入口（astro.config.mjs の
 * `katachiLinkCardOverride`）。LinkCard だけ自前のものにし、あとは Starlight のまま再エクスポートする。
 */
export * from '@astrojs/starlight/components';
export { default as LinkCard } from './LinkCard.astro';

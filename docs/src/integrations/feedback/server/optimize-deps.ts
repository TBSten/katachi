// dev の Vite に渡す、依存の事前バンドル（optimizeDeps）の設定。

/**
 * 事前バンドル（node_modules/.vite/deps/）に入れない依存。撮影のときに動的 import する modern-screenshot。
 *
 * deps/ は同じ node_modules を使うほかのプロセスに作り直される。dev の横で回した `astro build` は、設定が dev と違うので
 * deps/ を作り直し、dev でしか使わない依存を消す。dev は消えたファイルの URL を覚えたままなので 504 を返し、
 * 撮影は「Failed to fetch dynamically imported module」で失敗する。読み直しても dev を立て直すまで直らない。
 * 外しておけば node_modules の中のファイルを直接配るので、deps/ が作り直されても動く。
 * 初めて撮るときに依存が見つかってページが再読み込みされることも無い（外した依存は探されない）。
 *
 * 外せるのは、依存の無い ESM だけ（CommonJS や、ほかの package を import するものは事前バンドルが要る）。
 * test/optimize-deps.test.ts が、入口から辿って bare import が無いことを確かめる。
 *
 * toolbar アプリの入口の astro/toolbar も同じ理由で消えるが、Astro が toolbar アプリのあるときに include へ足すので、
 * ここで外しても外れない。アプリ（feedback/app.ts・memo/app.ts）は astro/toolbar を import せず、型だけを astro から取る。
 */
export const UNBUNDLED_DEPS = ['modern-screenshot'] as const

/** `astro dev` のときだけ足す Vite の設定。 */
export function devViteConfig() {
	return { optimizeDeps: { exclude: [...UNBUNDLED_DEPS] } }
}

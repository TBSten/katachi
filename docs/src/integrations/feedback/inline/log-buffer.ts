// ページの読み込みより前に入れる、ブラウザのエラーの置き場（dev 専用）。
// console の error・warn・log、未捕捉の例外、unhandledrejection、画像や script の読み込み失敗、
// Vite の HMR のエラー（vite:error）を、直近の分だけ window に溜める。読むのは feedback/collect.ts。
// 文字列で渡すのは、ページの <head> にそのまま差す script だから（バンドルを通らない）。
import { CONSOLE_LIMITS, LOG_GLOBAL } from '../core/console-section'

/** head-inline に差す script の本文。ES5 で書く（読み込み前の最初の 1 回だけ動けばよい）。 */
export const LOG_BUFFER_SCRIPT = `(function () {
  var G = ${JSON.stringify(LOG_GLOBAL)}, MAX = ${CONSOLE_LIMITS.bufferEntries}, LEN = ${CONSOLE_LIMITS.messageChars};
  if (window[G]) return;
  var buf = [];
  window[G] = { entries: function () { return buf.slice(); } };
  function text(v) {
    if (typeof v === 'string') return v;
    if (v && typeof v === 'object' && typeof v.message === 'string') return v.stack || v.name + ': ' + v.message;
    try { return JSON.stringify(v); } catch (e) { return String(v); }
  }
  function push(k, m) {
    m = String(m);
    if (m.length > LEN) m = m.slice(0, LEN) + '…';
    buf.push({ t: Date.now(), k: k, m: m });
    if (buf.length > MAX) buf.shift();
  }
  ['error', 'warn', 'log'].forEach(function (level) {
    var orig = console[level];
    console[level] = function () {
      try { push(level, Array.prototype.map.call(arguments, text).join(' ')); } catch (e) {}
      return orig.apply(console, arguments);
    };
  });
  // 読み込み失敗は window まで bubble しないので capture で受ける。
  window.addEventListener('error', function (e) {
    var t = e.target;
    if (t && t !== window && t.tagName) {
      push('resource', '<' + t.tagName.toLowerCase() + '> の読み込みに失敗: ' + (t.currentSrc || t.src || t.href || ''));
    } else {
      push('exception', (e.message || text(e.error)) + (e.filename ? ' (' + e.filename + ':' + e.lineno + ':' + e.colno + ')' : ''));
    }
  }, true);
  window.addEventListener('unhandledrejection', function (e) { push('rejection', text(e.reason)); });
  // Vite の HMR のエラー。ここは module ではないので、dev の client を動的に読んで hot に繋ぐ。
  try {
    import('/@vite/client').then(function (m) {
      m.createHotContext('/@katachi-feedback-log').on('vite:error', function (p) {
        push('vite', text(p && p.err ? (p.err.message || p.err) : p));
      });
    }).catch(function () {});
  } catch (e) {}
})();`

// 要素・テキストを選んで入力欄（スマホはシート、PC は popover）を開き、送るまでの列。モードの開始と終わりも含む。
import { test, ok, sleep } from './helpers/fixtures'
import { files, readRecord } from './helpers/store'
import { gap, frameOk } from './helpers/geometry'

test.describe('スマホ: タップで選んでシートから送る', () => {
	test.beforeEach(({ fb }) => test.skip(!fb.mobile, 'スマホの列'))

	test('モードの外の長押し（contextmenu）では自前の UI が出ず、標準のまま', async ({ fb }) => {
		const { page } = fb
		await fb.fresh()
		await page.evaluate(() => {
			const p = document.querySelector('main p')!
			const n = [...p.childNodes].find((x) => x.nodeType === 3 && (x.textContent ?? '').trim().length > 14)!
			const r = document.createRange()
			r.setStart(n, 0)
			r.setEnd(n, 12)
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
		})
		const prevented = await page.evaluate(() => {
			const e = new MouseEvent('contextmenu', { bubbles: true, cancelable: true, clientX: 100, clientY: 200 })
			document.querySelector('main p')!.dispatchEvent(e)
			return e.defaultPrevented
		})
		await page.waitForTimeout(300)
		ok('自前の UI が出ない・標準のまま', (await fb.rootCount()) === 0 && !prevented)
	})

	test('要素をタップ → ボタン → シート → 送信で記録ができ、UI も toolbar も閉じる', async ({ fb }) => {
		const { page } = fb
		await fb.fresh()
		await fb.enter()
		ok('モード開始で UI が出る', (await fb.rootCount()) === 1)
		await fb.shot('1-mode')
		const before = files().length
		await fb.para.tap({ position: { x: 6, y: 6 } })
		await page.waitForTimeout(300)
		const fab = fb.inUi('.fab')
		const vs = page.viewportSize()!
		ok('要素タップでボタンが出る', await fab.isVisible())
		const fr = (await fab.boundingBox())!
		ok('ボタンは画面内', fr.x >= 0 && fr.x + fr.width <= vs.width && fr.y >= 0 && fr.y + fr.height <= vs.height, JSON.stringify(fr))
		ok('枠が出る', await fb.inUi('.frame.picked').evaluate((e) => getComputedStyle(e).display !== 'none'))
		ok('タッチで hover 枠は出ない', await fb.inUi('.frame:not(.picked)').evaluate((e) => getComputedStyle(e).display === 'none'))
		await fb.shot('2-button')
		await fab.tap()
		await page.waitForTimeout(400)
		const d = (await fb.inUi('.dialog').boundingBox())!
		ok('シートは幅いっぱい・下端', Math.abs(d.width - vs.width) <= 1 && Math.abs(d.y + d.height - vs.height) <= 1, JSON.stringify(d))
		ok('最大高さ 85dvh 以下', d.height <= vs.height * 0.85 + 1, `${d.height} <= ${vs.height * 0.85}`)
		const ta = fb.inUi('.dialog textarea')
		await ta.tap()
		await ta.fill('スマホから要素タップで送るテスト')
		const fontSize = await ta.evaluate((e) => getComputedStyle(e).fontSize)
		ok('textarea の font-size >= 16px（iOS のズーム回避）', parseFloat(fontSize) >= 16, fontSize)
		const sub = fb.inUi('.dialog .primary')
		const sb = (await sub.boundingBox())!
		ok('送信ボタンの高さ >= 44px', sb.height >= 44, String(sb.height))
		ok('チェック行の高さ >= 44px', (await fb.inUi('.dialog summary').first().boundingBox())!.height >= 44)
		ok('送信ボタンはスクロール無しで画面内', sb.y >= 0 && sb.y + sb.height <= vs.height, JSON.stringify(sb))
		await fb.shot('3-sheet')
		await sub.tap()
		await fb.waitSaved(before)
		ok('記録ができる', files().length === before + 1, files().join(','))
		ok('UI の DOM が残らない', (await fb.rootCount()) === 0)
		ok('保存後 toolbar のアプリも閉じる', !(await fb.appActive()), await fb.attrs())
		await fb.shot('4-after')
		ok('「入力」が載る', /入力: pointer: coarse=true/.test(readRecord(files().at(-1)!)))
	})

	test('テキストを選ぶとボタンが出て、選んだ文字が記録に入る', async ({ fb }) => {
		const { page } = fb
		await fb.fresh()
		await fb.enter()
		const picked = (await fb.selectRange(0, 12)).text
		await page.waitForTimeout(500)
		ok('選択でボタンが出る（selectionchange）', await fb.inUi('.fab').isVisible(), `選択=${JSON.stringify(picked)}`)
		ok('要素枠は出ない', await fb.inUi('.frame.picked').evaluate((e) => getComputedStyle(e).display === 'none'))
		await fb.shot('5-text-button')
		// ボタンのタップが選択を上書きしないこと
		await fb.inUi('.fab').tap()
		await page.waitForTimeout(400)
		await fb.inUi('.dialog textarea').fill('テキスト選択のテスト')
		const before = files().length
		await fb.inUi('.dialog .primary').tap()
		await fb.waitSaved(before)
		const md = readRecord(files().at(-1)!)
		ok('選んだテキストが記録に入る', files().length === before + 1 && md.includes(picked.trim()) && /## 選んだ内容/.test(md), JSON.stringify(picked))
		ok('保存後 UI なし', (await fb.rootCount()) === 0)
	})

	test('選択の外をタップ → 要素選択 → 外タップ → リンク → やめる、の列', async ({ fb }) => {
		const { page } = fb
		await fb.fresh()
		await fb.enter()
		await fb.selectRange(0, 10)
		await page.waitForTimeout(500)
		const visBefore = await fb.inUi('.fab').isVisible()
		await page.locator('main h1').first().tap()
		await page.waitForTimeout(300)
		ok('テキスト選択の外タップでボタンが消える', visBefore && !(await fb.inUi('.fab').isVisible()))
		ok('モードは続く', (await fb.rootCount()) === 1)
		await page.waitForTimeout(400)
		ok('そのタップで要素は選ばれない', !(await fb.inUi('.frame.picked').evaluate((e) => getComputedStyle(e).display !== 'none')))
		await fb.para.tap({ position: { x: 6, y: 6 } })
		await page.waitForTimeout(300)
		ok('要素選択後はボタンが出ている（前段）', await fb.inUi('.fab').isVisible())
		await page.locator('main h1').first().tap()
		await page.waitForTimeout(300)
		ok('要素の外タップで枠もボタンも消える', !(await fb.inUi('.fab').isVisible()) && (await fb.inUi('.frame.picked').evaluate((e) => getComputedStyle(e).display === 'none')))

		const href0 = page.url()
		await page.locator('main p a').first().tap()
		await page.waitForTimeout(1000)
		ok('モード中のリンクタップで移動しない', page.url() === href0 && (await fb.rootCount()) === 1, page.url())
		ok('リンクはタップで要素として選ばれる', await fb.inUi('.fab').isVisible())

		await fb.shot('6-before-exit')
		const exitBtn = fb.inUi('.exit')
		const eb = (await exitBtn.boundingBox())!
		ok('やめるボタンは 44px 以上', eb.height >= 44 && eb.width >= 44, JSON.stringify(eb))
		await exitBtn.tap()
		await page.waitForTimeout(500)
		ok('やめるで UI が全部消える', (await fb.rootCount()) === 0)
		ok('toolbar アプリも閉じる', !(await fb.appActive()), await fb.attrs())
		ok('選択も残らない', (await page.evaluate(() => getSelection()!.toString())) === '')
		await fb.shot('7-after-exit')
		await fb.enter()
		ok('やめた後に再度開ける', (await fb.rootCount()) === 1)
		await exitBtn.tap()
	})
})

test.describe('PC: 右クリックのメニューから popover を開いて送る', () => {
	test.beforeEach(({ fb }) => test.skip(fb.mobile, 'PC の列'))

	test('要素を右クリック → popover が要素のそばに出て、スクロールに追従し、送信で閉じる', async ({ fb }) => {
		const { page } = fb
		const vs = page.viewportSize()!
		const pop = fb.inUi('.popover')
		await fb.fresh()
		await fb.enter()
		const before = files().length
		await fb.pickEl(fb.para)
		ok('popover が出る', await pop.isVisible())
		ok('背景の幕（.backdrop）が無い', (await fb.inUi('.backdrop').count()) === 0)
		const elBox = (await fb.para.boundingBox())!
		const pb = (await pop.boundingBox())!
		ok('要素のそば（隙間 24px 以内）', gap(elBox, pb) <= 24, `gap=${gap(elBox, pb).toFixed(1)}`)
		ok('幅は 360px 前後', Math.abs(pb.width - 360) <= 2, String(pb.width))
		ok('画面内', frameOk(pb, vs))
		ok('textarea にフォーカスがある', (await fb.activeTag()) === 'TEXTAREA')
		ok('「載せる情報」は閉じている', !(await fb.inUi('.popover details.info').evaluate((d: HTMLDetailsElement) => d.open)))
		const summary = await fb.inUi('.popover details.info > summary').innerText()
		ok('載せる情報の件数表示 (n/m)', /載せる情報 \(\d+\/\d+\)/.test(summary), summary)
		await fb.shot('1-popover-element')
		const y0 = await page.evaluate(() => scrollY)
		await page.mouse.move(300, 300)
		await page.mouse.wheel(0, 120)
		await page.waitForTimeout(300)
		const y1 = await page.evaluate(() => scrollY)
		ok('ページがスクロールできる', y1 > y0, `${y0}->${y1}`)
		const pb2 = (await pop.boundingBox())!
		ok('スクロール後も popover は要素のそば', gap((await fb.para.boundingBox())!, pb2) <= 24 && Math.abs(pb2.y - pb.y) > 50, `pop y ${pb.y}->${pb2.y}`)
		await page.mouse.wheel(0, 3000)
		await page.waitForTimeout(400)
		const elBox3 = (await fb.para.boundingBox())!
		const pb3 = (await pop.boundingBox())!
		ok('要素が画面の上へ出ても popover は画面の端に留まる', elBox3.y + elBox3.height < 0 && frameOk(pb3, vs) && pb3.y < 40, `pop=${JSON.stringify(pb3)}`)
		await page.evaluate(() => scrollTo(0, 0))
		await page.waitForTimeout(300)
		await fb.inUi('.popover textarea').fill('PC の popover から送るテスト')
		await fb.inUi('.popover .primary').click()
		await fb.waitSaved(before)
		ok('記録ができる', files().length === before + 1, files().join(','))
		ok('UI が残らない', (await fb.rootCount()) === 0)
		ok('toolbar アプリも閉じる', !(await fb.appActive()), await fb.attrs())
	})

	test('テキストを選ぶと浮かぶボタンが出て、popover で載せる情報を 1 つ外して送れる', async ({ fb }) => {
		const { page } = fb
		const pop = fb.inUi('.popover')
		await fb.fresh()
		await fb.enter()
		const before = files().length
		const st = await fb.selectRange(0, 14)
		await page.mouse.move(st.x, st.y)
		await page.mouse.down() // mouseup で選び直されないよう、選択の上で押して離す
		await page.mouse.up()
		await page.waitForTimeout(500)
		await fb.para.dblclick({ position: { x: 40, y: 8 } })
		await page.waitForTimeout(500)
		const picked = await page.evaluate(() => getSelection()!.toString())
		ok('浮かぶボタンが出る', await fb.inUi('.fab').isVisible(), JSON.stringify(picked))
		const textBox = await page.evaluate(() => {
			const r = getSelection()!.rangeCount ? getSelection()!.getRangeAt(0).getBoundingClientRect() : null
			return r ? { x: r.x, y: r.y, width: r.width, height: r.height } : null
		})
		await fb.inUi('.fab').click()
		await page.waitForTimeout(300)
		ok('popover が出る', await pop.isVisible())
		ok('popover は選択テキストのそば', !!textBox && gap(textBox, (await pop.boundingBox())!) <= 24)
		ok('「載せる情報」は閉じている', !(await fb.inUi('.popover details.info').evaluate((d: HTMLDetailsElement) => d.open)))
		await fb.shot('2-popover-text')
		const sum = fb.inUi('.popover details.info > summary')
		const n0 = (await sum.innerText()).match(/\((\d+)\/(\d+)\)/)
		await sum.click()
		await page.waitForTimeout(200)
		ok('開くとチェックとプレビューが並ぶ', (await fb.inUi('.popover .item').count()) >= 5 && (await fb.inUi('.popover .item summary input[type=checkbox]').count()) >= 5)
		await fb.inUi('.popover #chk-browser').uncheck()
		await page.waitForTimeout(100)
		const n1 = (await sum.innerText()).match(/\((\d+)\/(\d+)\)/)
		ok('件数が 1 減る', !!n0 && !!n1 && +n1[1] === +n0[1] - 1 && n1[2] === n0[2], `${n0?.[0]} -> ${n1?.[0]}`)
		await fb.shot('2b-popover-open')
		await fb.inUi('.popover textarea').fill('テキスト選択のテスト')
		await fb.inUi('.popover .primary').click()
		await fb.waitSaved(before)
		const md = files().length === before + 1 ? readRecord(files().at(-1)!) : ''
		ok('外した節（ブラウザ）が記録に無い', md !== '' && !/## ブラウザ/.test(md) && /## 選んだ内容/.test(md) && md.includes(picked.trim()))
		ok('UI が残らない', (await fb.rootCount()) === 0)
	})

	test('popover の外クリック: 空なら閉じる・書きかけなら閉じない → Esc → キャンセル', async ({ fb }) => {
		const { page } = fb
		const pop = fb.inUi('.popover')
		await fb.fresh()
		await fb.enter()
		await fb.pickEl(fb.para)
		await page.locator('main h1').first().click({ position: { x: 2, y: 2 } })
		await page.waitForTimeout(300)
		ok('コメントが空なら外クリックで popover が閉じる', (await pop.count()) === 0)
		ok('モードは続き、枠も消える', (await fb.rootCount()) === 1 && (await fb.inUi('.frame.picked').evaluate((e) => getComputedStyle(e).display === 'none')) && !(await fb.inUi('.fab').isVisible()))
		await fb.pickEl(fb.para)
		await fb.inUi('.popover textarea').fill('書きかけ')
		await page.locator('main h1').first().click({ position: { x: 2, y: 2 } })
		await page.waitForTimeout(300)
		ok('コメントがあれば外クリックでも閉じない', (await pop.count()) === 1 && (await fb.inUi('.popover textarea').inputValue()) === '書きかけ')
		ok('外クリックで別要素に選び直されない（元の要素のそば）', gap((await fb.para.boundingBox())!, (await pop.boundingBox())!) <= 24)
		await fb.esc()
		ok('Esc で UI が全部消え toolbar も閉じる', (await fb.rootCount()) === 0 && !(await fb.appActive()))
		await fb.enter()
		await fb.pickEl(fb.para)
		await fb.inUi('.popover .actions button:not(.primary):not(.copy)').click()
		await page.waitForTimeout(300)
		ok('キャンセルで UI が全部消え toolbar も閉じる', (await fb.rootCount()) === 0 && !(await fb.appActive()))
	})

	test('画面の下端にある要素では popover が要素の上に出る', async ({ fb }) => {
		const { page } = fb
		const vs = page.viewportSize()!
		await fb.fresh()
		await fb.enter()
		await page.evaluate(() => {
			const d = document.createElement('div')
			d.id = 'qa-low'
			d.textContent = '画面の下端にある要素'
			d.style.cssText = 'position:fixed;left:300px;bottom:12px;width:400px;height:28px;background:#fde68a;z-index:5'
			document.body.append(d)
		})
		const low = page.locator('#qa-low')
		await fb.pickEl(low)
		const pb = (await fb.inUi('.popover').boundingBox())!
		const eb = (await low.boundingBox())!
		ok('popover は画面内', frameOk(pb, vs), JSON.stringify(pb))
		ok('要素の上に出る', pb.y + pb.height <= eb.y + 1, `pop.bottom=${pb.y + pb.height} el.top=${eb.y}`)
		await fb.shot('7-popover-above')
		await sleep(100)
	})
})

// Standalone dev tool, not part of the frontend build. Requires the
// backend + frontend dev servers running (scripts/start.sh) and a
// `playwright` install (not a project dependency -- `npm install playwright`
// in a scratch dir, then `npx playwright install chromium`, then run this
// file with that install's node_modules on the path, e.g.
// `node -e "require('/path/to/that/node_modules/playwright')"` style require
// below swapped to a local path, or just `npm link` it in).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const OUT_DIR = path.join(__dirname, 'raw-clips');
fs.mkdirSync(OUT_DIR, { recursive: true });

const SIZE = { width: 1920, height: 1080 };

const OVERLAY_CSS = `
#__cursor {
  position: fixed; width: 30px; height: 30px; margin-left: -15px; margin-top: -15px;
  border: 3px solid #fff; border-radius: 50%; background: rgba(255,255,255,.18);
  pointer-events: none; z-index: 999999;
  box-shadow: 0 0 0 1px rgba(0,0,0,.6), 0 2px 6px rgba(0,0,0,.5);
  transition: left .5s cubic-bezier(.4,0,.2,1), top .5s cubic-bezier(.4,0,.2,1);
  left: 50vw; top: 50vh;
}
#__cursor.click { animation: __pulse .45s ease-out; }
@keyframes __pulse {
  0%   { box-shadow: 0 0 0 0 rgba(255,255,255,.9), 0 0 0 1px rgba(0,0,0,.6); }
  100% { box-shadow: 0 0 0 22px rgba(255,255,255,0), 0 0 0 1px rgba(0,0,0,.6); }
}
#__caption {
  position: fixed; left: 50%; bottom: 70px; transform: translateX(-50%);
  padding: 16px 32px; background: rgba(18,13,8,.9); color: #f2e9d8;
  font-family: 'Silkscreen', monospace, sans-serif; font-size: 24px; letter-spacing: .5px;
  border: 2px solid #e8a15c; border-radius: 6px; opacity: 0; transition: opacity .35s ease;
  z-index: 999998; white-space: nowrap;
}
#__caption.show { opacity: 1; }
`;

async function injectOverlay(page) {
  await page.addStyleTag({ content: OVERLAY_CSS });
  await page.evaluate(() => {
    if (document.getElementById('__cursor')) return;
    const c = document.createElement('div'); c.id = '__cursor'; document.body.appendChild(c);
    const cap = document.createElement('div'); cap.id = '__caption'; document.body.appendChild(cap);
    window.__cursorMoveTo = (x, y) => { c.style.left = x + 'px'; c.style.top = y + 'px'; };
    window.__cursorClick = () => { c.classList.remove('click'); void c.offsetWidth; c.classList.add('click'); };
    window.__setCaption = (t) => { cap.textContent = t; cap.classList.add('show'); };
    window.__clearCaption = () => { cap.classList.remove('show'); };
  });
}

async function moveCursor(page, x, y) {
  await page.evaluate(([x, y]) => window.__cursorMoveTo(x, y), [x, y]);
  await page.waitForTimeout(550);
  await page.mouse.move(x, y);
}

async function moveAndClick(page, locator, opts = {}) {
  const box = await locator.boundingBox();
  if (!box) throw new Error('element not visible for click');
  const x = box.x + box.width * (opts.xFrac ?? 0.5);
  const y = box.y + box.height * (opts.yFrac ?? 0.5);
  await moveCursor(page, x, y);
  await page.waitForTimeout(150);
  await page.evaluate(() => window.__cursorClick());
  await page.waitForTimeout(150);
  await locator.click({ position: { x: box.width * (opts.xFrac ?? 0.5), y: box.height * (opts.yFrac ?? 0.5) }, force: true });
}

async function hoverOnly(page, locator) {
  const box = await locator.boundingBox();
  if (!box) throw new Error('element not visible for hover');
  await moveCursor(page, box.x + box.width / 2, box.y + box.height / 2);
}

async function caption(page, text, holdMs = 2400) {
  await page.evaluate((t) => window.__setCaption(t), text);
  await page.waitForTimeout(holdMs);
  await page.evaluate(() => window.__clearCaption());
  await page.waitForTimeout(400);
}

async function withScene(browser, name, fn) {
  const tmpDir = path.join(OUT_DIR, '_tmp_' + name);
  fs.mkdirSync(tmpDir, { recursive: true });
  const context = await browser.newContext({
    viewport: SIZE,
    recordVideo: { dir: tmpDir, size: SIZE },
  });
  const page = await context.newPage();
  try {
    await fn(page);
  } catch (e) {
    console.error('SCENE FAILED:', name, e.message);
  }
  await context.close();
  const files = fs.readdirSync(tmpDir).filter(f => f.endsWith('.webm'));
  if (files.length) {
    fs.renameSync(path.join(tmpDir, files[0]), path.join(OUT_DIR, name + '.webm'));
  }
  fs.rmSync(tmpDir, { recursive: true, force: true });
  console.log('done:', name);
}

async function enterFarm(page) {
  await page.goto('http://localhost:5173/');
  await page.waitForSelector('.menu-btn', { timeout: 20000 });
  await injectOverlay(page);
  await moveAndClick(page, page.locator('.menu-btn').first());
  await page.waitForSelector('.hud-chip-cookie', { timeout: 20000 });
  await page.waitForTimeout(800);
}

async function clickBuilding(page, title) {
  const el = page.locator('.bf-name', { hasText: title }).first();
  await el.scrollIntoViewIfNeeded().catch(() => {});
  await moveAndClick(page, el);
  await page.waitForTimeout(500);
}

(async () => {
  const browser = await chromium.launch();

  await withScene(browser, '01-intro-menu', async (page) => {
    await page.goto('http://localhost:5173/');
    await page.waitForSelector('.menu-btn', { timeout: 20000 });
    await injectOverlay(page);
    await caption(page, 'COOKIE', 3000);
  });

  await withScene(browser, '02-farm-pan', async (page) => {
    await enterFarm(page);
    await page.keyboard.down('ArrowRight');
    await page.waitForTimeout(1800);
    await page.keyboard.up('ArrowRight');
    await page.keyboard.down('ArrowDown');
    await page.waitForTimeout(1200);
    await page.keyboard.up('ArrowDown');
    await page.waitForTimeout(1000);
    await moveAndClick(page, page.locator('.cam-center'));
    await page.waitForTimeout(1800);
  });

  await withScene(browser, '03-harvest-hover', async (page) => {
    await enterFarm(page);
    await hoverOnly(page, page.locator('.bf-scene').first());
    await caption(page, 'Ernten per Hover', 2600);
    await page.waitForTimeout(2500);
  });

  await withScene(browser, '04-collect-building', async (page) => {
    await enterFarm(page);
    await caption(page, 'Gebäude einsammeln', 2000);
    const badge = page.locator('.bf-collect-badge').first();
    await badge.scrollIntoViewIfNeeded().catch(() => {});
    await moveAndClick(page, badge);
    await page.waitForTimeout(2500);
  });

  await withScene(browser, '05-market-trade', async (page) => {
    await enterFarm(page);
    await clickBuilding(page, 'Markt');
    await page.waitForTimeout(800);
    await caption(page, 'Gemeinsamer Markt aller Spieler', 2600);
    const buyButtons = page.locator('button', { hasText: 'KAUFEN' });
    await moveAndClick(page, buyButtons.first());
    await page.waitForTimeout(1800);
    const sellButtons = page.locator('button', { hasText: 'VERKAUF' });
    await moveAndClick(page, sellButtons.nth(2));
    await page.waitForTimeout(2200);
  });

  await withScene(browser, '06-bake-start', async (page) => {
    await enterFarm(page);
    await clickBuilding(page, 'Backofen');
    await page.waitForTimeout(800);
    await caption(page, 'Rohstoffe zu Cookies backen', 2400);
    await moveAndClick(page, page.locator('button', { hasText: 'BACKEN STARTEN' }));
    await caption(page, 'Backzeit: 30 Sekunden', 3000);
    await page.waitForTimeout(28000);
  });

  await withScene(browser, '07-skilltree-allocate', async (page) => {
    await enterFarm(page);
    await moveAndClick(page, page.locator('.hud-menu-wrap > button'));
    await page.waitForTimeout(300);
    await moveAndClick(page, page.locator('.hud-menu-item').nth(1));
    await page.waitForTimeout(1000);
    await caption(page, 'Passiver Skillbaum', 2600);
    await page.mouse.wheel(0, -200);
    await page.waitForTimeout(1500);
  });

  await withScene(browser, '08-rathaus', async (page) => {
    await enterFarm(page);
    await clickBuilding(page, 'Rathaus');
    await caption(page, 'Bürger verwalten', 2600);
    await page.waitForTimeout(1500);
  });

  await withScene(browser, '09-lager', async (page) => {
    await enterFarm(page);
    await clickBuilding(page, 'Lager');
    await caption(page, 'Gemeinsames Lager', 2600);
    await page.waitForTimeout(1500);
  });

  await withScene(browser, '10-stats', async (page) => {
    await enterFarm(page);
    await moveAndClick(page, page.locator('.hud-menu-wrap > button'));
    await page.waitForTimeout(300);
    await moveAndClick(page, page.locator('.hud-menu-item').nth(2));
    await caption(page, 'Statistiken', 2600);
    await page.waitForTimeout(1500);
  });

  await withScene(browser, '11-leaderboard', async (page) => {
    await enterFarm(page);
    await moveAndClick(page, page.locator('.hud-menu-wrap > button'));
    await page.waitForTimeout(300);
    await moveAndClick(page, page.locator('.hud-menu-item').nth(3));
    await caption(page, 'Rangliste', 2600);
    await page.waitForTimeout(1500);
  });

  await withScene(browser, '12-outro-wide', async (page) => {
    await enterFarm(page);
    await moveAndClick(page, page.locator('.cam-center'));
    await page.waitForTimeout(4000);
  });

  await browser.close();
  console.log('ALL SCENES DONE');
})();

// Standalone dev tool, not part of the frontend build. Requires the
// backend + frontend dev servers running (scripts/start.sh) and a
// `playwright` install (not a project dependency -- `npm install
// playwright` in a scratch dir, `npx playwright install chromium`, run
// this file with that install's node_modules resolvable, e.g. `npm link`
// it into a scratch dir or adjust the require below to a local path).
// Meme-paced beats per docs/steam-website/trailer/storyboard.md. Reset
// dev save state before running (see docs/steam-website/README.md
// "Fallstricke").
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const OUT_DIR = path.join(__dirname, 'raw-clips');
fs.mkdirSync(OUT_DIR, { recursive: true });

const SIZE = { width: 1920, height: 1080 };
const CURSOR_MS = 180; // fast glide for meme pacing (vs 550ms showcase version)

const OVERLAY_CSS = `
#__cursor {
  position: fixed; width: 30px; height: 30px; margin-left: -15px; margin-top: -15px;
  border: 3px solid #fff; border-radius: 50%; background: rgba(255,255,255,.18);
  pointer-events: none; z-index: 999999;
  box-shadow: 0 0 0 1px rgba(0,0,0,.6), 0 2px 6px rgba(0,0,0,.5);
  transition: left ${CURSOR_MS}ms cubic-bezier(.2,.8,.2,1), top ${CURSOR_MS}ms cubic-bezier(.2,.8,.2,1);
  left: 50vw; top: 50vh;
}
#__cursor.click { animation: __pulse .3s ease-out; }
@keyframes __pulse {
  0%   { box-shadow: 0 0 0 0 rgba(255,255,255,.9), 0 0 0 1px rgba(0,0,0,.6); }
  100% { box-shadow: 0 0 0 22px rgba(255,255,255,0), 0 0 0 1px rgba(0,0,0,.6); }
}
`;

async function injectOverlay(page) {
  await page.addStyleTag({ content: OVERLAY_CSS });
  await page.evaluate(() => {
    if (document.getElementById('__cursor')) return;
    const c = document.createElement('div'); c.id = '__cursor'; document.body.appendChild(c);
    window.__cursorMoveTo = (x, y) => { c.style.left = x + 'px'; c.style.top = y + 'px'; };
    window.__cursorClick = () => { c.classList.remove('click'); void c.offsetWidth; c.classList.add('click'); };
  });
}

async function moveCursor(page, x, y) {
  await page.evaluate(([x, y]) => window.__cursorMoveTo(x, y), [x, y]);
  await page.waitForTimeout(CURSOR_MS + 40);
  await page.mouse.move(x, y);
}

async function moveAndClick(page, locator, opts = {}) {
  const box = await locator.boundingBox();
  if (!box) throw new Error('element not visible for click');
  const x = box.x + box.width * (opts.xFrac ?? 0.5);
  const y = box.y + box.height * (opts.yFrac ?? 0.5);
  await moveCursor(page, x, y);
  await page.evaluate(() => window.__cursorClick());
  await page.waitForTimeout(80);
  await locator.click({ position: { x: box.width * (opts.xFrac ?? 0.5), y: box.height * (opts.yFrac ?? 0.5) }, force: true });
}

async function withScene(browser, name, fn) {
  const tmpDir = path.join(OUT_DIR, '_tmp_' + name);
  fs.mkdirSync(tmpDir, { recursive: true });
  const context = await browser.newContext({ viewport: SIZE, recordVideo: { dir: tmpDir, size: SIZE } });
  const page = await context.newPage();
  try {
    await fn(page);
  } catch (e) {
    console.error('SCENE FAILED:', name, e.message);
  }
  await context.close();
  const files = fs.readdirSync(tmpDir).filter(f => f.endsWith('.webm'));
  if (files.length) fs.renameSync(path.join(tmpDir, files[0]), path.join(OUT_DIR, name + '.webm'));
  fs.rmSync(tmpDir, { recursive: true, force: true });
  console.log('done:', name);
}

async function enterFarm(page) {
  await page.goto('http://localhost:5173/');
  await page.waitForSelector('.menu-btn', { timeout: 20000 });
  await injectOverlay(page);
  await moveAndClick(page, page.locator('.menu-btn').first());
  await page.waitForSelector('.hud-chip-cookie', { timeout: 20000 });
  await page.waitForTimeout(500);
}

async function clickBuilding(page, title) {
  const el = page.locator('.bf-name', { hasText: title }).first();
  await el.scrollIntoViewIfNeeded().catch(() => {});
  await moveAndClick(page, el);
  await page.waitForTimeout(300);
}

(async () => {
  const browser = await chromium.launch();

  // Beat 1: cold open, resource number ticking during hover-harvest
  await withScene(browser, '01-number-tick', async (page) => {
    await enterFarm(page);
    const scene = page.locator('.bf-scene').first();
    const box = await scene.boundingBox();
    await moveCursor(page, box.x + box.width / 2, box.y + box.height / 2);
    await page.waitForTimeout(3500);
  });

  // Beat 2: collect-spam, pond/hof/huhn back to back
  await withScene(browser, '02-collect-spam', async (page) => {
    await enterFarm(page);
    for (const title of ['Zuckerteich', 'Bauernhof', 'Hühnerhof']) {
      const badge = page.locator('.bf-root', { has: page.locator('.bf-name', { hasText: title }) }).locator('.bf-collect-badge');
      await badge.scrollIntoViewIfNeeded().catch(() => {});
      await moveAndClick(page, badge);
      await page.waitForTimeout(250);
    }
    await page.waitForTimeout(1200);
  });

  // Beat 3+5: market swing, big buy (price up) then big sell (price down)
  await withScene(browser, '03-market-swing', async (page) => {
    await enterFarm(page);
    await clickBuilding(page, 'Markt');
    await page.waitForTimeout(500);
    // Root cause found and fixed (2026-08-17): `button:has-text('+')` on
    // the whole page matches `.build-fab` (bottom-right, opens Build-Shop)
    // BEFORE it matches a market quantity stepper, since build-fab sits
    // earlier in the DOM. `.first()` was hitting build-fab every time --
    // 6 rapid clicks kept swapping the Market dialog for the Build-Shop
    // dialog and back, which is what showed up as "background jumping
    // around, buggy" in the recorded clip. Scope to the dialog's own
    // quantity stepper class instead (`.mv-qty-btn`, first row = Zucker).
    const plus = page.locator('.mv-qty-btn', { hasText: '+' }).first();
    for (let i = 0; i < 6; i++) {
      await plus.click().catch(() => {});
      await page.waitForTimeout(60);
    }
    await moveAndClick(page, page.locator('button', { hasText: 'KAUFEN' }).first());
    await page.waitForTimeout(2200);
    await moveAndClick(page, page.locator('button', { hasText: 'VERKAUF' }).first());
    await page.waitForTimeout(2200);
  });

  // Beat 6: building montage, quick zoom/open across 4 buildings, no captions
  await withScene(browser, '06-building-montage', async (page) => {
    await enterFarm(page);
    for (const title of ['Zuckerteich', 'Kuhstall', 'Plantage', 'Backofen']) {
      const el = page.locator('.bf-name', { hasText: title }).first();
      await el.scrollIntoViewIfNeeded().catch(() => {});
      const box = await el.boundingBox();
      if (box) await moveCursor(page, box.x + box.width / 2, box.y + box.height / 2);
      await page.waitForTimeout(500);
    }
  });

  // Beat 7: skilltree zoom-out reveal
  await withScene(browser, '07-skilltree-reveal', async (page) => {
    await enterFarm(page);
    await moveAndClick(page, page.locator('.hud-menu-wrap button'));
    await page.waitForTimeout(200);
    await moveAndClick(page, page.locator('.hud-menu-item').nth(1));
    await page.waitForTimeout(600);
    // start zoomed in tight on center, then zoom out fast
    await page.mouse.wheel(0, 400); // zoom in first (adjust sign if inverted)
    await page.waitForTimeout(400);
    await page.mouse.wheel(0, -900); // fast zoom out
    await page.waitForTimeout(1000);
  });

  // Beat 8: bake claim payoff (job pre-finished via API/DB before this run)
  await withScene(browser, '08-bake-claim', async (page) => {
    await enterFarm(page);
    await clickBuilding(page, 'Backofen');
    await page.waitForTimeout(400);
    await moveAndClick(page, page.locator('button', { hasText: 'COOKIES EINLÖSEN' }));
    await page.waitForTimeout(1500);
  });

  // Beat 9: leaderboard flash
  await withScene(browser, '09-leaderboard', async (page) => {
    await enterFarm(page);
    await moveAndClick(page, page.locator('.hud-menu-wrap button'));
    await page.waitForTimeout(200);
    await moveAndClick(page, page.locator('.hud-menu-item').nth(3));
    await page.waitForTimeout(1500);
  });

  await browser.close();
  console.log('ALL SCENES DONE');
})();

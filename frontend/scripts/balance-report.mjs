// Wiederholbares Balance-Diagnose-Tool: liest Bürger-/Gebäude-/Skillpunkt-/
// Markt-Balance (live vom Dev-Server oder aus Fallback-Defaults), rechnet
// die echten Backend-Formeln nach und markiert Ausreißer gegen Zielbänder.
// Schreibt nie in Quellcode -- nur einen HTML-Report und eine datierte
// Markdown-Vorschlagsdatei, die als Übergabe-Artefakt für eine gemeinsame
// Claude+Entwickler-Balancing-Session dient.
//
// Plan: docs/plans/2026-08-13-open-balance-report-tool.md
// Run:  npm run balance:report -- [--live|--static] [--out <html-pfad>]

import { writeFileSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = fileURLToPath(new URL('.', import.meta.url))
const REPO_ROOT = join(SCRIPT_DIR, '..', '..')
const REPORT_DIR = join(REPO_ROOT, 'docs', 'balance-reports')
const BASE_URL = 'http://localhost:9876' // siehe src/services/api.js BASE_URL

const args = process.argv.slice(2)
const mode = args.includes('--static') ? 'static' : 'live'
const outArg = args.find((a) => a.startsWith('--out='))
const HTML_OUT = outArg ? outArg.slice('--out='.length) : join(REPORT_DIR, 'latest.html')

// ── Fallback-Defaults (1:1 aus GameBalanceConfig.java / MarketConfig.java
// abgeschrieben) -- hält das Skript lauffähig, auch ohne laufenden Server. ──
const STATIC_BALANCE = {
  citizenBaseCost: 50, citizenCostGrowth: 1.15,
  workersPerLevel: 1, wagePerMinPerWorker: 2.0,
  buildingCostGrowth: 2.0,
  skillPointBaseCost: 150, skillPointCostGrowth: 1.4,
  citizensPerRatLevel: 4,
  prestigeBaseThreshold: 4500,
  baseStorageCap: 100, storagePerLevel: 1000,
}
// application.properties: player.initial-cookies -- steht in PlayerConfig, nicht
// GameBalanceConfig/MarketConfig, also über keinen Admin-Endpoint live abrufbar.
// Hier hart hinterlegt wie die BUILDING_DEFS -- bei Änderung dort nachziehen.
const STARTING_COOKIES = 400
const STATIC_MARKET = {
  sellFeeRate: 0.15,
  initialSugarPrice: 1.0, initialFlourPrice: 1.5, initialEggsPrice: 2.0,
  initialButterPrice: 3.0, initialChocolatePrice: 5.0, initialMilkPrice: 1.2,
  initialSugarStock: 1000, initialFlourStock: 1000, initialEggsStock: 1000,
  initialButterStock: 1000, initialChocolateStock: 1000, initialMilkStock: 1000,
}

// Gebäude-Definitionen gibt es über keinen Admin-Endpoint -- 1:1 gespiegelt
// aus BuildingService.java:36-47 (BUILDINGS-Liste). Bei neuen Gebäuden dort
// UND hier nachziehen.
const BUILDING_DEFS = [
  { id: 'pond',   name: 'Zuckerteich', baseCost: 500, maxWorkers: 2, passiveRatePerSecPerWorker: 0.7, passiveResource: 'SUGAR' },
  { id: 'hof',    name: 'Bauernhof',   baseCost: 300, maxWorkers: 3, passiveRatePerSecPerWorker: 0.7, passiveResource: 'FLOUR' },
  { id: 'huhn',   name: 'Hühnerhof',   baseCost: 350, maxWorkers: 2, passiveRatePerSecPerWorker: 0.4, passiveResource: 'EGGS' },
  { id: 'butter', name: 'Butterei',    baseCost: 280, maxWorkers: 1, passiveRatePerSecPerWorker: 0.6, passiveResource: 'BUTTER' },
  { id: 'kakao',  name: 'Plantage',    baseCost: 380, maxWorkers: 2, passiveRatePerSecPerWorker: 0.6, passiveResource: 'CHOCOLATE' },
  { id: 'kuh',    name: 'Kuhstall',    baseCost: 600, maxWorkers: 4, passiveRatePerSecPerWorker: 1.2, passiveResource: 'MILK' },
  { id: 'lager',  name: 'Lager',       baseCost: 400, maxWorkers: 0, passiveRatePerSecPerWorker: 0.0, passiveResource: null },
  { id: 'rathaus',name: 'Rathaus',     baseCost: 400, maxWorkers: 0, passiveRatePerSecPerWorker: 0.0, passiveResource: null },
]
const RATHAUS_DEF = BUILDING_DEFS.find((d) => d.id === 'rathaus')

// Gebäude-eigene storageCapacity aus BuildingService.java:37-46 (letztes Feld
// pro BuildingDef) -- deckelt die pendingAmount-Ansammlung beim Offline-Settle
// (siehe settle()-Formel unten), unabhaengig vom globalen Lager-Cap.
const STORAGE_CAPACITY = {
  pond: 840, hof: 1260, huhn: 480, butter: 360, kakao: 720, kuh: 2880,
}

const RESOURCES = ['SUGAR', 'FLOUR', 'EGGS', 'BUTTER', 'CHOCOLATE', 'MILK']

// ── Zielbänder (direkt hier editierbar, kein separates Config-Format) ──
const CITIZEN_CHECK_COUNT = 30
const BUILDING_CHECK_LEVELS = 10
const SKILLPOINT_CHECK_COUNT = 40

// Fortschritts-Simulator (siehe simulateProgression()): wie lange braucht ein
// Spieler mit GENAU EINEM Produktionsgebäude bis zum ersten Prestige-Reset
// (Net Worth erreicht prestigeBaseThreshold). Ersetzt eine frühere
// Payback-in-Minuten-Metrik, die faelschlich Kosten durch Lohn-KOSTEN teilte
// statt durch tatsaechlichen Ertrag -- lieferte keine sinnvolle Aussage.
// Vorgabe (Diskussion 2026-08-13): Early-Game mit 1 Gebaeude soll ~2-3 Tage
// dauern, jede Entscheidung soll sich noch "eng" anfuehlen. Kein reines
// 24/7-Idle -- das Spiel braucht einen Heartbeat (WageScheduler.java:31-37,
// AFK-Timeout stoppt Lohn/Zins komplett), ABER passive Produktion laeuft
// unabhaengig davon lazy per Echtzeit-Differenz weiter (settle(), siehe
// BuildingService.java:278-289) und wird beim naechsten Login nachgeholt,
// gedeckelt auf storageCapacity. Angenommene Check-in-Rate: alle 12h kurz
// rein (sammeln, verkaufen was noetig, guenstigstes kaufen) -- deckt sich
// mit "Idle mit gelegentlichem Reinschauen" statt Dauer-Session. Wert direkt
// hier aendern, falls andere Session-Annahme gewuenscht.
const CHECKIN_INTERVAL_HOURS = 12
const SIM_MAX_DAYS = 20
const TARGET_DAYS_MIN = 2
const TARGET_DAYS_MAX = 3

// Aktive Hover-Ernte pro Check-in (Diskussion 2026-08-13): UserService#harvest
// braucht KEINEN Gebäudebesitz und deckelt Nachholzeit auf MAX_HARVEST_BATCH_MS
// (6s, UserService.java:33) -- kein Lazy-Catchup wie settle(), nur solange der
// Spieler aktiv hovert. Rate ohne Boni: amount = ticks = elapsedMs/900ms
// (UserService.java:32/224) = 1000/900 ≈ 1.11 Einheiten/Sekunde. Angenommene
// aktive Zeit pro Check-in: 1-2h (Mittelwert 1.5h) -- deutlich mehr als reine
// passive Produktion, wird direkt verkauft (Spieler ist ja aktiv da, laesst
// nichts am Cap verfallen) statt über den Lager-Cap zu laufen.
const HOVER_RATE_PER_SEC = 1000 / 900
const HOVER_SECONDS_PER_CHECKIN = 1.5 * 3600

const KEYSTONE_RATIO_DEVIATION = 0.4 // ±40% vom Median gilt als Ausreißer

const SLIPPAGE_QTY = 200
const SLIPPAGE_BAND_MULT_MIN = 0.5 // relativ zu sellFeeRate
const SLIPPAGE_BAND_MULT_MAX = 3.0

// ── Daten laden ──────────────────────────────────────────────────────────

async function loadLive() {
  const [configRes, nodesRes] = await Promise.all([
    fetch(`${BASE_URL}/api/v1/admin/config`),
    fetch(`${BASE_URL}/api/v1/admin/skilltree/nodes`),
  ])
  if (!configRes.ok || !nodesRes.ok) throw new Error(`Server antwortete mit Fehler (config ${configRes.status}, nodes ${nodesRes.status})`)
  const config = await configRes.json()
  const nodes = await nodesRes.json()
  return { balance: config.balance, market: config.market, nodes }
}

async function loadData() {
  if (mode === 'static') {
    return { balance: STATIC_BALANCE, market: STATIC_MARKET, nodes: [] }
  }
  try {
    const live = await loadLive()
    console.log(`[balance-report] Live-Daten von ${BASE_URL} geladen (${live.nodes.length} Skill-Nodes).`)
    return live
  } catch (err) {
    console.warn(`[balance-report] Live-Fetch fehlgeschlagen (${err.message}) -- falle auf --static-Defaults zurück.`)
    return { balance: STATIC_BALANCE, market: STATIC_MARKET, nodes: [] }
  }
}

// ── Formeln (Fundstelle im Backend jeweils im Kommentar) ────────────────

// BuildingService.java:184 -- citizenBaseCost * citizenCostGrowth^ownedCount
function citizenCost(balance, n) {
  return balance.citizenBaseCost * Math.pow(balance.citizenCostGrowth, n)
}
// BuildingService.java:308-310 -- baseCost * buildingCostGrowth^currentLevel (level 0 = erster Kauf)
function buildingCost(baseCost, growth, level) {
  return baseCost * Math.pow(growth, level)
}
// BuildingService.java:188-191 -- effectiveMaxWorkers = maxWorkers + (level-1) * workersPerLevel
// -> Grenzertrag pro Level-Aufstieg = workersPerLevel zusätzliche Arbeiter-Slots.
function buildingMarginalValuePerMin(def, balance, pricePerUnit) {
  if (!def.passiveResource) return null
  return balance.workersPerLevel * def.passiveRatePerSecPerWorker * 60 * pricePerUnit
}

// SkillTreeService.java:538-539 -- skillPointBaseCost * skillPointCostGrowth^totalSkillPointsBought
function skillPointCost(balance, n) {
  return balance.skillPointBaseCost * Math.pow(balance.skillPointCostGrowth, n)
}

// MarketService.java:557-583 -- AMM constant product. K kalibriert so, dass
// Spotpreis bei initialStock == initialPrice ist: price(stock) = K / stock^2.
function poolConstant(initialStock, initialPrice) {
  return initialStock * initialStock * initialPrice
}
function spotPrice(stock, k) {
  const s = Math.max(stock, 0.01)
  return k / (s * s)
}
function slippagePct(stock, k, amount, direction) {
  const before = spotPrice(stock, k)
  const after = direction === 'buy' ? spotPrice(stock - amount, k) : spotPrice(stock + amount, k)
  return ((after - before) / before) * 100
}
// MarketService.java:578-583 -- Cookie-Auszahlung (vor Gebuehr) fuer den Verkauf von `amount`
// Einheiten in den Pool (Integral ueber die Preiskurve).
function sellPayout(stock, k, amount) {
  const newStock = stock + amount
  return k / stock - k / newStock
}

function initialStockFor(market, resource) {
  return market[`initial${cap(resource)}Stock`]
}
function initialPriceFor(market, resource) {
  return market[`initial${cap(resource)}Price`]
}
function cap(resource) {
  return resource.charAt(0) + resource.slice(1).toLowerCase()
}

// ── Bisektion für Vorschlagswerte (nimmt monotone fn an) ─────────────────
function bisect(fn, lo, hi, target, iters = 60) {
  let flo = fn(lo)
  const increasing = fn(hi) >= flo
  for (let i = 0; i < iters; i++) {
    const mid = (lo + hi) / 2
    const fmid = fn(mid)
    if ((increasing && fmid < target) || (!increasing && fmid > target)) lo = mid
    else hi = mid
  }
  return (lo + hi) / 2
}

// ── Report-Module ─────────────────────────────────────────────────────────

// Rein informativ (Rohkurven fuer den HTML-Chart) -- keine Flags mehr hier,
// die kommen jetzt aus dem Fortschritts-Simulator (reportProgression).
function reportCitizens(balance) {
  const rows = []
  for (let n = 0; n < CITIZEN_CHECK_COUNT; n++) {
    rows.push({ n, cost: citizenCost(balance, n) })
  }
  return { rows }
}

function reportBuildings(balance, market) {
  const rows = []
  for (const def of BUILDING_DEFS) {
    if (!def.passiveResource) {
      rows.push({ building: def.name, note: 'kein Ressourcenoutput' })
      continue
    }
    const price = initialPriceFor(market, def.passiveResource)
    const marginalValuePerMin = buildingMarginalValuePerMin(def, balance, price)
    for (let level = 0; level < BUILDING_CHECK_LEVELS; level++) {
      const cost = buildingCost(def.baseCost, balance.buildingCostGrowth, level)
      rows.push({ building: def.name, level, cost, payback: cost / marginalValuePerMin })
    }
  }
  return { rows }
}

// ── Fortschritts-Simulator: Tage bis zum ersten Prestige-Reset mit genau
// einem Produktionsgebäude ────────────────────────────────────────────────
//
// Modell pro Check-in (alle CHECKIN_INTERVAL_HOURS): settle() (passive
// Produktion seit letztem Check-in, gedeckelt auf storageCapacity, siehe
// BuildingService.java:278-289) -> einsammeln -> so lange wie moeglich
// guenstigste sinnvolle Aktion kaufen (Buerger > Rathaus-Ausbau falls
// Buerger-Limit erreicht > Gebaeude-Level falls Arbeiter-Slots voll),
// verkauft dabei nur so viel Ressource wie fuer den jeweiligen Kauf noetig
// (AMM-Kosten inkl. sellFeeRate, siehe sellPayout-Formel) -- Rest bleibt als
// Ressourcenbestand liegen, weil das mehr zum Net Worth beitraegt als
// verkaufen (Verkauf frisst sellFeeRate + Slippage). Vereinfachung: nutzt
// den config-Startpreis als konstanten Marktpreis (ignoriert den eigenen
// Preis-Impact des simulierten Spielers ueber die Zeit) -- fuer die grobe
// Tage-Kurve ausreichend, siehe Plan-Doc "Nicht im Scope".
function simulateProgression(def, balance, market, prestigeBaseThreshold) {
  const price = initialPriceFor(market, def.passiveResource)
  const stock0 = initialStockFor(market, def.passiveResource)
  const k = poolConstant(stock0, price)
  const cap = STORAGE_CAPACITY[def.id] ?? Infinity

  // GameBalanceConfig.baseStorageCap/storagePerLevel -- gemeinsamer Topf ueber
  // alle Ressourcen, Lager startet pre-built auf Level 1 (siehe BUILDING_DEFS).
  // Produktion ueber den Cap hinaus wird verworfen, nicht gutgeschrieben
  // (UserService#harvest/PassiveIncomeService#collectBuilding). Lager-Ausbau
  // wird in v1 nicht simuliert (Fokus: "1 Gebäude"-Phase), Cap bleibt fix.
  const globalStorageCap = balance.baseStorageCap + balance.storagePerLevel * 1

  let cookies = STARTING_COOKIES
  let inventory = 0
  let pending = 0
  let citizens = 0
  let workers = 0
  let buildingLevel = 0
  let rathausLevel = 1 // pre-built, siehe BuildingService BUILDINGS-Liste

  const steps = Math.ceil((SIM_MAX_DAYS * 24) / CHECKIN_INTERVAL_HOURS)
  for (let step = 0; step <= steps; step++) {
    const days = (step * CHECKIN_INTERVAL_HOURS) / 24

    // settle() + einsammeln
    if (workers > 0) {
      const produced = def.passiveRatePerSecPerWorker * workers * (CHECKIN_INTERVAL_HOURS * 3600)
      pending = Math.min(cap, pending + produced)
    }
    inventory = Math.min(globalStorageCap, inventory + pending)
    pending = 0

    // Aktive Hover-Ernte (siehe Konstanten oben): direkt verkauft statt über
    // Inventory/Cap zu laufen -- Spieler ist ja aktiv da und wuerde nicht
    // ungenutzt am Cap verfallen lassen. Nutzt dieselbe (vereinfachte)
    // AMM-Formel gegen den statischen Start-Stock wie der Rest der Simulation.
    if (step > 0) {
      const hoverAmount = HOVER_RATE_PER_SEC * HOVER_SECONDS_PER_CHECKIN
      cookies += sellPayout(stock0, k, hoverAmount) * (1 - market.sellFeeRate)
    }

    // Kaufschleife: so lange bis nichts Sinnvolles mehr leistbar ist
    for (let guard = 0; guard < 500; guard++) {
      const maxCitizens = rathausLevel * balance.citizensPerRatLevel
      // BuildingService.java:188-191 -- effectiveMaxWorkers ist 0, solange das
      // Gebäude nicht mindestens Level 1 hat (noch nicht gekauft).
      const maxWorkers = buildingLevel > 0 ? def.maxWorkers + (buildingLevel - 1) * balance.workersPerLevel : 0

      let nextCost = null
      let action = null
      if (workers < maxWorkers && citizens < maxCitizens) {
        nextCost = citizenCost(balance, citizens)
        action = 'citizen'
      } else if (workers < maxWorkers && citizens >= maxCitizens) {
        nextCost = buildingCost(RATHAUS_DEF.baseCost, balance.buildingCostGrowth, rathausLevel - 1)
        action = 'rathaus'
      } else {
        nextCost = buildingCost(def.baseCost, balance.buildingCostGrowth, buildingLevel)
        action = 'building'
      }

      const shortfall = nextCost - cookies
      if (shortfall > 0) {
        if (inventory <= 0) break
        // so viel verkaufen wie fuer den Shortfall noetig (AMM-Kosten,
        // sellPayout-Formel), gedeckelt auf verfuegbaren Bestand
        const sellAmount = bisect(
          (amt) => sellPayout(stock0, k, amt) * (1 - market.sellFeeRate),
          0, inventory, shortfall, 40
        )
        const actualSellAmount = Math.min(sellAmount, inventory)
        const proceeds = sellPayout(stock0, k, actualSellAmount) * (1 - market.sellFeeRate)
        // Auch ein Teilverkauf lohnt sich (Cookies fuer die naechste Runde behalten
        // statt am Lager-Cap ungenutzt verfallen zu lassen) -- nur bei kompletter
        // Wertlosigkeit (proceeds ~ 0) gar nicht erst verkaufen.
        inventory -= actualSellAmount
        cookies += proceeds
        if (proceeds < shortfall - 0.01) break // reicht (noch) nicht fuer den Kauf
      }
      if (cookies < nextCost) break

      cookies -= nextCost
      if (action === 'citizen') { citizens++; workers++ }
      else if (action === 'rathaus') rathausLevel++
      else buildingLevel++
    }

    const netWorth = cookies + inventory * price
    if (process.env.DEBUG && step % 4 === 0) {
      console.error(`[debug ${def.id}] day ${round1(days)}: cookies=${round2(cookies)} inv=${round2(inventory)} citizens=${citizens} workers=${workers} lvl=${buildingLevel} rathaus=${rathausLevel} netWorth=${round2(netWorth)}`)
    }
    if (netWorth >= prestigeBaseThreshold) {
      return { building: def.name, daysToFirstPrestige: round1(days), reached: true }
    }
  }
  return { building: def.name, daysToFirstPrestige: null, reached: false }
}

// Nur Gebäude, die man mit den 400 Start-Cookies (STARTING_COOKIES) am Tag 1
// tatsächlich kaufen kann (Butterei 280/Bauernhof 300/Hühnerhof 350) --
// Zuckerteich (500)/Kuhstall (600) sind laut Design-Doc Abschnitt 3
// ("Start-Balance") bewusst KEIN Tag-1-Ziel, sondern spätere Käufe, sobald
// man sich per Hover-Ernte (aktiv, kein Lazy-Catchup -- UserService.java:32
// MAX_HARVEST_BATCH_MS=6s) oder aus einem der drei Starter-Gebäude die
// restlichen Cookies erspielt hat. Fuer diese zwei macht "Tage bis 1.
// Prestige AB TAG 1" als Metrik keinen Sinn, sie tauchen hier bewusst nicht auf.
function reportProgression(balance, market) {
  const rows = BUILDING_DEFS
    .filter((d) => d.passiveResource && d.baseCost <= STARTING_COOKIES)
    .map((def) => simulateProgression(def, balance, market, balance.prestigeBaseThreshold))

  const flags = []
  const target = (TARGET_DAYS_MIN + TARGET_DAYS_MAX) / 2
  for (const r of rows) {
    const days = r.reached ? r.daysToFirstPrestige : SIM_MAX_DAYS
    const outOfBand = !r.reached || days < TARGET_DAYS_MIN || days > TARGET_DAYS_MAX
    r.outOfBand = outOfBand
    if (!outOfBand) continue
    const def = BUILDING_DEFS.find((d) => d.name === r.building)
    const suggestedThreshold = bisect(
      (t) => {
        const sim = simulateProgression(def, balance, market, t)
        return sim.reached ? sim.daysToFirstPrestige : SIM_MAX_DAYS
      },
      1000, balance.prestigeBaseThreshold * 5, target, 25
    )
    flags.push({
      module: 'Fortschritt', id: `progression_${def.id}`,
      metric: r.reached ? `${r.daysToFirstPrestige} Tage bis 1. Prestige` : `Prestige nicht erreicht in ${SIM_MAX_DAYS} Tagen`,
      band: `${TARGET_DAYS_MIN}-${TARGET_DAYS_MAX} Tage`,
      field: 'GameBalanceConfig.prestigeBaseThreshold', current: balance.prestigeBaseThreshold, suggested: round2(suggestedThreshold),
      note: `Start mit nur "${r.building}" (Check-in alle ${CHECKIN_INTERVAL_HOURS}h): ${r.reached ? `${r.daysToFirstPrestige} Tage` : `Prestige nicht erreicht in ${SIM_MAX_DAYS} Tagen`} bis Net Worth die Prestige-Schwelle erreicht, Ziel ${TARGET_DAYS_MIN}-${TARGET_DAYS_MAX} Tage -- prestigeBaseThreshold ${balance.prestigeBaseThreshold} → ${round2(suggestedThreshold)} bringt es auf ~${target} Tage (gilt global für alle Startgebäude -- falls die einzelnen Gebäude stark auseinanderliegen, siehe Hinweis unten, eher an buildingCostGrowth/storageCapacity je Gebäude drehen statt nur an dieser einen globalen Schwelle).`,
    })
  }
  return { rows, flags }
}

function reportSkillPoints(balance, nodes) {
  const rows = []
  for (let n = 0; n < SKILLPOINT_CHECK_COUNT; n += 5) {
    rows.push({ n, cost: skillPointCost(balance, n) })
  }
  const flags = []
  if (nodes.length > 0) {
    const tiers = ['PASSIVE', 'NOTABLE', 'KEYSTONE']
    const avgByTier = {}
    for (const tier of tiers) {
      const values = nodes.filter((n) => n.nodeTier === tier).flatMap((n) => n.effects.map((e) => Math.abs(e.effectValue)))
      avgByTier[tier] = values.length ? values.reduce((a, b) => a + b, 0) / values.length : null
    }
    if (avgByTier.NOTABLE != null && avgByTier.PASSIVE != null && avgByTier.NOTABLE <= avgByTier.PASSIVE) {
      flags.push({
        module: 'Skill-Punkte', id: 'tier_order_notable', metric: `NOTABLE-Ø ${round3(avgByTier.NOTABLE)} <= PASSIVE-Ø ${round3(avgByTier.PASSIVE)}`,
        band: 'NOTABLE-Ø > PASSIVE-Ø erwartet', field: 'SkillTreeService#buildNodes() (NOTABLE-Nodes)', current: round3(avgByTier.NOTABLE), suggested: '—',
        note: `Durchschnittliche Effekt-Magnitude von NOTABLE-Nodes (${round3(avgByTier.NOTABLE)}) ist nicht größer als von PASSIVE-Nodes (${round3(avgByTier.PASSIVE)}) -- manuelle Prüfung nötig, kein Einzel-Konstanten-Fix möglich.`,
      })
    }
    if (avgByTier.KEYSTONE != null && avgByTier.NOTABLE != null && avgByTier.KEYSTONE <= avgByTier.NOTABLE) {
      flags.push({
        module: 'Skill-Punkte', id: 'tier_order_keystone', metric: `KEYSTONE-Ø ${round3(avgByTier.KEYSTONE)} <= NOTABLE-Ø ${round3(avgByTier.NOTABLE)}`,
        band: 'KEYSTONE-Ø > NOTABLE-Ø erwartet', field: 'SkillTreeService#buildNodes() (KEYSTONE-Nodes)', current: round3(avgByTier.KEYSTONE), suggested: '—',
        note: `Durchschnittliche Effekt-Magnitude von KEYSTONE-Nodes (${round3(avgByTier.KEYSTONE)}) ist nicht größer als von NOTABLE-Nodes (${round3(avgByTier.NOTABLE)}) -- manuelle Prüfung nötig, kein Einzel-Konstanten-Fix möglich.`,
      })
    }
  }
  return { rows, flags, skipped: nodes.length === 0 }
}

function reportKeystones(nodes) {
  if (nodes.length === 0) return { rows: [], flags: [], skipped: true }
  const keystones = nodes.filter((n) => n.nodeTier === 'KEYSTONE' && n.effects.length >= 2)
  const rows = keystones.map((n) => {
    const benefit = n.effects.filter((e) => e.effectValue > 0).reduce((a, e) => a + e.effectValue, 0)
    const malus = n.effects.filter((e) => e.effectValue < 0).reduce((a, e) => a + Math.abs(e.effectValue), 0)
    const ratio = malus > 0 ? benefit / malus : null
    return { id: n.id, name: n.nameDe, benefit, malus, ratio, effects: n.effects }
  }).filter((r) => r.ratio != null)

  const ratios = rows.map((r) => r.ratio).sort((a, b) => a - b)
  const median = ratios.length ? ratios[Math.floor(ratios.length / 2)] : null

  const flags = []
  if (median != null) {
    for (const r of rows) {
      const lo = median * (1 - KEYSTONE_RATIO_DEVIATION)
      const hi = median * (1 + KEYSTONE_RATIO_DEVIATION)
      if (r.ratio < lo || r.ratio > hi) {
        const malusEffect = r.effects.find((e) => e.effectValue < 0)
        const suggestedMalus = malusEffect ? -(r.benefit / median) : null
        flags.push({
          module: 'Keystones', id: r.id, metric: `Bonus:Malus ${round2(r.ratio)}`, band: `${round2(lo)}-${round2(hi)} (Median ${round2(median)})`,
          field: malusEffect ? `SkillTreeService#buildNodes() Node "${r.id}" Effect ${malusEffect.effectType}${malusEffect.targetResource ? '/' + malusEffect.targetResource : ''}` : `Node "${r.id}"`,
          current: malusEffect ? malusEffect.effectValue : '—',
          suggested: suggestedMalus != null ? round3(suggestedMalus) : '—',
          note: `${r.name} (${r.id}) hat Bonus:Malus-Verhältnis ${round2(r.ratio)}, Median über alle Keystones ist ${round2(median)} -- weicht deutlich ab.${malusEffect ? ` Malus ${malusEffect.effectValue} → ${round3(suggestedMalus)} würde das Verhältnis auf den Median bringen.` : ''}`,
        })
      }
    }
  }
  return { rows, flags, skipped: false }
}

function reportMarket(market) {
  const rows = []
  const flags = []
  for (const resource of RESOURCES) {
    const stock = initialStockFor(market, resource)
    const price = initialPriceFor(market, resource)
    const k = poolConstant(stock, price)
    const buyPct = slippagePct(stock, k, SLIPPAGE_QTY, 'buy')
    const sellPct = slippagePct(stock, k, SLIPPAGE_QTY, 'sell')
    const worstPct = Math.max(Math.abs(buyPct), Math.abs(sellPct))
    const lo = market.sellFeeRate * 100 * SLIPPAGE_BAND_MULT_MIN
    const hi = market.sellFeeRate * 100 * SLIPPAGE_BAND_MULT_MAX
    const outOfBand = worstPct < lo || worstPct > hi
    rows.push({ resource, stock, price, buyPct, sellPct, outOfBand })
    if (outOfBand) {
      const target = (lo + hi) / 2
      const suggestedStock = bisect(
        (s) => Math.max(Math.abs(slippagePct(s, poolConstant(s, price), SLIPPAGE_QTY, 'buy')), Math.abs(slippagePct(s, poolConstant(s, price), SLIPPAGE_QTY, 'sell'))),
        50, 100000, target
      )
      flags.push({
        module: 'Markt', id: resource, metric: `Slippage ${round1(worstPct)}% bei ${SLIPPAGE_QTY} Einheiten`, band: `${round1(lo)}-${round1(hi)}%`,
        field: `MarketConfig.initial${cap(resource)}Stock`, current: stock, suggested: Math.round(suggestedStock),
        note: `${resource}: Preis-Impact bei ${SLIPPAGE_QTY} Einheiten ist ${round1(worstPct)}%, Zielband ${round1(lo)}-${round1(hi)}% (relativ zur sellFeeRate ${round1(market.sellFeeRate * 100)}%) -- initial${cap(resource)}Stock ${stock} → ${Math.round(suggestedStock)} bringt es auf ~${round1(target)}%.`,
      })
    }
  }
  return { rows, flags }
}

// ── Formatierung ──────────────────────────────────────────────────────────
function round1(v) { return Math.round(v * 10) / 10 }
function round2(v) { return Math.round(v * 100) / 100 }
function round3(v) { return Math.round(v * 1000) / 1000 }

function padRow(cols, widths) {
  return cols.map((c, i) => String(c).padEnd(widths[i])).join(' | ')
}

function printTable(title, headers, widths, rows) {
  console.log(`\n${title}`)
  console.log(padRow(headers, widths))
  console.log(widths.map((w) => '-'.repeat(w)).join('-+-'))
  for (const r of rows) console.log(padRow(r, widths))
}

// ── SVG-Chart (kein externes CDN, inline Polyline) ────────────────────────
function svgLineChart(points, { width = 480, height = 180, color = '#c96b3b', flagColor = '#c94b3b' } = {}) {
  if (points.length === 0) return '<p>keine Daten</p>'
  const xs = points.map((p) => p.x)
  const ys = points.map((p) => p.y)
  const minX = Math.min(...xs), maxX = Math.max(...xs)
  const minY = Math.min(...ys), maxY = Math.max(...ys)
  const pad = 24
  const sx = (x) => pad + ((x - minX) / (maxX - minX || 1)) * (width - 2 * pad)
  const sy = (y) => height - pad - ((y - minY) / (maxY - minY || 1)) * (height - 2 * pad)
  const path = points.map((p) => `${sx(p.x)},${sy(p.y)}`).join(' ')
  const dots = points.filter((p) => p.flag).map((p) => `<circle cx="${sx(p.x)}" cy="${sy(p.y)}" r="3" fill="${flagColor}" />`).join('')
  return `<svg viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">
    <polyline points="${path}" fill="none" stroke="${color}" stroke-width="2" />
    ${dots}
    <text x="${pad}" y="${height - 6}" font-size="10" fill="#888">${round2(minY)}</text>
    <text x="${width - pad - 30}" y="${height - 6}" font-size="10" fill="#888">${round2(maxY)}</text>
  </svg>`
}

function buildHtmlReport({ progression, citizens, buildings, skillPoints, keystones, market }) {
  const citizenPoints = citizens.rows.map((r) => ({ x: r.n, y: r.cost }))
  const buildingCharts = BUILDING_DEFS.filter((d) => d.passiveResource).map((d) => {
    const rows = buildings.rows.filter((r) => r.building === d.name)
    const points = rows.map((r) => ({ x: r.level, y: r.payback }))
    return `<h3>${d.name}</h3>${svgLineChart(points)}`
  }).join('\n')
  const skillPointPoints = skillPoints.rows.map((r) => ({ x: r.n, y: r.cost }))
  const marketCharts = market.rows.map((r) => {
    return `<tr><td>${r.resource}</td><td>${round2(r.price)}</td><td>${Math.round(r.stock)}</td><td>${round1(r.buyPct)}%</td><td>${round1(r.sellPct)}%</td><td>${r.outOfBand ? '⚠' : ''}</td></tr>`
  }).join('\n')
  const progressionRows = progression.rows.map((r) => {
    return `<tr><td>${r.building}</td><td>${r.reached ? r.daysToFirstPrestige : `> ${SIM_MAX_DAYS}`}</td><td>${r.outOfBand ? '⚠' : ''}</td></tr>`
  }).join('\n')

  return `<!doctype html><html><head><meta charset="utf-8"><title>Cookie Balance Report</title>
<style>body{font-family:sans-serif;max-width:900px;margin:2rem auto;padding:0 1rem}
table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:4px 8px;text-align:right}
th:first-child,td:first-child{text-align:left}
h2{margin-top:2.5rem;border-bottom:2px solid #c96b3b}</style></head><body>
<h1>Cookie Balance Report</h1>
<p>Generiert: ${new Date().toISOString()} (Modus: ${mode})</p>

<h2>Fortschritt (Tage bis 1. Prestige, 1 Startgebäude, Check-in alle ${CHECKIN_INTERVAL_HOURS}h)</h2>
<p>Zielband: ${TARGET_DAYS_MIN}-${TARGET_DAYS_MAX} Tage</p>
<table><tr><th>Startgebäude</th><th>Tage</th><th></th></tr>
${progressionRows}
</table>

<h2>Bürger-Kosten (Rohkurve, kein Payback mehr -- siehe Fortschritt oben)</h2>
${svgLineChart(citizenPoints)}

<h2>Gebäude (Payback in Minuten pro Level, informativ)</h2>
${buildingCharts}

<h2>Skill-Punkte-Kosten</h2>
${svgLineChart(skillPointPoints)}

<h2>Markt (Slippage bei ${SLIPPAGE_QTY} Einheiten)</h2>
<table><tr><th>Ressource</th><th>Preis</th><th>Stock</th><th>Buy %</th><th>Sell %</th><th></th></tr>
${marketCharts}
</table>

${keystones.skipped ? '<p><em>Keystone-Modul übersprungen (keine Node-Daten -- --static ohne laufenden Server).</em></p>' : `
<h2>Keystones (Bonus:Malus)</h2>
<table><tr><th>Node</th><th>Bonus</th><th>Malus</th><th>Verhältnis</th><th></th></tr>
${keystones.rows.map((r) => `<tr><td>${r.name}</td><td>${round3(r.benefit)}</td><td>${round3(r.malus)}</td><td>${round2(r.ratio)}</td><td></td></tr>`).join('\n')}
</table>`}
</body></html>`
}

function buildMarkdownSuggestions(allFlags) {
  const now = new Date()
  const stamp = now.toISOString().slice(0, 16).replace('T', ' ')
  if (allFlags.length === 0) {
    return `# Balance-Vorschläge (${stamp})\n\nKeine Ausreißer gegen die aktuellen Zielbänder gefunden. Nichts zu tun.\n`
  }
  const byModule = {}
  for (const f of allFlags) {
    byModule[f.module] = byModule[f.module] || []
    byModule[f.module].push(f)
  }
  let md = `# Balance-Vorschläge (${stamp})\n\n`
  md += `Erzeugt von \`npm run balance:report\` (Modus: ${mode}). Für Claude: bitte mit dem\n`
  md += `Entwickler durchgehen, welche Vorschläge übernommen werden, dann die gewählten\n`
  md += `Werte in \`GameBalanceConfig.java\`/\`MarketConfig.java\`/\`SkillTreeService.java\`\n`
  md += `eintragen. Danach das Skript erneut laufen lassen, um zu bestätigen, dass die\n`
  md += `Flags weg/kleiner sind. Details: \`docs/plans/2026-08-13-open-balance-report-tool.md\`.\n\n`

  for (const [module, flags] of Object.entries(byModule)) {
    md += `## ${module}\n\n`
    for (const f of flags) {
      md += `### ${f.id}\n\n`
      md += `- Feld: \`${f.field}\`\n`
      md += `- Aktueller Wert: \`${f.current}\`\n`
      md += `- Metrik: ${f.metric} (Zielband: ${f.band})\n`
      md += `- Vorschlag: \`${f.suggested}\`\n`
      md += `- ${f.note}\n\n`
    }
  }
  return md
}

// ── Main ─────────────────────────────────────────────────────────────────

async function main() {
  const { balance, market, nodes } = await loadData()

  const progression = reportProgression(balance, market)
  const citizens = reportCitizens(balance)
  const buildings = reportBuildings(balance, market)
  const skillPoints = reportSkillPoints(balance, nodes)
  const keystones = reportKeystones(nodes)
  const marketReport = reportMarket(market)

  printTable(`Fortschritt (Tage bis 1. Prestige, 1 Startgebäude, Check-in alle ${CHECKIN_INTERVAL_HOURS}h, Ziel ${TARGET_DAYS_MIN}-${TARGET_DAYS_MAX}d)`,
    ['Gebäude', 'Tage', ''], [14, 8, 2],
    progression.rows.map((r) => [r.building, r.reached ? r.daysToFirstPrestige : `>${SIM_MAX_DAYS}`, r.outOfBand ? '⚠' : '']))

  printTable('Bürger-Kosten (Rohkurve)', ['n', 'Kosten'], [4, 10],
    citizens.rows.filter((_, i) => i % 5 === 0).map((r) => [r.n, round2(r.cost)]))

  printTable('Gebäude (Payback in Minuten, informativ)', ['Gebäude', 'Level', 'Kosten', 'Payback (min)'],
    [14, 6, 10, 14],
    buildings.rows.filter((r) => r.level !== undefined).filter((r) => r.level % 3 === 0)
      .map((r) => [r.building, r.level + 1, round2(r.cost), round1(r.payback)]))

  printTable('Skill-Punkte-Kosten', ['n', 'Kosten'], [4, 12],
    skillPoints.rows.map((r) => [r.n, round2(r.cost)]))

  if (!keystones.skipped) {
    printTable('Keystones (Bonus:Malus)', ['Node', 'Bonus', 'Malus', 'Verhältnis', ''], [16, 8, 8, 10, 2],
      keystones.rows.map((r) => [r.id, round3(r.benefit), round3(r.malus), round2(r.ratio), '']))
  } else {
    console.log('\nKeystones: übersprungen (keine Node-Daten, --static ohne Server)')
  }

  printTable('Markt (Slippage bei ' + SLIPPAGE_QTY + ' Einheiten)', ['Ressource', 'Preis', 'Stock', 'Buy %', 'Sell %', ''], [10, 8, 8, 8, 8, 2],
    marketReport.rows.map((r) => [r.resource, round2(r.price), Math.round(r.stock), round1(r.buyPct), round1(r.sellPct), r.outOfBand ? '⚠' : '']))

  const allFlags = [...progression.flags, ...skillPoints.flags, ...keystones.flags, ...marketReport.flags]
  console.log(`\n${allFlags.length} Ausreißer gefunden.`)

  mkdirSync(REPORT_DIR, { recursive: true })

  const html = buildHtmlReport({ progression, citizens, buildings, skillPoints, keystones, market: marketReport })
  writeFileSync(HTML_OUT, html, 'utf8')
  console.log(`HTML-Report: ${HTML_OUT}`)

  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 16)
  const mdPath = join(REPORT_DIR, `${stamp}-suggestions.md`)
  writeFileSync(mdPath, buildMarkdownSuggestions(allFlags), 'utf8')
  console.log(`Vorschlagsdatei: ${mdPath}`)
}

main().catch((err) => {
  console.error('[balance-report] Fehler:', err)
  process.exit(1)
})

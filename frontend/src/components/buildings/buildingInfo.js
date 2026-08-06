// Static per-building display info (title, icon, illustrative passive/hover/worker/wage figures).
// The population/wage economy shown here mirrors the "Cookie Layouts" design mockup;
// there is no backend model yet for assigned workers or wages (see game design doc §4/§6),
// so these numbers are illustrative placeholders until that system ships — only the
// hover-harvest amounts (SUGAR/FLOUR/EGGS/BUTTER/CHOCOLATE/MILK) are wired to the real API.
// wagePerMin must match BuildingService.java static definitions
// title/resource-label text lives in i18n (locales/{de,en}/buildingInfo.json) — use
// buildingTitle()/resourceLabel() below to resolve it, since this module has no
// component context of its own to call useI18n() from.
export const BUILDING_INFO = {
  pond: {
    titleKey: 'buildingInfo.pond', icon: 'zucker', side: 'right', overlayRate: '+1.4/s', workers: 2, wagePerMin: 4,
    resource: 'SUGAR',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+1.4/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+3.2/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  ofen: {
    titleKey: 'buildingInfo.ofen', icon: 'ofen', side: 'right', overlayRate: '1/2', workers: 2, wagePerMin: 4,
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '2 Batches', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+1 Batch', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  rathaus: {
    titleKey: 'buildingInfo.rathaus', icon: 'haus', side: 'left', overlayRate: '', workers: 0, wagePerMin: 0,
    rows: [
      { k: 'Einwohner', v: '→ Dialog', color: 'w' },
      { k: 'Funktion', v: 'Einwohner-Slots', color: 'g' },
    ],
  },
  markt: {
    titleKey: 'buildingInfo.markt', icon: 'stand', side: 'left', overlayRate: 'GEB. 8%', workers: 0, wagePerMin: 0,
    rows: [
      { k: 'Marktgebühr', v: '8 %', color: 'o' },
    ],
  },
  lager: {
    titleKey: 'buildingInfo.lager', icon: 'lager', side: 'left', overlayRate: '', workers: 0, wagePerMin: 0,
    rows: [
      { k: 'Gesamtlimit', v: '→ Dialog', color: 'w' },
      { k: 'Auto-Verkauf', v: 'bei Überfluss', color: 'y' },
    ],
    note: 'Wenn das Lager voll ist, wird Überproduktion automatisch zum aktuellen Marktpreis verkauft.',
  },
  hof: {
    titleKey: 'buildingInfo.hof', icon: 'mehl', side: 'right', overlayRate: '+2.1/s', workers: 3, wagePerMin: 6,
    resource: 'FLOUR',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+2.1/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+4.6/s', color: 'y' },
      { k: 'Arbeiter', v: '3', color: 'w' },
      { k: 'Lohn', v: '6 C/min', color: 'o' },
    ],
  },
  huhn: {
    titleKey: 'buildingInfo.huhn', icon: 'eier', side: 'right', overlayRate: '+0.8/s', workers: 2, wagePerMin: 4,
    resource: 'EGGS',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+0.8/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+2.0/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  butter: {
    titleKey: 'buildingInfo.butter', icon: 'butter', side: 'right', overlayRate: '+0.6/s', workers: 1, wagePerMin: 2,
    resource: 'BUTTER',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+0.6/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+1.8/s', color: 'y' },
      { k: 'Arbeiter', v: '1', color: 'w' },
      { k: 'Lohn', v: '2 C/min', color: 'o' },
    ],
  },
  kakao: {
    titleKey: 'buildingInfo.kakao', icon: 'schoko', side: 'left', overlayRate: '+1.2/s', workers: 2, wagePerMin: 4,
    resource: 'CHOCOLATE',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+1.2/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+2.8/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  kuh: {
    titleKey: 'buildingInfo.kuh', icon: 'milch', side: 'left', overlayRate: '+4.8/s', workers: 4, wagePerMin: 8,
    resource: 'MILK',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+4.8/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+8.4/s', color: 'y' },
      { k: 'Arbeiter', v: '4', color: 'w' },
      { k: 'Lohn', v: '8 C/min', color: 'o' },
    ],
  },
}

export const RESOURCE_LABEL_KEY = {
  SUGAR: 'buildingInfo.resourceSugar', FLOUR: 'buildingInfo.resourceFlour', EGGS: 'buildingInfo.resourceEggs',
  BUTTER: 'buildingInfo.resourceButter', CHOCOLATE: 'buildingInfo.resourceChocolate', MILK: 'buildingInfo.resourceMilk',
}

export const RESOURCE_ICON = {
  SUGAR: 'zucker', FLOUR: 'mehl', EGGS: 'eier',
  BUTTER: 'butter', CHOCOLATE: 'schoko', MILK: 'milch',
}

// Resolve translated display text — call from a component's computed/render scope
// so it stays reactive to locale changes (this module has no i18n context of its own).
export function buildingTitle(id, t) {
  const key = BUILDING_INFO[id]?.titleKey
  return key ? t(key) : ''
}

export function resourceLabel(resourceName, t) {
  const key = RESOURCE_LABEL_KEY[resourceName]
  return key ? t(key) : (resourceName ?? '')
}

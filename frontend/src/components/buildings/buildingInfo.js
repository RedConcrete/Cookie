// Static per-building display info (title, icon, illustrative passive/hover/worker/wage figures).
// The population/wage economy shown here mirrors the "Cookie Layouts" design mockup;
// there is no backend model yet for assigned workers or wages (see game design doc §4/§6),
// so these numbers are illustrative placeholders until that system ships — only the
// hover-harvest amounts (SUGAR/FLOUR/EGGS/BUTTER/CHOCOLATE/MILK) are wired to the real API.
// wagePerMin must match BuildingService.java static definitions
export const BUILDING_INFO = {
  pond: {
    title: 'ZUCKERTEICH', icon: 'zucker', side: 'right', overlayRate: '+1.4/s', workers: 2, wagePerMin: 4,
    resource: 'SUGAR',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+1.4/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+3.2/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  ofen: {
    title: 'BACKOFEN', icon: 'ofen', side: 'right', overlayRate: '1/2', workers: 2, wagePerMin: 4,
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '2 Batches', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+1 Batch', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  rathaus: {
    title: 'RATHAUS', icon: 'haus', side: 'left', overlayRate: '', workers: 0, wagePerMin: 0,
    rows: [
      { k: 'Einwohner', v: '→ Dialog', color: 'w' },
      { k: 'Funktion', v: 'Einwohner-Slots', color: 'g' },
    ],
  },
  markt: {
    title: 'MARKT', icon: 'stand', side: 'left', overlayRate: 'GEB. 8%', workers: 0, wagePerMin: 0,
    rows: [
      { k: 'Marktgebühr', v: '8 %', color: 'o' },
    ],
  },
  lager: {
    title: 'LAGER', icon: 'lager', side: 'left', overlayRate: '', workers: 0, wagePerMin: 0,
    rows: [
      { k: 'Gesamtlimit', v: '→ Dialog', color: 'w' },
      { k: 'Auto-Verkauf', v: 'bei Überfluss', color: 'y' },
    ],
    note: 'Wenn das Lager voll ist, wird Überproduktion automatisch zum aktuellen Marktpreis verkauft.',
  },
  hof: {
    title: 'BAUERNHOF', icon: 'mehl', side: 'right', overlayRate: '+2.1/s', workers: 3, wagePerMin: 6,
    resource: 'FLOUR',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+2.1/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+4.6/s', color: 'y' },
      { k: 'Arbeiter', v: '3', color: 'w' },
      { k: 'Lohn', v: '6 C/min', color: 'o' },
    ],
  },
  huhn: {
    title: 'HÜHNERHOF', icon: 'eier', side: 'right', overlayRate: '+0.8/s', workers: 2, wagePerMin: 4,
    resource: 'EGGS',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+0.8/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+2.0/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  butter: {
    title: 'BUTTEREI', icon: 'butter', side: 'right', overlayRate: '+0.6/s', workers: 1, wagePerMin: 2,
    resource: 'BUTTER',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+0.6/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+1.8/s', color: 'y' },
      { k: 'Arbeiter', v: '1', color: 'w' },
      { k: 'Lohn', v: '2 C/min', color: 'o' },
    ],
  },
  kakao: {
    title: 'PLANTAGE', icon: 'schoko', side: 'left', overlayRate: '+1.2/s', workers: 2, wagePerMin: 4,
    resource: 'CHOCOLATE',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+1.2/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+2.8/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  kuh: {
    title: 'KUHSTALL', icon: 'milch', side: 'left', overlayRate: '+4.8/s', workers: 4, wagePerMin: 8,
    resource: 'MILK',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+4.8/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+8.4/s', color: 'y' },
      { k: 'Arbeiter', v: '4', color: 'w' },
      { k: 'Lohn', v: '8 C/min', color: 'o' },
    ],
  },
}

export const RESOURCE_LABEL = {
  SUGAR: 'Zucker', FLOUR: 'Mehl', EGGS: 'Eier',
  BUTTER: 'Butter', CHOCOLATE: 'Schokolade', MILK: 'Milch',
}

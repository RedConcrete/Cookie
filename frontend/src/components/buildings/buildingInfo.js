// Static per-building display info (title, icon, illustrative passive/hover/worker/wage figures).
// The population/wage economy shown here mirrors the "Cookie Layouts" design mockup;
// there is no backend model yet for assigned workers or wages (see game design doc §4/§6),
// so these numbers are illustrative placeholders until that system ships — only the
// hover-harvest amounts (SUGAR/FLOUR/EGGS/BUTTER/CHOCOLATE/MILK) are wired to the real API.
export const BUILDING_INFO = {
  pond: {
    title: 'ZUCKERTEICH', icon: 'zucker', side: 'right', overlayRate: '+1.4/s', workers: 2,
    resource: 'SUGAR',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+1.4/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+3.2/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  ofen: {
    title: 'BACKOFEN', icon: 'ofen', side: 'right', overlayRate: '1/2', workers: 2,
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '2 Batches', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+1 Batch', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  rathaus: {
    title: 'RATHAUS', icon: 'haus', side: 'right', overlayRate: '12/16', workers: 1,
    rows: [
      { k: 'Einwohner', v: '12 / 16', color: 'w' },
      { k: 'Lohnsumme', v: '−34 C/min', color: 'o' },
      { k: 'Verwaltung', v: '1 Arbeiter', color: 'w' },
      { k: 'Lohn', v: '2 C/min', color: 'o' },
    ],
  },
  markt: {
    title: 'MARKT', icon: 'stand', side: 'left', overlayRate: 'GEB. 8%', workers: 1,
    rows: [
      { k: 'Marktgebühr', v: '8 %', color: 'o' },
      { k: 'Auto-Verkauf', v: 'aktiv', color: 'g' },
      { k: 'Arbeiter', v: '1', color: 'w' },
      { k: 'Lohn', v: '2 C/min', color: 'o' },
    ],
  },
  lager: {
    title: 'LAGER', icon: 'lager', side: 'left', overlayRate: '4.2K/6K', workers: 1,
    rows: [
      { k: 'Gesamtlimit', v: '4.2K / 6.0K', color: 'w' },
      { k: 'Erträge', v: 'keine', color: 'm' },
      { k: 'Betrieb', v: '−3 C/min', color: 'o' },
      { k: 'Arbeiter · Lohn', v: '1 · 2 C/min', color: 'o' },
      { k: 'Auto-Verkauf', v: 'zum Tagespreis', color: 'y' },
    ],
    note: 'Lager produziert nichts — es kostet nur und hebt das Limit, das für alle Ressourcen zusammen gerechnet wird. Alles was darüber produziert wird, verkauft sich automatisch zum aktuellen Preis, auch wenn der schlecht steht.',
  },
  hof: {
    title: 'BAUERNHOF', icon: 'mehl', side: 'right', overlayRate: '+2.1/s', workers: 3,
    resource: 'FLOUR',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+2.1/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+4.6/s', color: 'y' },
      { k: 'Arbeiter', v: '3', color: 'w' },
      { k: 'Lohn', v: '6 C/min', color: 'o' },
    ],
  },
  huhn: {
    title: 'HÜHNERHOF', icon: 'eier', side: 'right', overlayRate: '+0.8/s', workers: 2,
    resource: 'EGGS',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+0.8/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+2.0/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  butter: {
    title: 'BUTTEREI', icon: 'butter', side: 'right', overlayRate: '+0.6/s', workers: 1,
    resource: 'BUTTER',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+0.6/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+1.8/s', color: 'y' },
      { k: 'Arbeiter', v: '1', color: 'w' },
      { k: 'Lohn', v: '2 C/min', color: 'o' },
    ],
  },
  kakao: {
    title: 'PLANTAGE', icon: 'schoko', side: 'left', overlayRate: '+1.2/s', workers: 2,
    resource: 'CHOCOLATE',
    rows: [
      { k: 'Passiv · mit Arbeitern', v: '+1.2/s', color: 'g' },
      { k: 'Hover · ohne Lohn', v: '+2.8/s', color: 'y' },
      { k: 'Arbeiter', v: '2', color: 'w' },
      { k: 'Lohn', v: '4 C/min', color: 'o' },
    ],
  },
  kuh: {
    title: 'KUHSTALL', icon: 'milch', side: 'left', overlayRate: '+4.8/s', workers: 4,
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

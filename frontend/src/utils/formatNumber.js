// Zentrales Format-Utility fuer Ressourcen-/Cookie-/Preis-Anzeigen. Rundet IMMER auf (nie ab)
// und zeigt nie mehr als die angegebene Anzahl Nachkommastellen -- Standard-toFixed() rundet
// kaufmaennisch (auch ab), was z.B. bei knappen Kosten den Eindruck erwecken kann, man haette
// weniger als tatsaechlich vorhanden. Siehe docs/ROADMAP.md (Freund-Playtest-Feedback).
export function roundUp(value, decimals = 2) {
  const factor = 10 ** decimals
  return Math.ceil((Number(value) || 0) * factor) / factor
}

/** 1 Nachkommastelle, aufgerundet -- kompakte Anzeige (z.B. HUD-Ressourcenleiste). */
export function fmt(value) {
  return roundUp(value, 1).toFixed(1)
}

/** 2 Nachkommastellen, aufgerundet -- Standardanzeige fuer Betraege/Preise. */
export function fmt2(value) {
  return roundUp(value, 2).toFixed(2)
}

/** K/M-Kurzform (z.B. Net Worth), Basis immer aufgerundet auf 2 Nachkommastellen. */
export function fmtBig(value) {
  const v = Number(value) || 0
  if (v >= 1_000_000) return roundUp(v / 1_000_000, 2).toFixed(2) + 'M'
  if (v >= 1_000) return roundUp(v / 1_000, 2).toFixed(2) + 'K'
  return roundUp(v, 1).toFixed(1)
}

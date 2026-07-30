import { reactive } from 'vue'

// Badge/"Orden" showcase — frontend-only for now. There is no backend model yet
// for admin-granted badges (see cookie-game-design.md §8 "Kosmetik"); this keeps
// the granted list in memory for the current session so the admin panel and the
// profile vitrine can be demoed end-to-end. Wiring this to real persistence needs
// a PlayerCosmeticEntity + endpoints on the Spring Boot side.

const TEMPLATES = [
  { id: 'event_winner', name: 'Event-Sieger', icon: 'krone', color: '#e05a4a', category: 'EVENT' },
  { id: 'top3',         name: 'Top 3',        icon: 'medal', color: '#8fae5c', category: 'EVENT' },
  { id: 'community',    name: 'Community',    icon: 'stern', color: '#5aa0e0', category: 'COMMUNITY' },
]

const state = reactive({
  badges: [
    { id: 1, name: 'Beta-Bäcker',   desc: 'Community · Dank für Bug-Reports',        awardedOn: '2026-07-26', icon: 'stern', color: '#7a6bc4', category: 'COMMUNITY' },
    { id: 2, name: 'Marktmacher',   desc: 'Event · 2. Platz Handelsvolumen',          awardedOn: '2026-07-19', icon: 'krone', color: '#b48b1e', category: 'EVENT' },
    { id: 3, name: 'Nachtbäcker',   desc: 'Event · 24h-Backmarathon',                 awardedOn: '2026-07-05', icon: 'medal', color: '#8fae5c', category: 'EVENT' },
    { id: 4, name: 'Zuckerbaron',   desc: 'Event · 100K Zucker verkauft',             awardedOn: '2026-06-28', icon: 'stern', color: '#5aa0e0', category: 'EVENT' },
    { id: 5, name: 'Sommer-Sprint', desc: 'Event · 1. Platz Net-Worth-Rennen',        awardedOn: '2026-06-14', icon: 'krone', color: '#e05a4a', category: 'EVENT' },
    { id: 6, name: 'Erster Ofen',   desc: 'Community · Launch-Woche',                 awardedOn: '2026-03-02', icon: 'medal', color: '#e8b93c', category: 'COMMUNITY' },
  ],
  nextId: 7,
})

export function useBadges() {
  function grant(templateId, note, date) {
    const t = TEMPLATES.find(x => x.id === templateId)
    if (!t) return
    state.badges.unshift({
      id: state.nextId++, name: t.name, desc: note || t.category, icon: t.icon, color: t.color,
      category: t.category, awardedOn: date || new Date().toISOString().slice(0, 10),
    })
  }
  function revoke(id) {
    const i = state.badges.findIndex(b => b.id === id)
    if (i >= 0) state.badges.splice(i, 1)
  }
  return { badges: state.badges, templates: TEMPLATES, grant, revoke }
}

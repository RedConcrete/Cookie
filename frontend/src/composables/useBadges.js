import { reactive } from 'vue'

// Badge/"Orden" showcase — frontend-only for now. There is no backend model yet
// for admin-granted badges (see cookie-game-design.md §8 "Kosmetik"); this keeps
// the granted list in memory for the current session so the admin panel and the
// profile vitrine can be demoed end-to-end. Wiring this to real persistence needs
// a PlayerCosmeticEntity + endpoints on the Spring Boot side.
//
// State ist bewusst leer -- Orden werden ausschliesslich manuell vom Admin
// verliehen, nie vorab gesetzt. Achtung: aktuell ein einziger globaler State,
// nicht pro steamId getrennt (kommt erst mit der echten Backend-Anbindung).

const TEMPLATES = [
  { id: 'event_winner', name: 'Event-Sieger', icon: 'krone', color: '#e67146', category: 'EVENT' },
  { id: 'top3',         name: 'Top 3',        icon: 'medal', color: '#6dba79', category: 'EVENT' },
  { id: 'community',    name: 'Community',    icon: 'stern', color: '#6dba79', category: 'COMMUNITY' },
]

const state = reactive({
  badges: [],
  nextId: 1,
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

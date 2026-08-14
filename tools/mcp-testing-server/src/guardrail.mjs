// Harte Leitplanke (siehe docs/plans/2026-08-14-open-mcp-ki-testing-balancing.md v1):
// dieser Server darf NIE gegen ein Backend laufen, das nicht erkennbar im Dev-Modus
// ist. Zwei unabhängige Checks, beide muessen passen -- Host-Whitelist zuerst (billig,
// kein Netzwerk-Call), dann eine echte Abfrage des devMode-Flags gegen den Server.
// Wird vor JEDEM Tool-Aufruf erneut geprueft, nicht nur beim Start (siehe api-client.mjs
// guardedFetch) -- ein Server-Neustart mit anderer Config waehrend einer laufenden
// MCP-Session darf keinen bereits verbundenen Agent-Client unbemerkt live schalten.

const LOCALHOST_HOSTS = new Set(['localhost', '127.0.0.1', '::1'])

export function assertLocalhost(baseUrl) {
  let host
  try {
    host = new URL(baseUrl).hostname
  } catch {
    throw new Error(`Guardrail: "${baseUrl}" ist keine gueltige URL.`)
  }
  if (!LOCALHOST_HOSTS.has(host)) {
    throw new Error(
      `Guardrail: Ziel-Host "${host}" ist nicht localhost. Dieser MCP-Server darf ` +
      `ausschliesslich gegen einen lokalen Dev-Server laufen -- niemals gegen den ` +
      `Live-Beta-Server oder einen anderen entfernten Host. Abbruch.`
    )
  }
}

const HEALTH_CHECK_TIMEOUT_MS = 3000

export async function assertDevMode(baseUrl) {
  assertLocalhost(baseUrl)
  let res
  try {
    res = await fetch(`${baseUrl}/api/v1/config`, { signal: AbortSignal.timeout(HEALTH_CHECK_TIMEOUT_MS) })
  } catch (err) {
    const reason = err.name === 'TimeoutError' ? `keine Antwort innerhalb ${HEALTH_CHECK_TIMEOUT_MS}ms` : err.message
    throw new Error(`Guardrail: Backend unter ${baseUrl} nicht erreichbar (${reason}). Läuft der Dev-Server (scripts/start.sh)?`)
  }
  if (!res.ok) {
    throw new Error(`Guardrail: /api/v1/config antwortete mit HTTP ${res.status} -- Backend-Zustand unklar, Abbruch.`)
  }
  const cfg = await res.json()
  if (cfg.devMode !== true) {
    throw new Error(
      `Guardrail: Ziel-Backend meldet devMode=${cfg.devMode}. Dieser MCP-Server darf ` +
      `ausschliesslich gegen ein Backend mit app.dev-mode=true laufen. Abbruch -- keine ` +
      `Aktion wurde ausgefuehrt.`
    )
  }
}

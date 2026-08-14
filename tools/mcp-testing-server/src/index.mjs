#!/usr/bin/env node
// Dev-only MCP-Server: wrapt die bestehende Cookie-REST-API, damit ein KI-Agent als
// DEV_PLAYER_001 Spielaktionen ausfuehren kann (Markt, Ernte, Gebaeude-Einsammeln).
// Kein eigener Datenzugriff, keine Admin-Rechte -- nur die normalen Gameplay-Endpunkte,
// die ein echter Spieler auch hat. Siehe docs/plans/2026-08-14-open-mcp-ki-testing-balancing.md
// (Abschnitt v1) fuer den Plan dahinter und src/guardrail.mjs fuer die Dev-Mode-Sperre.
//
// Start: npm start  (oder node src/index.mjs)
// Env:   MCP_TARGET_BASE_URL (default http://localhost:9876)
//        MCP_DEV_PLAYER_ID   (default DEV_PLAYER_001)

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { z } from 'zod'
import { assertDevMode } from './guardrail.mjs'
import { BASE_URL, DEV_PLAYER_ID, getGameState, marketTrade, harvest, collectBuilding } from './api-client.mjs'

const RESOURCE_NAMES = ['SUGAR', 'FLOUR', 'EGGS', 'BUTTER', 'CHOCOLATE', 'MILK']

function textResult(value) {
  return { content: [{ type: 'text', text: JSON.stringify(value, null, 2) }] }
}

function errorResult(err) {
  return { content: [{ type: 'text', text: `Fehler: ${err.message}` }], isError: true }
}

const server = new McpServer({ name: 'cookie-mcp-testing-server', version: '0.1.0' })

server.registerTool(
  'game_get_state',
  {
    title: 'Spielstand abfragen',
    description:
      'Liest den aktuellen Serverstand des Dev-Players (Cookies, Ressourcen, Lager-Cap, ' +
      'Buerger, alle Gebaeude inkl. buildingId/pendingAmount, aktuelle Marktpreise). Vor ' +
      'jeder weiteren Aktion aufrufen statt den Zustand zu raten.',
    inputSchema: {},
  },
  async () => {
    try {
      const state = await getGameState(DEV_PLAYER_ID)
      return textResult(state)
    } catch (err) {
      return errorResult(err)
    }
  }
)

server.registerTool(
  'market_buy',
  {
    title: 'Am Markt kaufen',
    description:
      'Kauft eine Ressourcenmenge am AMM-Markt (bewegt Preis/Stock, siehe MarketService). ' +
      'Server validiert Kosten/Bestand/Cooldown neu -- Fehlermeldungen (z.B. negative Menge, ' +
      'Markt leer, Cooldown aktiv) kommen unveraendert vom Backend zurueck.',
    inputSchema: {
      resource: z.enum(RESOURCE_NAMES).describe('Ressourcenname, z.B. SUGAR'),
      amount: z.number().describe('Menge, muss > 0 sein (negative Werte testen bewusst den Negative-Amount-Exploit-Fix)'),
    },
  },
  async ({ resource, amount }) => {
    try {
      const result = await marketTrade('BUY', resource, amount, DEV_PLAYER_ID)
      return textResult(result)
    } catch (err) {
      return errorResult(err)
    }
  }
)

server.registerTool(
  'market_sell',
  {
    title: 'Am Markt verkaufen',
    description: 'Verkauft eine Ressourcenmenge am AMM-Markt. Gleiche Serverseitige Validierung wie market_buy.',
    inputSchema: {
      resource: z.enum(RESOURCE_NAMES).describe('Ressourcenname, z.B. SUGAR'),
      amount: z.number().describe('Menge, muss > 0 sein und im Bestand des Dev-Players vorhanden'),
    },
  },
  async ({ resource, amount }) => {
    try {
      const result = await marketTrade('SELL', resource, amount, DEV_PLAYER_ID)
      return textResult(result)
    } catch (err) {
      return errorResult(err)
    }
  }
)

server.registerTool(
  'farm_harvest',
  {
    title: 'Ressource hovern/ernten',
    description:
      'Erntet eine Ressource per aktivem Hover (UserService#harvest). Die Menge wird rein ' +
      'serverseitig aus verstrichener Zeit berechnet (gedeckelt auf 6s pro Aufruf) -- kein ' +
      'amount-Parameter, wiederholtes Aufrufen simuliert laengeres Hovern.',
    inputSchema: {
      resource: z.enum(RESOURCE_NAMES).describe('Ressourcenname, z.B. SUGAR'),
    },
  },
  async ({ resource }) => {
    try {
      const result = await harvest(resource, DEV_PLAYER_ID)
      return textResult(result)
    } catch (err) {
      return errorResult(err)
    }
  }
)

server.registerTool(
  'farm_collect_building',
  {
    title: 'Gebaeude-Produktion einsammeln',
    description:
      'Sammelt die passiv angesammelte Produktion eines Gebaeudes ein (PassiveIncomeService). ' +
      'buildingId kommt aus game_get_state (buildings[].id). Server lehnt ab, wenn der ' +
      'Collect-Cooldown noch laeuft oder das Gebaeude dem Dev-Player nicht gehoert.',
    inputSchema: {
      buildingId: z.string().describe('Gebaeude-ID aus game_get_state, z.B. "hof"'),
    },
  },
  async ({ buildingId }) => {
    try {
      const result = await collectBuilding(buildingId, DEV_PLAYER_ID)
      return textResult(result)
    } catch (err) {
      return errorResult(err)
    }
  }
)

async function main() {
  // Harte Leitplanke: Server startet erst gar nicht, wenn das Ziel-Backend nicht
  // erkennbar im Dev-Modus laeuft. Einzelne Tool-Calls pruefen zusaetzlich erneut
  // (siehe api-client.mjs guardedFetch) fuer den Fall eines Backend-Neustarts mit
  // anderer Config waehrend einer laufenden Session.
  try {
    await assertDevMode(BASE_URL)
  } catch (err) {
    console.error(`[mcp-testing-server] ${err.message}`)
    process.exit(1)
  }
  console.error(`[mcp-testing-server] Dev-Mode bestaetigt (${BASE_URL}), spiele als ${DEV_PLAYER_ID}.`)

  const transport = new StdioServerTransport()
  await server.connect(transport)
}

main().catch((err) => {
  console.error('[mcp-testing-server] Fataler Fehler:', err)
  process.exit(1)
})

<template>
  <div style="padding: 60px; font-family: sans-serif; font-size: 15px; line-height: 1.8;">
    <h2>Nested Tooltip Demo</h2>

    <p>
      Hover über
      <NestedTooltip :content="glossarTooltip">
        <strong style="cursor: help; text-decoration: underline dotted;">Reaktivität</strong>
      </NestedTooltip>
      in Vue 3 basiert auf Proxies.
    </p>

    <p>
      Auch inline verwendbar:
      <NestedTooltip content="Einfacher Text ohne nested Elemente.">
        <span style="cursor: help; border-bottom: 1px dashed #666;">einfacher Tooltip</span>
      </NestedTooltip>
    </p>
  </div>
</template>

<script setup>
import NestedTooltip from './NestedTooltip.vue'
import './NestedTooltip.css'

// Strukturierter Content: Array aus Segmenten
// { text } = normaler Text
// { text, tooltip } = highlighted mit eigenem Tooltip (string oder wieder Array)
const glossarTooltip = [
  { text: 'Das ' },
  {
    text: 'Reaktivitätssystem',
    tooltip: [
      { text: 'Basiert auf ' },
      {
        text: 'ES6 Proxy',
        tooltip: 'Ein Proxy umschließt ein Objekt und fängt Zugriffe (get/set) ab — so erkennt Vue welche Daten sich ändern.',
      },
      { text: '. Abhängigkeiten werden beim Lesen automatisch getrackt.' },
    ],
  },
  { text: ' erkennt automatisch, welche ' },
  {
    text: 'computed properties',
    tooltip: [
      { text: 'Gecachte, abgeleitete Werte. Werden nur neu berechnet wenn ihre ' },
      {
        text: 'reaktiven Abhängigkeiten',
        tooltip: 'Alle ref() und reactive() Werte, die während der Berechnung gelesen wurden.',
      },
      { text: ' sich ändern.' },
    ],
  },
  { text: ' neu berechnet werden müssen.' },
]
</script>

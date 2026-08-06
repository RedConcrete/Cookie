import { createI18n } from 'vue-i18n'

// Each locale's namespace files (one per component/view) are auto-merged here,
// so adding a new namespace only means dropping a JSON file in locales/<lang>/.
const deModules = import.meta.glob('./locales/de/*.json', { eager: true })
const enModules = import.meta.glob('./locales/en/*.json', { eager: true })

function buildMessages(modules) {
  const messages = {}
  for (const path in modules) {
    const name = path.match(/([^/]+)\.json$/)[1]
    messages[name] = modules[path].default ?? modules[path]
  }
  return messages
}

export const SUPPORTED_LOCALES = ['de', 'en']
const STORAGE_KEY = 'cookieLang'

function detectLocale() {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && SUPPORTED_LOCALES.includes(saved)) return saved
  const browserLang = navigator.language?.slice(0, 2)
  return SUPPORTED_LOCALES.includes(browserLang) ? browserLang : 'de'
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: 'de',
  messages: {
    de: buildMessages(deModules),
    en: buildMessages(enModules),
  },
})

export function setLocale(locale) {
  if (!SUPPORTED_LOCALES.includes(locale)) return
  i18n.global.locale.value = locale
  localStorage.setItem(STORAGE_KEY, locale)
  document.documentElement.setAttribute('lang', locale)
}

document.documentElement.setAttribute('lang', i18n.global.locale.value)

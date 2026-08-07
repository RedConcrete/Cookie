import { onMounted, onUnmounted } from 'vue'

// Verallgemeinert das Hamburger-Menue-Muster aus FarmGridView.vue (mousedown-Listener auf
// document, ignoriert Klicks innerhalb der uebergebenen Elemente) fuer beliebige Popups/
// Popovers. `elRefs` kann ein einzelnes Template-Ref oder ein Array davon sein (auch fuer
// Vue-Komponenten-Refs, deren echtes DOM-Element unter `.$el` haengt -- z.B. Teleport-Panels).
export function useClickOutside(elRefs, onOutside, isActive = () => true) {
  const refs = Array.isArray(elRefs) ? elRefs : [elRefs]

  function handler(e) {
    if (!isActive()) return
    for (const r of refs) {
      const node = r?.value?.$el ?? r?.value
      if (node && node.contains(e.target)) return
    }
    onOutside(e)
  }

  onMounted(() => document.addEventListener('mousedown', handler))
  onUnmounted(() => document.removeEventListener('mousedown', handler))
}

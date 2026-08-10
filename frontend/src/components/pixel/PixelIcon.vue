<template>
  <img
    class="pixel-icon"
    :class="{ 'pixel-icon-smooth': SMOOTH.has(name) }"
    :src="ICONS[name]"
    :style="{ width: size + 'px', height: size + 'px' }"
    alt=""
  />
</template>

<script setup>
import cookie from '../../assets/icons/cookie.svg'
import zucker from '../../assets/icons/zucker.svg'
import mehl   from '../../assets/icons/mehl.svg'
import eier   from '../../assets/icons/eier.svg'
import butter from '../../assets/icons/butter.svg'
import schoko from '../../assets/icons/schoko.svg'
import milch  from '../../assets/icons/milch.svg'
import haus   from '../../assets/icons/haus.svg'
import stand  from '../../assets/icons/stand.svg'
import ofen   from '../../assets/icons/ofen.svg'
import shop   from '../../assets/icons/shop.svg'
import pokal  from '../../assets/icons/pokal.svg'
import einw   from '../../assets/icons/einw.svg'
import lohn   from '../../assets/icons/lohn.svg'
import lager  from '../../assets/icons/lager.svg'
import medal  from '../../assets/icons/medal.svg'
import krone  from '../../assets/icons/krone.svg'
import stern  from '../../assets/icons/stern.svg'
import upgrade from '../../assets/icons/upgrade.svg'
import zentrieren from '../../assets/icons/zentrieren.svg'
import check   from '../../assets/icons/check.svg'
import sanduhr from '../../assets/icons/sanduhr.svg'
import music   from '../../assets/icons/music.svg'
import sound   from '../../assets/icons/sound.svg'
import mute    from '../../assets/icons/mute.svg'
import steam   from '../../assets/icons/steam.svg'
import flagDe  from '../../assets/icons/flag-de.svg'
import flagEn  from '../../assets/icons/flag-en.svg'
import discord from '../../assets/icons/discord.svg'
import keystoneMilk4 from '../../assets/icons/keystone_milk4.svg'
import keystoneBake4 from '../../assets/icons/keystone_bake4.svg'
import keystoneMarket4 from '../../assets/icons/keystone_market4.svg'
import keystoneDispo4 from '../../assets/icons/keystone_dispo4.svg'
import keystoneAlleskoenner from '../../assets/icons/keystone_alleskoenner.svg'

const ICONS = {
  cookie, zucker, mehl, eier, butter, schoko, milch, haus, stand,
  ofen, shop, pokal, einw, lohn, lager, medal, krone, stern,
  upgrade, zentrieren, check, sanduhr, music, sound, mute, steam,
  'flag-de': flagDe, 'flag-en': flagEn, discord,
  keystoneMilk4, keystoneBake4, keystoneMarket4, keystoneDispo4, keystoneAlleskoenner,
}

// Echte Marken-Logos (unveraendertes Original-SVG, kein 8x8-Pixel-Raster) --
// bei denen soll image-rendering:pixelated NICHT greifen, sonst wird die
// Kurvenfuehrung beim Skalieren hart/klobig statt sauber.
const SMOOTH = new Set(['steam', 'flag-de', 'flag-en', 'discord'])

defineProps({
  name: { type: String, required: true }, // key into ICONS, e.g. 'zucker', 'cookie', 'einw'
  size: { type: Number, default: 16 },     // rendered box size in px
})
</script>

<style scoped>
.pixel-icon { flex-shrink: 0; image-rendering: pixelated; }
.pixel-icon-smooth { image-rendering: auto; }
</style>

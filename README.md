# 🧁 Open Idle Economy Game (Arbeitstitel)

Ein **Open-Source Idle- & Economy-Spiel**, entwickelt mit **HTML, CSS und JavaScript**.  
Der Fokus liegt auf **Ressourcenproduktion**, **Progressionssystemen**, **Online-Handel** und einer **modularen Architektur**, die sich leicht erweitern lässt.

Das Spiel soll:
- 🌐 im **Browser** laufen
- 🖥️ als **Desktop-App (z. B. über Steam)** gestartet werden

---

## 🎮 Spielkonzept

Dieses Projekt ist ein **systemorientiertes Idle Game**, inspiriert von klassischen Cookie-Clicker-Mechaniken, aber erweitert um Online- und Langzeit-Progression:

- Ressourcen generieren
- Produktionsketten & Rezepte
- Markt (Kaufen / Verkaufen)
- Achievements
- Ascension (permanente Fortschritte)
- Seasons mit Resets & Badges
- Events mit veränderten Regeln
- Skins & kosmetische Inhalte

👉 **Kein Echtzeit-Gameplay**, Fokus liegt auf:
- Systemdesign
- Skalierbarkeit
- Wirtschaft
- Erweiterbarkeit

---

## 🧩 Design & UI

- UI vollständig in **HTML / CSS / JavaScript**
- Design bewusst **nicht festgelegt**
- Theme, Layout und Stil sind austauschbar
- Ziel: klare, funktionale UI für komplexe Systeme

---

## 🏗️ Architektur (Übersicht)

Frontend (HTML / CSS / JS) ---> REST / WebSocket

Backend API (Idle, Economy, Seasons) <--- Auth / Inventar / Progress
Desktop Wrapper (optional)
(Electron / Tauri)

### Frontend
- Darstellung & UI
- Client-State
- API-Kommunikation
- Keine vertrauenswürdige Spiellogik

### Backend
- Idle-Berechnungen
- Wirtschaft & Markt
- Anti-Cheat
- Seasons & Events
- Persistente Speicherung

---

## 🎮 Desktop & Steam (optional)

Das Spiel soll als Desktop-App ausgeliefert werden:

- Desktop Wrapper (z. B. Electron oder Tauri)
- Steam Login (SteamID)
- Achievements
- Playtime Tracking
- Steam Inventory (Skins)
- Steam Market (handelbare Items)
- Steam Cloud

⚠️ **Steam API Keys, AppIDs und Backend-Code sind NICHT Teil dieses Repos**

---

## 🤝 Contributing
Beiträge sind sehr willkommen ❤️

Möglichkeiten:

UI / UX

Game-Design

Wirtschaft & Balancing

Dokumentation

Bugfixes

Feature-Ideen

Server

Workflow
Fork erstellen

Feature-Branch anlegen

Pull Request öffnen

---
## 📬 Kontakt & Diskussion
Ideen, Vorschläge oder Feedback?
👉 Bitte nutze Issues oder Discussions auf GitHub.

Viel Spaß beim Entwickeln 🚀

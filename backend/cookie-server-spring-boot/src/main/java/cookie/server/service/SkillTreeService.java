package cookie.server.service;

import cookie.server.config.GameBalanceConfig;
import cookie.server.dto.SkillEdgeDto;
import cookie.server.dto.SkillEffectDto;
import cookie.server.dto.SkillNodeStatusDto;
import cookie.server.dto.SkillTreeDto;
import cookie.server.entity.PlayerSkillNodeEntity;
import cookie.server.entity.SkillEdgeEntity;
import cookie.server.entity.SkillNodeEffectEntity;
import cookie.server.entity.SkillNodeEntity;
import cookie.server.entity.UserEntity;
import cookie.server.enums.EffectType;
import cookie.server.enums.NodeTier;
import cookie.server.repository.PlayerSkillNodeRepository;
import cookie.server.repository.SkillEdgeRepository;
import cookie.server.repository.SkillNodeRepository;
import cookie.server.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SkillTreeService {

    public static final String ROOT_ID = "root";

    private final SkillNodeRepository skillNodeRepository;
    private final SkillEdgeRepository skillEdgeRepository;
    private final PlayerSkillNodeRepository playerSkillNodeRepository;
    private final UserRepository userRepository;
    private final GameBalanceConfig balance;

    // Alle Knoten im Speicher gecacht -- wird bei jedem Effekt-Lookup (Ernte/Backen/Markt)
    // gebraucht und soll nicht bei jedem Tick ein findAll() gegen die DB ausloesen.
    // Aktualisiert bei Seed und bei jedem Admin-Edit (siehe refreshCache()).
    private final Map<String, SkillNodeEntity> nodeCache = new ConcurrentHashMap<>();

    // Kurzschreibweise fuer den Knotenaufbau in buildNodes() -- ein Knoten kann mehrere
    // davon haben (Keystones: Vorteil + Nachteil als zweiter Effekt mit negativem value).
    private record Effect(EffectType type, String targetResource, double value) {}

    public SkillTreeService(SkillNodeRepository skillNodeRepository,
                            SkillEdgeRepository skillEdgeRepository,
                            PlayerSkillNodeRepository playerSkillNodeRepository,
                            UserRepository userRepository,
                            GameBalanceConfig balance) {
        this.skillNodeRepository = skillNodeRepository;
        this.skillEdgeRepository = skillEdgeRepository;
        this.playerSkillNodeRepository = playerSkillNodeRepository;
        this.userRepository = userRepository;
        this.balance = balance;
    }

    // Upsert statt "nur wenn komplett leer": erlaubt, buildNodes()/buildEdges() spaeter um
    // neue Knoten/Zweige zu erweitern, ohne dass sie auf einer bereits befuellten DB (Dev/Live-
    // Beta) fuer immer fehlen wuerden. Bestehende, bereits allozierte Knoten bleiben unangetastet
    // -- es werden nur fehlende IDs nachgezogen. Aendert sich Name/Effekt-Text eines bestehenden
    // Knotens (wie bei diesem Umbau), zieht das NICHT automatisch nach -- dafuer einmalig
    // skill_nodes/skill_node_effects leeren (DB ist disposable, siehe CLAUDE.md/ROADMAP).
    @PostConstruct
    public void seedTree() {
        Set<String> existingNodeIds = skillNodeRepository.findAll().stream()
                .map(SkillNodeEntity::getId).collect(Collectors.toSet());
        List<SkillNodeEntity> missingNodes = buildNodes().stream()
                .filter(n -> !existingNodeIds.contains(n.getId())).toList();
        if (!missingNodes.isEmpty()) skillNodeRepository.saveAll(missingNodes);

        Set<String> existingEdgeIds = skillEdgeRepository.findAll().stream()
                .map(SkillEdgeEntity::getId).collect(Collectors.toSet());
        List<SkillEdgeEntity> missingEdges = buildEdges().stream()
                .filter(e -> !existingEdgeIds.contains(e.getId())).toList();
        if (!missingEdges.isEmpty()) skillEdgeRepository.saveAll(missingEdges);

        refreshCache();
    }

    public void refreshCache() {
        nodeCache.clear();
        skillNodeRepository.findAll().forEach(n -> nodeCache.put(n.getId(), n));
    }

    // Radiales 11-Branch-Layout (2026-08-10, zweite Fassung nach dem Lager-Branch-Pass): alle
    // Branches auf einem Kreis um root, gleichmaessig alle 360/11 = 32.7 Grad (Kompass-Bearing,
    // 0=Nord=-y, 90=Ost=+x). x/y ist reiner Anzeigewert ohne Spiellogik-Bezug (Allokation laeuft
    // ausschliesslich ueber Kanten) -- ALLE bestehenden Koordinaten wurden hier ein zweites Mal
    // neu verteilt (von 36 auf 32.7 Grad Abstand), weil 10 Branches bereits jeden 36-Grad-Slot
    // belegten und fuer den neuen STORAGE-Branch (Lager-Branch-Plan) kein Platz mehr war. Radial
    // umverteilen statt STORAGE irgendwo reinzuquetschen ist die robuste Loesung -- jede neue
    // Branch-Anzahl braucht ab jetzt denselben Schritt: alle Bearings neu auf 360/n verteilen,
    // NICHT versuchen, in bestehende Luecken zu pressen (das hat beim Cross-Branch-Wheel schon
    // zu einer Kollision mit DISPO gefuehrt, siehe `docs/cookie-game-design.md` §9 History).
    // Reihenfolge um den Kreis: MILK(0) SUGAR(32.7) DISPO(65.5) FLOUR(98.2) BAKING(130.9)
    // MARKET(163.6) EGGS(196.4) BUTTER(229.1) CORE(261.8) CHOCOLATE(294.5) STORAGE(327.3).
    // Radien pro Tier: 150/300/450/600 (Keystone-Tier), Notable meist auf Tier 3 (450).
    // Fork-Knoten (_5) liegen bei Radius 300 leicht abgewinkelt vom Hauptarm -- Winkel je nach
    // Nachbar-Branch gespiegelt, damit sich zwei benachbarte Forks nicht in derselben Luecke
    // treffen (siehe einzelne Kommentare unten). Vor jeder Aenderung hier: Kollisions-/
    // Kreuzungs-Skript gegen alle Knoten/Kanten laufen lassen (Python, Node-Boxen als Kreise mit
    // Radius 28/34/40 je Tier, Kantensegmente auf Schnitt pruefen) -- Verfahren beschrieben in
    // docs/plans/2026-08-10-open-skillbaum-lager-branch.md.
    // PoE-Mesh-Ausbau (2026-08-12): die 5 Rohstoff-Branches (SUGAR/FLOUR/EGGS/BUTTER/CHOCOLATE)
    // reichen jetzt bis Radius 750 (2. Keystone-Ring) statt 600 -- ab Radius 300 (`<res>_2`)
    // faechert der Branch in einen Ertrags-Pfad (`_y1/_y2/_y3`, Bearing+8 Grad) und einen
    // Lohn-Pfad (`_w1/_w2/_w3`, Bearing-8 Grad) auf, je mit eigenem Keystone-Ende, plus 2
    // Cross-Link-Kanten zwischen den Pfaden (`_y1`-`_w1`, `_y2`-`_w2`) fuer echtes PoE-Mesh-Gefuehl
    // statt reiner Baumstruktur. Kein NOTABLE-Tier mehr in diesen Branches (nur PASSIVE/KEYSTONE).
    // +-8 Grad Fanning wurde gegen alle Nachbar-Bearings durchgerechnet (auch EGGS<->BUTTER, die
    // einzigen zwei direkt benachbarten Rohstoff-Branches) -- bleibt an jeder Stelle klar
    // kollisionsfrei. `WORLD_SIZE` in `SkillTreeView.vue` dafuer von 1500 auf 1800 erhoeht.
    private List<SkillNodeEntity> buildNodes() {
        return List.of(
            node(ROOT_ID, "Ursprung", "Origin", "Startpunkt des Skill-Baums", "Starting point of the skill tree",
                    "CORE", NodeTier.PASSIVE, 0, 0, true, List.of()),

            // Branch MILK -- Bearing 0, resourcen-spezifisch
            node("milk_1", "Bessere Melkkannen", "Better Milk Pails", "+5% Milch pro Ernte-Tick", "+5% milk per harvest tick",
                    "MILK", NodeTier.PASSIVE, 0, -150, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "MILK", 0.05))),
            node("milk_2", "Sanftere Hand", "Gentler Hand", "+5% Milch pro Ernte-Tick", "+5% milk per harvest tick",
                    "MILK", NodeTier.PASSIVE, 0, -300, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "MILK", 0.05))),
            node("milk_3", "Weidewissen", "Pasture Knowledge", "+7% Milch pro Ernte-Tick", "+7% milk per harvest tick",
                    "MILK", NodeTier.PASSIVE, 0, -450, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "MILK", 0.07))),
            node("milk_4", "Meister-Melker", "Master Milker", "+10% Milch pro Ernte-Tick", "+10% milk per harvest tick",
                    "MILK", NodeTier.KEYSTONE, 0, -600, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "MILK", 0.10))),
            node("milk_5", "Zweite Kanne", "Second Pail", "+7% Milch pro Ernte-Tick", "+7% milk per harvest tick",
                    "MILK", NodeTier.PASSIVE, 73, -291, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "MILK", 0.07))),

            // Branch SUGAR -- Bearing 32.7. Gebaeude: Zuckerteich (pond). PoE-Mesh (2026-08-12):
            // ab sugar_2 Fork in Ertrag-Pfad (_y, Bearing+8) und Lohn-Pfad (_w, Bearing-8), je
            // eigener Keystone-Abschluss, 2 Cross-Link-Kanten zwischen den Pfaden (siehe buildEdges).
            node("sugar_1", "Feinkörniger Zucker", "Fine Grain Sugar", "+4% Zucker pro Ernte-Tick", "+4% sugar per harvest tick",
                    "SUGAR", NodeTier.PASSIVE, 81, -126, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "SUGAR", 0.04))),
            node("sugar_2", "Faire Bezahlung", "Fair Pay", "-1% Lohn im Zuckerteich", "-1% wage at the sugar pond",
                    "SUGAR", NodeTier.PASSIVE, 162, -252, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "SUGAR", 0.01))),
            node("sugar_y1", "Zuckerrohr-Anbau", "Sugarcane Cultivation", "+5% Zucker pro Ernte-Tick", "+5% sugar per harvest tick",
                    "SUGAR", NodeTier.PASSIVE, 293, -341, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "SUGAR", 0.05))),
            node("sugar_y2", "Raffinerie-Technik", "Refinery Technique", "+7% Zucker pro Ernte-Tick", "+7% sugar per harvest tick",
                    "SUGAR", NodeTier.PASSIVE, 391, -455, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "SUGAR", 0.07))),
            node("sugar_y3", "Zucker-Baron", "Sugar Baron",
                    "+20% Zucker pro Ernte-Tick, aber Arbeiter im Zuckerteich kosten 5% mehr Lohn",
                    "+20% sugar per harvest tick, but workers at the sugar pond cost 5% more wage",
                    "SUGAR", NodeTier.KEYSTONE, 489, -569, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "SUGAR", 0.20),
                            new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "SUGAR", -0.05))),
            node("sugar_w1", "Saisonarbeiter-Vertrag", "Seasonal Worker Contract", "-1.5% Lohn im Zuckerteich", "-1.5% wage at the sugar pond",
                    "SUGAR", NodeTier.PASSIVE, 188, -409, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "SUGAR", 0.015))),
            node("sugar_w2", "Effiziente Schichtplanung", "Efficient Shift Planning", "-2% Lohn im Zuckerteich", "-2% wage at the sugar pond",
                    "SUGAR", NodeTier.PASSIVE, 251, -545, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "SUGAR", 0.02))),
            node("sugar_w3", "Lohndrücker", "Wage Cutter",
                    "-12% Lohn im Zuckerteich, aber die Zucker-Ernte fällt 5% kleiner aus",
                    "-12% wage at the sugar pond, but the sugar harvest drops by 5%",
                    "SUGAR", NodeTier.KEYSTONE, 313, -681, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "SUGAR", 0.12),
                            new Effect(EffectType.HARVEST_YIELD, "SUGAR", -0.05))),

            // Branch DISPO -- Bearing 65.5, senkt den Zinssatz auf negative Cookies (siehe
            // WageService#deductWageForUser, balance.debtInterestRate). Kein Fork.
            node("dispo_1", "Guter Draht zur Bank", "Good Bank Connections", "-1% Dispo-Zinsen", "-1% overdraft interest",
                    "DISPO", NodeTier.PASSIVE, 136, -62, false,
                    List.of(new Effect(EffectType.WAGE_INTEREST_REDUCTION, null, 0.01))),
            node("dispo_2", "Bonitätsprüfung bestanden", "Passed Credit Check", "-1% Dispo-Zinsen", "-1% overdraft interest",
                    "DISPO", NodeTier.PASSIVE, 273, -125, false,
                    List.of(new Effect(EffectType.WAGE_INTEREST_REDUCTION, null, 0.01))),
            node("dispo_3", "Verhandelter Rahmen", "Negotiated Credit Line", "-1.5% Dispo-Zinsen", "-1.5% overdraft interest",
                    "DISPO", NodeTier.PASSIVE, 409, -187, false,
                    List.of(new Effect(EffectType.WAGE_INTEREST_REDUCTION, null, 0.015))),
            node("dispo_4", "Goldener Kredit", "Golden Credit", "-2% Dispo-Zinsen", "-2% overdraft interest",
                    "DISPO", NodeTier.KEYSTONE, 546, -249, false,
                    List.of(new Effect(EffectType.WAGE_INTEREST_REDUCTION, null, 0.02))),

            // Branch FLOUR -- Bearing 98.2. Gebaeude: Bauernhof (hof). PoE-Mesh (2026-08-12):
            // ab flour_2 Fork in Ertrag-Pfad (_y, Bearing+8) und Lohn-Pfad (_w, Bearing-8), je
            // eigener Keystone-Abschluss, 2 Cross-Link-Kanten zwischen den Pfaden (siehe buildEdges).
            node("flour_1", "Gutes Saatgut", "Good Seed Stock", "+4% Mehl pro Ernte-Tick", "+4% flour per harvest tick",
                    "FLOUR", NodeTier.PASSIVE, 148, 21, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "FLOUR", 0.04))),
            node("flour_2", "Anständiger Lohn", "Decent Wage", "-1% Lohn auf dem Bauernhof", "-1% wage at the farm",
                    "FLOUR", NodeTier.PASSIVE, 297, 43, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "FLOUR", 0.01))),
            node("flour_y1", "Fruchtbare Böden", "Fertile Soil", "+5% Mehl pro Ernte-Tick", "+5% flour per harvest tick",
                    "FLOUR", NodeTier.PASSIVE, 432, 126, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "FLOUR", 0.05))),
            node("flour_y2", "Mühlenmeisterschaft", "Milling Mastery", "+7% Mehl pro Ernte-Tick", "+7% flour per harvest tick",
                    "FLOUR", NodeTier.PASSIVE, 576, 168, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "FLOUR", 0.07))),
            node("flour_y3", "Mühlen-Baron", "Mill Baron",
                    "+20% Mehl pro Ernte-Tick, aber Arbeiter auf dem Bauernhof kosten 5% mehr Lohn",
                    "+20% flour per harvest tick, but workers at the farm cost 5% more wage",
                    "FLOUR", NodeTier.KEYSTONE, 720, 211, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "FLOUR", 0.20),
                            new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "FLOUR", -0.05))),
            node("flour_w1", "Erntehelfer-Vertrag", "Harvest Hand Contract", "-1.5% Lohn auf dem Bauernhof", "-1.5% wage at the farm",
                    "FLOUR", NodeTier.PASSIVE, 450, 2, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "FLOUR", 0.015))),
            node("flour_w2", "Optimierte Feldarbeit", "Optimized Field Work", "-2% Lohn auf dem Bauernhof", "-2% wage at the farm",
                    "FLOUR", NodeTier.PASSIVE, 600, 2, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "FLOUR", 0.02))),
            node("flour_w3", "Sparsamer Verwalter", "Frugal Steward",
                    "-12% Lohn auf dem Bauernhof, aber die Mehl-Ernte fällt 5% kleiner aus",
                    "-12% wage at the farm, but the flour harvest drops by 5%",
                    "FLOUR", NodeTier.KEYSTONE, 750, 3, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "FLOUR", 0.12),
                            new Effect(EffectType.HARVEST_YIELD, "FLOUR", -0.05))),

            // Branch BAKING -- Bearing 130.9, global
            node("bake_1", "Warmer Ofen", "Warm Oven", "+2% Cookie-Ausbeute beim Backen", "+2% cookie yield when baking",
                    "BAKING", NodeTier.PASSIVE, 113, 98, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.02))),
            node("bake_2", "Gleichmäßige Hitze", "Even Heat", "+2% Cookie-Ausbeute beim Backen", "+2% cookie yield when baking",
                    "BAKING", NodeTier.PASSIVE, 227, 196, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.02))),
            node("bake_3", "Süßes Händchen", "Sweet Touch", "+3% Cookie-Ausbeute beim Backen", "+3% cookie yield when baking",
                    "BAKING", NodeTier.PASSIVE, 340, 295, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.03))),
            node("bake_4", "Meisterbäcker", "Master Baker", "+5% Cookie-Ausbeute beim Backen", "+5% cookie yield when baking",
                    "BAKING", NodeTier.KEYSTONE, 453, 393, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.05))),
            // Fork-Winkel -14 Grad (Richtung FLOUR-Seite, weg von der Bruecke/MARKET) -- vermeidet
            // Kollision mit bridge_bake_market.
            node("bake_5", "Geheimrezept", "Secret Recipe", "+4% Cookie-Ausbeute beim Backen", "+4% cookie yield when baking",
                    "BAKING", NodeTier.PASSIVE, 268, 136, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.04))),

            // Cross-Branch-Wheel: Bruecke zwischen BAKING und MARKET, verlangt Vorarbeit in
            // beiden Aesten (bake_3 UND market_3 alloziert, siehe requiresAllPrereqs unten und
            // isAdjacentToAllocated). NICHT MILK-BAKING (urspruenglicher Entwurf) -- deren
            // damalige gemeinsame Diagonale war von DISPO belegt. Position = Bearing-Mittelwert
            // zwischen BAKING(130.9) und MARKET(163.6) = 147.3, mit Rest-Puffer zu beiden.
            node("bridge_bake_market", "Kreuzung der Höfe", "Crossroads of the Farms",
                    "+3% Ernte-Ertrag (alle Ressourcen) -- verbindet BAKING und MARKET",
                    "+3% harvest yield (all resources) -- links BAKING and MARKET",
                    "CORE", NodeTier.PASSIVE, 151, 236, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, null, 0.03)), true),

            // Genereller Keystone: nur ueber die Bruecke erreichbar, kleiner globaler Bonus,
            // keine Nachteile (siehe Plan Abschnitt 5 -- bewusst der eine reine Positiv-Keystone
            // im Baum, kuenftige Branch-Keystones bekommen echte Tradeoffs).
            node("keystone_alleskoenner", "Alleskönner-Ader", "Jack-of-All-Trades Vein",
                    "+5% Ernte-Ertrag (alle Ressourcen)", "+5% harvest yield (all resources)",
                    "CORE", NodeTier.KEYSTONE, 216, 337, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, null, 0.05))),

            // Branch MARKET -- Bearing 163.6, global. Kein Fork.
            node("market_1", "Verhandlungsgeschick", "Negotiation Skill", "-0.5% Markt-Verkaufsgebühr", "-0.5% market sell fee",
                    "MARKET", NodeTier.PASSIVE, 42, 144, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.005))),
            node("market_2", "Guter Ruf", "Good Reputation", "-0.5% Markt-Verkaufsgebühr", "-0.5% market sell fee",
                    "MARKET", NodeTier.PASSIVE, 85, 288, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.005))),
            node("market_3", "Marktkenner", "Market Expert", "-0.75% Markt-Verkaufsgebühr", "-0.75% market sell fee",
                    "MARKET", NodeTier.PASSIVE, 127, 432, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.0075))),
            node("market_4", "Händlerlizenz", "Trader's License", "-1% Markt-Verkaufsgebühr", "-1% market sell fee",
                    "MARKET", NodeTier.KEYSTONE, 169, 576, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.01))),

            // Branch EGGS -- Bearing 196.4. Gebaeude: Hühnerhof (huhn). PoE-Mesh (2026-08-12):
            // ab eggs_2 Fork in Ertrag-Pfad (_y, Bearing+8) und Lohn-Pfad (_w, Bearing-8), je
            // eigener Keystone-Abschluss, 2 Cross-Link-Kanten zwischen den Pfaden (siehe buildEdges).
            node("eggs_1", "Fleißige Hennen", "Diligent Hens", "+4% Eier pro Ernte-Tick", "+4% eggs per harvest tick",
                    "EGGS", NodeTier.PASSIVE, -42, 144, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "EGGS", 0.04))),
            node("eggs_2", "Gerechter Lohn", "Just Wage", "-1% Lohn im Hühnerhof", "-1% wage at the henhouse",
                    "EGGS", NodeTier.PASSIVE, -85, 288, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "EGGS", 0.01))),
            node("eggs_y1", "Ausgewähltes Zuchtgeflügel", "Selective Poultry Breeding", "+5% Eier pro Ernte-Tick", "+5% eggs per harvest tick",
                    "EGGS", NodeTier.PASSIVE, -186, 410, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "EGGS", 0.05))),
            node("eggs_y2", "Hühnerhof-Expertise", "Henhouse Expertise", "+7% Eier pro Ernte-Tick", "+7% eggs per harvest tick",
                    "EGGS", NodeTier.PASSIVE, -248, 546, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "EGGS", 0.07))),
            node("eggs_y3", "Hühner-Baron", "Poultry Baron",
                    "+20% Eier pro Ernte-Tick, aber Arbeiter im Hühnerhof kosten 5% mehr Lohn",
                    "+20% eggs per harvest tick, but workers at the henhouse cost 5% more wage",
                    "EGGS", NodeTier.KEYSTONE, -310, 683, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "EGGS", 0.20),
                            new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "EGGS", -0.05))),
            node("eggs_w1", "Zeitverträge", "Temporary Contracts", "-1.5% Lohn im Hühnerhof", "-1.5% wage at the henhouse",
                    "EGGS", NodeTier.PASSIVE, -66, 445, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "EGGS", 0.015))),
            node("eggs_w2", "Automatisierte Fütterung", "Automated Feeding", "-2% Lohn im Hühnerhof", "-2% wage at the henhouse",
                    "EGGS", NodeTier.PASSIVE, -88, 594, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "EGGS", 0.02))),
            node("eggs_w3", "Kostendrücker", "Cost Cutter",
                    "-12% Lohn im Hühnerhof, aber die Eier-Ernte fällt 5% kleiner aus",
                    "-12% wage at the henhouse, but the egg harvest drops by 5%",
                    "EGGS", NodeTier.KEYSTONE, -110, 742, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "EGGS", 0.12),
                            new Effect(EffectType.HARVEST_YIELD, "EGGS", -0.05))),

            // Branch BUTTER -- Bearing 229.1. Gebaeude: Butterei (butter). Reihenfolge bewusst
            // gespiegelt (Lohn zuerst, Ertrag zweitens) -- Punkt aus dem Plan: nicht jeder Branch
            // soll mit "mehr Ertrag" starten, sonst wirken alle 5 Rohstoff-Zweige identisch.
            // PoE-Mesh (2026-08-12): ab butter_2 Fork in Ertrag-Pfad (_y, Bearing+8) und Lohn-Pfad
            // (_w, Bearing-8), je eigener Keystone-Abschluss, 2 Cross-Link-Kanten (siehe buildEdges).
            node("butter_1", "Sparsame Buchhaltung", "Frugal Bookkeeping", "-1% Lohn in der Butterei", "-1% wage at the creamery",
                    "BUTTER", NodeTier.PASSIVE, -113, 98, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "BUTTER", 0.01))),
            node("butter_2", "Reichhaltige Sahne", "Rich Cream", "+4% Butter pro Ernte-Tick", "+4% butter per harvest tick",
                    "BUTTER", NodeTier.PASSIVE, -227, 196, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "BUTTER", 0.04))),
            node("butter_y1", "Butterei-Expertise", "Creamery Expertise", "+5% Butter pro Ernte-Tick", "+5% butter per harvest tick",
                    "BUTTER", NodeTier.PASSIVE, -378, 244, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "BUTTER", 0.05))),
            node("butter_y2", "Verfeinerte Rezeptur", "Refined Recipe", "+7% Butter pro Ernte-Tick", "+7% butter per harvest tick",
                    "BUTTER", NodeTier.PASSIVE, -504, 326, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "BUTTER", 0.07))),
            node("butter_y3", "Butter-Baron", "Butter Baron",
                    "+20% Butter pro Ernte-Tick, aber Arbeiter in der Butterei kosten 5% mehr Lohn",
                    "+20% butter per harvest tick, but workers at the creamery cost 5% more wage",
                    "BUTTER", NodeTier.KEYSTONE, -630, 407, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "BUTTER", 0.20),
                            new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "BUTTER", -0.05))),
            node("butter_w1", "Genossenschaftsvertrag", "Cooperative Contract", "-1.5% Lohn in der Butterei", "-1.5% wage at the creamery",
                    "BUTTER", NodeTier.PASSIVE, -296, 339, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "BUTTER", 0.015))),
            node("butter_w2", "Schlanke Betriebsführung", "Lean Operations", "-2% Lohn in der Butterei", "-2% wage at the creamery",
                    "BUTTER", NodeTier.PASSIVE, -394, 452, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "BUTTER", 0.02))),
            node("butter_w3", "Lohndrücker der Butterei", "Creamery Wage Cutter",
                    "-12% Lohn in der Butterei, aber die Butter-Ernte fällt 5% kleiner aus",
                    "-12% wage at the creamery, but the butter harvest drops by 5%",
                    "BUTTER", NodeTier.KEYSTONE, -493, 565, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "BUTTER", 0.12),
                            new Effect(EffectType.HARVEST_YIELD, "BUTTER", -0.05))),

            // Branch CORE -- Bearing 261.8, generalistisch. Konvergierender Fork unveraendert
            // (core_2/core_3 laufen auf core_4 zusammen, testet Mehrfach-Eltern-Konnektivitaet).
            node("core_1", "Fleißige Hände", "Diligent Hands", "+4% Ernte-Ertrag (alle Ressourcen)", "+4% harvest yield (all resources)",
                    "CORE", NodeTier.PASSIVE, -148, 21, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, null, 0.04))),
            node("core_2", "Ausdauer", "Stamina", "+1.5% Cookie-Ausbeute beim Backen", "+1.5% cookie yield when baking",
                    "CORE", NodeTier.PASSIVE, -281, 144, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.015))),
            node("core_3", "Sparsamkeit", "Frugality", "-0.5% Markt-Verkaufsgebühr", "-0.5% market sell fee",
                    "CORE", NodeTier.PASSIVE, -310, -59, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.005))),
            node("core_4", "Alleskönner", "Jack of All Trades", "+6% Ernte-Ertrag (alle Ressourcen)", "+6% harvest yield (all resources)",
                    "CORE", NodeTier.PASSIVE, -445, 64, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, null, 0.06))),

            // Branch CHOCOLATE -- Bearing 294.5. Gebaeude: Plantage (kakao). PoE-Mesh (2026-08-12):
            // ab chocolate_2 Fork in Ertrag-Pfad (_y, Bearing+8) und Lohn-Pfad (_w, Bearing-8), je
            // eigener Keystone-Abschluss, 2 Cross-Link-Kanten zwischen den Pfaden (siehe buildEdges).
            node("chocolate_1", "Edelkakao", "Fine Cocoa", "+4% Schokolade pro Ernte-Tick", "+4% chocolate per harvest tick",
                    "CHOCOLATE", NodeTier.PASSIVE, -136, -62, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "CHOCOLATE", 0.04))),
            node("chocolate_2", "Faire Erntelöhne", "Fair Harvest Wages", "-1% Lohn auf der Plantage", "-1% wage at the plantation",
                    "CHOCOLATE", NodeTier.PASSIVE, -273, -125, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "CHOCOLATE", 0.01))),
            node("chocolate_y1", "Selektierte Kakaobohnen", "Selected Cocoa Beans", "+5% Schokolade pro Ernte-Tick", "+5% chocolate per harvest tick",
                    "CHOCOLATE", NodeTier.PASSIVE, -380, -242, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "CHOCOLATE", 0.05))),
            node("chocolate_y2", "Confiseur-Expertise", "Confectioner Expertise", "+7% Schokolade pro Ernte-Tick", "+7% chocolate per harvest tick",
                    "CHOCOLATE", NodeTier.PASSIVE, -506, -322, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "CHOCOLATE", 0.07))),
            node("chocolate_y3", "Schoko-Baron", "Chocolate Baron",
                    "+20% Schokolade pro Ernte-Tick, aber Arbeiter auf der Plantage kosten 5% mehr Lohn",
                    "+20% chocolate per harvest tick, but workers at the plantation cost 5% more wage",
                    "CHOCOLATE", NodeTier.KEYSTONE, -633, -403, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "CHOCOLATE", 0.20),
                            new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "CHOCOLATE", -0.05))),
            node("chocolate_w1", "Saisonpflücker-Vertrag", "Seasonal Picker Contract", "-1.5% Lohn auf der Plantage", "-1.5% wage at the plantation",
                    "CHOCOLATE", NodeTier.PASSIVE, -432, -128, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "CHOCOLATE", 0.015))),
            node("chocolate_w2", "Effiziente Plantagenführung", "Efficient Plantation Management", "-2% Lohn auf der Plantage", "-2% wage at the plantation",
                    "CHOCOLATE", NodeTier.PASSIVE, -575, -170, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "CHOCOLATE", 0.02))),
            node("chocolate_w3", "Plantagen-Sparfuchs", "Plantation Penny-Pincher",
                    "-12% Lohn auf der Plantage, aber die Schokoladen-Ernte fällt 5% kleiner aus",
                    "-12% wage at the plantation, but the chocolate harvest drops by 5%",
                    "CHOCOLATE", NodeTier.KEYSTONE, -719, -213, false,
                    List.of(new Effect(EffectType.RESOURCE_WAGE_REDUCTION, "CHOCOLATE", 0.12),
                            new Effect(EffectType.HARVEST_YIELD, "CHOCOLATE", -0.05))),

            // Branch STORAGE -- Bearing 327.3, neu (Lager-Branch-Plan, 2026-08-10). Gebaeude:
            // Lager (pre-built, upgradeable Cap-Level). STORAGE_CAP_BONUS wirkt auf das
            // Hauptlager (gemeinsamer Topf ueber alle 6 Ressourcen), BUILDING_BUFFER_BONUS auf
            // den Pro-Gebaeude-Zwischenspeicher (der eigentliche Engpass bei Abwesenheit).
            node("storage_1", "Ordentliche Regale", "Tidy Shelves", "+5% Hauptlager-Kapazität", "+5% main storage capacity",
                    "STORAGE", NodeTier.PASSIVE, -81, -126, false,
                    List.of(new Effect(EffectType.STORAGE_CAP_BONUS, null, 0.05))),
            node("storage_2", "Isolierte Fässer", "Insulated Barrels", "+10% Gebäude-Zwischenspeicher", "+10% building buffer",
                    "STORAGE", NodeTier.PASSIVE, -162, -252, false,
                    List.of(new Effect(EffectType.BUILDING_BUFFER_BONUS, null, 0.10))),
            node("storage_3", "Erweiterter Anbau", "Extended Wing", "+8% Hauptlager-Kapazität", "+8% main storage capacity",
                    "STORAGE", NodeTier.PASSIVE, -243, -379, false,
                    List.of(new Effect(EffectType.STORAGE_CAP_BONUS, null, 0.08))),
            node("storage_4", "Übervolle Scheune", "Overflowing Barn",
                    "Gebäude sammeln deutlich länger ungestört weiter, aber das Hauptlager selbst schrumpft",
                    "Buildings keep collecting undisturbed for much longer, but the main storage itself shrinks",
                    "STORAGE", NodeTier.KEYSTONE, -324, -505, false,
                    List.of(new Effect(EffectType.BUILDING_BUFFER_BONUS, null, 0.25),
                            new Effect(EffectType.STORAGE_CAP_BONUS, null, -0.10))),
            node("storage_5", "Doppelter Boden", "Double Floor", "+10% Hauptlager-Kapazität", "+10% main storage capacity",
                    "STORAGE", NodeTier.PASSIVE, -96, -284, false,
                    List.of(new Effect(EffectType.STORAGE_CAP_BONUS, null, 0.10)))
        );
    }

    private List<SkillEdgeEntity> buildEdges() {
        return List.of(
            edge(ROOT_ID, "milk_1"), edge("milk_1", "milk_2"), edge("milk_2", "milk_3"),
            edge("milk_3", "milk_4"), edge("milk_2", "milk_5"),

            edge(ROOT_ID, "sugar_1"), edge("sugar_1", "sugar_2"),
            edge("sugar_2", "sugar_y1"), edge("sugar_y1", "sugar_y2"), edge("sugar_y2", "sugar_y3"),
            edge("sugar_2", "sugar_w1"), edge("sugar_w1", "sugar_w2"), edge("sugar_w2", "sugar_w3"),
            edge("sugar_y1", "sugar_w1"), edge("sugar_y2", "sugar_w2"),

            edge(ROOT_ID, "dispo_1"), edge("dispo_1", "dispo_2"), edge("dispo_2", "dispo_3"),
            edge("dispo_3", "dispo_4"),

            edge(ROOT_ID, "flour_1"), edge("flour_1", "flour_2"),
            edge("flour_2", "flour_y1"), edge("flour_y1", "flour_y2"), edge("flour_y2", "flour_y3"),
            edge("flour_2", "flour_w1"), edge("flour_w1", "flour_w2"), edge("flour_w2", "flour_w3"),
            edge("flour_y1", "flour_w1"), edge("flour_y2", "flour_w2"),

            edge(ROOT_ID, "bake_1"), edge("bake_1", "bake_2"), edge("bake_2", "bake_3"),
            edge("bake_3", "bake_4"), edge("bake_2", "bake_5"),

            // Bruecke: beide Praereq-Kanten zeigen auf bridge_bake_market (toNode), so erkennt
            // requiresAllPrereqs, welche Kanten "Voraussetzung" statt "Folgeknoten" sind.
            edge("bake_3", "bridge_bake_market"), edge("market_3", "bridge_bake_market"),
            edge("bridge_bake_market", "keystone_alleskoenner"),

            edge(ROOT_ID, "market_1"), edge("market_1", "market_2"), edge("market_2", "market_3"),
            edge("market_3", "market_4"),

            edge(ROOT_ID, "eggs_1"), edge("eggs_1", "eggs_2"),
            edge("eggs_2", "eggs_y1"), edge("eggs_y1", "eggs_y2"), edge("eggs_y2", "eggs_y3"),
            edge("eggs_2", "eggs_w1"), edge("eggs_w1", "eggs_w2"), edge("eggs_w2", "eggs_w3"),
            edge("eggs_y1", "eggs_w1"), edge("eggs_y2", "eggs_w2"),

            edge(ROOT_ID, "butter_1"), edge("butter_1", "butter_2"),
            edge("butter_2", "butter_y1"), edge("butter_y1", "butter_y2"), edge("butter_y2", "butter_y3"),
            edge("butter_2", "butter_w1"), edge("butter_w1", "butter_w2"), edge("butter_w2", "butter_w3"),
            edge("butter_y1", "butter_w1"), edge("butter_y2", "butter_w2"),

            edge(ROOT_ID, "core_1"), edge("core_1", "core_2"), edge("core_1", "core_3"),
            edge("core_2", "core_4"), edge("core_3", "core_4"),

            edge(ROOT_ID, "chocolate_1"), edge("chocolate_1", "chocolate_2"),
            edge("chocolate_2", "chocolate_y1"), edge("chocolate_y1", "chocolate_y2"), edge("chocolate_y2", "chocolate_y3"),
            edge("chocolate_2", "chocolate_w1"), edge("chocolate_w1", "chocolate_w2"), edge("chocolate_w2", "chocolate_w3"),
            edge("chocolate_y1", "chocolate_w1"), edge("chocolate_y2", "chocolate_w2"),

            edge(ROOT_ID, "storage_1"), edge("storage_1", "storage_2"), edge("storage_2", "storage_3"),
            edge("storage_3", "storage_4"), edge("storage_2", "storage_5")
        );
    }

    private SkillNodeEntity node(String id, String nameDe, String nameEn, String descDe, String descEn,
                                  String branch, NodeTier tier, int x, int y, boolean isRoot,
                                  List<Effect> effects) {
        return node(id, nameDe, nameEn, descDe, descEn, branch, tier, x, y, isRoot, effects, false);
    }

    private SkillNodeEntity node(String id, String nameDe, String nameEn, String descDe, String descEn,
                                  String branch, NodeTier tier, int x, int y, boolean isRoot,
                                  List<Effect> effects, boolean requiresAllPrereqs) {
        SkillNodeEntity n = new SkillNodeEntity();
        n.setId(id);
        n.setNameDe(nameDe);
        n.setNameEn(nameEn);
        n.setDescriptionDe(descDe);
        n.setDescriptionEn(descEn);
        n.setBranch(branch);
        n.setNodeTier(tier);
        n.setX(x);
        n.setY(y);
        n.setRoot(isRoot);
        n.setRequiresAllPrereqs(requiresAllPrereqs);
        List<SkillNodeEffectEntity> effectEntities = new ArrayList<>();
        for (Effect e : effects) {
            SkillNodeEffectEntity ee = new SkillNodeEffectEntity();
            ee.setEffectType(e.type().name());
            ee.setTargetResource(e.targetResource());
            ee.setEffectValue(e.value());
            effectEntities.add(ee);
        }
        n.setEffects(effectEntities);
        return n;
    }

    private SkillEdgeEntity edge(String from, String to) {
        SkillEdgeEntity e = new SkillEdgeEntity();
        e.setId(from + "-" + to);
        e.setFromNode(from);
        e.setToNode(to);
        return e;
    }

    // ── Effekt-Resolver ────────────────────────────────────────────────

    public double getEffectTotal(String userId, EffectType type, String targetResource) {
        Set<String> allocated = allocatedNodeIds(userId);
        return nodeCache.values().stream()
                .filter(n -> allocated.contains(n.getId()))
                .flatMap(n -> n.getEffects().stream())
                .filter(e -> e.getEffectType().equals(type.name()))
                .filter(e -> e.getTargetResource() == null || e.getTargetResource().equalsIgnoreCase(targetResource))
                .mapToDouble(SkillNodeEffectEntity::getEffectValue)
                .sum();
    }

    private Set<String> allocatedNodeIds(String userId) {
        Set<String> ids = playerSkillNodeRepository.findByUserId(userId).stream()
                .map(PlayerSkillNodeEntity::getNodeId)
                .collect(Collectors.toCollection(HashSet::new));
        ids.add(ROOT_ID);
        return ids;
    }

    // ── Kauf / Allokation ────────────────────────────────────────────

    public double nextPointCost(int totalSkillPointsBought) {
        return Math.round(balance.getSkillPointBaseCost()
                * Math.pow(balance.getSkillPointCostGrowth(), totalSkillPointsBought) * 100.0) / 100.0;
    }

    @Transactional
    public SkillTreeDto buySkillPoint(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        double cost = nextPointCost(user.getTotalSkillPointsBought());
        if (user.getCookies() < cost) {
            throw new IllegalArgumentException("Nicht genug Cookies. Brauche " + cost + ", habe " + user.getCookies());
        }

        user.setCookies(user.getCookies() - cost);
        user.setSkillPoints(user.getSkillPoints() + 1);
        user.setTotalSkillPointsBought(user.getTotalSkillPointsBought() + 1);
        user.setTotalSkillPointCookiesSpent(user.getTotalSkillPointCookiesSpent() + cost);
        userRepository.save(user);

        return getTreeStatus(userId);
    }

    @Transactional
    public SkillTreeDto allocateNode(String userId, String nodeId) {
        SkillNodeEntity nodeEntity = nodeCache.get(nodeId);
        if (nodeEntity == null) throw new NoSuchElementException("Skill node not found: " + nodeId);
        if (nodeEntity.isRoot()) throw new IllegalArgumentException("Root ist immer aktiv.");

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        Set<String> allocated = allocatedNodeIds(userId);
        if (allocated.contains(nodeId)) {
            throw new IllegalStateException("Knoten bereits freigeschaltet.");
        }
        if (user.getSkillPoints() < 1) {
            throw new IllegalArgumentException("Kein Skill-Punkt übrig.");
        }
        if (!isAdjacentToAllocated(nodeId, allocated)) {
            throw new IllegalStateException("Knoten ist nicht mit dem freigeschalteten Baum verbunden.");
        }

        String pkId = userId + "#" + nodeId;
        PlayerSkillNodeEntity pn = new PlayerSkillNodeEntity();
        pn.setId(pkId);
        pn.setUserId(userId);
        pn.setNodeId(nodeId);
        playerSkillNodeRepository.save(pn);

        user.setSkillPoints(user.getSkillPoints() - 1);
        userRepository.save(user);

        return getTreeStatus(userId);
    }

    // Normale Knoten: EIN alloziierter Nachbar reicht (OR, klassisches PoE-Pathing). Knoten mit
    // requiresAllPrereqs=true (bisher nur bridge_milk_bake) verlangen dagegen, dass ALLE Kanten,
    // die auf sie zeigen (toNode == nodeId, die "Voraussetzungs"-Richtung aus buildEdges()),
    // bereits alloziert sind (AND) -- das erzwingt Vorarbeit in mehreren Branches gleichzeitig.
    private boolean isAdjacentToAllocated(String nodeId, Set<String> allocated) {
        List<SkillEdgeEntity> touching = skillEdgeRepository.findByFromNodeOrToNode(nodeId, nodeId);
        SkillNodeEntity node = nodeCache.get(nodeId);
        if (node != null && node.isRequiresAllPrereqs()) {
            List<SkillEdgeEntity> prereqEdges = touching.stream()
                    .filter(e -> e.getToNode().equals(nodeId)).toList();
            return !prereqEdges.isEmpty() && prereqEdges.stream().allMatch(e -> allocated.contains(e.getFromNode()));
        }
        return touching.stream().anyMatch(e ->
                allocated.contains(e.getFromNode().equals(nodeId) ? e.getToNode() : e.getFromNode()));
    }

    // ── Respec (Knoten gegen Cookies zurückgeben) ───────────────────────
    // User-Entscheidung (siehe docs/plans/2026-08-10-open-skillbaum-respec.md): immer derselbe
    // Flat-Preis pro entferntem Knoten, kein Wachstum wie bei der Skill-Punkt-Kaufkurve.

    @Transactional
    public SkillTreeDto deallocateNode(String userId, String nodeId) {
        SkillNodeEntity nodeEntity = nodeCache.get(nodeId);
        if (nodeEntity == null) throw new NoSuchElementException("Skill node not found: " + nodeId);
        if (nodeEntity.isRoot()) throw new IllegalArgumentException("Root kann nicht entfernt werden.");

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        Set<String> allocated = allocatedNodeIds(userId);
        if (!allocated.contains(nodeId)) {
            throw new IllegalStateException("Knoten ist nicht freigeschaltet.");
        }
        if (user.getCookies() < balance.getRespecCostFlat()) {
            throw new IllegalArgumentException("Nicht genug Cookies für Respec.");
        }

        Set<String> remaining = new HashSet<>(allocated);
        remaining.remove(nodeId);

        List<SkillEdgeEntity> allEdges = skillEdgeRepository.findAll();
        Set<String> reachable = reachableFromRoot(remaining, allEdges);
        for (String other : allocated) {
            if (other.equals(nodeId) || other.equals(ROOT_ID)) continue;
            if (!reachable.contains(other)) {
                throw new IllegalStateException(
                        "Knoten kann nicht entfernt werden -- " + other + " würde vom Baum abgeschnitten.");
            }
            // Reine Erreichbarkeit reicht bei requiresAllPrereqs-Knoten nicht (Undirected-BFS
            // findet sie ggf. noch ueber den jeweils ANDEREN Pflicht-Nachbarn) -- deren
            // AND-Bedingung muss zusaetzlich separat weiterhin erfuellt sein, sonst koennte ein
            // Respec z.B. bridge_bake_market mit nur noch einem der zwei Pflicht-Vorgaenger
            // alloziert stehen lassen.
            SkillNodeEntity otherEntity = nodeCache.get(other);
            if (otherEntity != null && otherEntity.isRequiresAllPrereqs()) {
                boolean allPrereqsStillAllocated = allEdges.stream()
                        .filter(e -> e.getToNode().equals(other))
                        .allMatch(e -> remaining.contains(e.getFromNode()));
                if (!allPrereqsStillAllocated) {
                    throw new IllegalStateException(
                            "Knoten kann nicht entfernt werden -- " + other + " würde eine Voraussetzung verlieren.");
                }
            }
        }

        user.setCookies(user.getCookies() - balance.getRespecCostFlat());
        user.setSkillPoints(user.getSkillPoints() + 1);
        userRepository.save(user);
        playerSkillNodeRepository.deleteById(userId + "#" + nodeId);

        return getTreeStatus(userId);
    }

    // BFS ab root, nur ueber Kanten die zu Knoten in allocatedSet (oder root selbst) fuehren --
    // eigene Methode statt isAdjacentToAllocated()-Wiederverwendung, weil die nur direkte
    // Nachbarschaft prueft (reicht fuer Allokation), Respec aber echte transitive
    // Erreichbarkeit ueber den ganzen verbleibenden Baum braucht (sonst uebersieht man z.B.
    // Ketten wie root-A-B-C, bei denen B entfernt A zwar noch direkt an root haengt, C aber
    // trotzdem abgeschnitten waere).
    private Set<String> reachableFromRoot(Set<String> allocatedSet, List<SkillEdgeEntity> allEdges) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(ROOT_ID);
        visited.add(ROOT_ID);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (SkillEdgeEntity e : allEdges) {
                if (!e.getFromNode().equals(current) && !e.getToNode().equals(current)) continue;
                String neighbor = e.getFromNode().equals(current) ? e.getToNode() : e.getFromNode();
                if (visited.contains(neighbor)) continue;
                if (!neighbor.equals(ROOT_ID) && !allocatedSet.contains(neighbor)) continue;
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }
        return visited;
    }

    // Anti-Cheat-/Daten-Integritaets-Reparatur (siehe docs/ROADMAP.md, "Anti-Cheat-Re-
    // Verifikation fuer Skill-Allokationen"): allocateNode()/deallocateNode() halten die
    // Konnektivitaet fuer sich genommen immer ein, aber der Admin-Editor kann Edges/Nodes
    // loeschen, OHNE dass das gegen bereits alloziierte Spieler-Zeilen re-validiert wird --
    // ein Spieler kann dadurch nachtraeglich vom Baum abgeschnitten dastehen (2026-08-21 live
    // beobachtet). Wird nach jeder topologie-verkleinernden Admin-Aktion (Edge/Node loeschen)
    // automatisch aufgerufen, siehe AdminConfigController. Kein Spielerfehler -> Skillpunkt
    // wird erstattet statt der Knoten kommentarlos zu verschwinden.
    @Transactional
    public int repairDisconnectedAllocations() {
        List<SkillEdgeEntity> allEdges = skillEdgeRepository.findAll();
        Map<String, List<PlayerSkillNodeEntity>> byUser = playerSkillNodeRepository.findAll().stream()
                .collect(Collectors.groupingBy(PlayerSkillNodeEntity::getUserId));

        int repaired = 0;
        for (Map.Entry<String, List<PlayerSkillNodeEntity>> entry : byUser.entrySet()) {
            List<PlayerSkillNodeEntity> rows = entry.getValue();
            Set<String> allocated = rows.stream().map(PlayerSkillNodeEntity::getNodeId).collect(Collectors.toSet());
            Set<String> reachable = reachableFromRoot(allocated, allEdges);
            List<PlayerSkillNodeEntity> orphaned = rows.stream()
                    .filter(pn -> !reachable.contains(pn.getNodeId())).toList();
            if (orphaned.isEmpty()) continue;

            UserEntity user = userRepository.findById(entry.getKey()).orElse(null);
            if (user == null) continue;
            playerSkillNodeRepository.deleteAll(orphaned);
            user.setSkillPoints(user.getSkillPoints() + orphaned.size());
            userRepository.save(user);
            repaired += orphaned.size();
        }
        return repaired;
    }

    // ── Status-DTO ───────────────────────────────────────────────────

    public SkillTreeDto getTreeStatus(String userId) {
        Set<String> allocated = allocatedNodeIds(userId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        List<SkillNodeStatusDto> nodes = nodeCache.values().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(n -> {
                    SkillNodeStatusDto dto = new SkillNodeStatusDto();
                    dto.setId(n.getId());
                    dto.setNameDe(n.getNameDe());
                    dto.setNameEn(n.getNameEn());
                    dto.setDescriptionDe(n.getDescriptionDe());
                    dto.setDescriptionEn(n.getDescriptionEn());
                    dto.setBranch(n.getBranch());
                    dto.setNodeTier(n.getNodeTier() != null ? n.getNodeTier().name() : NodeTier.PASSIVE.name());
                    dto.setIcon(n.getIcon());
                    dto.setEffects(n.getEffects().stream()
                            .map(e -> new SkillEffectDto(e.getEffectType(), e.getTargetResource(), e.getEffectValue()))
                            .collect(Collectors.toList()));
                    dto.setX(n.getX());
                    dto.setY(n.getY());
                    dto.setRoot(n.isRoot());
                    boolean isAllocated = allocated.contains(n.getId());
                    dto.setAllocated(isAllocated);
                    dto.setAllocatable(!isAllocated && !n.isRoot() && isAdjacentToAllocated(n.getId(), allocated));
                    return dto;
                }).collect(Collectors.toList());

        List<SkillEdgeDto> edges = skillEdgeRepository.findAll().stream()
                .map(e -> new SkillEdgeDto(e.getFromNode(), e.getToNode()))
                .collect(Collectors.toList());

        SkillTreeDto dto = new SkillTreeDto();
        dto.setNodes(nodes);
        dto.setEdges(edges);
        dto.setSkillPoints(user.getSkillPoints());
        dto.setTotalSkillPointsBought(user.getTotalSkillPointsBought());
        dto.setTotalSkillPointCookiesSpent(user.getTotalSkillPointCookiesSpent());
        dto.setNextPointCost(nextPointCost(user.getTotalSkillPointsBought()));
        dto.setRespecCostFlat(balance.getRespecCostFlat());
        return dto;
    }
}

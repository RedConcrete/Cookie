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

import java.util.ArrayList;
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

    private List<SkillNodeEntity> buildNodes() {
        return List.of(
            node(ROOT_ID, "Ursprung", "Origin", "Startpunkt des Skill-Baums", "Starting point of the skill tree",
                    "CORE", NodeTier.PASSIVE, 0, 0, true, List.of()),

            // Branch MILK (Norden, resourcen-spezifisch)
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
                    "MILK", NodeTier.PASSIVE, 150, -300, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, "MILK", 0.07))),

            // Branch BAKING (Osten, global)
            node("bake_1", "Warmer Ofen", "Warm Oven", "+2% Cookie-Ausbeute beim Backen", "+2% cookie yield when baking",
                    "BAKING", NodeTier.PASSIVE, 150, 0, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.02))),
            node("bake_2", "Gleichmäßige Hitze", "Even Heat", "+2% Cookie-Ausbeute beim Backen", "+2% cookie yield when baking",
                    "BAKING", NodeTier.PASSIVE, 300, 0, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.02))),
            node("bake_3", "Süßes Händchen", "Sweet Touch", "+3% Cookie-Ausbeute beim Backen", "+3% cookie yield when baking",
                    "BAKING", NodeTier.PASSIVE, 450, 0, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.03))),
            node("bake_4", "Meisterbäcker", "Master Baker", "+5% Cookie-Ausbeute beim Backen", "+5% cookie yield when baking",
                    "BAKING", NodeTier.KEYSTONE, 600, 0, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.05))),
            node("bake_5", "Geheimrezept", "Secret Recipe", "+4% Cookie-Ausbeute beim Backen", "+4% cookie yield when baking",
                    "BAKING", NodeTier.PASSIVE, 300, 150, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.04))),

            // Branch MARKET (Süden, global)
            node("market_1", "Verhandlungsgeschick", "Negotiation Skill", "-0.5% Markt-Verkaufsgebühr", "-0.5% market sell fee",
                    "MARKET", NodeTier.PASSIVE, 0, 150, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.005))),
            node("market_2", "Guter Ruf", "Good Reputation", "-0.5% Markt-Verkaufsgebühr", "-0.5% market sell fee",
                    "MARKET", NodeTier.PASSIVE, 0, 300, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.005))),
            node("market_3", "Marktkenner", "Market Expert", "-0.75% Markt-Verkaufsgebühr", "-0.75% market sell fee",
                    "MARKET", NodeTier.PASSIVE, 0, 450, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.0075))),
            node("market_4", "Händlerlizenz", "Trader's License", "-1% Markt-Verkaufsgebühr", "-1% market sell fee",
                    "MARKET", NodeTier.KEYSTONE, 0, 600, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.01))),

            // Branch CORE (Westen, generalistisch)
            node("core_1", "Fleißige Hände", "Diligent Hands", "+4% Ernte-Ertrag (alle Ressourcen)", "+4% harvest yield (all resources)",
                    "CORE", NodeTier.PASSIVE, -150, 0, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, null, 0.04))),
            node("core_2", "Ausdauer", "Stamina", "+1.5% Cookie-Ausbeute beim Backen", "+1.5% cookie yield when baking",
                    "CORE", NodeTier.PASSIVE, -300, -100, false,
                    List.of(new Effect(EffectType.BAKE_OUTPUT, null, 0.015))),
            node("core_3", "Sparsamkeit", "Frugality", "-0.5% Markt-Verkaufsgebühr", "-0.5% market sell fee",
                    "CORE", NodeTier.PASSIVE, -300, 100, false,
                    List.of(new Effect(EffectType.MARKET_FEE_REDUCTION, null, 0.005))),
            node("core_4", "Alleskönner", "Jack of All Trades", "+6% Ernte-Ertrag (alle Ressourcen)", "+6% harvest yield (all resources)",
                    "CORE", NodeTier.NOTABLE, -450, 0, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, null, 0.06))),

            // Branch DISPO (Nordosten) -- senkt den Zinssatz auf negative Cookies (siehe
            // WageService#deductWageForUser, balance.debtInterestRate).
            node("dispo_1", "Guter Draht zur Bank", "Good Bank Connections", "-1% Dispo-Zinsen", "-1% overdraft interest",
                    "DISPO", NodeTier.PASSIVE, 150, -150, false,
                    List.of(new Effect(EffectType.WAGE_INTEREST_REDUCTION, null, 0.01))),
            node("dispo_2", "Bonitätsprüfung bestanden", "Passed Credit Check", "-1% Dispo-Zinsen", "-1% overdraft interest",
                    "DISPO", NodeTier.PASSIVE, 300, -300, false,
                    List.of(new Effect(EffectType.WAGE_INTEREST_REDUCTION, null, 0.01))),
            node("dispo_3", "Verhandelter Rahmen", "Negotiated Credit Line", "-1.5% Dispo-Zinsen", "-1.5% overdraft interest",
                    "DISPO", NodeTier.PASSIVE, 450, -450, false,
                    List.of(new Effect(EffectType.WAGE_INTEREST_REDUCTION, null, 0.015))),
            node("dispo_4", "Goldener Kredit", "Golden Credit", "-2% Dispo-Zinsen", "-2% overdraft interest",
                    "DISPO", NodeTier.KEYSTONE, 600, -600, false,
                    List.of(new Effect(EffectType.WAGE_INTEREST_REDUCTION, null, 0.02))),

            // Cross-Branch-Wheel: Bruecke zwischen BAKING (Osten) und MARKET (Sueden), verlangt
            // Vorarbeit in beiden Aesten (bake_3 UND market_3 alloziert, siehe requiresAllPrereqs
            // unten und isAdjacentToAllocated). NICHT MILK-BAKING (urspruenglicher Entwurf) --
            // deren gemeinsame NO-Diagonale ist bereits von DISPO belegt (dispo_1..4 laufen exakt
            // y=-x), jede direkte Verbindung zwischen den beiden Aesten haette zwangslaeufig
            // DISPO-Kanten gekreuzt. Der SO-Quadrant zwischen BAKING/MARKET ist dagegen komplett
            // frei -- Position als Notable (staerkerer Effekt + groessere Optik, passend zur
            // Kantenlaenge von zwei vollen Schritten statt der ueblichen ~150/212).
            node("bridge_bake_market", "Kreuzung der Höfe", "Crossroads of the Farms",
                    "+3% Ernte-Ertrag (alle Ressourcen) -- verbindet BAKING und MARKET",
                    "+3% harvest yield (all resources) -- links BAKING and MARKET",
                    "CORE", NodeTier.NOTABLE, 280, 280, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, null, 0.03)), true),

            // Genereller Keystone: nur ueber die Bruecke erreichbar, kleiner globaler Bonus,
            // keine Nachteile (siehe Plan Abschnitt 5 -- bewusst der eine reine Positiv-Keystone
            // im Baum, kuenftige Branch-Keystones bekommen echte Tradeoffs).
            node("keystone_alleskoenner", "Alleskönner-Ader", "Jack-of-All-Trades Vein",
                    "+5% Ernte-Ertrag (alle Ressourcen)", "+5% harvest yield (all resources)",
                    "CORE", NodeTier.KEYSTONE, 380, 380, false,
                    List.of(new Effect(EffectType.HARVEST_YIELD, null, 0.05)))
        );
    }

    private List<SkillEdgeEntity> buildEdges() {
        return List.of(
            edge(ROOT_ID, "milk_1"), edge("milk_1", "milk_2"), edge("milk_2", "milk_3"),
            edge("milk_3", "milk_4"), edge("milk_2", "milk_5"),

            edge(ROOT_ID, "bake_1"), edge("bake_1", "bake_2"), edge("bake_2", "bake_3"),
            edge("bake_3", "bake_4"), edge("bake_2", "bake_5"),

            edge(ROOT_ID, "market_1"), edge("market_1", "market_2"), edge("market_2", "market_3"),
            edge("market_3", "market_4"),

            edge(ROOT_ID, "core_1"), edge("core_1", "core_2"), edge("core_1", "core_3"),
            edge("core_2", "core_4"), edge("core_3", "core_4"),

            edge(ROOT_ID, "dispo_1"), edge("dispo_1", "dispo_2"), edge("dispo_2", "dispo_3"),
            edge("dispo_3", "dispo_4"),

            // Bruecke: beide Praereq-Kanten zeigen auf bridge_bake_market (toNode), so erkennt
            // requiresAllPrereqs, welche Kanten "Voraussetzung" statt "Folgeknoten" sind.
            edge("bake_3", "bridge_bake_market"), edge("market_3", "bridge_bake_market"),
            edge("bridge_bake_market", "keystone_alleskoenner")
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
        return dto;
    }
}

package cookie.server.service;

import cookie.server.config.GameBalanceConfig;
import cookie.server.dto.SkillEdgeDto;
import cookie.server.dto.SkillNodeStatusDto;
import cookie.server.dto.SkillTreeDto;
import cookie.server.entity.PlayerSkillNodeEntity;
import cookie.server.entity.SkillEdgeEntity;
import cookie.server.entity.SkillNodeEntity;
import cookie.server.entity.UserEntity;
import cookie.server.enums.EffectType;
import cookie.server.repository.PlayerSkillNodeRepository;
import cookie.server.repository.SkillEdgeRepository;
import cookie.server.repository.SkillNodeRepository;
import cookie.server.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @PostConstruct
    public void seedTree() {
        if (skillNodeRepository.count() == 0) {
            skillNodeRepository.saveAll(buildNodes());
            skillEdgeRepository.saveAll(buildEdges());
        }
        refreshCache();
    }

    public void refreshCache() {
        nodeCache.clear();
        skillNodeRepository.findAll().forEach(n -> nodeCache.put(n.getId(), n));
    }

    private List<SkillNodeEntity> buildNodes() {
        return List.of(
            node(ROOT_ID, "Ursprung", "Startpunkt des Skill-Baums", "CORE", null, null, 0, 0, 0, true),

            // Branch MILK (Norden, resourcen-spezifisch)
            node("milk_1", "Bessere Melkkannen", "+5% Milch pro Ernte-Tick", "MILK", EffectType.HARVEST_YIELD, "MILK", 0.05, 0, -150, false),
            node("milk_2", "Sanftere Hand", "+5% Milch pro Ernte-Tick", "MILK", EffectType.HARVEST_YIELD, "MILK", 0.05, 0, -300, false),
            node("milk_3", "Weidewissen", "+7% Milch pro Ernte-Tick", "MILK", EffectType.HARVEST_YIELD, "MILK", 0.07, 0, -450, false),
            node("milk_4", "Meister-Melker", "+10% Milch pro Ernte-Tick (Keystone)", "MILK", EffectType.HARVEST_YIELD, "MILK", 0.10, 0, -600, false),
            node("milk_5", "Zweite Kanne", "+7% Milch pro Ernte-Tick", "MILK", EffectType.HARVEST_YIELD, "MILK", 0.07, 150, -300, false),

            // Branch BAKING (Osten, global)
            node("bake_1", "Warmer Ofen", "+2% Cookie-Ausbeute beim Backen", "BAKING", EffectType.BAKE_OUTPUT, null, 0.02, 150, 0, false),
            node("bake_2", "Gleichmäßige Hitze", "+2% Cookie-Ausbeute beim Backen", "BAKING", EffectType.BAKE_OUTPUT, null, 0.02, 300, 0, false),
            node("bake_3", "Süßes Händchen", "+3% Cookie-Ausbeute beim Backen", "BAKING", EffectType.BAKE_OUTPUT, null, 0.03, 450, 0, false),
            node("bake_4", "Meisterbäcker", "+5% Cookie-Ausbeute beim Backen (Keystone)", "BAKING", EffectType.BAKE_OUTPUT, null, 0.05, 600, 0, false),
            node("bake_5", "Geheimrezept", "+4% Cookie-Ausbeute beim Backen", "BAKING", EffectType.BAKE_OUTPUT, null, 0.04, 300, 150, false),

            // Branch MARKET (Süden, global)
            node("market_1", "Verhandlungsgeschick", "-0.5% Markt-Verkaufsgebühr", "MARKET", EffectType.MARKET_FEE_REDUCTION, null, 0.005, 0, 150, false),
            node("market_2", "Guter Ruf", "-0.5% Markt-Verkaufsgebühr", "MARKET", EffectType.MARKET_FEE_REDUCTION, null, 0.005, 0, 300, false),
            node("market_3", "Marktkenner", "-0.75% Markt-Verkaufsgebühr", "MARKET", EffectType.MARKET_FEE_REDUCTION, null, 0.0075, 0, 450, false),
            node("market_4", "Händlerlizenz", "-1% Markt-Verkaufsgebühr (Keystone)", "MARKET", EffectType.MARKET_FEE_REDUCTION, null, 0.01, 0, 600, false),

            // Branch CORE (Westen, generalistisch)
            node("core_1", "Fleißige Hände", "+4% Ernte-Ertrag (alle Ressourcen)", "CORE", EffectType.HARVEST_YIELD, null, 0.04, -150, 0, false),
            node("core_2", "Ausdauer", "+1.5% Cookie-Ausbeute beim Backen", "CORE", EffectType.BAKE_OUTPUT, null, 0.015, -300, -100, false),
            node("core_3", "Sparsamkeit", "-0.5% Markt-Verkaufsgebühr", "CORE", EffectType.MARKET_FEE_REDUCTION, null, 0.005, -300, 100, false),
            node("core_4", "Alleskönner", "+6% Ernte-Ertrag (alle Ressourcen)", "CORE", EffectType.HARVEST_YIELD, null, 0.06, -450, 0, false)
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
            edge("core_2", "core_4"), edge("core_3", "core_4")
        );
    }

    private SkillNodeEntity node(String id, String name, String desc, String branch,
                                  EffectType effectType, String targetResource, double effectValue,
                                  int x, int y, boolean isRoot) {
        SkillNodeEntity n = new SkillNodeEntity();
        n.setId(id);
        n.setName(name);
        n.setDescription(desc);
        n.setBranch(branch);
        n.setEffectType(effectType);
        n.setTargetResource(targetResource);
        n.setEffectValue(effectValue);
        n.setX(x);
        n.setY(y);
        n.setRoot(isRoot);
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
                .filter(n -> allocated.contains(n.getId()) && n.getEffectType() == type)
                .filter(n -> n.getTargetResource() == null || n.getTargetResource().equalsIgnoreCase(targetResource))
                .mapToDouble(SkillNodeEntity::getEffectValue)
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

    private boolean isAdjacentToAllocated(String nodeId, Set<String> allocated) {
        List<SkillEdgeEntity> touching = skillEdgeRepository.findByFromNodeOrToNode(nodeId, nodeId);
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
                    dto.setName(n.getName());
                    dto.setDescription(n.getDescription());
                    dto.setBranch(n.getBranch());
                    dto.setEffectType(n.getEffectType() != null ? n.getEffectType().name() : null);
                    dto.setTargetResource(n.getTargetResource());
                    dto.setEffectValue(n.getEffectValue());
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

package cookie.server.controller;

import cookie.server.config.AppConfig;
import cookie.server.config.GameBalanceConfig;
import cookie.server.config.MarketConfig;
import cookie.server.entity.RecipeEntity;
import cookie.server.entity.SkillNodeEntity;
import cookie.server.repository.RecipeRepository;
import cookie.server.repository.SkillNodeRepository;
import cookie.server.service.SkillTreeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Live editierbare Balance-Config fürs Admin-Panel. Aendert die laufenden
 * Spring-Beans direkt (MarketConfig/GameBalanceConfig sind Singletons, auf die
 * alle Services schon verweisen) -- kein Neustart noetig, Aenderung wirkt sofort
 * im naechsten Tick/Request. Skill-Knoten und Rezepte sind DB-Zeilen und damit
 * ohnehin schon "live", hier nur ein Edit-Endpunkt dafuer.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminConfigController {

    private final AppConfig appConfig;
    private final MarketConfig marketConfig;
    private final GameBalanceConfig balanceConfig;
    private final SkillNodeRepository skillNodeRepository;
    private final SkillTreeService skillTreeService;
    private final RecipeRepository recipeRepository;

    public AdminConfigController(AppConfig appConfig, MarketConfig marketConfig,
                                  GameBalanceConfig balanceConfig,
                                  SkillNodeRepository skillNodeRepository,
                                  SkillTreeService skillTreeService,
                                  RecipeRepository recipeRepository) {
        this.appConfig = appConfig;
        this.marketConfig = marketConfig;
        this.balanceConfig = balanceConfig;
        this.skillNodeRepository = skillNodeRepository;
        this.skillTreeService = skillTreeService;
        this.recipeRepository = recipeRepository;
    }

    private boolean badToken(String token) {
        return !appConfig.getAdminToken().equals(token);
    }

    // ── Markt- & Balance-Config ──────────────────────────────────────

    @GetMapping("/config")
    public ResponseEntity<?> getConfig(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        return ResponseEntity.ok(Map.of("market", marketConfig, "balance", balanceConfig));
    }

    @PutMapping("/config/market")
    public ResponseEntity<?> updateMarketConfig(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody MarketConfig update) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");

        marketConfig.setUpdateIntervalMs(update.getUpdateIntervalMs());
        marketConfig.setMinPrice(update.getMinPrice());
        marketConfig.setStockFluctuationRatio(update.getStockFluctuationRatio());
        marketConfig.setSellFeeRate(update.getSellFeeRate());
        marketConfig.setActivePlayerWindowDays(update.getActivePlayerWindowDays());
        marketConfig.setStockPerActivePlayer(update.getStockPerActivePlayer());
        marketConfig.setInitialSugarPrice(update.getInitialSugarPrice());
        marketConfig.setInitialFlourPrice(update.getInitialFlourPrice());
        marketConfig.setInitialEggsPrice(update.getInitialEggsPrice());
        marketConfig.setInitialButterPrice(update.getInitialButterPrice());
        marketConfig.setInitialChocolatePrice(update.getInitialChocolatePrice());
        marketConfig.setInitialMilkPrice(update.getInitialMilkPrice());
        marketConfig.setInitialSugarStock(update.getInitialSugarStock());
        marketConfig.setInitialFlourStock(update.getInitialFlourStock());
        marketConfig.setInitialEggsStock(update.getInitialEggsStock());
        marketConfig.setInitialButterStock(update.getInitialButterStock());
        marketConfig.setInitialChocolateStock(update.getInitialChocolateStock());
        marketConfig.setInitialMilkStock(update.getInitialMilkStock());

        return ResponseEntity.ok(marketConfig);
    }

    @PutMapping("/config/balance")
    public ResponseEntity<?> updateBalanceConfig(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody GameBalanceConfig update) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");

        balanceConfig.setBaseStorageCap(update.getBaseStorageCap());
        balanceConfig.setStoragePerLevel(update.getStoragePerLevel());
        balanceConfig.setCitizensPerRatLevel(update.getCitizensPerRatLevel());
        balanceConfig.setCitizenBaseCost(update.getCitizenBaseCost());
        balanceConfig.setCitizenCostGrowth(update.getCitizenCostGrowth());
        balanceConfig.setWorkersPerLevel(update.getWorkersPerLevel());
        balanceConfig.setBuildingCostGrowth(update.getBuildingCostGrowth());
        balanceConfig.setPrestigeBaseThreshold(update.getPrestigeBaseThreshold());
        balanceConfig.setPrestigeThresholdGrowth(update.getPrestigeThresholdGrowth());
        balanceConfig.setPrestigeMultiplierPerLevel(update.getPrestigeMultiplierPerLevel());
        balanceConfig.setSkillPointBaseCost(update.getSkillPointBaseCost());
        balanceConfig.setSkillPointCostGrowth(update.getSkillPointCostGrowth());

        return ResponseEntity.ok(balanceConfig);
    }

    // ── Skill Tree ───────────────────────────────────────────────────

    @GetMapping("/skilltree/nodes")
    public ResponseEntity<?> listSkillNodesAdmin(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        return ResponseEntity.ok(skillNodeRepository.findAll());
    }

    @PutMapping("/skilltree/nodes/{id}")
    public ResponseEntity<?> updateSkillNode(
            @PathVariable String id,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody SkillNodeEntity update) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        SkillNodeEntity existing = skillNodeRepository.findById(id).orElseThrow(
                () -> new java.util.NoSuchElementException("Skill node not found: " + id));

        existing.setName(update.getName());
        existing.setDescription(update.getDescription());
        existing.setBranch(update.getBranch());
        existing.setEffectType(update.getEffectType());
        existing.setTargetResource(update.getTargetResource());
        existing.setEffectValue(update.getEffectValue());
        existing.setX(update.getX());
        existing.setY(update.getY());
        SkillNodeEntity saved = skillNodeRepository.save(existing);
        skillTreeService.refreshCache();
        return ResponseEntity.ok(saved);
    }

    // ── Rezepte ──────────────────────────────────────────────────────

    @GetMapping("/recipes")
    public ResponseEntity<?> listRecipesAdmin(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        return ResponseEntity.ok(recipeRepository.findAll());
    }

    @PutMapping("/recipes/{id}")
    public ResponseEntity<?> updateRecipe(
            @PathVariable String id,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody RecipeEntity update) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        RecipeEntity existing = recipeRepository.findById(id).orElseThrow(
                () -> new java.util.NoSuchElementException("Recipe not found: " + id));

        existing.setSugar(update.getSugar());
        existing.setFlour(update.getFlour());
        existing.setEggs(update.getEggs());
        existing.setButter(update.getButter());
        existing.setChocolate(update.getChocolate());
        existing.setMilk(update.getMilk());
        existing.setOutput(update.getOutput());
        existing.setBakeDurationSeconds(update.getBakeDurationSeconds());
        return ResponseEntity.ok(recipeRepository.save(existing));
    }
}

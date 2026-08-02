package cookie.server.controller;

import cookie.server.config.AppConfig;
import cookie.server.config.GameBalanceConfig;
import cookie.server.config.MarketConfig;
import cookie.server.entity.RecipeEntity;
import cookie.server.entity.UpgradeEntity;
import cookie.server.repository.RecipeRepository;
import cookie.server.repository.UpgradeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Live editierbare Balance-Config fürs Admin-Panel. Aendert die laufenden
 * Spring-Beans direkt (MarketConfig/GameBalanceConfig sind Singletons, auf die
 * alle Services schon verweisen) -- kein Neustart noetig, Aenderung wirkt sofort
 * im naechsten Tick/Request. Upgrades und Rezepte sind DB-Zeilen und damit
 * ohnehin schon "live", hier nur ein Edit-Endpunkt dafuer.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminConfigController {

    private final AppConfig appConfig;
    private final MarketConfig marketConfig;
    private final GameBalanceConfig balanceConfig;
    private final UpgradeRepository upgradeRepository;
    private final RecipeRepository recipeRepository;

    public AdminConfigController(AppConfig appConfig, MarketConfig marketConfig,
                                  GameBalanceConfig balanceConfig,
                                  UpgradeRepository upgradeRepository,
                                  RecipeRepository recipeRepository) {
        this.appConfig = appConfig;
        this.marketConfig = marketConfig;
        this.balanceConfig = balanceConfig;
        this.upgradeRepository = upgradeRepository;
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
        balanceConfig.setPassiveTickSeconds(update.getPassiveTickSeconds());
        balanceConfig.setPrestigeBaseThreshold(update.getPrestigeBaseThreshold());
        balanceConfig.setPrestigeThresholdGrowth(update.getPrestigeThresholdGrowth());
        balanceConfig.setPrestigeMultiplierPerLevel(update.getPrestigeMultiplierPerLevel());

        return ResponseEntity.ok(balanceConfig);
    }

    // ── Upgrades ─────────────────────────────────────────────────────

    @GetMapping("/upgrades")
    public ResponseEntity<?> listUpgradesAdmin(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        return ResponseEntity.ok(upgradeRepository.findAll());
    }

    @PutMapping("/upgrades/{id}")
    public ResponseEntity<?> updateUpgrade(
            @PathVariable String id,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody UpgradeEntity update) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        UpgradeEntity existing = upgradeRepository.findById(id).orElseThrow(
                () -> new java.util.NoSuchElementException("Upgrade not found: " + id));

        existing.setBaseCost(update.getBaseCost());
        existing.setEffectPerLevel(update.getEffectPerLevel());
        existing.setMaxLevel(update.getMaxLevel());
        return ResponseEntity.ok(upgradeRepository.save(existing));
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

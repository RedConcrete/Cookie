package cookie.server.controller;

import cookie.server.config.AppConfig;
import cookie.server.config.GameBalanceConfig;
import cookie.server.config.MarketConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final AppConfig appConfig;
    private final MarketConfig marketConfig;
    private final GameBalanceConfig balanceConfig;

    public ConfigController(AppConfig appConfig, MarketConfig marketConfig, GameBalanceConfig balanceConfig) {
        this.appConfig = appConfig;
        this.marketConfig = marketConfig;
        this.balanceConfig = balanceConfig;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
            "devMode", appConfig.isDevMode(),
            "sellFeeRate", marketConfig.getSellFeeRate(),
            // Frontend spiegelt das, um den Collect-Button waehrend des Cooldowns clientseitig
            // auszublenden (siehe FarmGridView.vue) -- vermeidet sinnlose 400er-Flut bei
            // schnellem Klicken, der eigentliche Schutz bleibt der serverseitige Cooldown-Check
            // in PassiveIncomeService (Client-Wert nie vertrauen).
            "collectCooldownMs", balanceConfig.getCollectCooldownMs(),
            // Frontend spiegelt das, um Buy/Sell waehrend der Markt-Sperre clientseitig zu
            // deaktivieren (Pixel-Sanduhr, siehe MarketView.vue) -- der eigentliche Schutz
            // bleibt der serverseitige Check in MarketService#performAction.
            "marketTradeCooldownMs", marketConfig.getTradeCooldownMs(),
            // Frontend spiegelt das, um denselben Schwellenwert fuer den AFK-Kick zu
            // verwenden (siehe useIdleTimeout.js) -- der eigentliche Schutz (kein Lohn/Zins-Tick
            // mehr) bleibt serverseitig in WageScheduler#deductWages.
            "afkTimeoutMinutes", balanceConfig.getAfkTimeoutMinutes()
        ));
    }
}

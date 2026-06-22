package cookie.server.controller;

import cookie.server.dto.BuyUpgradeRequestDto;
import cookie.server.dto.UpgradeWithStatusDto;
import cookie.server.service.UpgradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/upgrades")
public class UpgradeController {

    private final UpgradeService upgradeService;

    public UpgradeController(UpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    @GetMapping
    public ResponseEntity<List<UpgradeWithStatusDto>> getUpgrades(@RequestParam String userId) {
        return ResponseEntity.ok(upgradeService.getUpgradesForPlayer(userId));
    }

    @PostMapping("/buy/{userId}")
    public ResponseEntity<List<UpgradeWithStatusDto>> buyUpgrade(
            @PathVariable String userId,
            @RequestBody BuyUpgradeRequestDto request) {
        return ResponseEntity.ok(upgradeService.buyUpgrade(userId, request.getUpgradeId()));
    }
}

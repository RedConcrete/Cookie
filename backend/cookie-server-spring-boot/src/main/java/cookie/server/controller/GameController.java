package cookie.server.controller;

import cookie.server.dto.*;
import cookie.server.service.BakeService;
import cookie.server.service.MarketService;
import cookie.server.service.PrestigeService;
import cookie.server.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/game")
public class GameController {

    private final UserService userService;
    private final MarketService marketService;
    private final BakeService bakeService;
    private final PrestigeService prestigeService;

    public GameController(UserService userService, MarketService marketService,
                          BakeService bakeService, PrestigeService prestigeService) {
        this.userService = userService;
        this.marketService = marketService;
        this.bakeService = bakeService;
        this.prestigeService = prestigeService;
    }

    @GetMapping("/init/{userId}")
    public ResponseEntity<UserMarketDataDto> initializeGame(@PathVariable String userId, @RequestParam(defaultValue = "20") int marketHistoryAmount) {
        UserInformationDto user = userService.getUser(userId);
        return ResponseEntity.ok(new UserMarketDataDto(
                user,
                marketService.getMarketData(marketHistoryAmount)
        ));
    }

    @PostMapping("/produce/{userId}")
    public ResponseEntity<UserInformationDto> produce(@PathVariable String userId, @RequestBody ProduceRequestDto request) {
        return ResponseEntity.ok(userService.produce(userId, request.getAmount()));
    }

    @PostMapping("/harvest/{userId}")
    public ResponseEntity<UserInformationDto> harvest(@PathVariable String userId, @RequestBody HarvestRequestDto request) {
        return ResponseEntity.ok(userService.harvest(userId, request.getResource()));
    }

    @PostMapping("/bake/start/{userId}")
    public ResponseEntity<BakeJobStatusDto> bakeStart(@PathVariable String userId, @RequestBody BakeStartRequestDto request) {
        return ResponseEntity.ok(bakeService.startBake(userId, request.getRecipeId(), request.getBatches()));
    }

    @GetMapping("/bake/status/{userId}")
    public ResponseEntity<BakeJobStatusDto> bakeStatus(@PathVariable String userId) {
        return ResponseEntity.ok(bakeService.getStatus(userId));
    }

    @PostMapping("/bake/claim/{userId}")
    public ResponseEntity<UserInformationDto> bakeClaim(@PathVariable String userId) {
        return ResponseEntity.ok(bakeService.claim(userId));
    }

    @GetMapping("/prestige/status/{userId}")
    public ResponseEntity<PrestigeStatusDto> prestigeStatus(@PathVariable String userId) {
        return ResponseEntity.ok(prestigeService.getStatus(userId));
    }

    @PostMapping("/prestige/{userId}")
    public ResponseEntity<PrestigeStatusDto> prestige(@PathVariable String userId) {
        return ResponseEntity.ok(prestigeService.prestige(userId));
    }
}

package cookie.server.controller;

import cookie.server.dto.UserInformationDto;
import cookie.server.dto.UserMarketDataDto;
import cookie.server.service.MarketService;
import cookie.server.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/game")
public class GameController {

    private final UserService userService;
    private final MarketService marketService;

    public GameController(UserService userService, MarketService marketService) {
        this.userService = userService;
        this.marketService = marketService;
    }

    @GetMapping("/init/{userId}")
    public ResponseEntity<UserMarketDataDto> initializeGame(@PathVariable String userId, @RequestParam(defaultValue = "20") int marketHistoryAmount) {
        UserInformationDto user = userService.getUser(userId);
        return ResponseEntity.ok(new UserMarketDataDto(
                user,
                marketService.getMarketData(marketHistoryAmount)
        ));
    }
}

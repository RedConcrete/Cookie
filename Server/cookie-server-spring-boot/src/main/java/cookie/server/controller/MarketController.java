package cookie.server.controller;

import cookie.server.dto.MarketDto;
import cookie.server.dto.MarketRequestDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.service.MarketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {
    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping
    public ResponseEntity<List<MarketDto>> getMarketInfo(
            @RequestParam("amountMarketObjects") int amount) {
        return ResponseEntity.ok(marketService.getMarketData(amount));
    }

    @PostMapping
    public ResponseEntity<UserInformationDto> processMarketAction(
            @RequestBody MarketRequestDto request) {
        return ResponseEntity.ok(marketService.performAction(request));
    }
}

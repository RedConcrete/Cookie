package cookie.server.controller;

import cookie.server.config.SteamAuthInterceptor;
import cookie.server.dto.MarketDto;
import cookie.server.dto.MarketRequestDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.exception.AuthException;
import cookie.server.service.MarketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    @GetMapping("/get/{amount}")
    public ResponseEntity<List<MarketDto>> getMarketInfo(@PathVariable int amount) {
        return ResponseEntity.ok(marketService.getMarketData(amount));
    }

    @GetMapping("/all")
    public ResponseEntity<List<MarketDto>> getMarketInfo() {
        return ResponseEntity.ok(marketService.getAllMarketData());
    }

    @GetMapping("/history")
    public ResponseEntity<List<MarketDto>> getFullHistory() {
        return ResponseEntity.ok(marketService.getFullHistory());
    }

    // Einziger Endpunkt mit Identitaet im Body statt Pfad/Query -- SteamAuthInterceptor
    // kann das generisch nicht pruefen (siehe dort), deshalb hier explizit anhand des vom
    // Interceptor gesetzten Request-Attributs. Attribut ist null im Dev-Mode (Interceptor
    // gibt dort vorher true zurueck) -- Check wird dann uebersprungen, heutiges Verhalten
    // bleibt unveraendert.
    @PostMapping
    public ResponseEntity<UserInformationDto> processMarketAction(
            @Valid @RequestBody MarketRequestDto request,
            HttpServletRequest httpRequest) {
        Object verifiedSteamId = httpRequest.getAttribute(SteamAuthInterceptor.VERIFIED_STEAM_ID_ATTR);
        if (verifiedSteamId != null && !verifiedSteamId.equals(request.getUserId())) {
            throw new AuthException("Session gehört nicht zu diesem Spieler.");
        }
        return ResponseEntity.ok(marketService.performAction(request));
    }
}

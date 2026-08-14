package cookie.server.service;

import cookie.server.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Verifiziert Steam-Identitaeten server-seitig -- zwei unabhaengige Verfahren:
 *  - Session-Ticket (Electron-Client via steamworks.js) gegen ISteamUserAuth/AuthenticateUserTicket.
 *  - OpenID 2.0 (Browser-Web-Login) gegen steamcommunity.com/openid/login.
 * Beide liefern nur bei app.dev-mode=false eine echte Rolle (siehe SteamAuthInterceptor) --
 * hier trotzdem immer echt verifiziert, damit ein lokaler Test mit devMode=false moeglich ist.
 */
@Service
public class SteamAuthService {
    private static final Logger log = LoggerFactory.getLogger(SteamAuthService.class);

    // Steam AppID dieses Spiels -- siehe frontend/electron/main.js steamworks.init(2816100).
    private static final long STEAM_APP_ID = 2816100L;
    private static final String AUTH_TICKET_ENDPOINT = "https://api.steampowered.com/ISteamUserAuth/AuthenticateUserTicket/v1/";
    private static final String APP_OWNERSHIP_ENDPOINT = "https://api.steampowered.com/ISteamUser/CheckAppOwnership/v0002/";
    private static final String OPENID_ENDPOINT = "https://steamcommunity.com/openid/login";
    private static final Pattern STEAM_OPENID_ID_PATTERN = Pattern.compile("^https://steamcommunity\\.com/openid/id/(\\d+)$");

    private final AppConfig appConfig;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public SteamAuthService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Harte Absicherung gegen Fehlkonfiguration: in Produktion (devMode=false) OHNE
     * konfigurierten Steam-Web-API-Key wuerde der Server zwar noch Tickets/OpenID prüfen
     * koennen (die Ticket-Pruefung selbst braucht den Key -- ohne Key schlaegt JEDE
     * Verifikation fehl-geschlossen fehl, siehe verifyTicket), aber das faellt sonst erst
     * beim ersten Spieler-Login auf statt beim Deploy. Laut geloggt, kein harter Abbruch --
     * ein Server, der wegen einer fehlenden Env-Var gar nicht erst hochkommt, ist im
     * Störungsfall schwerer zu debuggen als einer, der laut meckert und dann (korrekt)
     * jeden echten Login ablehnt.
     */
    @PostConstruct
    void checkConfiguration() {
        if (!appConfig.isDevMode() && appConfig.getSteamWebApiKey().isBlank()) {
            log.error("KONFIGURATIONSFEHLER: app.dev-mode=false ohne app.steam-web-api-key -- " +
                    "kein Spieler kann sich einloggen (Ticket-Verifikation schlaegt fail-closed fehl). " +
                    "Key setzen: https://steamcommunity.com/dev/apikey");
        }
    }

    /** Prueft ein Session-Ticket gegen Steam, liefert die verifizierte SteamID oder leer. */
    public Optional<String> verifyTicket(String ticketHex) {
        String key = appConfig.getSteamWebApiKey();
        if (key == null || key.isBlank()) {
            log.warn("Ticket-Verifikation abgelehnt: kein app.steam-web-api-key konfiguriert.");
            return Optional.empty();
        }
        if (ticketHex == null || ticketHex.isBlank()) return Optional.empty();

        try {
            String url = AUTH_TICKET_ENDPOINT
                    + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&appid=" + STEAM_APP_ID
                    + "&ticket=" + URLEncoder.encode(ticketHex, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("AuthenticateUserTicket antwortete mit HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode params = mapper.readTree(response.body()).path("response").path("params");
            if (!"OK".equals(params.path("result").asText(null))) {
                log.warn("AuthenticateUserTicket: Ticket nicht gueltig ({})", response.body());
                return Optional.empty();
            }
            if (params.path("vacbanned").asBoolean(false) || params.path("publisherbanned").asBoolean(false)) {
                log.warn("AuthenticateUserTicket: Ticket gueltig, aber Account gebannt.");
                return Optional.empty();
            }
            String steamId = params.path("steamid").asText(null);
            return steamId == null || steamId.isBlank() ? Optional.empty() : Optional.of(steamId);
        } catch (Exception e) {
            log.warn("Ticket-Verifikation fehlgeschlagen: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Prueft, ob eine SteamID das Spiel tatsaechlich besitzt (nicht nur ein echter Account
     * ist). Noetig, weil OpenID (verifyOpenIdCallback) NUR die Identitaet bestaetigt, nicht
     * den Kauf -- ohne diesen Check koennte sich jeder beliebige Steam-Account per
     * Browser-Login kostenlos einloggen. Fail-closed: jeder Fehler (Netzwerk, fehlender/
     * falsch-berechtigter Key) zaehlt als "besitzt nicht", nie als "besitzt".
     */
    public boolean ownsGame(String steamId64) {
        String key = appConfig.getSteamWebApiKey();
        if (key == null || key.isBlank()) {
            log.warn("Ownership-Check abgelehnt: kein app.steam-web-api-key konfiguriert.");
            return false;
        }
        try {
            String url = APP_OWNERSHIP_ENDPOINT
                    + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&steamid=" + URLEncoder.encode(steamId64, StandardCharsets.UTF_8)
                    + "&appid=" + STEAM_APP_ID;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("CheckAppOwnership antwortete mit HTTP {}", response.statusCode());
                return false;
            }
            JsonNode result = mapper.readTree(response.body()).path("appownership");
            boolean owns = result.path("ownsapp").asBoolean(false);
            if (!owns) log.info("CheckAppOwnership: {} besitzt das Spiel nicht ({})", steamId64, result.path("result").asText());
            return owns;
        } catch (Exception e) {
            log.warn("Ownership-Check fehlgeschlagen fuer {}: {}", steamId64, e.getMessage());
            return false;
        }
    }

    /** Baut die Redirect-URL zu Steams OpenID-Login-Seite (checkid_setup). */
    public String buildOpenIdLoginUrl(String returnToUrl, String realmUrl) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("openid.ns", "http://specs.openid.net/auth/2.0");
        params.put("openid.mode", "checkid_setup");
        params.put("openid.return_to", returnToUrl);
        params.put("openid.realm", realmUrl);
        params.put("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select");
        params.put("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");
        return OPENID_ENDPOINT + "?" + toQueryString(params);
    }

    /**
     * Verifiziert einen Steam-OpenID-Callback (siehe AuthController#steamCallback) gegen
     * steamcommunity.com (check_authentication) und liefert die SteamID64 oder leer.
     * `params` = alle openid.*-Query-Parameter genau wie von Steam zurueckgeliefert.
     */
    public Optional<String> verifyOpenIdCallback(Map<String, String> params) {
        String claimedId = params.get("openid.claimed_id");
        String identity = params.get("openid.identity");
        String opEndpoint = params.get("openid.op_endpoint");
        if (claimedId == null || identity == null) return Optional.empty();
        if (!OPENID_ENDPOINT.equals(opEndpoint)) {
            log.warn("OpenID-Callback: unerwarteter op_endpoint {}", opEndpoint);
            return Optional.empty();
        }
        Matcher m = STEAM_OPENID_ID_PATTERN.matcher(claimedId);
        if (!m.matches() || !claimedId.equals(identity)) {
            log.warn("OpenID-Callback: claimed_id/identity haben nicht die erwartete Steam-Form.");
            return Optional.empty();
        }
        String steamId = m.group(1);

        Map<String, String> verifyParams = new LinkedHashMap<>(params);
        verifyParams.put("openid.mode", "check_authentication");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(OPENID_ENDPOINT))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(toQueryString(verifyParams)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            boolean valid = response.statusCode() == 200 && response.body().lines()
                    .anyMatch(line -> line.trim().equals("is_valid:true"));
            if (!valid) {
                log.warn("OpenID-Callback: check_authentication lehnte ab ({})", response.body());
                return Optional.empty();
            }
            return Optional.of(steamId);
        } catch (Exception e) {
            log.warn("OpenID-Verifikation fehlgeschlagen: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String toQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}

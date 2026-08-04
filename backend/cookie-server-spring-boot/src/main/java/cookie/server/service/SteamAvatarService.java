package cookie.server.service;

import cookie.server.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Holt die Steam-Avatar-URL ueber die Steam Web API (ISteamUser/GetPlayerSummaries).
 * No-op wenn kein API-Key konfiguriert ist (app.steam-web-api-key leer) -- Aufrufer
 * bekommt dann einfach null zurueck, kein Fehler.
 */
@Service
public class SteamAvatarService {
    private static final Logger log = LoggerFactory.getLogger(SteamAvatarService.class);
    private static final String ENDPOINT = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/";

    private final AppConfig appConfig;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public SteamAvatarService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /** Liefert die avatarfull-URL oder null (kein Key konfiguriert / Fehler / unbekannte SteamID). */
    public String fetchAvatarUrl(String steamId64) {
        String key = appConfig.getSteamWebApiKey();
        if (key == null || key.isBlank()) return null;

        try {
            String url = ENDPOINT + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&steamids=" + URLEncoder.encode(steamId64, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Steam Web API GetPlayerSummaries returned {}", response.statusCode());
                return null;
            }
            JsonNode players = mapper.readTree(response.body()).path("response").path("players");
            if (!players.isArray() || players.isEmpty()) return null;
            JsonNode avatar = players.get(0).path("avatarfull");
            return avatar.isMissingNode() ? null : avatar.asText();
        } catch (Exception e) {
            log.warn("Steam-Avatar-Abruf fehlgeschlagen fuer {}: {}", steamId64, e.getMessage());
            return null;
        }
    }
}

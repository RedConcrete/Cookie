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
        Profile profile = fetchProfile(steamId64);
        return profile == null ? null : profile.avatarUrl();
    }

    public record Profile(String avatarUrl, String personaName) {}

    /** Wie fetchAvatarUrl, liefert zusaetzlich den Steam-Anzeigenamen (personaname) --
     * genutzt vom OpenID-Web-Login (siehe AuthController), der anders als der
     * Electron-Client keinen lokalen Namen ueber steamworks.js hat. */
    public Profile fetchProfile(String steamId64) {
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
            JsonNode player = players.get(0);
            JsonNode avatar = player.path("avatarfull");
            JsonNode name = player.path("personaname");
            return new Profile(
                    avatar.isMissingNode() ? null : avatar.asText(),
                    name.isMissingNode() ? null : name.asText()
            );
        } catch (Exception e) {
            log.warn("Steam-Profil-Abruf fehlgeschlagen fuer {}: {}", steamId64, e.getMessage());
            return null;
        }
    }

    public record AvatarImage(byte[] bytes, String contentType) {}

    /** Downloads the actual image bytes from an avatarfull URL (as returned by fetchAvatarUrl). Null on any failure. */
    public AvatarImage downloadAvatarImage(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(avatarUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("Avatar-Bild-Download returned {}", response.statusCode());
                return null;
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
            return new AvatarImage(response.body(), contentType);
        } catch (Exception e) {
            log.warn("Avatar-Bild-Download fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }
}

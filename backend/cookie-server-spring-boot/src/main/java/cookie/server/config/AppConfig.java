package cookie.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private boolean devMode = false;
    private String adminToken = "change-me-in-production";

    // Optional. Leer = Avatar-Resync deaktiviert (SteamAvatarService no-op).
    // Key erzeugen: https://steamcommunity.com/dev/apikey -- als Server-Secret
    // setzen (APP_STEAM-WEB-API-KEY env var o.ae.), nie committen.
    private String steamWebApiKey = "";

    public boolean isDevMode() { return devMode; }
    public void setDevMode(boolean devMode) { this.devMode = devMode; }

    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }

    public String getSteamWebApiKey() { return steamWebApiKey; }
    public void setSteamWebApiKey(String steamWebApiKey) { this.steamWebApiKey = steamWebApiKey; }
}

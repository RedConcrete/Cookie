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
    // setzen (APP_STEAM-WEB-API-KEY env var o.ae.), nie committen. Wird auch
    // fuer den Anzeigenamen-Abruf im Steam-OpenID-Web-Login gebraucht (siehe
    // SteamAuthService), NICHT fuer die Ticket-/OpenID-Verifikation selbst --
    // die laufen ohne Key gegen Steams Auth-Endpunkte.
    private String steamWebApiKey = "";

    // Basis-URL, zu der der Steam-OpenID-Web-Login nach erfolgreichem Login
    // zurueckleitet (siehe AuthController#steamCallback). In Produktion auf
    // die tatsaechliche oeffentliche Frontend-URL setzen.
    private String frontendBaseUrl = "http://localhost:5173";

    // Oeffentlich erreichbare Basis-URL DIESES Backends (nicht des Frontends) --
    // wird als openid.return_to/openid.realm gegenueber Steam angegeben (siehe
    // AuthController#steamLogin/SteamAuthService#buildOpenIdLoginUrl). Muss die
    // tatsaechliche, von aussen erreichbare Domain sein, sonst schlaegt Steams
    // OpenID-Redirect fehl. In Produktion z.B. https://cookie.r3dconcrete.de.
    private String publicBaseUrl = "http://localhost:9876";

    public boolean isDevMode() { return devMode; }
    public void setDevMode(boolean devMode) { this.devMode = devMode; }

    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }

    public String getSteamWebApiKey() { return steamWebApiKey; }
    public void setSteamWebApiKey(String steamWebApiKey) { this.steamWebApiKey = steamWebApiKey; }

    public String getFrontendBaseUrl() { return frontendBaseUrl; }
    public void setFrontendBaseUrl(String frontendBaseUrl) { this.frontendBaseUrl = frontendBaseUrl; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
}

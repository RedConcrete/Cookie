package cookie.server.config;

import cookie.server.service.SteamSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Erzwingt eine gueltige Steam-Session auf jedem /api/v1/**-Call, AUSSER
 * app.dev-mode=true (siehe AppConfig) -- dort heutiges Verhalten unveraendert
 * (kein Auth-Check, DEV_PLAYER_001, MCP-Testserver unter tools/mcp-testing-server/
 * funktioniert unveraendert weiter). /api/v1/config, /api/v1/auth/** und
 * /api/v1/admin/** sind von dieser Registrierung ausgeschlossen (siehe
 * SteamAuthWebConfig) -- Admin bleibt beim bestehenden X-Admin-Token-Mechanismus.
 *
 * Traegt ein Request eine userId/steamId in Pfad oder Query, muss sie mit der
 * verifizierten Session uebereinstimmen (Owner-Check) -- verhindert, dass ein
 * Spieler mit gueltiger eigener Session fremde Accounts anspricht. Einzige
 * Ausnahme: Endpunkte, die laut Produktentscheidung oeffentlich fuer JEDEN
 * eingeloggten Spieler einsehbar bleiben (Rangliste-Profile, siehe
 * OWNER_CHECK_EXEMPT_PATHS) -- die brauchen weiterhin eine Session (kein
 * anonymer Zugriff, siehe Klassendoc), aber keinen Identitaets-Match.
 *
 * Der einzige Endpunkt mit Identitaet im Body statt Pfad/Query
 * (POST /api/v1/market) wird hier NICHT geprueft (Body vor preHandle generisch
 * zu lesen waere fragil) -- MarketController macht den Owner-Check dort selbst
 * anhand des von diesem Interceptor gesetzten Request-Attributs "verifiedSteamId".
 */
@Component
public class SteamAuthInterceptor implements HandlerInterceptor {

    public static final String VERIFIED_STEAM_ID_ATTR = "verifiedSteamId";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String[] OWNER_CHECK_EXEMPT_PATHS = {
            "/api/v1/players/*/networth",
            "/api/v1/players/*/networth/history",
            "/api/v1/players/*/profile",
    };

    private final AppConfig appConfig;
    private final SteamSessionService sessionService;

    public SteamAuthInterceptor(AppConfig appConfig, SteamSessionService sessionService) {
        this.appConfig = appConfig;
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (appConfig.isDevMode()) return true;

        String token = request.getHeader("X-Session-Token");
        Optional<String> verified = sessionService.resolveSteamId(token);
        if (verified.isEmpty()) {
            unauthorized(response, "Keine gültige Steam-Session. Bitte Spiel neu starten/neu einloggen.");
            return false;
        }
        String verifiedSteamId = verified.get();

        String claimed = claimedIdentity(request);
        boolean exempt = isOwnerCheckExempt(request.getRequestURI());
        if (claimed != null && !exempt && !claimed.equals(verifiedSteamId)) {
            unauthorized(response, "Session gehört nicht zu diesem Spieler.");
            return false;
        }

        request.setAttribute(VERIFIED_STEAM_ID_ATTR, verifiedSteamId);
        return true;
    }

    @SuppressWarnings("unchecked")
    private String claimedIdentity(HttpServletRequest request) {
        Map<String, String> pathVars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVars != null) {
            if (pathVars.containsKey("userId")) return pathVars.get("userId");
            if (pathVars.containsKey("steamId")) return pathVars.get("steamId");
        }
        // SkillTreeController#getTree ist der einzige Endpunkt mit Identitaet als Query-Param.
        return request.getParameter("userId");
    }

    private boolean isOwnerCheckExempt(String requestUri) {
        for (String pattern : OWNER_CHECK_EXEMPT_PATHS) {
            if (PATH_MATCHER.match(pattern, requestUri)) return true;
        }
        return false;
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}

package cookie.server.controller;

import cookie.server.config.AppConfig;
import cookie.server.exception.AuthException;
import cookie.server.service.SteamAuthService;
import cookie.server.service.SteamAvatarService;
import cookie.server.service.SteamSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Steam-Auth-Einstiegspunkte -- zwei getrennte Flows, beide muenden in dieselbe
 * SteamSessionService-Session (siehe dort, X-Session-Token), die der
 * SteamAuthInterceptor bei app.dev-mode=false auf jedem anderen Endpunkt verlangt:
 *  - POST /steam            : Electron-Client, Session-Ticket via steamworks.js.
 *  - GET  /steam/login+callback : Browser-Web-Login via Steam OpenID.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AppConfig appConfig;
    private final SteamAuthService steamAuthService;
    private final SteamSessionService sessionService;
    private final SteamAvatarService steamAvatarService;

    public AuthController(AppConfig appConfig, SteamAuthService steamAuthService,
                           SteamSessionService sessionService, SteamAvatarService steamAvatarService) {
        this.appConfig = appConfig;
        this.steamAuthService = steamAuthService;
        this.sessionService = sessionService;
        this.steamAvatarService = steamAvatarService;
    }

    @PostMapping("/steam")
    public ResponseEntity<?> authenticateWithTicket(@RequestBody Map<String, String> body) {
        String claimedSteamId = body.get("steamId");
        String ticket = body.get("ticket");
        if (claimedSteamId == null || claimedSteamId.isBlank()) {
            throw new AuthException("steamId fehlt.");
        }

        String verifiedSteamId = steamAuthService.verifyTicket(ticket)
                .orElseThrow(() -> new AuthException("Steam-Ticket ungültig oder abgelaufen."));
        if (!verifiedSteamId.equals(claimedSteamId)) {
            throw new AuthException("Ticket gehört nicht zu dieser SteamID.");
        }

        SteamSessionService.Session session = sessionService.createSession(verifiedSteamId);
        return ResponseEntity.ok(sessionResponse(session));
    }

    @GetMapping("/steam/login")
    public void steamLogin(HttpServletResponse response) throws IOException {
        String returnTo = appConfig.getPublicBaseUrl() + "/api/v1/auth/steam/callback";
        String url = steamAuthService.buildOpenIdLoginUrl(returnTo, appConfig.getPublicBaseUrl());
        response.sendRedirect(url);
    }

    @GetMapping("/steam/callback")
    public void steamCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> params = allQueryParams(request);
        String steamId = steamAuthService.verifyOpenIdCallback(params).orElse(null);

        if (steamId == null) {
            response.sendRedirect(appConfig.getFrontendBaseUrl() + "/?authError=steam_openid_failed");
            return;
        }
        // OpenID bestaetigt nur die Identitaet, nicht den Kauf (anders als der Ticket-Flow,
        // wo Steam den App-Start selbst schon an den Besitz koppelt) -- ohne diesen Check
        // koennte sich jeder beliebige Steam-Account per Browser kostenlos einloggen.
        if (!steamAuthService.ownsGame(steamId)) {
            response.sendRedirect(appConfig.getFrontendBaseUrl() + "/?authError=steam_no_ownership");
            return;
        }

        SteamSessionService.Session session = sessionService.createSession(steamId);
        SteamAvatarService.Profile profile = steamAvatarService.fetchProfile(steamId);
        String name = profile != null && profile.personaName() != null ? profile.personaName() : "";

        // steamId/name werden zusaetzlich zum Token mitgegeben -- das Frontend braucht sie
        // synchron beim Laden (App.vue#authInfo), bevor irgendein authentifizierter Call
        // moeglich ist, siehe api.js request().
        String redirect = appConfig.getFrontendBaseUrl() + "/"
                + "?webSession=" + urlEncode(session.token())
                + "&steamId=" + urlEncode(steamId)
                + "&name=" + urlEncode(name);
        response.sendRedirect(redirect);
    }

    private Map<String, Object> sessionResponse(SteamSessionService.Session session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionToken", session.token());
        body.put("expiresAt", session.expiresAt().toString());
        return body;
    }

    private static Map<String, String> allQueryParams(HttpServletRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            result.put(name, request.getParameter(name));
        }
        return result;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}

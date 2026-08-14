package cookie.server.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kurzlebige Server-Sessions nach erfolgreicher Steam-Verifikation (Ticket- oder
 * OpenID-Flow, siehe SteamAuthService/AuthController). Rein in-memory -- bewusste
 * Entscheidung (siehe docs/plans, Steam-Auth-Plan 2026-08-14): kein DB-Schema-Change,
 * Server-Neustart zwingt alle Spieler zum Neu-Einloggen, das ist hier unkritisch.
 * Nur relevant bei app.dev-mode=false (siehe SteamAuthInterceptor).
 */
@Service
public class SteamSessionService {

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public record Session(String token, String steamId, Instant expiresAt) {}

    public Session createSession(String steamId) {
        String token = generateToken();
        Session session = new Session(token, steamId, Instant.now().plus(SESSION_TTL));
        sessions.put(token, session);
        return session;
    }

    /** Liefert die verifizierte SteamID fuer einen Session-Token, leer wenn unbekannt/abgelaufen. */
    public Optional<String> resolveSteamId(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Session session = sessions.get(token);
        if (session == null) return Optional.empty();
        if (Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.steamId());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

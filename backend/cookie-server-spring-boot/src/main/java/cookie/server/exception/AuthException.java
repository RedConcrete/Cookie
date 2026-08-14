package cookie.server.exception;

/** Fehlende/ungueltige/nicht-passende Steam-Session (siehe SteamAuthInterceptor, AuthController). */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}

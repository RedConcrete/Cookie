package cookie.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registriert SteamAuthInterceptor auf allen /api/v1/**-Endpunkten ausser den
 * unten gelisteten -- siehe SteamAuthInterceptor fuer die eigentliche Logik. */
@Configuration
public class SteamAuthWebConfig implements WebMvcConfigurer {

    private final SteamAuthInterceptor steamAuthInterceptor;

    public SteamAuthWebConfig(SteamAuthInterceptor steamAuthInterceptor) {
        this.steamAuthInterceptor = steamAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(steamAuthInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/config",
                        "/api/v1/auth/**",
                        "/api/v1/admin/**"
                );
    }
}

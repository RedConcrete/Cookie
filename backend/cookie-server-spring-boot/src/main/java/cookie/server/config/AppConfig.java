package cookie.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private boolean devMode = false;
    private String adminToken = "change-me-in-production";

    public boolean isDevMode() { return devMode; }
    public void setDevMode(boolean devMode) { this.devMode = devMode; }

    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }
}

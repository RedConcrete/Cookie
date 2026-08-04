package cookie.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private String steamId;

    // Steam display name, resynced from steamworks.js on every login. Null for
    // accounts created before this field existed or players who never launched via Steam.
    private String displayName;

    // Steam-Avatar-URL (avatarfull), ueber die Steam Web API resynct (siehe
    // SteamAvatarService) -- nur wenn STEAM_WEB_API_KEY gesetzt ist. Null sonst.
    @Column(length = 512)
    private String avatarUrl;

    // Optimistic Locking: WageScheduler (60s) und PassiveIncomeScheduler (5s) schreiben
    // beide unabhaengig auf denselben User -- ohne Version-Check gewinnt "last write wins"
    // und einer der beiden Writes (Lohn-Abzug oder Produktions-Gutschrift) geht stillschweigend
    // verloren. Mit @Version wirft JPA eine OptimisticLockException, wenn zwischen Lesen und
    // Schreiben ein anderer Prozess die Zeile geaendert hat -- Aufrufer muss dann neu laden
    // und erneut versuchen (siehe Retry-Schleifen in den Schedulern).
    @Version
    private Long version;

    private String token;

    private double cookies;
    private double sugar;
    private double flour;
    private double eggs;
    private double butter;
    private double chocolate;
    private double milk;
    private double lifetimeCookiesBaked;
    private int prestigeLevel;
    private int totalPrestiges;
    @Column(name = "workers_idle", columnDefinition = "boolean default false")
    private boolean workersIdle;

    @Column(name = "owned_citizens", columnDefinition = "integer default 0")
    private int ownedCitizens = 0;

    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Long getVersion() {
        return version;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public double getCookies() {
        return cookies;
    }

    public void setCookies(double cookies) {
        this.cookies = cookies;
    }

    public double getSugar() {
        return sugar;
    }

    public void setSugar(double sugar) {
        this.sugar = sugar;
    }

    public double getFlour() {
        return flour;
    }

    public void setFlour(double flour) {
        this.flour = flour;
    }

    public double getEggs() {
        return eggs;
    }

    public void setEggs(double eggs) {
        this.eggs = eggs;
    }

    public double getButter() {
        return butter;
    }

    public void setButter(double butter) {
        this.butter = butter;
    }

    public double getChocolate() {
        return chocolate;
    }

    public void setChocolate(double chocolate) {
        this.chocolate = chocolate;
    }

    public double getMilk() {
        return milk;
    }

    public void setMilk(double milk) {
        this.milk = milk;
    }

    public double getLifetimeCookiesBaked() {
        return lifetimeCookiesBaked;
    }

    public void setLifetimeCookiesBaked(double lifetimeCookiesBaked) {
        this.lifetimeCookiesBaked = lifetimeCookiesBaked;
    }

    public int getPrestigeLevel() {
        return prestigeLevel;
    }

    public void setPrestigeLevel(int prestigeLevel) {
        this.prestigeLevel = prestigeLevel;
    }

    public int getTotalPrestiges() {
        return totalPrestiges;
    }

    public void setTotalPrestiges(int totalPrestiges) {
        this.totalPrestiges = totalPrestiges;
    }

    public boolean isWorkersIdle() { return workersIdle; }
    public void setWorkersIdle(boolean workersIdle) { this.workersIdle = workersIdle; }
    public int getOwnedCitizens() { return ownedCitizens; }
    public void setOwnedCitizens(int ownedCitizens) { this.ownedCitizens = ownedCitizens; }
}
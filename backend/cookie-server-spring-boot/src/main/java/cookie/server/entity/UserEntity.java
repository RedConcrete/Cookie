package cookie.server.entity;

import cookie.server.enums.ResourceName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

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

    // Cached copy of the actual avatar image bytes, downloaded once from `avatarUrl`
    // and served back out via UserController#getAvatar -- so the client always loads
    // it from our own server (with a long Cache-Control) instead of hammering Steam's
    // CDN on every page load, which is what caused the avatar to flicker in/out.
    @Lob
    @Column(name = "avatar_bytes")
    private byte[] avatarBytes;

    @Column(name = "avatar_content_type", length = 64)
    private String avatarContentType;

    // Server clock of the last accepted harvest call for this player -- the harvest
    // amount is elapsed-time-based (see UserService#harvest), never a client-supplied
    // amount, so a hover session can be synced in infrequent batches instead of one
    // request per tick while still being impossible to fake ("Client-Werte nie
    // vertrauen" -- CLAUDE.md).
    private LocalDateTime lastHarvestAt;

    // Server-Zeitpunkt des letzten Spielstarts (siehe GameController#initializeGame). Nur
    // fuer "aktive Spieler in den letzten N Tagen" (Markt-Liquiditaets-Skalierung, siehe
    // MarketService#recalculateDynamicStockBase) -- deshalb bewusst NICHT bei jedem generischen
    // getUser()-Aufruf mitgezogen, sonst wuerde blosses Ansehen eines fremden Profils dessen
    // Aktiv-Status faelschlich auffrischen.
    private LocalDateTime lastActiveAt;

    // Server-Zeitpunkt des letzten angenommenen Markt-Trades (Kauf/Verkauf) -- Anti-Spam-
    // Sperre, siehe MarketService#performAction/MarketConfig#getTradeCooldownMs. Gilt global
    // pro Spieler ueber alle Ressourcen hinweg, nicht pro Ressource.
    private LocalDateTime lastMarketTradeAt;

    // Betrag + Zeitpunkt der letzten tatsaechlich abgebuchten Lohnzahlung (WageService --
    // nur gesetzt, wenn wirklich abgebucht wurde, nicht im Idle-mangels-Cookies-Fall). Das
    // Frontend pollt das ueber /api/v1/farm/wage-status/{userId} und vergleicht den
    // Zeitstempel, um die fallende rote Zahl am Cookie-HUD genau einmal pro Abbuchung
    // auszuloesen (siehe GameController#getWageStatus).
    @Column(name = "last_wage_amount", columnDefinition = "double precision default 0")
    private double lastWageAmount = 0;
    @Column(name = "last_wage_at")
    private LocalDateTime lastWageAt;

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

    @Column(name = "skill_points", columnDefinition = "integer default 0")
    private int skillPoints = 0;

    @Column(name = "total_skill_points_bought", columnDefinition = "integer default 0")
    private int totalSkillPointsBought = 0;

    @Column(name = "total_skill_point_cookies_spent", columnDefinition = "double precision default 0")
    private double totalSkillPointCookiesSpent = 0;

    // Lifetime-Zaehler fuers Statistik-Dialog (2026-08-07) -- nie verringert, nur fuer Anzeige,
    // fliessen nicht in Net Worth/Spiellogik ein. columnDefinition mit Default noetig, sonst
    // scheitert ddl-auto=update mit NOT NULL an bereits befuellten Zeilen (siehe ROADMAP.md).
    @Column(name = "lifetime_sugar_harvested", columnDefinition = "double precision default 0")
    private double lifetimeSugarHarvested = 0;
    @Column(name = "lifetime_flour_harvested", columnDefinition = "double precision default 0")
    private double lifetimeFlourHarvested = 0;
    @Column(name = "lifetime_eggs_harvested", columnDefinition = "double precision default 0")
    private double lifetimeEggsHarvested = 0;
    @Column(name = "lifetime_butter_harvested", columnDefinition = "double precision default 0")
    private double lifetimeButterHarvested = 0;
    @Column(name = "lifetime_chocolate_harvested", columnDefinition = "double precision default 0")
    private double lifetimeChocolateHarvested = 0;
    @Column(name = "lifetime_milk_harvested", columnDefinition = "double precision default 0")
    private double lifetimeMilkHarvested = 0;
    @Column(name = "lifetime_cookies_spent_on_market", columnDefinition = "double precision default 0")
    private double lifetimeCookiesSpentOnMarket = 0;
    @Column(name = "lifetime_cookies_earned_from_market", columnDefinition = "double precision default 0")
    private double lifetimeCookiesEarnedFromMarket = 0;

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

    public byte[] getAvatarBytes() {
        return avatarBytes;
    }

    public void setAvatarBytes(byte[] avatarBytes) {
        this.avatarBytes = avatarBytes;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public void setAvatarContentType(String avatarContentType) {
        this.avatarContentType = avatarContentType;
    }

    public LocalDateTime getLastHarvestAt() {
        return lastHarvestAt;
    }

    public void setLastHarvestAt(LocalDateTime lastHarvestAt) {
        this.lastHarvestAt = lastHarvestAt;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public LocalDateTime getLastMarketTradeAt() {
        return lastMarketTradeAt;
    }

    public void setLastMarketTradeAt(LocalDateTime lastMarketTradeAt) {
        this.lastMarketTradeAt = lastMarketTradeAt;
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

    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int skillPoints) { this.skillPoints = skillPoints; }

    public int getTotalSkillPointsBought() { return totalSkillPointsBought; }
    public void setTotalSkillPointsBought(int totalSkillPointsBought) { this.totalSkillPointsBought = totalSkillPointsBought; }

    public double getTotalSkillPointCookiesSpent() { return totalSkillPointCookiesSpent; }
    public void setTotalSkillPointCookiesSpent(double totalSkillPointCookiesSpent) { this.totalSkillPointCookiesSpent = totalSkillPointCookiesSpent; }

    public double getLifetimeSugarHarvested() { return lifetimeSugarHarvested; }
    public void setLifetimeSugarHarvested(double v) { this.lifetimeSugarHarvested = v; }

    public double getLifetimeFlourHarvested() { return lifetimeFlourHarvested; }
    public void setLifetimeFlourHarvested(double v) { this.lifetimeFlourHarvested = v; }

    public double getLifetimeEggsHarvested() { return lifetimeEggsHarvested; }
    public void setLifetimeEggsHarvested(double v) { this.lifetimeEggsHarvested = v; }

    public double getLifetimeButterHarvested() { return lifetimeButterHarvested; }
    public void setLifetimeButterHarvested(double v) { this.lifetimeButterHarvested = v; }

    public double getLifetimeChocolateHarvested() { return lifetimeChocolateHarvested; }
    public void setLifetimeChocolateHarvested(double v) { this.lifetimeChocolateHarvested = v; }

    public double getLifetimeMilkHarvested() { return lifetimeMilkHarvested; }
    public void setLifetimeMilkHarvested(double v) { this.lifetimeMilkHarvested = v; }

    public double getLifetimeCookiesSpentOnMarket() { return lifetimeCookiesSpentOnMarket; }
    public void setLifetimeCookiesSpentOnMarket(double v) { this.lifetimeCookiesSpentOnMarket = v; }

    public double getLifetimeCookiesEarnedFromMarket() { return lifetimeCookiesEarnedFromMarket; }
    public void setLifetimeCookiesEarnedFromMarket(double v) { this.lifetimeCookiesEarnedFromMarket = v; }

    /** Bucht `amount` auf den passenden Lifetime-Zaehler -- gemeinsame Stelle fuer Hover-Ernte
     *  (UserService#harvest) und passive Produktion (PassiveIncomeService#creditUser). */
    public void addLifetimeHarvested(ResourceName resource, double amount) {
        switch (resource) {
            case SUGAR     -> lifetimeSugarHarvested     += amount;
            case FLOUR     -> lifetimeFlourHarvested     += amount;
            case EGGS      -> lifetimeEggsHarvested      += amount;
            case BUTTER    -> lifetimeButterHarvested    += amount;
            case CHOCOLATE -> lifetimeChocolateHarvested += amount;
            case MILK      -> lifetimeMilkHarvested      += amount;
        }
    }
    public int getOwnedCitizens() { return ownedCitizens; }
    public void setOwnedCitizens(int ownedCitizens) { this.ownedCitizens = ownedCitizens; }

    public double getLastWageAmount() { return lastWageAmount; }
    public void setLastWageAmount(double lastWageAmount) { this.lastWageAmount = lastWageAmount; }

    public LocalDateTime getLastWageAt() { return lastWageAt; }
    public void setLastWageAt(LocalDateTime lastWageAt) { this.lastWageAt = lastWageAt; }
}
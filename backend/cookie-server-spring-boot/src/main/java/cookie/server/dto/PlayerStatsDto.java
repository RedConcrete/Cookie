package cookie.server.dto;

/**
 * Persönliche Statistik-Übersicht (Lifetime-Zähler + aktuell aktive Boni). Nur für den
 * eigenen Account gedacht -- anders als PlayerProfileDto nicht für andere Spieler gedacht
 * (Rangliste zeigt kein PlayerStatsDto), siehe cookie-game-design.md Abschnitt "Statistik-Dialog".
 */
public class PlayerStatsDto {
    private double lifetimeSugarHarvested;
    private double lifetimeFlourHarvested;
    private double lifetimeEggsHarvested;
    private double lifetimeButterHarvested;
    private double lifetimeChocolateHarvested;
    private double lifetimeMilkHarvested;
    private double lifetimeCookiesBaked;
    private double lifetimeCookiesSpentOnMarket;
    private double lifetimeCookiesEarnedFromMarket;
    private int totalPrestiges;

    // Aktuell aktive Boni (Skill-Baum + Gebäude kombiniert wo zutreffend)
    private double harvestYieldSugarBonus;
    private double harvestYieldFlourBonus;
    private double harvestYieldEggsBonus;
    private double harvestYieldButterBonus;
    private double harvestYieldChocolateBonus;
    private double harvestYieldMilkBonus;
    private double bakeOutputBonus;
    private double effectiveMarketFeeRate;

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

    public double getLifetimeCookiesBaked() { return lifetimeCookiesBaked; }
    public void setLifetimeCookiesBaked(double v) { this.lifetimeCookiesBaked = v; }

    public double getLifetimeCookiesSpentOnMarket() { return lifetimeCookiesSpentOnMarket; }
    public void setLifetimeCookiesSpentOnMarket(double v) { this.lifetimeCookiesSpentOnMarket = v; }

    public double getLifetimeCookiesEarnedFromMarket() { return lifetimeCookiesEarnedFromMarket; }
    public void setLifetimeCookiesEarnedFromMarket(double v) { this.lifetimeCookiesEarnedFromMarket = v; }

    public int getTotalPrestiges() { return totalPrestiges; }
    public void setTotalPrestiges(int v) { this.totalPrestiges = v; }

    public double getHarvestYieldSugarBonus() { return harvestYieldSugarBonus; }
    public void setHarvestYieldSugarBonus(double v) { this.harvestYieldSugarBonus = v; }

    public double getHarvestYieldFlourBonus() { return harvestYieldFlourBonus; }
    public void setHarvestYieldFlourBonus(double v) { this.harvestYieldFlourBonus = v; }

    public double getHarvestYieldEggsBonus() { return harvestYieldEggsBonus; }
    public void setHarvestYieldEggsBonus(double v) { this.harvestYieldEggsBonus = v; }

    public double getHarvestYieldButterBonus() { return harvestYieldButterBonus; }
    public void setHarvestYieldButterBonus(double v) { this.harvestYieldButterBonus = v; }

    public double getHarvestYieldChocolateBonus() { return harvestYieldChocolateBonus; }
    public void setHarvestYieldChocolateBonus(double v) { this.harvestYieldChocolateBonus = v; }

    public double getHarvestYieldMilkBonus() { return harvestYieldMilkBonus; }
    public void setHarvestYieldMilkBonus(double v) { this.harvestYieldMilkBonus = v; }

    public double getBakeOutputBonus() { return bakeOutputBonus; }
    public void setBakeOutputBonus(double v) { this.bakeOutputBonus = v; }

    public double getEffectiveMarketFeeRate() { return effectiveMarketFeeRate; }
    public void setEffectiveMarketFeeRate(double v) { this.effectiveMarketFeeRate = v; }
}

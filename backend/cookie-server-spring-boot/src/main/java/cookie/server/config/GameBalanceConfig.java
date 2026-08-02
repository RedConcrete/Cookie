package cookie.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Balance-Zahlen für Gebäude, Bürger und Prestige, gebündelt in einer einzigen
 * live editierbaren Bean (wie {@link MarketConfig}) statt als Compile-Time-Konstanten.
 * Admin kann diese Werte zur Laufzeit über /api/v1/admin/config ändern, ohne Neustart.
 */
@Component
@ConfigurationProperties(prefix = "balance")
public class GameBalanceConfig {

    /** Basis-Lagerkapazität ohne Lager-Ausbau. */
    private double baseStorageCap = 100;

    /** Zusätzliche Lagerkapazität pro Lager-Level. */
    private double storagePerLevel = 1000;

    /** Maximale Bürger pro Rathaus-Level. */
    private int citizensPerRatLevel = 4;

    /** Kosten für den ersten Bürger. */
    private double citizenBaseCost = 50;

    /** Wachstumsfaktor der Bürger-Kosten pro bereits angeworbenem Bürger. */
    private double citizenCostGrowth = 1.15;

    /** Zusätzliche Arbeiter-Slots pro Gebäude-Ausbaustufe über Stufe 1 hinaus. */
    private int workersPerLevel = 1;

    /** Wachstumsfaktor der Gebäude-Bau-/Ausbaukosten pro Stufe (cost = baseCost × growth^level). */
    private double buildingCostGrowth = 2.0;

    /** Tick-Länge (Sekunden) für passive Gebäude-Produktion. */
    private double passiveTickSeconds = 5.0;

    /** Prestige-Schwelle bei Stufe 0 (Net Worth, ab der Prestige Stufe 1 möglich ist). */
    private double prestigeBaseThreshold = 100_000;

    /** Wachstumsfaktor der Prestige-Schwelle pro Stufe. */
    private double prestigeThresholdGrowth = 1.5;

    /** Bonus auf Backen-Output/Ernte-Menge pro Prestige-Stufe (0.1 = +10 % pro Stufe). */
    private double prestigeMultiplierPerLevel = 0.1;

    public double getBaseStorageCap() { return baseStorageCap; }
    public void setBaseStorageCap(double v) { this.baseStorageCap = v; }

    public double getStoragePerLevel() { return storagePerLevel; }
    public void setStoragePerLevel(double v) { this.storagePerLevel = v; }

    public int getCitizensPerRatLevel() { return citizensPerRatLevel; }
    public void setCitizensPerRatLevel(int v) { this.citizensPerRatLevel = v; }

    public double getCitizenBaseCost() { return citizenBaseCost; }
    public void setCitizenBaseCost(double v) { this.citizenBaseCost = v; }

    public double getCitizenCostGrowth() { return citizenCostGrowth; }
    public void setCitizenCostGrowth(double v) { this.citizenCostGrowth = v; }

    public int getWorkersPerLevel() { return workersPerLevel; }
    public void setWorkersPerLevel(int v) { this.workersPerLevel = v; }

    public double getBuildingCostGrowth() { return buildingCostGrowth; }
    public void setBuildingCostGrowth(double v) { this.buildingCostGrowth = v; }

    public double getPassiveTickSeconds() { return passiveTickSeconds; }
    public void setPassiveTickSeconds(double v) { this.passiveTickSeconds = v; }

    public double getPrestigeBaseThreshold() { return prestigeBaseThreshold; }
    public void setPrestigeBaseThreshold(double v) { this.prestigeBaseThreshold = v; }

    public double getPrestigeThresholdGrowth() { return prestigeThresholdGrowth; }
    public void setPrestigeThresholdGrowth(double v) { this.prestigeThresholdGrowth = v; }

    public double getPrestigeMultiplierPerLevel() { return prestigeMultiplierPerLevel; }
    public void setPrestigeMultiplierPerLevel(double v) { this.prestigeMultiplierPerLevel = v; }
}

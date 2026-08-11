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

    /** Lohn pro zugewiesenem Arbeiter und Minute (Produktionsgebäude). Wert 2.0 reproduziert
     * exakt die bisherigen pauschalen BuildingDef.wagePerMin-Werte bei Stufe-1-Vollbesatzung
     * (z.B. Plantage 4 C/min bei 2 Basis-Arbeitern = 2.0/Arbeiter), skaliert ab da aber mit
     * tatsächlicher Arbeiterzahl statt pauschal pro Gebäude. */
    private double wagePerMinPerWorker = 2.0;

    /** Wachstumsfaktor der Gebäude-Bau-/Ausbaukosten pro Stufe (cost = baseCost × growth^level). */
    private double buildingCostGrowth = 2.0;

    /** Prestige-Schwelle bei Stufe 0 (Net Worth, ab der Prestige Stufe 1 möglich ist). */
    private double prestigeBaseThreshold = 100_000;

    /** Wachstumsfaktor der Prestige-Schwelle pro Stufe. */
    private double prestigeThresholdGrowth = 1.5;

    /** Bonus auf Backen-Output/Ernte-Menge pro Prestige-Stufe (0.1 = +10 % pro Stufe). */
    private double prestigeMultiplierPerLevel = 0.1;

    /** Kosten für den ersten Skill-Punkt. Bewusst deutlich teurer als der
     * erste Bürger (50) -- der Skill-Baum ist der Haupt-Cookie-Sink und soll
     * sich von Anfang an nach einer echten Investition anfühlen, nicht nach
     * einem Nebenbei-Kauf. */
    private double skillPointBaseCost = 150;

    /** Wachstumsfaktor der Skill-Punkt-Kosten pro bereits gekauftem Punkt.
     * Bewusst deutlich steiler als Bürger/Gebäude (1.15) -- soll auch nach
     * vielen Punkten noch ein spürbares Ziel bleiben, das langes Spielen
     * belohnt statt sich schnell "flach" anzufühlen. Die pro-Knoten-Effekte
     * sind bewusst klein gehalten (siehe buildNodes()) -- der Baum lebt vom
     * Sammeln vieler Punkte über die Zeit, nicht von 2-3 Käufen mit riesigem
     * Einzeleffekt. */
    private double skillPointCostGrowth = 1.4;

    /** Mindestabstand zwischen zwei Collect-Aufrufen auf dasselbe Gebäude (Anti-Spam). */
    private long collectCooldownMs = 150;

    /** Basis-Zinssatz auf negative Cookies pro Lohn-Tick (Dispo-Kredit statt Idle-Sperre bei
     * zu wenig Guthaben, siehe WageService#deductWageForUser). Reduzierbar über den DISPO-Zweig
     * im Skill-Baum (EffectType.WAGE_INTEREST_REDUCTION), aber nie unter debtInterestRateFloor. */
    private double debtInterestRate = 0.10;

    /** Mindest-Zinssatz auf negative Cookies, auch mit allen DISPO-Skillknoten alloziert --
     * verhindert, dass der Dispo komplett zinsfrei wird. */
    private double debtInterestRateFloor = 0.02;

    /** Dispo-Grenze = aktueller Gesamtlohn/Minute × diesen Faktor. Darüber greift wieder die
     * alte Idle-Sperre (harter Stopp) statt weiter ins Minus zu rutschen -- verhindert eine
     * endlose Zinsspirale. */
    private double debtLimitMultiplier = 8.0;

    /** Wie viele Abrechnungshistorie-Einträge (WageLedgerEntity) pro Spieler maximal behalten
     * werden -- ältere werden bei jeder neuen Abbuchung hart gelöscht (siehe WageService). Bei
     * einem Eintrag/Minute entsprechen 200 Einträge ca. 3.3 Stunden Historie. */
    private int wageLedgerMaxEntries = 200;

    /** Ohne Heartbeat (siehe UserEntity#lastHeartbeatAt) länger als diese Zahl Minuten gilt ein
     * Spieler als AFK -- WageScheduler rechnet für ihn dann keinen Lohn/Zinsen mehr ab, bis er
     * wieder aktiv ist. Gespiegelt ans Frontend über ConfigController. */
    private int afkTimeoutMinutes = 10;

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

    public double getWagePerMinPerWorker() { return wagePerMinPerWorker; }
    public void setWagePerMinPerWorker(double v) { this.wagePerMinPerWorker = v; }

    public double getBuildingCostGrowth() { return buildingCostGrowth; }
    public void setBuildingCostGrowth(double v) { this.buildingCostGrowth = v; }

    public double getPrestigeBaseThreshold() { return prestigeBaseThreshold; }
    public void setPrestigeBaseThreshold(double v) { this.prestigeBaseThreshold = v; }

    public double getPrestigeThresholdGrowth() { return prestigeThresholdGrowth; }
    public void setPrestigeThresholdGrowth(double v) { this.prestigeThresholdGrowth = v; }

    public double getPrestigeMultiplierPerLevel() { return prestigeMultiplierPerLevel; }
    public void setPrestigeMultiplierPerLevel(double v) { this.prestigeMultiplierPerLevel = v; }

    public double getSkillPointBaseCost() { return skillPointBaseCost; }
    public void setSkillPointBaseCost(double v) { this.skillPointBaseCost = v; }

    public double getSkillPointCostGrowth() { return skillPointCostGrowth; }
    public void setSkillPointCostGrowth(double v) { this.skillPointCostGrowth = v; }

    public long getCollectCooldownMs() { return collectCooldownMs; }
    public void setCollectCooldownMs(long v) { this.collectCooldownMs = v; }

    public int getWageLedgerMaxEntries() { return wageLedgerMaxEntries; }
    public void setWageLedgerMaxEntries(int v) { this.wageLedgerMaxEntries = v; }

    public int getAfkTimeoutMinutes() { return afkTimeoutMinutes; }
    public void setAfkTimeoutMinutes(int v) { this.afkTimeoutMinutes = v; }

    public double getDebtInterestRate() { return debtInterestRate; }
    public void setDebtInterestRate(double v) { this.debtInterestRate = v; }

    public double getDebtInterestRateFloor() { return debtInterestRateFloor; }
    public void setDebtInterestRateFloor(double v) { this.debtInterestRateFloor = v; }

    public double getDebtLimitMultiplier() { return debtLimitMultiplier; }
    public void setDebtLimitMultiplier(double v) { this.debtLimitMultiplier = v; }
}

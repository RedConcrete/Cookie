package cookie.server.dto;

import java.util.List;

public class PlayerProfileDto {
    private String steamId;
    private int rank;
    private double netWorth;
    private double cookies;
    private double resourceValue;
    private double upgradeValue;
    private int prestigeLevel;
    private double lifetimeCookiesBaked;
    private List<UpgradeWithStatusDto> upgrades;
    private List<SeasonResultDto> seasonHistory;

    public String getSteamId() { return steamId; }
    public void setSteamId(String steamId) { this.steamId = steamId; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public double getNetWorth() { return netWorth; }
    public void setNetWorth(double netWorth) { this.netWorth = netWorth; }

    public double getCookies() { return cookies; }
    public void setCookies(double cookies) { this.cookies = cookies; }

    public double getResourceValue() { return resourceValue; }
    public void setResourceValue(double resourceValue) { this.resourceValue = resourceValue; }

    public double getUpgradeValue() { return upgradeValue; }
    public void setUpgradeValue(double upgradeValue) { this.upgradeValue = upgradeValue; }

    public int getPrestigeLevel() { return prestigeLevel; }
    public void setPrestigeLevel(int prestigeLevel) { this.prestigeLevel = prestigeLevel; }

    public double getLifetimeCookiesBaked() { return lifetimeCookiesBaked; }
    public void setLifetimeCookiesBaked(double lifetimeCookiesBaked) { this.lifetimeCookiesBaked = lifetimeCookiesBaked; }

    public List<UpgradeWithStatusDto> getUpgrades() { return upgrades; }
    public void setUpgrades(List<UpgradeWithStatusDto> upgrades) { this.upgrades = upgrades; }

    public List<SeasonResultDto> getSeasonHistory() { return seasonHistory; }
    public void setSeasonHistory(List<SeasonResultDto> seasonHistory) { this.seasonHistory = seasonHistory; }
}

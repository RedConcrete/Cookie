package cookie.server.dto;

public class LeaderboardEntryDto {
    private int rank;
    private String steamId;
    private double netWorth;
    private double cookies;
    private double resourceValue;
    private double upgradeValue;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public String getSteamId() { return steamId; }
    public void setSteamId(String steamId) { this.steamId = steamId; }

    public double getNetWorth() { return netWorth; }
    public void setNetWorth(double netWorth) { this.netWorth = netWorth; }

    public double getCookies() { return cookies; }
    public void setCookies(double cookies) { this.cookies = cookies; }

    public double getResourceValue() { return resourceValue; }
    public void setResourceValue(double resourceValue) { this.resourceValue = resourceValue; }

    public double getUpgradeValue() { return upgradeValue; }
    public void setUpgradeValue(double upgradeValue) { this.upgradeValue = upgradeValue; }
}

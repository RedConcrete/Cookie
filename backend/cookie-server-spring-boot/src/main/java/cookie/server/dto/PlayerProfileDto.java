package cookie.server.dto;

import java.util.List;

public class PlayerProfileDto {
    private String steamId;
    private String displayName;
    private String avatarUrl;
    private int rank;
    private double netWorth;
    private double cookies;
    private double resourceValue;
    private double skillTreeValue;
    private int prestigeLevel;
    private double lifetimeCookiesBaked;
    private List<SkillNodeStatusDto> skillNodes;
    private List<SeasonResultDto> seasonHistory;

    public String getSteamId() { return steamId; }
    public void setSteamId(String steamId) { this.steamId = steamId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public double getNetWorth() { return netWorth; }
    public void setNetWorth(double netWorth) { this.netWorth = netWorth; }

    public double getCookies() { return cookies; }
    public void setCookies(double cookies) { this.cookies = cookies; }

    public double getResourceValue() { return resourceValue; }
    public void setResourceValue(double resourceValue) { this.resourceValue = resourceValue; }

    public double getSkillTreeValue() { return skillTreeValue; }
    public void setSkillTreeValue(double skillTreeValue) { this.skillTreeValue = skillTreeValue; }

    public int getPrestigeLevel() { return prestigeLevel; }
    public void setPrestigeLevel(int prestigeLevel) { this.prestigeLevel = prestigeLevel; }

    public double getLifetimeCookiesBaked() { return lifetimeCookiesBaked; }
    public void setLifetimeCookiesBaked(double lifetimeCookiesBaked) { this.lifetimeCookiesBaked = lifetimeCookiesBaked; }

    public List<SkillNodeStatusDto> getSkillNodes() { return skillNodes; }
    public void setSkillNodes(List<SkillNodeStatusDto> skillNodes) { this.skillNodes = skillNodes; }

    public List<SeasonResultDto> getSeasonHistory() { return seasonHistory; }
    public void setSeasonHistory(List<SeasonResultDto> seasonHistory) { this.seasonHistory = seasonHistory; }
}

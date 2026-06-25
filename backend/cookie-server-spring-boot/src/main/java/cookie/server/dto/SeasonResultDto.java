package cookie.server.dto;

public class SeasonResultDto {
    private String seasonId;
    private String seasonName;
    private int finalRank;
    private double finalNetWorth;
    private int prestigeLevelAtEnd;

    public String getSeasonId() { return seasonId; }
    public void setSeasonId(String seasonId) { this.seasonId = seasonId; }

    public String getSeasonName() { return seasonName; }
    public void setSeasonName(String seasonName) { this.seasonName = seasonName; }

    public int getFinalRank() { return finalRank; }
    public void setFinalRank(int finalRank) { this.finalRank = finalRank; }

    public double getFinalNetWorth() { return finalNetWorth; }
    public void setFinalNetWorth(double finalNetWorth) { this.finalNetWorth = finalNetWorth; }

    public int getPrestigeLevelAtEnd() { return prestigeLevelAtEnd; }
    public void setPrestigeLevelAtEnd(int prestigeLevelAtEnd) { this.prestigeLevelAtEnd = prestigeLevelAtEnd; }
}

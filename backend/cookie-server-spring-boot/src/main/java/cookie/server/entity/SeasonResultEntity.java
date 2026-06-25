package cookie.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "season_results")
public class SeasonResultEntity {

    @Id
    private String id;

    private String seasonId;
    private String userId;
    private double finalNetWorth;
    private int finalRank;
    private int prestigeLevelAtEnd;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSeasonId() { return seasonId; }
    public void setSeasonId(String seasonId) { this.seasonId = seasonId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getFinalNetWorth() { return finalNetWorth; }
    public void setFinalNetWorth(double finalNetWorth) { this.finalNetWorth = finalNetWorth; }

    public int getFinalRank() { return finalRank; }
    public void setFinalRank(int finalRank) { this.finalRank = finalRank; }

    public int getPrestigeLevelAtEnd() { return prestigeLevelAtEnd; }
    public void setPrestigeLevelAtEnd(int prestigeLevelAtEnd) { this.prestigeLevelAtEnd = prestigeLevelAtEnd; }
}

package cookie.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_upgrades")
public class PlayerUpgradeEntity {

    @Id
    private String id; // userId + "#" + upgradeId

    private String userId;
    private String upgradeId;
    private int level;
    private double totalSpent;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUpgradeId() { return upgradeId; }
    public void setUpgradeId(String upgradeId) { this.upgradeId = upgradeId; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }
}

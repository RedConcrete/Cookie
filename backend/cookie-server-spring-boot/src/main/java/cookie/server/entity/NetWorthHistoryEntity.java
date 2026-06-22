package cookie.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "networth_history")
public class NetWorthHistoryEntity {

    @Id
    private String id;
    private String userId;
    private LocalDateTime timestamp;
    private double netWorth;
    private double cookies;
    private double resourceValue;
    private double upgradeValue;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public double getNetWorth() { return netWorth; }
    public void setNetWorth(double netWorth) { this.netWorth = netWorth; }

    public double getCookies() { return cookies; }
    public void setCookies(double cookies) { this.cookies = cookies; }

    public double getResourceValue() { return resourceValue; }
    public void setResourceValue(double resourceValue) { this.resourceValue = resourceValue; }

    public double getUpgradeValue() { return upgradeValue; }
    public void setUpgradeValue(double upgradeValue) { this.upgradeValue = upgradeValue; }
}

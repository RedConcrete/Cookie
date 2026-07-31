package cookie.server.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "player_buildings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "building_id"}))
public class PlayerBuildingEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "building_id")
    private String buildingId;

    @Column(name = "level")
    private int level;

    @Column(name = "workers")
    private int workers = 1;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getBuildingId() { return buildingId; }
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getWorkers() { return workers; }
    public void setWorkers(int workers) { this.workers = workers; }
}

package cookie.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "bake_jobs")
public class BakeJobEntity {

    @Id
    private String id;

    private String userId;
    private String recipeId;
    private int batches;
    private LocalDateTime startedAt;
    private LocalDateTime completesAt;
    private boolean claimed;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRecipeId() { return recipeId; }
    public void setRecipeId(String recipeId) { this.recipeId = recipeId; }

    public int getBatches() { return batches; }
    public void setBatches(int batches) { this.batches = batches; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletesAt() { return completesAt; }
    public void setCompletesAt(LocalDateTime completesAt) { this.completesAt = completesAt; }

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
}

package cookie.server.dto;

import java.time.LocalDateTime;

public class SeasonDto {
    private String id;
    private String name;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active;
    private int playerCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getPlayerCount() { return playerCount; }
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }
}

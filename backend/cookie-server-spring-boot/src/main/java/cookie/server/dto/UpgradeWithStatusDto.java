package cookie.server.dto;

public class UpgradeWithStatusDto {
    private String id;
    private String name;
    private String description;
    private String type;
    private String targetResource;
    private int currentLevel;
    private int maxLevel;
    private double nextLevelCost;
    private double effectPerLevel;
    private double totalSpent;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public int getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(int currentLevel) { this.currentLevel = currentLevel; }

    public int getMaxLevel() { return maxLevel; }
    public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }

    public double getNextLevelCost() { return nextLevelCost; }
    public void setNextLevelCost(double nextLevelCost) { this.nextLevelCost = nextLevelCost; }

    public double getEffectPerLevel() { return effectPerLevel; }
    public void setEffectPerLevel(double effectPerLevel) { this.effectPerLevel = effectPerLevel; }

    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }
}

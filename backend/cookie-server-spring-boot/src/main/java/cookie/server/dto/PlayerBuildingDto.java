package cookie.server.dto;

public class PlayerBuildingDto {
    private String id;
    private String name;
    private int level;
    private double nextLevelCost;
    private double wagePerMin;
    private int storageCapBonus;
    private boolean canUpgrade;
    private int workers;
    private int maxWorkers;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public double getNextLevelCost() { return nextLevelCost; }
    public void setNextLevelCost(double nextLevelCost) { this.nextLevelCost = nextLevelCost; }
    public double getWagePerMin() { return wagePerMin; }
    public void setWagePerMin(double wagePerMin) { this.wagePerMin = wagePerMin; }
    public int getStorageCapBonus() { return storageCapBonus; }
    public void setStorageCapBonus(int storageCapBonus) { this.storageCapBonus = storageCapBonus; }
    public boolean isCanUpgrade() { return canUpgrade; }
    public void setCanUpgrade(boolean canUpgrade) { this.canUpgrade = canUpgrade; }
    public int getWorkers() { return workers; }
    public void setWorkers(int workers) { this.workers = workers; }
    public int getMaxWorkers() { return maxWorkers; }
    public void setMaxWorkers(int maxWorkers) { this.maxWorkers = maxWorkers; }

    private double passiveRatePerTick; // amount produced per 5-second scheduler tick
    public double getPassiveRatePerTick() { return passiveRatePerTick; }
    public void setPassiveRatePerTick(double passiveRatePerTick) { this.passiveRatePerTick = passiveRatePerTick; }

    private boolean preBuilt;
    public boolean isPreBuilt() { return preBuilt; }
    public void setPreBuilt(boolean preBuilt) { this.preBuilt = preBuilt; }
}

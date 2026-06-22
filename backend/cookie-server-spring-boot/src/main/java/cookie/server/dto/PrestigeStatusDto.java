package cookie.server.dto;

public class PrestigeStatusDto {
    private int prestigeLevel;
    private int totalPrestiges;
    private double multiplier;
    private double currentNetWorth;
    private double threshold;
    private boolean canPrestige;

    public int getPrestigeLevel() { return prestigeLevel; }
    public void setPrestigeLevel(int prestigeLevel) { this.prestigeLevel = prestigeLevel; }

    public int getTotalPrestiges() { return totalPrestiges; }
    public void setTotalPrestiges(int totalPrestiges) { this.totalPrestiges = totalPrestiges; }

    public double getMultiplier() { return multiplier; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier; }

    public double getCurrentNetWorth() { return currentNetWorth; }
    public void setCurrentNetWorth(double currentNetWorth) { this.currentNetWorth = currentNetWorth; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public boolean isCanPrestige() { return canPrestige; }
    public void setCanPrestige(boolean canPrestige) { this.canPrestige = canPrestige; }
}

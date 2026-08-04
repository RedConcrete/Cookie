package cookie.server.dto;

public class UserInformationDto {
    private String steamId;
    private String displayName;
    private double cookies;
    private double sugar;
    private double flour;
    private double eggs;
    private double butter;
    private double chocolate;
    private double milk;
    private boolean workersIdle;
    private double totalResourceCap;

    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getCookies() {
        return cookies;
    }

    public void setCookies(double cookies) {
        this.cookies = cookies;
    }

    public double getSugar() {
        return sugar;
    }

    public void setSugar(double sugar) {
        this.sugar = sugar;
    }

    public double getFlour() {
        return flour;
    }

    public void setFlour(double flour) {
        this.flour = flour;
    }

    public double getEggs() {
        return eggs;
    }

    public void setEggs(double eggs) {
        this.eggs = eggs;
    }

    public double getButter() {
        return butter;
    }

    public void setButter(double butter) {
        this.butter = butter;
    }

    public double getChocolate() {
        return chocolate;
    }

    public void setChocolate(double chocolate) {
        this.chocolate = chocolate;
    }

    public double getMilk() {
        return milk;
    }

    public void setMilk(double milk) {
        this.milk = milk;
    }

    public boolean isWorkersIdle() { return workersIdle; }
    public void setWorkersIdle(boolean workersIdle) { this.workersIdle = workersIdle; }
    public double getTotalResourceCap() { return totalResourceCap; }
    public void setTotalResourceCap(double totalResourceCap) { this.totalResourceCap = totalResourceCap; }

    private int ownedCitizens;
    public int getOwnedCitizens() { return ownedCitizens; }
    public void setOwnedCitizens(int ownedCitizens) { this.ownedCitizens = ownedCitizens; }
}


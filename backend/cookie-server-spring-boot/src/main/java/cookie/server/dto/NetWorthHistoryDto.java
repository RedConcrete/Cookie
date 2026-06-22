package cookie.server.dto;

import cookie.server.entity.NetWorthHistoryEntity;

public class NetWorthHistoryDto {
    private String timestamp;
    private double netWorth;
    private double cookies;
    private double resourceValue;
    private double upgradeValue;

    public NetWorthHistoryDto(NetWorthHistoryEntity e) {
        this.timestamp     = e.getTimestamp().toString();
        this.netWorth      = e.getNetWorth();
        this.cookies       = e.getCookies();
        this.resourceValue = e.getResourceValue();
        this.upgradeValue  = e.getUpgradeValue();
    }

    public String getTimestamp()     { return timestamp; }
    public double getNetWorth()      { return netWorth; }
    public double getCookies()       { return cookies; }
    public double getResourceValue() { return resourceValue; }
    public double getUpgradeValue()  { return upgradeValue; }
}

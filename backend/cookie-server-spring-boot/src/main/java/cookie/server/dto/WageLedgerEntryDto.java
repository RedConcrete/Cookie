package cookie.server.dto;

import java.util.Map;

public class WageLedgerEntryDto {
    private String id;
    private long createdAtEpochMs;
    private double totalAmount;
    private Map<String, Double> breakdown;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
    public void setCreatedAtEpochMs(long createdAtEpochMs) { this.createdAtEpochMs = createdAtEpochMs; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public Map<String, Double> getBreakdown() { return breakdown; }
    public void setBreakdown(Map<String, Double> breakdown) { this.breakdown = breakdown; }
}

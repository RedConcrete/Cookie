package cookie.server.dto;

public class WageStatusDto {
    private double cookies;
    private double lastWageAmount;
    private long lastWageAtEpochMs;
    private double effectiveInterestRate;
    private double debtLimit;

    public double getCookies() { return cookies; }
    public void setCookies(double cookies) { this.cookies = cookies; }

    public double getLastWageAmount() { return lastWageAmount; }
    public void setLastWageAmount(double lastWageAmount) { this.lastWageAmount = lastWageAmount; }

    public long getLastWageAtEpochMs() { return lastWageAtEpochMs; }
    public void setLastWageAtEpochMs(long lastWageAtEpochMs) { this.lastWageAtEpochMs = lastWageAtEpochMs; }

    public double getEffectiveInterestRate() { return effectiveInterestRate; }
    public void setEffectiveInterestRate(double effectiveInterestRate) { this.effectiveInterestRate = effectiveInterestRate; }

    public double getDebtLimit() { return debtLimit; }
    public void setDebtLimit(double debtLimit) { this.debtLimit = debtLimit; }
}

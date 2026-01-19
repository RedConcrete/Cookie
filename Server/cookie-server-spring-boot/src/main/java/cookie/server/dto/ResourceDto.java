package cookie.server.dto;

import cookie.server.enums.ResourceName;

import java.time.LocalDateTime;

public class ResourceDto {
    private ResourceName name;
    private double amount;
    private LocalDateTime version;

    public ResourceName getName() {
        return name;
    }

    public void setName(ResourceName name) {
        this.name = name;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LocalDateTime getVersion() {
        return version;
    }

    public void setVersion(LocalDateTime version) {
        this.version = version;
    }
}

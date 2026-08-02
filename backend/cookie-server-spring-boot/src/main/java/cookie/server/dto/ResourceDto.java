package cookie.server.dto;

import cookie.server.enums.ResourceName;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public class ResourceDto {
    private ResourceName name;
    @Positive
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

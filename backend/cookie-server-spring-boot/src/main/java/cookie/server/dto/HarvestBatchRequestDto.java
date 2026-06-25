package cookie.server.dto;

import java.util.Map;

public class HarvestBatchRequestDto {
    private Map<String, Double> amounts; // resource name → amount

    public Map<String, Double> getAmounts() { return amounts; }
    public void setAmounts(Map<String, Double> amounts) { this.amounts = amounts; }
}

package cookie.server.entity;

import cookie.server.enums.UpgradeType;
import jakarta.persistence.*;

@Entity
@Table(name = "upgrades")
public class UpgradeEntity {

    @Id
    private String id;

    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private UpgradeType type;

    private String targetResource; // nur für AUTOMATION

    private double baseCost;
    private double effectPerLevel;
    private int maxLevel; // 0 = unbegrenzt

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UpgradeType getType() { return type; }
    public void setType(UpgradeType type) { this.type = type; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public double getBaseCost() { return baseCost; }
    public void setBaseCost(double baseCost) { this.baseCost = baseCost; }

    public double getEffectPerLevel() { return effectPerLevel; }
    public void setEffectPerLevel(double effectPerLevel) { this.effectPerLevel = effectPerLevel; }

    public int getMaxLevel() { return maxLevel; }
    public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }
}

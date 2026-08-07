package cookie.server.entity;

import cookie.server.enums.EffectType;
import jakarta.persistence.*;

@Entity
@Table(name = "skill_nodes")
public class SkillNodeEntity {

    @Id
    private String id;

    private String name;
    private String description;
    private String branch; // "MILK" | "BAKING" | "MARKET" | "CORE" -- drives UI grouping/color

    @Enumerated(EnumType.STRING)
    private EffectType effectType;

    private String targetResource; // nullable -- resource name for HARVEST_YIELD nodes, null = global
    private double effectValue;
    private boolean isRoot;
    private int x;
    private int y;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public EffectType getEffectType() { return effectType; }
    public void setEffectType(EffectType effectType) { this.effectType = effectType; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public double getEffectValue() { return effectValue; }
    public void setEffectValue(double effectValue) { this.effectValue = effectValue; }

    public boolean isRoot() { return isRoot; }
    public void setRoot(boolean root) { isRoot = root; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
}

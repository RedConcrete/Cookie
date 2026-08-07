package cookie.server.dto;

public class SkillNodeStatusDto {
    private String id;
    private String name;
    private String description;
    private String branch;
    private String effectType;
    private String targetResource;
    private double effectValue;
    private int x;
    private int y;
    private boolean isRoot;
    private boolean allocated;
    private boolean allocatable;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getEffectType() { return effectType; }
    public void setEffectType(String effectType) { this.effectType = effectType; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public double getEffectValue() { return effectValue; }
    public void setEffectValue(double effectValue) { this.effectValue = effectValue; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public boolean isRoot() { return isRoot; }
    public void setRoot(boolean root) { isRoot = root; }

    public boolean isAllocated() { return allocated; }
    public void setAllocated(boolean allocated) { this.allocated = allocated; }

    public boolean isAllocatable() { return allocatable; }
    public void setAllocatable(boolean allocatable) { this.allocatable = allocatable; }
}

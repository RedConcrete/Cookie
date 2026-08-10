package cookie.server.dto;

import java.util.List;

public class SkillNodeStatusDto {
    private String id;
    private String nameDe;
    private String nameEn;
    private String descriptionDe;
    private String descriptionEn;
    private String branch;
    private String nodeTier;
    private List<SkillEffectDto> effects;
    private int x;
    private int y;
    private boolean isRoot;
    private boolean allocated;
    private boolean allocatable;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNameDe() { return nameDe; }
    public void setNameDe(String nameDe) { this.nameDe = nameDe; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public String getDescriptionDe() { return descriptionDe; }
    public void setDescriptionDe(String descriptionDe) { this.descriptionDe = descriptionDe; }

    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getNodeTier() { return nodeTier; }
    public void setNodeTier(String nodeTier) { this.nodeTier = nodeTier; }

    public List<SkillEffectDto> getEffects() { return effects; }
    public void setEffects(List<SkillEffectDto> effects) { this.effects = effects; }

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

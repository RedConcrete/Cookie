package cookie.server.dto;

public class SkillEffectDto {
    private String effectType;
    private String targetResource;
    private double effectValue;

    public SkillEffectDto() {}

    public SkillEffectDto(String effectType, String targetResource, double effectValue) {
        this.effectType = effectType;
        this.targetResource = targetResource;
        this.effectValue = effectValue;
    }

    public String getEffectType() { return effectType; }
    public void setEffectType(String effectType) { this.effectType = effectType; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public double getEffectValue() { return effectValue; }
    public void setEffectValue(double effectValue) { this.effectValue = effectValue; }
}

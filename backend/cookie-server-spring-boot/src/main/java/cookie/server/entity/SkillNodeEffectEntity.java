package cookie.server.entity;

import jakarta.persistence.*;

// effectType bewusst reiner String (kein @Enumerated) -- jeder neue EffectType-Wert soll nie
// wieder eine Postgres-CHECK-Constraint reissen (siehe docs/plans/2026-08-10-open-skillbaum-wheel-keystones.md
// Abschnitt 2). Umwandlung Enum<->String passiert nur an den Raendern (SkillTreeService).
@Entity
@Table(name = "skill_node_effects")
public class SkillNodeEffectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side der Beziehung ist SkillNodeEntity.effects (@JoinColumn) -- dieses Feld ist nur
    // ein lesbarer Spiegel derselben Spalte, kein Schreibzugriff, sonst doppelte Column-Mapping.
    @Column(insertable = false, updatable = false)
    private String nodeId;
    private String effectType;
    private String targetResource; // nullable -- null = global
    private double effectValue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getEffectType() { return effectType; }
    public void setEffectType(String effectType) { this.effectType = effectType; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public double getEffectValue() { return effectValue; }
    public void setEffectValue(double effectValue) { this.effectValue = effectValue; }
}

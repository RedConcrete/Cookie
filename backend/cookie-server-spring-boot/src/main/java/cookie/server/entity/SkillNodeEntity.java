package cookie.server.entity;

import cookie.server.enums.NodeTier;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "skill_nodes")
public class SkillNodeEntity {

    @Id
    private String id;

    private String nameDe;
    private String nameEn;
    private String descriptionDe;
    private String descriptionEn;
    private String branch; // "MILK" | "BAKING" | "MARKET" | "CORE" | "DISPO" -- drives UI grouping/color

    @Enumerated(EnumType.STRING)
    private NodeTier nodeTier = NodeTier.PASSIVE;

    private boolean isRoot;
    private int x;
    private int y;

    // Normale Knoten: isAdjacentToAllocated() reicht EIN alloziierter Nachbar (OR). Cross-
    // Branch-Bruecken sollen dagegen ALLE eingehenden Kanten alloziert brauchen (AND) --
    // sonst waere "in beiden angrenzenden Branches vorgearbeitet" nicht erzwingbar. Default
    // false laesst das Verhalten fuer alle bestehenden 22 Knoten unveraendert.
    private boolean requiresAllPrereqs;

    // Unidirektionale Collection ueber die "nodeId"-Spalte in skill_node_effects -- kein
    // @ManyToOne-Rueckverweis im Kind noetig, Baum hat auch voll ausgebaut nur ~65 Knoten,
    // passt komplett eager in den bestehenden In-Memory-nodeCache (siehe SkillTreeService).
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "nodeId")
    private List<SkillNodeEffectEntity> effects = new ArrayList<>();

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

    public NodeTier getNodeTier() { return nodeTier; }
    public void setNodeTier(NodeTier nodeTier) { this.nodeTier = nodeTier; }

    public boolean isRoot() { return isRoot; }
    public void setRoot(boolean root) { isRoot = root; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public List<SkillNodeEffectEntity> getEffects() { return effects; }
    public void setEffects(List<SkillNodeEffectEntity> effects) { this.effects = effects; }

    public boolean isRequiresAllPrereqs() { return requiresAllPrereqs; }
    public void setRequiresAllPrereqs(boolean requiresAllPrereqs) { this.requiresAllPrereqs = requiresAllPrereqs; }
}

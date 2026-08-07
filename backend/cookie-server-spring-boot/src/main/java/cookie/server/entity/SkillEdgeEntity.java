package cookie.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "skill_edges")
public class SkillEdgeEntity {

    @Id
    private String id; // fromNode + "-" + toNode

    private String fromNode;
    private String toNode;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromNode() { return fromNode; }
    public void setFromNode(String fromNode) { this.fromNode = fromNode; }

    public String getToNode() { return toNode; }
    public void setToNode(String toNode) { this.toNode = toNode; }
}

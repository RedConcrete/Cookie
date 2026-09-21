package cookie.server.dto;

import cookie.server.entity.SkillEdgeEntity;
import cookie.server.entity.SkillNodeEntity;

import java.util.List;

// Voller Baum-Snapshot (Struktur, kein Spielerstand) fuer den Dev-Export/Import-Endpunkt --
// siehe docs/plans/2026-08-21-open-skillbaum-export-import-sharing.md, Feature 1.
public class SkillTreeExportDto {
    private List<SkillNodeEntity> nodes;
    private List<SkillEdgeEntity> edges;

    public List<SkillNodeEntity> getNodes() { return nodes; }
    public void setNodes(List<SkillNodeEntity> nodes) { this.nodes = nodes; }

    public List<SkillEdgeEntity> getEdges() { return edges; }
    public void setEdges(List<SkillEdgeEntity> edges) { this.edges = edges; }
}

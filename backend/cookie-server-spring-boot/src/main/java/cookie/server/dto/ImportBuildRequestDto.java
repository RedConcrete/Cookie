package cookie.server.dto;

import java.util.List;

// Spieler-Build-Code, clientseitig aus BuildShareDialog.vue decodiert -- siehe
// docs/plans/2026-08-21-done-skillbaum-export-import-sharing.md, Feature 2.
public class ImportBuildRequestDto {
    private List<String> nodeIds;

    public List<String> getNodeIds() { return nodeIds; }
    public void setNodeIds(List<String> nodeIds) { this.nodeIds = nodeIds; }
}

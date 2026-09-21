package cookie.server.dto;

import java.util.List;

// Antwort auf POST /api/v1/skilltree/import-build/{userId} -- sowohl fuer den Dry-Run
// (Vorschau, nichts geaendert) als auch den echten Import (Diff bereits angewendet).
// Siehe docs/plans/2026-08-21-open-skillbaum-export-import-sharing.md, Feature 2.
public class ImportBuildResultDto {
    private boolean dryRun;
    private List<String> unknownNodeIds;
    private int nodesAdded;
    private int nodesRemoved;
    private int pointsRefunded;
    private int pointsBought;
    private double respecCost;
    private double pointsCost;
    private double totalCost;
    private boolean canAfford;
    private SkillTreeDto tree;

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public List<String> getUnknownNodeIds() { return unknownNodeIds; }
    public void setUnknownNodeIds(List<String> unknownNodeIds) { this.unknownNodeIds = unknownNodeIds; }

    public int getNodesAdded() { return nodesAdded; }
    public void setNodesAdded(int nodesAdded) { this.nodesAdded = nodesAdded; }

    public int getNodesRemoved() { return nodesRemoved; }
    public void setNodesRemoved(int nodesRemoved) { this.nodesRemoved = nodesRemoved; }

    public int getPointsRefunded() { return pointsRefunded; }
    public void setPointsRefunded(int pointsRefunded) { this.pointsRefunded = pointsRefunded; }

    public int getPointsBought() { return pointsBought; }
    public void setPointsBought(int pointsBought) { this.pointsBought = pointsBought; }

    public double getRespecCost() { return respecCost; }
    public void setRespecCost(double respecCost) { this.respecCost = respecCost; }

    public double getPointsCost() { return pointsCost; }
    public void setPointsCost(double pointsCost) { this.pointsCost = pointsCost; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public boolean isCanAfford() { return canAfford; }
    public void setCanAfford(boolean canAfford) { this.canAfford = canAfford; }

    public SkillTreeDto getTree() { return tree; }
    public void setTree(SkillTreeDto tree) { this.tree = tree; }
}

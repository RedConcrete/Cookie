package cookie.server.dto;

import java.util.List;

public class SkillTreeDto {
    private List<SkillNodeStatusDto> nodes;
    private List<SkillEdgeDto> edges;
    private int skillPoints;
    private int totalSkillPointsBought;
    private double totalSkillPointCookiesSpent;
    private double nextPointCost;
    private double respecCostFlat;

    public List<SkillNodeStatusDto> getNodes() { return nodes; }
    public void setNodes(List<SkillNodeStatusDto> nodes) { this.nodes = nodes; }

    public List<SkillEdgeDto> getEdges() { return edges; }
    public void setEdges(List<SkillEdgeDto> edges) { this.edges = edges; }

    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int skillPoints) { this.skillPoints = skillPoints; }

    public int getTotalSkillPointsBought() { return totalSkillPointsBought; }
    public void setTotalSkillPointsBought(int totalSkillPointsBought) { this.totalSkillPointsBought = totalSkillPointsBought; }

    public double getTotalSkillPointCookiesSpent() { return totalSkillPointCookiesSpent; }
    public void setTotalSkillPointCookiesSpent(double totalSkillPointCookiesSpent) { this.totalSkillPointCookiesSpent = totalSkillPointCookiesSpent; }

    public double getNextPointCost() { return nextPointCost; }
    public void setNextPointCost(double nextPointCost) { this.nextPointCost = nextPointCost; }

    public double getRespecCostFlat() { return respecCostFlat; }
    public void setRespecCostFlat(double respecCostFlat) { this.respecCostFlat = respecCostFlat; }
}

package cookie.server.dto;

public class BakeStartRequestDto {
    private String recipeId;
    private int batches;

    public String getRecipeId() { return recipeId; }
    public void setRecipeId(String recipeId) { this.recipeId = recipeId; }

    public int getBatches() { return batches; }
    public void setBatches(int batches) { this.batches = batches; }
}

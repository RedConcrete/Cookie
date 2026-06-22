package cookie.server.dto;

public class BakeJobStatusDto {
    private boolean active;
    private String jobId;
    private RecipeDto recipe;
    private int batches;
    private double totalCookies;
    private long remainingSeconds;
    private boolean done;
    private boolean claimed;

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public RecipeDto getRecipe() { return recipe; }
    public void setRecipe(RecipeDto recipe) { this.recipe = recipe; }

    public int getBatches() { return batches; }
    public void setBatches(int batches) { this.batches = batches; }

    public double getTotalCookies() { return totalCookies; }
    public void setTotalCookies(double totalCookies) { this.totalCookies = totalCookies; }

    public long getRemainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; }

    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
}

package cookie.server.dto;

import cookie.server.entity.RecipeEntity;

public class RecipeDto {
    private String id;
    private String name;
    private double sugar;
    private double flour;
    private double eggs;
    private double butter;
    private double chocolate;
    private double milk;
    private double output;
    private int bakeDurationSeconds;

    public RecipeDto() {}

    public RecipeDto(RecipeEntity e) {
        this.id = e.getId();
        this.name = e.getName();
        this.sugar = e.getSugar();
        this.flour = e.getFlour();
        this.eggs = e.getEggs();
        this.butter = e.getButter();
        this.chocolate = e.getChocolate();
        this.milk = e.getMilk();
        this.output = e.getOutput();
        this.bakeDurationSeconds = e.getBakeDurationSeconds();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getSugar() { return sugar; }
    public double getFlour() { return flour; }
    public double getEggs() { return eggs; }
    public double getButter() { return butter; }
    public double getChocolate() { return chocolate; }
    public double getMilk() { return milk; }
    public double getOutput() { return output; }
    public int getBakeDurationSeconds() { return bakeDurationSeconds; }
}

package cookie.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipes")
public class RecipeEntity {

    @Id
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getSugar() { return sugar; }
    public void setSugar(double sugar) { this.sugar = sugar; }

    public double getFlour() { return flour; }
    public void setFlour(double flour) { this.flour = flour; }

    public double getEggs() { return eggs; }
    public void setEggs(double eggs) { this.eggs = eggs; }

    public double getButter() { return butter; }
    public void setButter(double butter) { this.butter = butter; }

    public double getChocolate() { return chocolate; }
    public void setChocolate(double chocolate) { this.chocolate = chocolate; }

    public double getMilk() { return milk; }
    public void setMilk(double milk) { this.milk = milk; }

    public double getOutput() { return output; }
    public void setOutput(double output) { this.output = output; }

    public int getBakeDurationSeconds() { return bakeDurationSeconds; }
    public void setBakeDurationSeconds(int bakeDurationSeconds) { this.bakeDurationSeconds = bakeDurationSeconds; }
}

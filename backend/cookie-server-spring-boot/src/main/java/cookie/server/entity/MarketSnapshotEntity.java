package cookie.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_snapshots")
public class MarketSnapshotEntity {

    @Id
    private String id;

    private LocalDateTime date;
    private double sugarPrice;
    private double flourPrice;
    private double eggsPrice;
    private double butterPrice;
    private double chocolatePrice;
    private double milkPrice;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public double getSugarPrice() { return sugarPrice; }
    public void setSugarPrice(double v) { this.sugarPrice = v; }

    public double getFlourPrice() { return flourPrice; }
    public void setFlourPrice(double v) { this.flourPrice = v; }

    public double getEggsPrice() { return eggsPrice; }
    public void setEggsPrice(double v) { this.eggsPrice = v; }

    public double getButterPrice() { return butterPrice; }
    public void setButterPrice(double v) { this.butterPrice = v; }

    public double getChocolatePrice() { return chocolatePrice; }
    public void setChocolatePrice(double v) { this.chocolatePrice = v; }

    public double getMilkPrice() { return milkPrice; }
    public void setMilkPrice(double v) { this.milkPrice = v; }
}

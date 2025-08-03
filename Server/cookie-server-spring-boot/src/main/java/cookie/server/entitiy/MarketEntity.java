package cookie.server.entitiy;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "market")
public class MarketEntity {

    @Id
    private String id;

    private LocalDateTime date;

    private double sugarPrice;
    private double flourPrice;
    private double eggsPrice;
    private double butterPrice;
    private double chocolatePrice;
    private double milkPrice;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public double getSugarPrice() {
        return sugarPrice;
    }

    public void setSugarPrice(double sugarPrice) {
        this.sugarPrice = sugarPrice;
    }

    public double getFlourPrice() {
        return flourPrice;
    }

    public void setFlourPrice(double flourPrice) {
        this.flourPrice = flourPrice;
    }

    public double getEggsPrice() {
        return eggsPrice;
    }

    public void setEggsPrice(double eggsPrice) {
        this.eggsPrice = eggsPrice;
    }

    public double getButterPrice() {
        return butterPrice;
    }

    public void setButterPrice(double butterPrice) {
        this.butterPrice = butterPrice;
    }

    public double getChocolatePrice() {
        return chocolatePrice;
    }

    public void setChocolatePrice(double chocolatePrice) {
        this.chocolatePrice = chocolatePrice;
    }

    public double getMilkPrice() {
        return milkPrice;
    }

    public void setMilkPrice(double milkPrice) {
        this.milkPrice = milkPrice;
    }
}
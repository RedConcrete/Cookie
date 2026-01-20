package cookie.server.entitiy;

import jakarta.persistence.*;

/**
 * Speichert den aktuellen Lagerbestand des Marktes.
 * Es gibt immer nur einen Eintrag (Singleton-Tabelle).
 */
@Entity
@Table(name = "market_stock")
public class MarketStockEntity {

    @Id
    private String id = "SINGLETON";

    private double sugarStock;
    private double flourStock;
    private double eggsStock;
    private double butterStock;
    private double chocolateStock;
    private double milkStock;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getSugarStock() {
        return sugarStock;
    }

    public void setSugarStock(double sugarStock) {
        this.sugarStock = sugarStock;
    }

    public double getFlourStock() {
        return flourStock;
    }

    public void setFlourStock(double flourStock) {
        this.flourStock = flourStock;
    }

    public double getEggsStock() {
        return eggsStock;
    }

    public void setEggsStock(double eggsStock) {
        this.eggsStock = eggsStock;
    }

    public double getButterStock() {
        return butterStock;
    }

    public void setButterStock(double butterStock) {
        this.butterStock = butterStock;
    }

    public double getChocolateStock() {
        return chocolateStock;
    }

    public void setChocolateStock(double chocolateStock) {
        this.chocolateStock = chocolateStock;
    }

    public double getMilkStock() {
        return milkStock;
    }

    public void setMilkStock(double milkStock) {
        this.milkStock = milkStock;
    }
}

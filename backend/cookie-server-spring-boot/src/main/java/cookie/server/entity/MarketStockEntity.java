package cookie.server.entity;

import jakarta.persistence.*;

/**
 * Speichert den aktuellen Lagerbestand des Marktes sowie eine geglättete
 * Baseline pro Ressource (EMA über ~1h), die als Rückfall-Ziel für die
 * Hintergrund-Fluktuation dient statt eines fixen Ausgangsbestands.
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

    private double sugarStockBaseline;
    private double flourStockBaseline;
    private double eggsStockBaseline;
    private double butterStockBaseline;
    private double chocolateStockBaseline;
    private double milkStockBaseline;

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

    public double getSugarStockBaseline() {
        return sugarStockBaseline;
    }

    public void setSugarStockBaseline(double sugarStockBaseline) {
        this.sugarStockBaseline = sugarStockBaseline;
    }

    public double getFlourStockBaseline() {
        return flourStockBaseline;
    }

    public void setFlourStockBaseline(double flourStockBaseline) {
        this.flourStockBaseline = flourStockBaseline;
    }

    public double getEggsStockBaseline() {
        return eggsStockBaseline;
    }

    public void setEggsStockBaseline(double eggsStockBaseline) {
        this.eggsStockBaseline = eggsStockBaseline;
    }

    public double getButterStockBaseline() {
        return butterStockBaseline;
    }

    public void setButterStockBaseline(double butterStockBaseline) {
        this.butterStockBaseline = butterStockBaseline;
    }

    public double getChocolateStockBaseline() {
        return chocolateStockBaseline;
    }

    public void setChocolateStockBaseline(double chocolateStockBaseline) {
        this.chocolateStockBaseline = chocolateStockBaseline;
    }

    public double getMilkStockBaseline() {
        return milkStockBaseline;
    }

    public void setMilkStockBaseline(double milkStockBaseline) {
        this.milkStockBaseline = milkStockBaseline;
    }
}

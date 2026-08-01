package cookie.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration für die Marktpreisberechnung.
 *
 * Preismodell: AMM (constant product, wie Uniswap). Jede Ressource ist ein Pool aus
 * Ressourcen-Reserve (Lagerbestand) gegen eine virtuelle Cookie-Reserve, beide multipliziert
 * ergeben die Konstante K = initialStock² × initialPrice. Preis = K / stock, leitet sich also
 * immer direkt aus dem aktuellen Bestand ab -- kein separater Preisdeckel/-boden noetig.
 * Handel bewegt den Stock direkt; der Zufalls-Tick bewegt den Stock um kleine Phantom-Betraege
 * (siehe stockFluctuationRatio), nicht den Preis selbst.
 */
@Configuration
@ConfigurationProperties(prefix = "market")
public class MarketConfig {

    /**
     * Intervall in Millisekunden für die zufällige Preisanpassung.
     * Standard: 2000ms (2 Sekunden)
     */
    private int updateIntervalMs = 2000;

    /**
     * Absolute Notbremse: minimaler Preis für alle Ressourcen (verhindert negative/Null-Preise
     * durch Rundungsfehler). Kein Gameplay-Deckel, nur numerische Sicherheit.
     * Standard: 0.01
     */
    private double minPrice = 0.01;

    /**
     * Anteil des Ausgangsbestands, der beim Zufalls-Tick maximal als Phantom-Kauf/-Verkauf
     * zwischen Markt und Pool bewegt wird (z.B. 0.02 = bis zu 2 % des Ausgangsbestands pro Tick).
     * Simuliert Hintergrund-Marktaktivitaet ohne echten Spieler dahinter.
     * Standard: 0.02
     */
    private double stockFluctuationRatio = 0.02;

    /**
     * Anteil des Verkaufserlöses, der als Gebühr vernichtet wird.
     * 0.05 = 5% Gebühr: Spieler erhält 95% des Marktpreises.
     */
    private double sellFeeRate = 0.05;

    /**
     * Startpreis für Sugar.
     */
    private double initialSugarPrice = 1.0;

    /**
     * Startpreis für Flour.
     */
    private double initialFlourPrice = 1.5;

    /**
     * Startpreis für Eggs.
     */
    private double initialEggsPrice = 2.0;

    /**
     * Startpreis für Butter.
     */
    private double initialButterPrice = 3.0;

    /**
     * Startpreis für Chocolate.
     */
    private double initialChocolatePrice = 5.0;

    /**
     * Startpreis für Milk.
     */
    private double initialMilkPrice = 1.2;

    /**
     * Anfangsbestand für Sugar im Markt.
     */
    private double initialSugarStock = 1000.0;

    /**
     * Anfangsbestand für Flour im Markt.
     */
    private double initialFlourStock = 1000.0;

    /**
     * Anfangsbestand für Eggs im Markt.
     */
    private double initialEggsStock = 1000.0;

    /**
     * Anfangsbestand für Butter im Markt.
     */
    private double initialButterStock = 1000.0;

    /**
     * Anfangsbestand für Chocolate im Markt.
     */
    private double initialChocolateStock = 1000.0;

    /**
     * Anfangsbestand für Milk im Markt.
     */
    private double initialMilkStock = 1000.0;

    // Getters and Setters

    public int getUpdateIntervalMs() {
        return updateIntervalMs;
    }

    public void setUpdateIntervalMs(int updateIntervalMs) {
        this.updateIntervalMs = updateIntervalMs;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(double minPrice) {
        this.minPrice = minPrice;
    }

    public double getStockFluctuationRatio() {
        return stockFluctuationRatio;
    }

    public void setStockFluctuationRatio(double stockFluctuationRatio) {
        this.stockFluctuationRatio = stockFluctuationRatio;
    }

    public double getSellFeeRate() {
        return sellFeeRate;
    }

    public void setSellFeeRate(double sellFeeRate) {
        this.sellFeeRate = sellFeeRate;
    }

    public double getInitialSugarPrice() {
        return initialSugarPrice;
    }

    public void setInitialSugarPrice(double initialSugarPrice) {
        this.initialSugarPrice = initialSugarPrice;
    }

    public double getInitialFlourPrice() {
        return initialFlourPrice;
    }

    public void setInitialFlourPrice(double initialFlourPrice) {
        this.initialFlourPrice = initialFlourPrice;
    }

    public double getInitialEggsPrice() {
        return initialEggsPrice;
    }

    public void setInitialEggsPrice(double initialEggsPrice) {
        this.initialEggsPrice = initialEggsPrice;
    }

    public double getInitialButterPrice() {
        return initialButterPrice;
    }

    public void setInitialButterPrice(double initialButterPrice) {
        this.initialButterPrice = initialButterPrice;
    }

    public double getInitialChocolatePrice() {
        return initialChocolatePrice;
    }

    public void setInitialChocolatePrice(double initialChocolatePrice) {
        this.initialChocolatePrice = initialChocolatePrice;
    }

    public double getInitialMilkPrice() {
        return initialMilkPrice;
    }

    public void setInitialMilkPrice(double initialMilkPrice) {
        this.initialMilkPrice = initialMilkPrice;
    }

    public double getInitialSugarStock() {
        return initialSugarStock;
    }

    public void setInitialSugarStock(double initialSugarStock) {
        this.initialSugarStock = initialSugarStock;
    }

    public double getInitialFlourStock() {
        return initialFlourStock;
    }

    public void setInitialFlourStock(double initialFlourStock) {
        this.initialFlourStock = initialFlourStock;
    }

    public double getInitialEggsStock() {
        return initialEggsStock;
    }

    public void setInitialEggsStock(double initialEggsStock) {
        this.initialEggsStock = initialEggsStock;
    }

    public double getInitialButterStock() {
        return initialButterStock;
    }

    public void setInitialButterStock(double initialButterStock) {
        this.initialButterStock = initialButterStock;
    }

    public double getInitialChocolateStock() {
        return initialChocolateStock;
    }

    public void setInitialChocolateStock(double initialChocolateStock) {
        this.initialChocolateStock = initialChocolateStock;
    }

    public double getInitialMilkStock() {
        return initialMilkStock;
    }

    public void setInitialMilkStock(double initialMilkStock) {
        this.initialMilkStock = initialMilkStock;
    }
}

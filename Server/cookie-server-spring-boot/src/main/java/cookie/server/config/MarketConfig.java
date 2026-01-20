package cookie.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration für die Marktpreisberechnung.
 *
 * Formel für Handelseinfluss: p_neu = p_alt ± (amount × totalResourceAmount) / tradeDivisor
 * Formel für Zufallsschwankung: P = p ± (random × totalResourceAmount) / randomDivisor
 */
@Configuration
@ConfigurationProperties(prefix = "market")
public class MarketConfig {

    /**
     * Divisor für den Handelseinfluss auf den Preis.
     * Je höher der Wert, desto geringer der Einfluss von Trades.
     * Standard: 10
     */
    private double tradeDivisor = 10.0;

    /**
     * Divisor für die zufällige Preisschwankung.
     * Je höher der Wert, desto geringer die Zufallsschwankungen.
     * Standard: 100
     */
    private double randomDivisor = 100.0;

    /**
     * Intervall in Millisekunden für die zufällige Preisanpassung.
     * Standard: 2000ms (2 Sekunden)
     */
    private int updateIntervalMs = 2000;

    /**
     * Minimaler Preis für alle Ressourcen (verhindert negative Preise).
     * Standard: 0.01
     */
    private double minPrice = 0.01;

    /**
     * Maximaler Preis für alle Ressourcen.
     * Standard: 10000.0
     */
    private double maxPrice = 10000.0;

    /**
     * Multiplikator für den Handelseinfluss.
     * Höhere Werte = stärkerer Preiseinfluss durch Trades.
     * Standard: 1.0
     */
    private double tradeImpactMultiplier = 1.0;

    /**
     * Multiplikator für die Zufallsschwankung.
     * Höhere Werte = stärkere zufällige Preisbewegungen.
     * Standard: 1.0
     */
    private double randomImpactMultiplier = 1.0;

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

    public double getTradeDivisor() {
        return tradeDivisor;
    }

    public void setTradeDivisor(double tradeDivisor) {
        this.tradeDivisor = tradeDivisor;
    }

    public double getRandomDivisor() {
        return randomDivisor;
    }

    public void setRandomDivisor(double randomDivisor) {
        this.randomDivisor = randomDivisor;
    }

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

    public double getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(double maxPrice) {
        this.maxPrice = maxPrice;
    }

    public double getTradeImpactMultiplier() {
        return tradeImpactMultiplier;
    }

    public void setTradeImpactMultiplier(double tradeImpactMultiplier) {
        this.tradeImpactMultiplier = tradeImpactMultiplier;
    }

    public double getRandomImpactMultiplier() {
        return randomImpactMultiplier;
    }

    public void setRandomImpactMultiplier(double randomImpactMultiplier) {
        this.randomImpactMultiplier = randomImpactMultiplier;
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

package cookie.server.dto;

import cookie.server.entity.MarketEntity;

import java.time.LocalDateTime;

public class MarketDto {
    private LocalDateTime date;
    private double sugarPrice;
    private double flourPrice;
    private double eggsPrice;
    private double butterPrice;
    private double chocolatePrice;
    private double milkPrice;

    public MarketDto(MarketEntity market) {
        this.date = market.getDate();
        this.sugarPrice = market.getSugarPrice();
        this.flourPrice = market.getFlourPrice();
        this.eggsPrice = market.getEggsPrice();
        this.butterPrice = market.getButterPrice();
        this.chocolatePrice = market.getChocolatePrice();
        this.milkPrice = market.getMilkPrice();
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

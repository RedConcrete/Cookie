package cookie.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration fuer Spieler-Startwerte.
 */
@Configuration
@ConfigurationProperties(prefix = "player")
public class PlayerConfig {

    private double initialCookies = 100.0;
    private double initialSugar = 0.0;
    private double initialFlour = 0.0;
    private double initialEggs = 0.0;
    private double initialButter = 0.0;
    private double initialChocolate = 0.0;
    private double initialMilk = 0.0;

    public double getInitialCookies() {
        return initialCookies;
    }

    public void setInitialCookies(double initialCookies) {
        this.initialCookies = initialCookies;
    }

    public double getInitialSugar() {
        return initialSugar;
    }

    public void setInitialSugar(double initialSugar) {
        this.initialSugar = initialSugar;
    }

    public double getInitialFlour() {
        return initialFlour;
    }

    public void setInitialFlour(double initialFlour) {
        this.initialFlour = initialFlour;
    }

    public double getInitialEggs() {
        return initialEggs;
    }

    public void setInitialEggs(double initialEggs) {
        this.initialEggs = initialEggs;
    }

    public double getInitialButter() {
        return initialButter;
    }

    public void setInitialButter(double initialButter) {
        this.initialButter = initialButter;
    }

    public double getInitialChocolate() {
        return initialChocolate;
    }

    public void setInitialChocolate(double initialChocolate) {
        this.initialChocolate = initialChocolate;
    }

    public double getInitialMilk() {
        return initialMilk;
    }

    public void setInitialMilk(double initialMilk) {
        this.initialMilk = initialMilk;
    }
}

package cookie.server.scheduler;

import cookie.server.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MarketScheduler.class);

    private final MarketService marketService;

    public MarketScheduler(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * Führt periodisch zufällige Preisschwankungen durch.
     * Das Intervall wird in application.properties konfiguriert (in Millisekunden).
     */
    @Scheduled(fixedRateString = "${market.update-interval-ms:2000}")
    public void updateMarketPrices() {
        logger.debug("Applying random market price fluctuations...");
        marketService.applyRandomPriceFluctuation();
        logger.debug("Market prices updated.");
    }
}

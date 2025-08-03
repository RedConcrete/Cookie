package cookie.server.service;

import cookie.server.entitiy.MarketEntity;
import cookie.server.repository.MarketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MarketGenService {

    private final MarketRepository marketRepository;

    @Autowired
    public MarketGenService(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @Scheduled(fixedRate = 30000)
    public void generateMarket() {
        MarketEntity market = new MarketEntity();

        market.setId(UUID.randomUUID().toString());
        market.setDate(LocalDateTime.now());

        marketRepository.save(market);
    }
}

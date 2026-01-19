package cookie.server.service;

import cookie.server.config.MarketConfig;
import cookie.server.dto.MarketDto;
import cookie.server.dto.MarketRequestDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entitiy.MarketEntity;
import cookie.server.entitiy.UserEntity;
import cookie.server.enums.MarketAction;
import cookie.server.enums.ResourceName;
import cookie.server.repository.MarketRepository;
import cookie.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class MarketService {
    private static final Logger logger = LoggerFactory.getLogger(MarketService.class);

    private final MarketRepository marketRepository;
    private final UserRepository userRepository;
    private final MarketConfig marketConfig;

    // Lock fuer Thread-Sicherheit bei Market-Operationen
    private final ReentrantLock marketLock = new ReentrantLock();

    // Zaehler fuer Cleanup - nur alle 100 Updates ausfuehren
    private final AtomicInteger updateCounter = new AtomicInteger(0);
    private static final int CLEANUP_INTERVAL = 100;

    public MarketService(MarketRepository marketRepository, UserRepository userRepository, MarketConfig marketConfig) {
        this.marketRepository = marketRepository;
        this.userRepository = userRepository;
        this.marketConfig = marketConfig;
    }

    public List<MarketDto> getMarketData(int amount) {
        return marketRepository.findAllByOrderByDateDesc(PageRequest.of(0, amount))
                .stream()
                .map(MarketDto::new)
                .collect(Collectors.toList());
    }

    public List<MarketDto> getAllMarketData() {
        return marketRepository.findAllByOrderByDateDesc(PageRequest.of(0, 500))
                .stream()
                .map(MarketDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserInformationDto performAction(MarketRequestDto request) {
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User not found: " + request.getUserId()));

        MarketEntity currentMarket = getOrCreateCurrentMarket();
        ResourceName resource = request.getResource().getName();
        double amount = request.getResource().getAmount();
        MarketAction action = request.getAction();

        double currentPrice = getPrice(currentMarket, resource);
        double totalCost = currentPrice * amount;

        if (action == MarketAction.BUY) {
            if (user.getCookies() < totalCost) {
                throw new IllegalArgumentException("Not enough cookies. Need: " + totalCost + ", Have: " + user.getCookies());
            }
            user.setCookies(user.getCookies() - totalCost);
            addResourceToUser(user, resource, amount);
        } else if (action == MarketAction.SELL) {
            double userAmount = getResourceFromUser(user, resource);
            if (userAmount < amount) {
                throw new IllegalArgumentException("Not enough " + resource + ". Need: " + amount + ", Have: " + userAmount);
            }
            addResourceToUser(user, resource, -amount);
            user.setCookies(user.getCookies() + totalCost);
        }

        userRepository.save(user);

        // Preis nach dem Trade anpassen - erstellt neuen Eintrag
        createNewMarketEntryAfterTrade(currentMarket, resource, amount, action);

        return toDto(user);
    }

    /**
     * Erstellt einen neuen Markteintrag nach einem Trade.
     */
    private void createNewMarketEntryAfterTrade(MarketEntity currentMarket, ResourceName resource, double amount, MarketAction action) {
        marketLock.lock();
        try {
            double totalResource = getTotalResourceAmount(resource);
            double currentPrice = getPrice(currentMarket, resource);

            double priceChange = (amount * Math.max(totalResource, 1)) / marketConfig.getTradeDivisor();
            priceChange *= marketConfig.getTradeImpactMultiplier();

            double newPrice;
            if (action == MarketAction.BUY) {
                newPrice = currentPrice + priceChange;
            } else {
                newPrice = currentPrice - priceChange;
            }

            newPrice = clampPrice(newPrice);

            MarketEntity newMarket = copyMarketWithNewId(currentMarket);
            setPrice(newMarket, resource, newPrice);
            newMarket.setDate(LocalDateTime.now());

            // Validierung vor dem Speichern
            if (isValidMarket(newMarket)) {
                marketRepository.save(newMarket);
            } else {
                logger.warn("Invalid market entry detected after trade, skipping save");
            }
        } finally {
            marketLock.unlock();
        }
    }

    /**
     * Wendet zufaellige Preisschwankungen auf alle Ressourcen an.
     */
    @Transactional
    public void applyRandomPriceFluctuation() {
        marketLock.lock();
        try {
            MarketEntity currentMarket = getOrCreateCurrentMarket();

            // Validiere dass currentMarket gueltige Preise hat
            if (!isValidMarket(currentMarket)) {
                logger.warn("Current market has invalid prices, reinitializing...");
                currentMarket = createInitialMarket();
            }

            MarketEntity newMarket = copyMarketWithNewId(currentMarket);

            for (ResourceName resource : ResourceName.values()) {
                double totalResource = getTotalResourceAmount(resource);
                double currentPrice = getPrice(currentMarket, resource);

                // Falls currentPrice 0 ist, nutze den Initialpreis
                if (currentPrice <= 0) {
                    currentPrice = getInitialPrice(resource);
                }

                double random = (Math.random() * 2) - 1;
                double priceChange = (random * Math.max(totalResource, 1)) / marketConfig.getRandomDivisor();
                priceChange *= marketConfig.getRandomImpactMultiplier();

                double newPrice = clampPrice(currentPrice + priceChange);
                setPrice(newMarket, resource, newPrice);
            }

            newMarket.setDate(LocalDateTime.now());

            // Validierung vor dem Speichern
            if (isValidMarket(newMarket)) {
                marketRepository.save(newMarket);
            } else {
                logger.error("Invalid market entry detected, creating fresh initial market");
                createInitialMarket();
            }

            // Cleanup nur alle CLEANUP_INTERVAL Updates
            if (updateCounter.incrementAndGet() >= CLEANUP_INTERVAL) {
                updateCounter.set(0);
                cleanupOldEntries(500);
            }
        } finally {
            marketLock.unlock();
        }
    }

    /**
     * Prueft ob ein MarketEntity gueltige Preise hat (keine 0-Werte).
     */
    private boolean isValidMarket(MarketEntity market) {
        return market != null &&
                market.getSugarPrice() > 0 &&
                market.getFlourPrice() > 0 &&
                market.getEggsPrice() > 0 &&
                market.getButterPrice() > 0 &&
                market.getChocolatePrice() > 0 &&
                market.getMilkPrice() > 0;
    }

    /**
     * Gibt den Initial-Preis fuer eine Ressource zurueck.
     */
    private double getInitialPrice(ResourceName resource) {
        return switch (resource) {
            case SUGAR -> marketConfig.getInitialSugarPrice();
            case FLOUR -> marketConfig.getInitialFlourPrice();
            case EGGS -> marketConfig.getInitialEggsPrice();
            case BUTTER -> marketConfig.getInitialButterPrice();
            case CHOCOLATE -> marketConfig.getInitialChocolatePrice();
            case MILK -> marketConfig.getInitialMilkPrice();
        };
    }

    /**
     * Erstellt eine Kopie des MarketEntity mit neuer ID.
     */
    private MarketEntity copyMarketWithNewId(MarketEntity source) {
        MarketEntity copy = new MarketEntity();
        copy.setId(UUID.randomUUID().toString());

        // Kopiere Preise, aber nutze Initialpreise falls Source 0 hat
        copy.setSugarPrice(source.getSugarPrice() > 0 ? source.getSugarPrice() : marketConfig.getInitialSugarPrice());
        copy.setFlourPrice(source.getFlourPrice() > 0 ? source.getFlourPrice() : marketConfig.getInitialFlourPrice());
        copy.setEggsPrice(source.getEggsPrice() > 0 ? source.getEggsPrice() : marketConfig.getInitialEggsPrice());
        copy.setButterPrice(source.getButterPrice() > 0 ? source.getButterPrice() : marketConfig.getInitialButterPrice());
        copy.setChocolatePrice(source.getChocolatePrice() > 0 ? source.getChocolatePrice() : marketConfig.getInitialChocolatePrice());
        copy.setMilkPrice(source.getMilkPrice() > 0 ? source.getMilkPrice() : marketConfig.getInitialMilkPrice());

        return copy;
    }

    /**
     * Begrenzt den Preis auf min/max Werte.
     */
    private double clampPrice(double price) {
        return Math.max(marketConfig.getMinPrice(), Math.min(marketConfig.getMaxPrice(), price));
    }

    /**
     * Entfernt alte Markteintraege, behaelt nur die neuesten.
     */
    @Transactional
    protected void cleanupOldEntries(int keepCount) {
        try {
            long totalCount = marketRepository.count();
            if (totalCount > keepCount) {
                List<MarketEntity> allEntries = marketRepository.findAllByOrderByDateDesc(
                        PageRequest.of(0, (int) totalCount));
                if (allEntries.size() > keepCount) {
                    List<MarketEntity> toDelete = allEntries.subList(keepCount, allEntries.size());
                    marketRepository.deleteAll(toDelete);
                    logger.debug("Cleaned up {} old market entries", toDelete.size());
                }
            }
        } catch (Exception e) {
            logger.error("Error during cleanup: {}", e.getMessage());
        }
    }

    public MarketEntity getOrCreateCurrentMarket() {
        List<MarketEntity> markets = marketRepository.findAllByOrderByDateDesc(PageRequest.of(0, 1));
        if (markets.isEmpty()) {
            return createInitialMarket();
        }

        MarketEntity latest = markets.get(0);
        // Falls der letzte Eintrag ungueltig ist, erstelle neuen
        if (!isValidMarket(latest)) {
            logger.warn("Latest market entry is invalid, creating new initial market");
            return createInitialMarket();
        }

        return latest;
    }

    private MarketEntity createInitialMarket() {
        MarketEntity market = new MarketEntity();
        market.setId(UUID.randomUUID().toString());
        market.setDate(LocalDateTime.now());
        market.setSugarPrice(marketConfig.getInitialSugarPrice());
        market.setFlourPrice(marketConfig.getInitialFlourPrice());
        market.setEggsPrice(marketConfig.getInitialEggsPrice());
        market.setButterPrice(marketConfig.getInitialButterPrice());
        market.setChocolatePrice(marketConfig.getInitialChocolatePrice());
        market.setMilkPrice(marketConfig.getInitialMilkPrice());

        logger.info("Created initial market with prices: Sugar={}, Flour={}, Eggs={}, Butter={}, Chocolate={}, Milk={}",
                market.getSugarPrice(), market.getFlourPrice(), market.getEggsPrice(),
                market.getButterPrice(), market.getChocolatePrice(), market.getMilkPrice());

        return marketRepository.save(market);
    }

    private double getPrice(MarketEntity market, ResourceName resource) {
        double price = switch (resource) {
            case SUGAR -> market.getSugarPrice();
            case FLOUR -> market.getFlourPrice();
            case EGGS -> market.getEggsPrice();
            case BUTTER -> market.getButterPrice();
            case CHOCOLATE -> market.getChocolatePrice();
            case MILK -> market.getMilkPrice();
        };
        // Fallback auf Initialpreis wenn 0
        return price > 0 ? price : getInitialPrice(resource);
    }

    private void setPrice(MarketEntity market, ResourceName resource, double price) {
        // Stelle sicher dass Preis nie 0 oder negativ ist
        double safePrice = Math.max(marketConfig.getMinPrice(), price);
        switch (resource) {
            case SUGAR -> market.setSugarPrice(safePrice);
            case FLOUR -> market.setFlourPrice(safePrice);
            case EGGS -> market.setEggsPrice(safePrice);
            case BUTTER -> market.setButterPrice(safePrice);
            case CHOCOLATE -> market.setChocolatePrice(safePrice);
            case MILK -> market.setMilkPrice(safePrice);
        }
    }

    private double getTotalResourceAmount(ResourceName resource) {
        return switch (resource) {
            case SUGAR -> userRepository.getTotalSugar();
            case FLOUR -> userRepository.getTotalFlour();
            case EGGS -> userRepository.getTotalEggs();
            case BUTTER -> userRepository.getTotalButter();
            case CHOCOLATE -> userRepository.getTotalChocolate();
            case MILK -> userRepository.getTotalMilk();
        };
    }

    private double getResourceFromUser(UserEntity user, ResourceName resource) {
        return switch (resource) {
            case SUGAR -> user.getSugar();
            case FLOUR -> user.getFlour();
            case EGGS -> user.getEggs();
            case BUTTER -> user.getButter();
            case CHOCOLATE -> user.getChocolate();
            case MILK -> user.getMilk();
        };
    }

    private void addResourceToUser(UserEntity user, ResourceName resource, double amount) {
        switch (resource) {
            case SUGAR -> user.setSugar(user.getSugar() + amount);
            case FLOUR -> user.setFlour(user.getFlour() + amount);
            case EGGS -> user.setEggs(user.getEggs() + amount);
            case BUTTER -> user.setButter(user.getButter() + amount);
            case CHOCOLATE -> user.setChocolate(user.getChocolate() + amount);
            case MILK -> user.setMilk(user.getMilk() + amount);
        }
    }

    private UserInformationDto toDto(UserEntity entity) {
        UserInformationDto dto = new UserInformationDto();
        dto.setSteamId(entity.getSteamId());
        dto.setCookies(entity.getCookies());
        dto.setSugar(entity.getSugar());
        dto.setFlour(entity.getFlour());
        dto.setEggs(entity.getEggs());
        dto.setButter(entity.getButter());
        dto.setChocolate(entity.getChocolate());
        dto.setMilk(entity.getMilk());
        return dto;
    }
}

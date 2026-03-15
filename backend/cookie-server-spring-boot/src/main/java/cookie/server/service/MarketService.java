package cookie.server.service;

import cookie.server.config.MarketConfig;
import cookie.server.dto.MarketDto;
import cookie.server.dto.MarketRequestDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entity.MarketEntity;
import cookie.server.entity.MarketStockEntity;
import cookie.server.entity.UserEntity;
import cookie.server.enums.MarketAction;
import cookie.server.enums.ResourceName;
import cookie.server.repository.MarketRepository;
import cookie.server.repository.MarketStockRepository;
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
    private final MarketStockRepository marketStockRepository;
    private final UserRepository userRepository;
    private final MarketConfig marketConfig;
    private final cookie.server.handler.MarketWebSocketHandler webSocketHandler;

    // Lock fuer Thread-Sicherheit bei Market-Operationen
    private final ReentrantLock marketLock = new ReentrantLock();

    // Zaehler fuer Cleanup - nur alle 100 Updates ausfuehren
    private final AtomicInteger updateCounter = new AtomicInteger(0);
    private static final int CLEANUP_INTERVAL = 100;

    private static final String STOCK_SINGLETON_ID = "SINGLETON";

    public MarketService(MarketRepository marketRepository, MarketStockRepository marketStockRepository,
                         UserRepository userRepository, MarketConfig marketConfig,
                         cookie.server.handler.MarketWebSocketHandler webSocketHandler) {
        this.marketRepository = marketRepository;
        this.marketStockRepository = marketStockRepository;
        this.userRepository = userRepository;
        this.marketConfig = marketConfig;
        this.webSocketHandler = webSocketHandler;
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
        MarketStockEntity stock = getOrCreateMarketStock();
        ResourceName resource = request.getResource().getName();
        double amount = request.getResource().getAmount();
        MarketAction action = request.getAction();

        double currentPrice = getPrice(currentMarket, resource);
        double totalCost = currentPrice * amount;

        if (action == MarketAction.BUY) {
            // Pruefen ob der Markt genug auf Lager hat
            double marketStock = getStock(stock, resource);
            if (marketStock < amount) {
                throw new IllegalArgumentException("Market out of stock for " + resource + ". Available: " + marketStock + ", Requested: " + amount);
            }
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

        // Preis nach dem Trade anpassen und Stock aktualisieren
        createNewMarketEntryAfterTrade(currentMarket, stock, resource, amount, action);

        return toDto(user);
    }

    /**
     * Erstellt einen neuen Markteintrag nach einem Trade.
     * Aktualisiert sowohl Preis als auch Lagerbestand.
     */
    private void createNewMarketEntryAfterTrade(MarketEntity currentMarket, MarketStockEntity stock, ResourceName resource, double amount, MarketAction action) {
        marketLock.lock();
        try {
            double marketStock = getStock(stock, resource);
            double currentPrice = getPrice(currentMarket, resource);

            // Preisaenderung: Je mehr Stock vorhanden, desto geringer die Preisaenderung
            // Formel: (amount / stock) * price * multiplier
            // Bei niedrigem Stock -> grosse Preisaenderung
            // Bei hohem Stock -> kleine Preisaenderung
            double priceChange = (amount / Math.max(marketStock, 1)) * currentPrice * marketConfig.getTradeImpactMultiplier();

            double newPrice;
            double newStock;
            if (action == MarketAction.BUY) {
                // Spieler kauft -> Preis steigt, Markt-Stock sinkt
                newPrice = currentPrice + priceChange;
                newStock = marketStock - amount;
            } else {
                // Spieler verkauft -> Preis sinkt, Markt-Stock steigt
                newPrice = currentPrice - priceChange;
                newStock = marketStock + amount;
            }

            newPrice = clampPrice(newPrice);
            newStock = Math.max(0, newStock);

            // Neuen Markteintrag fuer Preis-Historie erstellen
            MarketEntity newMarket = copyMarketWithNewId(currentMarket);
            setPrice(newMarket, resource, newPrice);
            newMarket.setDate(LocalDateTime.now());

            // Stock in separater Tabelle aktualisieren (ueberschreibt sich selbst)
            setStock(stock, resource, newStock);
            marketStockRepository.save(stock);

            // Validierung vor dem Speichern
            if (isValidMarket(newMarket)) {
                marketRepository.save(newMarket);
            } else {
                logger.warn("Invalid market entry detected after trade, skipping save");
            }
            
            // Broadcast update
            webSocketHandler.broadcastMarketUpdate(getMarketData(20));

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
            MarketStockEntity stock = getOrCreateMarketStock();

            // Validiere dass currentMarket gueltige Preise hat
            if (!isValidMarket(currentMarket)) {
                logger.warn("Current market has invalid prices, reinitializing...");
                currentMarket = createInitialMarket();
            }

            MarketEntity newMarket = copyMarketWithNewId(currentMarket);

            for (ResourceName resource : ResourceName.values()) {
                double marketStock = getStock(stock, resource);
                double currentPrice = getPrice(currentMarket, resource);

                // Falls currentPrice 0 ist, nutze den Initialpreis
                if (currentPrice <= 0) {
                    currentPrice = getInitialPrice(resource);
                }

                // Preisschwankung: Je weniger Stock, desto volatiler der Preis
                // Bei niedrigem Stock -> groessere Schwankungen
                // Bei hohem Stock -> kleinere Schwankungen
                double random = (Math.random() * 2) - 1;
                double priceChange = (random / Math.max(marketStock, 1)) * currentPrice * marketConfig.getRandomImpactMultiplier() * marketConfig.getRandomDivisor();

                double newPrice = clampPrice(currentPrice + priceChange);
                setPrice(newMarket, resource, newPrice);
                // Stock bleibt gleich bei zufaelliger Preisschwankung
            }

            newMarket.setDate(LocalDateTime.now());

            // Validierung vor dem Speichern
            if (isValidMarket(newMarket)) {
                marketRepository.save(newMarket);
            } else {
                logger.error("Invalid market entry detected, creating fresh initial market");
                createInitialMarket();
            }

            if (updateCounter.incrementAndGet() >= CLEANUP_INTERVAL) {
                updateCounter.set(0);
                cleanupOldEntries(500);
            }
            
            // Broadcast update
            webSocketHandler.broadcastMarketUpdate(getMarketData(20));
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
     * Stock wird nicht kopiert - dieser ist in separater Tabelle.
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

    /**
     * Holt oder erstellt den MarketStock Singleton-Eintrag.
     */
    private MarketStockEntity getOrCreateMarketStock() {
        return marketStockRepository.findById(STOCK_SINGLETON_ID)
                .orElseGet(this::createInitialMarketStock);
    }

    /**
     * Erstellt den initialen MarketStock Eintrag.
     */
    private MarketStockEntity createInitialMarketStock() {
        MarketStockEntity stock = new MarketStockEntity();
        stock.setSugarStock(marketConfig.getInitialSugarStock());
        stock.setFlourStock(marketConfig.getInitialFlourStock());
        stock.setEggsStock(marketConfig.getInitialEggsStock());
        stock.setButterStock(marketConfig.getInitialButterStock());
        stock.setChocolateStock(marketConfig.getInitialChocolateStock());
        stock.setMilkStock(marketConfig.getInitialMilkStock());

        logger.info("Created initial market stock: Sugar={}, Flour={}, Eggs={}, Butter={}, Chocolate={}, Milk={}",
                stock.getSugarStock(), stock.getFlourStock(), stock.getEggsStock(),
                stock.getButterStock(), stock.getChocolateStock(), stock.getMilkStock());

        return marketStockRepository.save(stock);
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

    private double getStock(MarketStockEntity stock, ResourceName resource) {
        return switch (resource) {
            case SUGAR -> stock.getSugarStock();
            case FLOUR -> stock.getFlourStock();
            case EGGS -> stock.getEggsStock();
            case BUTTER -> stock.getButterStock();
            case CHOCOLATE -> stock.getChocolateStock();
            case MILK -> stock.getMilkStock();
        };
    }

    private void setStock(MarketStockEntity stock, ResourceName resource, double amount) {
        double safeStock = Math.max(0, amount);
        switch (resource) {
            case SUGAR -> stock.setSugarStock(safeStock);
            case FLOUR -> stock.setFlourStock(safeStock);
            case EGGS -> stock.setEggsStock(safeStock);
            case BUTTER -> stock.setButterStock(safeStock);
            case CHOCOLATE -> stock.setChocolateStock(safeStock);
            case MILK -> stock.setMilkStock(safeStock);
        }
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
